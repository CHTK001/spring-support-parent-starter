package com.chua.starter.pay.support.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.handler.PayOrderRefundCreator;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PayRefundStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 退款
 * @author CH
 * @since 2024/12/30
 */
public class RefundOrder {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantMapper payMerchantMapper;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public RefundOrder(TransactionTemplate transactionTemplate, PayMerchantMapper payMerchantMapper, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantMapper = payMerchantMapper;
        this.payMerchantOrderMapper = payMerchantOrderMapper;
    }

    /**
     * 退款
     * @param refundRequest 退款请求
     * @return 退款结果
     */
    public ReturnResult<PayRefundResponse> update(PayRefundRequest refundRequest) {
        PayMerchantOrder payMerchantOrder = payMerchantOrderMapper.selectOne(Wrappers.<PayMerchantOrder>lambdaQuery().eq(PayMerchantOrder::getPayMerchantOrderCode, refundRequest.getPayMerchantOrderCode()));
        if(null == payMerchantOrder) {
            return ReturnResult.error("订单不存在");
        }

        if(payMerchantOrder.getPayMerchantOrderStatus().startsWith("400")) {
            return ReturnResult.error("订单已退款");
        }

        if(!payMerchantOrder.getPayMerchantOrderStatus().startsWith("200")) {
            return ReturnResult.error("订单未支付");
        }

        PayMerchant payMerchant = getPayMerchant(payMerchantOrder, refundRequest.isForce());
        if(null == payMerchant) {
            return ReturnResult.illegal("商户不存在, 请联系管理员");
        }

        TradeType type = TradeType.valueOf(payMerchantOrder.getPayMerchantOrderTradeType());
        String tradeType = type.getName();
        PayConfigDetector<?> payConfigDetector = ServiceProvider.of(PayConfigDetector.class).getNewExtension(tradeType);
        if(null == payConfigDetector) {
            return ReturnResult.illegal("当前系统不支持该退款方式");
        }

        ReturnResult<?> checked = payConfigDetector.check(payMerchant, type);
        if(!checked.isOk()) {
            return ReturnResult.illegal(checked.getMsg());
        }

        return transactionTemplate.execute(status -> {
            PayOrderRefundCreator orderRefundCreator = ServiceProvider.of(PayOrderRefundCreator.class).getNewExtension(payMerchantOrder.getPayMerchantOrderTradeType(), checked.getData());
            if(null == orderRefundCreator) {
                return ReturnResult.illegal("当前系统不支持退款, 请联系客服");
            }
            payMerchantOrder.setPayMerchantOrderStatus("4000");
            payMerchantOrder.setPayMerchantOrderRefundReason(refundRequest.getRefundReason());
            payMerchantOrder.setPayMerchantOrderRefundCode("R" + payMerchantOrder.getPayMerchantOrderCode());
            ReturnResult<PayRefundResponse> handle = orderRefundCreator.handle(payMerchantOrder, refundRequest);
            PayRefundResponse payRefundResponse = handle.getData();
            payMerchantOrder.setPayMerchantOrderRefundCreateTime(payRefundResponse.getCreateTime());
            payMerchantOrder.setPayMerchantOrderRefundSuccessTime(payRefundResponse.getSuccessTime());
            PayRefundStatus refundStatus = payRefundResponse.getStatus();
            if(refundStatus == PayRefundStatus.CLOSED) {
                payMerchantOrder.setPayMerchantOrderStatus("5001");
            }
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return handle;
        });
    }

    /**
     * 获取商户
     * @param payMerchantOrder 订单
     * @param force 强制
     * @return 商户
     */
    private PayMerchant getPayMerchant(PayMerchantOrder payMerchantOrder, boolean force) {
        return payMerchantMapper.getMerchant(payMerchantOrder.getPayMerchantCode(), force);
    }
}

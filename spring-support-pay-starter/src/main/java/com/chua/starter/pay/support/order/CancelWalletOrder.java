package com.chua.starter.pay.support.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.handler.PayOrderWalletRefundCreator;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PayRefundStatus;
import com.chua.starter.pay.support.service.PayMerchantService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 取消
 * @author CH
 * @since 2024/12/30
 */
public class CancelWalletOrder {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public CancelWalletOrder(TransactionTemplate transactionTemplate, PayMerchantService payMerchantService, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantService = payMerchantService;
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

        String payMerchantOrderStatus = payMerchantOrder.getPayMerchantOrderStatus();
        if("4000".equals(payMerchantOrderStatus)) {
            return ReturnResult.error("订单正在退款");
        }

        if("5002".equals(payMerchantOrderStatus)) {
            return ReturnResult.error("订单已取消");
        }

        if(payMerchantOrderStatus.startsWith("500")) {
            return ReturnResult.error("订单已关闭");
        }

        if("4002".equals(payMerchantOrderStatus)) {
            return ReturnResult.error("订单已退款");
        }

        if("2003".equals(payMerchantOrderStatus)) {
            return ReturnResult.error("订单未支付");
        }

        if(payMerchantOrderStatus.startsWith("100")) {
            return ReturnResult.error("订单未支付");
        }

        if(payMerchantOrderStatus.startsWith("300")) {
            return ReturnResult.error("订单已超时");
        }

        PayMerchant payMerchant = getPayMerchant(payMerchantOrder);
        if(null == payMerchant) {
            return ReturnResult.illegal("商户不存在, 请联系管理员");
        }

        TradeType type = TradeType.valueOf(payMerchantOrder.getPayMerchantOrderTradeType().toUpperCase());
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
            PayOrderWalletRefundCreator orderRefundCreator = ServiceProvider.of(PayOrderWalletRefundCreator.class).getNewExtension(payMerchantOrder.getPayMerchantOrderTradeType(), checked.getData());
            if(null == orderRefundCreator) {
                return ReturnResult.illegal("当前系统不支持退款, 请联系客服");
            }
            payMerchantOrder.setPayMerchantOrderStatus("5002");
            payMerchantOrder.setPayMerchantOrderRefundReason(refundRequest.getRefundReason());
            payMerchantOrder.setPayMerchantOrderRefundCode("R" + payMerchantOrder.getPayMerchantOrderCode());
            ReturnResult<PayRefundResponse> handle = orderRefundCreator.handle(payMerchantOrder, refundRequest);
            PayRefundResponse payRefundResponse = handle.getData();
            payRefundResponse.setOrder(payMerchantOrder);
            payMerchantOrder.setPayMerchantOrderRefundCreateTime(payRefundResponse.getCreateTime());
            payMerchantOrder.setPayMerchantOrderRefundSuccessTime(payRefundResponse.getSuccessTime());
            PayRefundStatus refundStatus = payRefundResponse.getStatus();
            if(refundStatus == PayRefundStatus.CLOSED) {
                payMerchantOrder.setPayMerchantOrderStatus("5000");
            } else if(refundStatus == PayRefundStatus.PROCESSING) {
                payMerchantOrder.setPayMerchantOrderStatus("4000");
            } else if(refundStatus == PayRefundStatus.SUCCESS) {
                payMerchantOrder.setPayMerchantOrderStatus("5002");
            } else if(refundStatus == PayRefundStatus.ABNORMAL) {
                payMerchantOrder.setPayMerchantOrderStatus("4003");
            }
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return handle;
        });
    }

    /**
     * 获取商户
     * @param payMerchantOrder 订单
     * @return 商户
     */
    private PayMerchant getPayMerchant(PayMerchantOrder payMerchantOrder) {
        return payMerchantService.getOneByCode(payMerchantOrder.getPayMerchantCode()).getData();
    }
}

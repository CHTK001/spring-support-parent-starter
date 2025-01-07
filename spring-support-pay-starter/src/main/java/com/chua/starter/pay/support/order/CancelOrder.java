package com.chua.starter.pay.support.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.handler.PayOrderRefundCreator;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PayRefundStatus;
import com.chua.starter.pay.support.service.PayMerchantService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 关闭
 * @author CH
 * @since 2024/12/30
 */
public class CancelOrder {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public CancelOrder(TransactionTemplate transactionTemplate, PayMerchantService payMerchantService, PayMerchantOrderMapper payMerchantOrderMapper) {
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


        return transactionTemplate.execute(status -> {
            payMerchantOrder.setPayMerchantOrderStatus("5000");
            payMerchantOrder.setPayMerchantOrderRefundReason(refundRequest.getRefundReason());
            payMerchantOrder.setPayMerchantOrderRefundCode("RC" + payMerchantOrder.getPayMerchantOrderCode());
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return ReturnResult.ok();
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

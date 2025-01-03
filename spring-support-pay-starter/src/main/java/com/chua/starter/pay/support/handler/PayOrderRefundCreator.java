package com.chua.starter.pay.support.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;

/**
 * 支付退款处理器
 * @author CH
 * @since 2024/12/30
 */
public interface PayOrderRefundCreator {

    /**
     * 生成订单
     *
     * @param payMerchantOrder 支付订单
     * @param refundRequest 退款请求
     * @return 订单
     */
    ReturnResult<PayRefundResponse> handle(PayMerchantOrder payMerchantOrder, PayRefundRequest refundRequest);
}

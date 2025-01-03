package com.chua.starter.pay.support.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.result.PayOrderResponse;

/**
 * 支付订单处理器
 * @author CH
 * @since 2024/12/30
 */
public interface PayOrderCreator {

    /**
     * 生成订单
     * @param payMerchantOrder 支付订单
     * @return
     */
    ReturnResult<PayOrderResponse> handle(PayMerchantOrder payMerchantOrder);
}

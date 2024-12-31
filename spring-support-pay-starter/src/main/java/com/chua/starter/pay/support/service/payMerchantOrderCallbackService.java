package com.chua.starter.pay.support.service;

import com.chua.starter.pay.support.entity.PayMerchantOrder;

/**
 * 支付回调
 * @author CH
 * @since 2024/12/31
 */
public interface payMerchantOrderCallbackService {
    /**
     * 支付回调
     * @param order 订单
     */
    void listen(PayMerchantOrder order);
}

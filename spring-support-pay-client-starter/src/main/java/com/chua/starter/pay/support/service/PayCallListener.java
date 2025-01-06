package com.chua.starter.pay.support.service;

import com.chua.starter.pay.support.entity.PayMerchantOrder;

/**
 * 支付监听
 * @author CH
 */
public interface PayCallListener {


    /**
     * 支付监听
     * @param order 订单
     */
    void listen(PayMerchantOrder order);
}

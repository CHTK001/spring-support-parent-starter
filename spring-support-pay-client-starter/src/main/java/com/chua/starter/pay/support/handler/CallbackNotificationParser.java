package com.chua.starter.pay.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.OrderCallbackRequest;
import com.chua.starter.pay.support.service.PayMerchantService;

/**
 * 通知解析
 *
 * @author CH
 * @since 2024/12/31
 */
public interface CallbackNotificationParser {

    /**
     * 获取请求
     *
     * @return OrderCallbackRequest
     */
    OrderCallbackRequest getRequest();


    /**
     * 获取订单
     *
     * @return 订单
     */
    PayMerchantOrder getOrder();

    /**
     * 请求ID
     *
     * @return 请求ID
     */
    String id();

    /**
     * 解析
     *
     * @return OrderCallbackRequest
     */
    boolean parser(PayMerchantService payMerchantService, PayMerchantOrderMapper payMerchantOrderMapper);

    /**
     * 获取订单
     *
     * @param request 回调
     * @return 订单
     */
    static PayMerchantOrder getPayMerchantOrder(PayMerchantOrderMapper payMerchantOrderMapper, OrderCallbackRequest request) {
        String outTradeId = request.getOutTradeId();
        return payMerchantOrderMapper.selectOne(Wrappers.<PayMerchantOrder>lambdaQuery()
                .eq(PayMerchantOrder::getPayMerchantOrderCode, outTradeId)
        );
    }
}

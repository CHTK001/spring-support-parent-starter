package com.chua.starter.pay.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.OrderCallbackRequest;

/**
 * 通知解析
 * @author CH
 * @since 2024/12/31
 */
public interface CallbackNotificationParser {

    /**
     * 获取请求
     * @return OrderCallbackRequest
     */
    OrderCallbackRequest getRequest();


    /**
     * 获取订单
     * @return 订单
     */
    PayMerchantOrder getOrder();

    /**
     * 请求ID
     * @return 请求ID
     */
    String id();
    /**
     * 解析
     * @return OrderCallbackRequest
     */
    boolean parser(PayMerchantMapper payMerchantMapper, PayMerchantOrderMapper payMerchantOrderMapper);

    /**
     * 获取订单
     * @param request 回调
     * @return 订单
     */
    static PayMerchantOrder getPayMerchantOrder(PayMerchantOrderMapper payMerchantOrderMapper, OrderCallbackRequest request) {
        String outTradeId = request.getOutTradeId();
        if(StringUtils.isNotBlank(outTradeId)) {
            return payMerchantOrderMapper.selectOne(Wrappers.<PayMerchantOrder>lambdaQuery()
                    .eq(PayMerchantOrder::getPayMerchantOrderCode, outTradeId)
            );
        }
        return payMerchantOrderMapper.selectOne(Wrappers.<PayMerchantOrder>lambdaQuery()
                .eq(PayMerchantOrder::getPayMerchantOrderSignNonce, request.getSignNonce())
                .eq(PayMerchantOrder::getPayMerchantOrderSignTimestamp, request.getSignTimestamp())
        );
    }
}

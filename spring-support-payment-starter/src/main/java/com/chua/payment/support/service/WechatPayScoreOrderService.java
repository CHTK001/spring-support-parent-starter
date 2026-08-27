package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.dto.WechatPayScoreOrderQueryRequest;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;

/**
 * 微信支付分订单服务接口，定义微信免密支付订单的创建、同步、完成与取消契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WechatPayScoreOrderService {

    WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request);

    WechatPayScoreOrder getByOutOrderNo(String outOrderNo);

    Page<WechatPayScoreOrder> page(WechatPayScoreOrderQueryRequest request);

    WechatPayScoreOrder syncOrder(String outOrderNo);

    WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request);

    WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request);

    void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload);
}


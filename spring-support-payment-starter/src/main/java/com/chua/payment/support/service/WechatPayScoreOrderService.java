package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;

/**
 * 微信支付分订单服务
 */
public interface WechatPayScoreOrderService {

    WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request);

    WechatPayScoreOrder getByOutOrderNo(String outOrderNo);

    Page<WechatPayScoreOrder> page(int pageNum, int pageSize, Long merchantId, Long channelId, String openId, String state);

    WechatPayScoreOrder syncOrder(String outOrderNo);

    WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request);

    WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request);

    void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload);
}

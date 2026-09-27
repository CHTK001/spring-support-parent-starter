package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.dto.WechatPayScoreOrderQueryRequest;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;

/**
 * 微信支付分订单服务接口，定义微信先享后付支付订单的创建、同步、完成与取消契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WechatPayScoreOrderService {

    /**
     * 创建支付分订单。
     * @param request 请求参数
     * @return 创建出的支付分订单
     */
    WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request);

    /**
     * 根据出订单号获取支付分订单。
     * @param outOrderNo 出订单号
     * @return 获取到的支付分订单
     */
    WechatPayScoreOrder getByOutOrderNo(String outOrderNo);

    /**
     * 分页查询支付分订单。
     * @param request 请求参数
     */
    Page<WechatPayScoreOrder> page(WechatPayScoreOrderQueryRequest request);

    /**
     * 同步订单状态。
     * @param outOrderNo 出订单号
     * @return 同步后的支付分订单
     */
    WechatPayScoreOrder syncOrder(String outOrderNo);

    /**
     * 完成支付分订单。
     * @param outOrderNo 出订单号
     * @param request 请求参数
     * @return 完成后的支付分订单
     */
    WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request);

    /**
     * 取消支付分订单。
     * @param outOrderNo 出订单号
     * @param request 请求参数
     * @return 取消后的支付分订单
     */
    WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request);

    /**
     * 处理微信支付分通知。
     * @param channelId 渠道标识
     * @param outOrderNo 出订单号
     * @param payload 通知报文
     * @param responsePayload 响应报文
     */
    void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload);
}

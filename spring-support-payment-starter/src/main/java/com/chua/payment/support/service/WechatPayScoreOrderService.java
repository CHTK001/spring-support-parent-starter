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

    /**
    * 创建订单。
    * @param request 请求
    * @return 创建订单的结果
    */
    WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request);

    /**
    * 获取by出订单no。
    * @param outOrderNo 出订单no
    * @return 获取by出订单no的结果
    */
    WechatPayScoreOrder getByOutOrderNo(String outOrderNo);

    /**
    * page。
    * @param request 请求
    * @return page的结果
    */
    Page<WechatPayScoreOrder> page(WechatPayScoreOrderQueryRequest request);

    /**
    * 同步订单。
    * @param outOrderNo 出订单no
    * @return 同步订单的结果
    */
    WechatPayScoreOrder syncOrder(String outOrderNo);

    /**
    * 完成订单。
    * @param outOrderNo 出订单no
    * @param request 请求
    * @return 完成订单的结果
    */
    WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request);

    /**
    * cancel订单。
    * @param outOrderNo 出订单no
    * @param request 请求
    * @return cancel订单的结果
    */
    WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request);

    /**
    * 把手通知。
    * @param channelId 通道标识
    * @param outOrderNo 出订单no
    * @param payload payload
    * @param responsePayload 响应payload
    */
    void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload);
}


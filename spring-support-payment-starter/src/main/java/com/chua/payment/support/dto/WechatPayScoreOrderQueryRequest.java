package com.chua.payment.support.dto;

/**
 * 微信支付分订单分页查询请求参数对象。
 *
 * @param pageNum     页码
 * @param pageSize    每页大小
 * @param merchantId  客户标识
 * @param channelId   渠道标识
 * @param openId      用户OpenId标识（可选）
 * @param state       订单状态（可选）
 * @author CH
 * @since 4.0.0.50
 */
public record WechatPayScoreOrderQueryRequest(
        int pageNum,
        int pageSize,
        Long merchantId,
        Long channelId,
        String openId,
        String state
) {
}

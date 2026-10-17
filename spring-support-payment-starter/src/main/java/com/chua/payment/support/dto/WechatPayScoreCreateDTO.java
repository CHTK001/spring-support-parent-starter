package com.chua.payment.support.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分创建请求
 *
 * @param merchantId         商户ID
 * @param channelId          渠道标识
 * @param userId             用户标识
 * @param outOrderNo         分订单号
 * @param serviceId          服务标识
 * @param openId             用户OpenId标识
 * @param totalAmount        总金额
 * @param notifyUrl          通知URL
 * @param serviceIntroduction 服务介绍
 * @param startTime          开始时间
 * @param endTime            结束时间
 * @param reason             创建原因
 * @param finishType         完成类型
 * @param needUserConfirm    是否需要用户确认
 * @param attach             附加数据
 * @param postPayments       后付商品列表
 * @param postDiscounts      后付优惠列表
 * @param extraParams        extra参数
 * @param remark             备注
 * @author CH
 * @since 4.0.0
 */
public record WechatPayScoreCreateDTO(
        Long merchantId,
        Long channelId,
        Long userId,
        String outOrderNo,
        String serviceId,
        String openId,
        BigDecimal totalAmount,
        String notifyUrl,
        String serviceIntroduction,
        String startTime,
        String endTime,
        String reason,
        String finishType,
        Boolean needUserConfirm,
        String attach,
        List<Map<String, Object>> postPayments,
        List<Map<String, Object>> postDiscounts,
        Map<String, Object> extraParams,
        String remark) {
}

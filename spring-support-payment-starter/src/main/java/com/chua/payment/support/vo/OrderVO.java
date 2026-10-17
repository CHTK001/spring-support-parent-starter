package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * @param id                 标识
 * @param orderNo            订单号
 * @param businessOrderNo    业务订单号
 * @param merchantId         商户ID
 * @param merchantName       商户名称
 * @param channelId          渠道标识
 * @param channelName        渠道名称
 * @param userId             用户标识
 * @param channelType        渠道类型
 * @param channelSubType     渠道SUB类型
 * @param orderAmount        订单金额
 * @param paidAmount         已付金额
 * @param refundAmount       退款金额
 * @param discountAmount     优惠金额
 * @param currency           币种
 * @param status             状态
 * @param statusDesc         状态描述
 * @param subject            主题
 * @param body               请求体
 * @param notifyUrl          通知URL
 * @param returnUrl          返回值URL
 * @param expireTime         过期时间
 * @param payTime            支付时间
 * @param completeTime       完成时间
 * @param refundTime         退款时间
 * @param thirdPartyOrderNo  第三方订单号
 * @param remark             备注
 * @param deleted            删除标记
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @since 2026-03-18
 * @author CH
 */
public record OrderVO(
        Long id,
        String orderNo,
        String businessOrderNo,
        Long merchantId,
        String merchantName,
        Long channelId,
        String channelName,
        Long userId,
        String channelType,
        String channelSubType,
        BigDecimal orderAmount,
        BigDecimal paidAmount,
        BigDecimal refundAmount,
        BigDecimal discountAmount,
        String currency,
        String status,
        String statusDesc,
        String subject,
        String body,
        String notifyUrl,
        String returnUrl,
        LocalDateTime expireTime,
        LocalDateTime payTime,
        LocalDateTime completeTime,
        LocalDateTime refundTime,
        String thirdPartyOrderNo,
        String remark,
        Integer deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}

package com.chua.payment.support.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建数据传输对象
 *
 * @param merchantId      商户ID
 * @param channelId       渠道标识
 * @param userId          用户标识
 * @param businessOrderNo 业务订单NO
 * @param channelType     渠道类型
 * @param channelSubType  渠道SUB类型
 * @param orderAmount     订单金额
 * @param discountAmount  优惠金额
 * @param currency        币种
 * @param subject         主题
 * @param body            请求体
 * @param notifyUrl       通知URL
 * @param returnUrl       返回值URL
 * @param expireMinutes   过期分钟数
 * @param remark          备注
 * @since 2026-03-18
 * @author CH
 */
public record OrderCreateDTO(
        Long merchantId,
        Long channelId,
        Long userId,
        String businessOrderNo,
        String channelType,
        String channelSubType,
        BigDecimal orderAmount,
        BigDecimal discountAmount,
        String currency,
        String subject,
        String body,
        String notifyUrl,
        String returnUrl,
        Integer expireMinutes,
        String remark) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}

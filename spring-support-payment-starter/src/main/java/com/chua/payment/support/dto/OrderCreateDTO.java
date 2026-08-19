package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建数据传输对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class OrderCreateDTO implements Serializable {
    /** MerchantID */
    private Long merchantId;

    /** 通道ID */
    private Long channelId;

    /** 用户ID */
    private Long userId;

    /** Business排序NO */
    private String businessOrderNo;

    /** 通道类型 */
    private String channelType;

    /** 通道SUB类型 */
    private String channelSubType;

    /** 排序amount */
    private BigDecimal orderAmount;

    /** Discountamount */
    private BigDecimal discountAmount;

    /** Currency */
    private String currency;

    /** Subject */
    private String subject;

    /** 请求体 */
    private String body;

    /** NotifyURL */
    private String notifyUrl;

    /** 返回值URL */
    private String returnUrl;

    /** Expireminutes */
    private Integer expireMinutes;

    /** Remark */
    private String remark;
}

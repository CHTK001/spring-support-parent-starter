package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建数据传输对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class OrderCreateDTO implements Serializable {
    private Long merchantId;

    private Long channelId;

    private Long userId;

    private String businessOrderNo;

    private String channelType;

    private String channelSubType;

    private BigDecimal orderAmount;

    private BigDecimal discountAmount;

    private String currency;

    private String subject;

    private String body;

    private String notifyUrl;

    private String returnUrl;

    private Integer expireMinutes;

    private String remark;
}

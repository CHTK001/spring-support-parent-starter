package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class OrderVO implements Serializable {
    private Long id;

    private String orderNo;

    private String businessOrderNo;

    private Long merchantId;

    private String merchantName;

    private Long channelId;

    private String channelName;

    private Long userId;

    private String channelType;

    private String channelSubType;

    private BigDecimal orderAmount;

    private BigDecimal paidAmount;

    private BigDecimal refundAmount;

    private BigDecimal discountAmount;

    private String currency;

    private String status;

    private String statusDesc;

    private String subject;

    private String body;

    private String notifyUrl;

    private String returnUrl;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime completeTime;

    private LocalDateTime refundTime;

    private String thirdPartyOrderNo;

    private String remark;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

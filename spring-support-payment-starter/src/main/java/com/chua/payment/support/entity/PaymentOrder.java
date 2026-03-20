package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("payment_order")
public class PaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String businessOrderNo;

    private Long merchantId;

    private Long channelId;

    private Long userId;

    private String channelType;

    private String channelSubType;

    private BigDecimal orderAmount;

    private BigDecimal paidAmount;

    private BigDecimal refundAmount;

    private BigDecimal discountAmount;

    private String currency;

    private String status;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

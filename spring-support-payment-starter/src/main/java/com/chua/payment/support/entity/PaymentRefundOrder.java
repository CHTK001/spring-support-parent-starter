package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体
 */
@Data
@TableName("payment_refund_order")
public class PaymentRefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    private Long channelId;

    private String sourceOrderStatus;

    private String thirdPartyRefundNo;

    private BigDecimal refundAmount;

    private String status;

    private String reason;

    private Integer notifyStatus;

    private String requestPayload;

    private String responsePayload;

    private String operator;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("pay_transaction_record")
public class TransactionRecord {
    @TableId(value = "pay_transaction_record_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_transaction_record_transaction_no")
    private String transactionNo;

    @TableField("pay_transaction_record_order_id")
    private Long orderId;

    @TableField("pay_transaction_record_order_no")
    private String orderNo;

    @TableField("pay_transaction_record_merchant_id")
    private Long merchantId;

    @TableField("pay_transaction_record_channel_id")
    private Long channelId;

    @TableField("pay_transaction_record_transaction_type")
    private String transactionType;

    @TableField("pay_transaction_record_amount")
    private BigDecimal amount;

    @TableField("pay_transaction_record_channel_type")
    private String channelType;

    @TableField("pay_transaction_record_third_party_transaction_no")
    private String thirdPartyTransactionNo;

    @TableField("pay_transaction_record_status")
    private Integer status;

    @TableField("pay_transaction_record_request_payload")
    private String requestPayload;

    @TableField("pay_transaction_record_response_payload")
    private String responsePayload;

    @TableField("pay_transaction_record_remark")
    private String remark;

    @TableField(value = "pay_transaction_record_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

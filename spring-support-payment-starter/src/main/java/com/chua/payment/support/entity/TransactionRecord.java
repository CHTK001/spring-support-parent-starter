package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_transaction_record")
public class TransactionRecord {
    @TableId(value = "pay_transaction_record_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_transaction_record_transaction_no")
    /** TransactionNO */
    private String transactionNo;

    @TableField("pay_transaction_record_order_id")
    /** 排序ID */
    private Long orderId;

    @TableField("pay_transaction_record_order_no")
    /** 排序NO */
    private String orderNo;

    @TableField("pay_transaction_record_merchant_id")
    /** MerchantID */
    private Long merchantId;

    @TableField("pay_transaction_record_channel_id")
    /** 通道ID */
    private Long channelId;

    @TableField("pay_transaction_record_transaction_type")
    /** Transaction类型 */
    private String transactionType;

    @TableField("pay_transaction_record_amount")
    /** Amount */
    private BigDecimal amount;

    @TableField("pay_transaction_record_channel_type")
    /** 通道类型 */
    private String channelType;

    @TableField("pay_transaction_record_third_party_transaction_no")
    /** ThirdpartytransactionNO */
    private String thirdPartyTransactionNo;

    @TableField("pay_transaction_record_status")
    /** 状态 */
    private Integer status;

    @TableField("pay_transaction_record_request_payload")
    /** 请求payload */
    private String requestPayload;

    @TableField("pay_transaction_record_response_payload")
    /** 响应payload */
    private String responsePayload;

    @TableField("pay_transaction_record_remark")
    /** Remark */
    private String remark;

    @TableField(value = "pay_transaction_record_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;
}

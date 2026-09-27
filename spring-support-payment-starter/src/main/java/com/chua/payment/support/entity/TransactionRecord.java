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
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_transaction_record_transaction_no")
    /**
     * 交易流水号
     */
    private String transactionNo;

    @TableField("pay_transaction_record_order_id")
    /**
     * 订单标识
     */
    private Long orderId;

    @TableField("pay_transaction_record_order_no")
    /**
     * 订单号
     */
    private String orderNo;

    @TableField("pay_transaction_record_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_transaction_record_channel_id")
    /**
     * 渠道标识
     */
    private Long channelId;

    @TableField("pay_transaction_record_transaction_type")
    /**
     * 交易类型
     */
    private String transactionType;

    @TableField("pay_transaction_record_amount")
    /**
     * 交易金额
     */
    private BigDecimal amount;

    @TableField("pay_transaction_record_channel_type")
    /**
     * 渠道类型
     */
    private String channelType;

    @TableField("pay_transaction_record_third_party_transaction_no")
    /**
     * 第三方交易号
     */
    private String thirdPartyTransactionNo;

    @TableField("pay_transaction_record_status")
    /**
     * 状态
     */
    private Integer status;

    @TableField("pay_transaction_record_request_payload")
    /**
     * 请求报文
     */
    private String requestPayload;

    @TableField("pay_transaction_record_response_payload")
    /**
     * 响应报文
     */
    private String responsePayload;

    @TableField("pay_transaction_record_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField(value = "pay_transaction_record_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

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
@TableName("transaction_record")
public class TransactionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String transactionNo;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    private Long channelId;

    private String transactionType;

    private BigDecimal amount;

    private String channelType;

    private String thirdPartyTransactionNo;

    private Integer status;

    private String requestPayload;

    private String responsePayload;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public LocalDateTime getCreateTime() {
        return createdAt;
    }
}

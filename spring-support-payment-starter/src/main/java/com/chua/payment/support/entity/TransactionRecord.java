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

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 流水号
     */
    private String transactionNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 交易类型：PAY/REFUND
     */
    private String transactionType;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 支付渠道
     */
    private String channelType;

    /**
     * 第三方流水号
     */
    private String thirdPartyTransactionNo;

    /**
     * 状态：0失败 1成功
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建时间别名（兼容方法引用）
     */
    public LocalDateTime getCreateTime() {
        return createdAt;
    }
}

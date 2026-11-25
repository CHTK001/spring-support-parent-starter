package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 渠道对账单实体类
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_reconciliation_statement")
public class ReconciliationStatement {

    @TableId(value = "pay_reconciliation_statement_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_reconciliation_statement_no")
    /**
     * 对账单号
     */
    private String statementNo;

    @TableField("pay_reconciliation_statement_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_reconciliation_statement_channel_id")
    /**
     * 支付渠道ID
     */
    private Long channelId;

    @TableField("pay_reconciliation_statement_channel_type")
    /**
     * 渠道类型: WECHAT/ALIPAY
     */
    private String channelType;

    @TableField("pay_reconciliation_statement_bill_date")
    /**
     * 账单日期
     */
    private LocalDate billDate;

    @TableField("pay_reconciliation_statement_channel_count")
    /**
     * 渠道账单笔数
     */
    private Integer channelCount;

    @TableField("pay_reconciliation_statement_channel_amount")
    /**
     * 渠道账单总金额
     */
    private BigDecimal channelAmount;

    @TableField("pay_reconciliation_statement_local_count")
    /**
     * 本地流水笔数
     */
    private Integer localCount;

    @TableField("pay_reconciliation_statement_local_amount")
    /**
     * 本地流水总金额
     */
    private BigDecimal localAmount;

    @TableField("pay_reconciliation_statement_diff_count")
    /**
     * 差异数
     */
    private Integer diffCount;

    @TableField("pay_reconciliation_statement_status")
    /**
     * 状态: MATCHED-对账一致/DIFF-存在差异/FAILED-对账失败
     */
    private String status;

    @TableField("pay_reconciliation_statement_fail_reason")
    /**
     * 失败原因
     */
    private String failReason;

    @TableField("pay_reconciliation_statement_created_at")
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField("pay_reconciliation_statement_updated_at")
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

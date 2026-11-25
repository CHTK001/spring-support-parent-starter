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
 * 渠道对账差异明细实体类
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_reconciliation_diff")
public class ReconciliationDiff {

    @TableId(value = "pay_reconciliation_diff_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_reconciliation_diff_statement_id")
    /**
     * 对账单ID
     */
    private Long statementId;

    @TableField("pay_reconciliation_diff_statement_no")
    /**
     * 对账单号
     */
    private String statementNo;

    @TableField("pay_reconciliation_diff_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_reconciliation_diff_channel_type")
    /**
     * 渠道类型: WECHAT/ALIPAY
     */
    private String channelType;

    @TableField("pay_reconciliation_diff_bill_date")
    /**
     * 账单日期
     */
    private LocalDate billDate;

    @TableField("pay_reconciliation_diff_type")
    /**
     * 差异类型: LOCAL_MISSING-渠道有本地无/CHANNEL_MISSING-本地有渠道无/AMOUNT_MISMATCH-金额不一致
     */
    private String diffType;

    @TableField("pay_reconciliation_diff_order_no")
    /**
     * 商户订单号
     */
    private String orderNo;

    @TableField("pay_reconciliation_diff_third_party_no")
    /**
     * 第三方交易号
     */
    private String thirdPartyNo;

    @TableField("pay_reconciliation_diff_channel_amount")
    /**
     * 渠道账单金额
     */
    private BigDecimal channelAmount;

    @TableField("pay_reconciliation_diff_local_amount")
    /**
     * 本地流水金额
     */
    private BigDecimal localAmount;

    @TableField("pay_reconciliation_diff_diff_amount")
    /**
     * 差额(渠道-本地)
     */
    private BigDecimal diffAmount;

    @TableField("pay_reconciliation_diff_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_reconciliation_diff_created_at")
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

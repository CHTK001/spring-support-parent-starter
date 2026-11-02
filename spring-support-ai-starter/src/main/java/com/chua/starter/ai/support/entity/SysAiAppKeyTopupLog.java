package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 应用密钥充值/消费流水实体——对应 sys_ai_app_key_topup_log 表。
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("sys_ai_app_key_topup_log")
public class SysAiAppKeyTopupLog {

    /**
     * 流水主键
     */
    @TableId(value = "sys_ai_app_key_topup_log_id", type = IdType.AUTO)
    private Long id;

    /**
     * AppKey 主键
     */
    @TableField("sys_ai_app_key_topup_log_app_key_id")
    private Long appKeyId;

    /**
     * AppKey
     */
    @TableField("sys_ai_app_key_topup_log_app_key")
    private String appKey;

    /**
     * 流水类型：RECHARGE 充值 / CONSUME 消费 / REFUND 退款 / ADJUST 调整
     */
    @TableField("sys_ai_app_key_topup_log_type")
    private String type;

    /**
     * 金额（充值/退款为正，消费/调整为负）
     */
    @TableField("sys_ai_app_key_topup_log_amount")
    private BigDecimal amount;

    /**
     * 变动前余额
     */
    @TableField("sys_ai_app_key_topup_log_balance_before")
    private BigDecimal balanceBefore;

    /**
     * 变动后余额
     */
    @TableField("sys_ai_app_key_topup_log_balance_after")
    private BigDecimal balanceAfter;

    /**
     * 备注
     */
    @TableField("sys_ai_app_key_topup_log_remark")
    private String remark;

    /**
     * 流水时间
     */
    @TableField("sys_ai_app_key_topup_log_created_at")
    private LocalDateTime createdAt;

    /**
     * 流水类型常量
     */
    public interface Types {
        /**
         * 充值
         */
        String RECHARGE = "RECHARGE";
        /**
         * 消费
         */
        String CONSUME = "CONSUME";
        /**
         * 退款
         */
        String REFUND = "REFUND";
        /**
         * 调整
         */
        String ADJUST = "ADJUST";
    }
}

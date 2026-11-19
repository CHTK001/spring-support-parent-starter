package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商余额历史（追加写，供趋势图）。
 *
 * <p>每次余额回源（手动刷新 / 时间轮定时刷新）向本表追加每个 Key 的一行读数，
 * 与 {@link SysAiProviderCredit} 的「最新快照」互补：快照回答「现在剩多少」，
 * 历史回答「这段时间怎么变化的」。只存密钥哈希与掩码，不落明文；
 * 保留窗口之外的旧行由 {@code ProviderCreditSnapshotService#pruneHistory} 清理。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Data
@TableName("sys_ai_provider_credit_history")
public class SysAiProviderCreditHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "sys_ai_provider_credit_history_id", type = IdType.AUTO)
    private Long id;

    /**
     * 供应商 ID
     */
    @TableField("sys_ai_provider_credit_history_provider_id")
    private String providerId;

    /**
     * 余额密钥哈希（SHA-256 十六进制，不存明文）
     */
    @TableField("sys_ai_provider_credit_history_key_hash")
    private String keyHash;

    /**
     * 余额密钥掩码（展示用）
     */
    @TableField("sys_ai_provider_credit_history_key_mask")
    private String keyMask;

    /**
     * 本次是否查到余额
     */
    @TableField("sys_ai_provider_credit_history_ok")
    private Boolean ok;

    /**
     * 剩余余额
     */
    @TableField("sys_ai_provider_credit_history_remain")
    private Double remain;

    /**
     * 已用余额
     */
    @TableField("sys_ai_provider_credit_history_used")
    private Double used;

    /**
     * 币种（USD/CNY/tokens）
     */
    @TableField("sys_ai_provider_credit_history_currency")
    private String currency;

    /**
     * 命中的余额实现（host/openai/moss/...）
     */
    @TableField("sys_ai_provider_credit_history_source")
    private String source;

    /**
     * 本次回源时间（毫秒）
     */
    @TableField("sys_ai_provider_credit_history_synced_at")
    private Long syncedAt;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime;
}

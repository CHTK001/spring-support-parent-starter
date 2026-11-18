package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商余额快照（按 Key 持久化）。
 *
 * <p>每个供应商的每个余额密钥一行，存最近一次成功/失败的读数；密钥只存
 * SHA-256 哈希与掩码，不落明文。手动刷新与时间轮定时刷新都写本表，
 * 进程重启后面板仍能展示最近一次读数（带 synced_at 年龄）。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Data
@TableName("sys_ai_provider_credit")
public class SysAiProviderCredit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "sys_ai_provider_credit_id", type = IdType.AUTO)
    private Long id;

    /**
     * 供应商 ID
     */
    @TableField("sys_ai_provider_credit_provider_id")
    private String providerId;

    /**
     * 余额密钥哈希（SHA-256 十六进制，不存明文）
     */
    @TableField("sys_ai_provider_credit_key_hash")
    private String keyHash;

    /**
     * 余额密钥掩码（展示用）
     */
    @TableField("sys_ai_provider_credit_key_mask")
    private String keyMask;

    /**
     * 本次是否查到余额
     */
    @TableField("sys_ai_provider_credit_ok")
    private Boolean ok;

    /**
     * 剩余余额
     */
    @TableField("sys_ai_provider_credit_remain")
    private Double remain;

    /**
     * 已用余额
     */
    @TableField("sys_ai_provider_credit_used")
    private Double used;

    /**
     * 总额（remain+used）
     */
    @TableField("sys_ai_provider_credit_total")
    private Double total;

    /**
     * 币种（USD/CNY/%）
     */
    @TableField("sys_ai_provider_credit_currency")
    private String currency;

    /**
     * 命中的余额实现（host/openai/moss/...）
     */
    @TableField("sys_ai_provider_credit_source")
    private String source;

    /**
     * 多段读数 JSON（label/amount/currency）
     */
    @TableField("sys_ai_provider_credit_parts")
    private String parts;

    /**
     * 供应商专属结构化数据 JSON（sub2api 订阅明细等；快照回放时还原 extra）
     */
    @TableField("sys_ai_provider_credit_extra")
    private String extra;

    /**
     * 失败原因（ok=true 时为空）
     */
    @TableField("sys_ai_provider_credit_error")
    private String error;

    /**
     * 本次回源时间（毫秒）
     */
    @TableField("sys_ai_provider_credit_synced_at")
    private Long syncedAt;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime;

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updateTime;
}

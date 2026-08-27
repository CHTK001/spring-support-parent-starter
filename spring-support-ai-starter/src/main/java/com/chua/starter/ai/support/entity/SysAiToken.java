package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 令牌实体——对应 sys_ai_token 表。
 * <p>
 * 每个 Token 绑定到一个分组，具备费用统计、使用计数、限流和允许模型列表等完整能力。
 * AppKey 用于身份认证，Token 用于鉴权+计量。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_token")
public class SysAiToken {

    /**
     * 令牌主键
     */
    @TableId(type = IdType.AUTO)
    /** SYSAI令牌ID */
    private Integer id;

    /**
     * 令牌名称
     */
    @TableField("sys_ai_token_name")
    private String name;

    /**
     * 令牌密钥（明文）
     */
    @TableField("sys_ai_token_key")
    private String key;

    /**
     * 令牌密钥前缀（如 sk-）
     */
    @TableField("sys_ai_token_key_prefix")
    private String keyPrefix;

    /**
     * 令牌密钥哈希（用于快速查找）
     */
    @TableField("sys_ai_token_key_hash")
    private String keyHash;

    /**
     * 归属用户 ID
     */
    private Integer sysUserId;

    /**
     * 绑定 AppKey ID（可选，AppKey 认证后关联 Token 计量）
     */
    private Integer sysAiAppKeyId;

    /**
     * 分组 ID——Token 绑定的分组
     */
    @TableField("sys_ai_token_group_id")
    private Integer groupId;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_token_status")
    private Integer status;

    /**
     * 过期时间
     */
    @TableField("sys_ai_token_expire_time")
    private LocalDateTime expireTime;

    /**
     * 输入费率（每千 token）
     */
    @TableField("sys_ai_token_input_fee")
    private BigDecimal inputFee;

    /**
     * 输出费率（每千 token）
     */
    @TableField("sys_ai_token_output_fee")
    private BigDecimal outputFee;

    /**
     * 累计输入费用
     */
    @TableField("sys_ai_token_total_input_fee")
    private BigDecimal totalInputFee;

    /**
     * 累计输出费用
     */
    @TableField("sys_ai_token_total_output_fee")
    private BigDecimal totalOutputFee;

    /**
     * 累计总费用
     */
    @TableField("sys_ai_token_total_fee")
    private BigDecimal totalFee;

    /**
     * 累计使用次数
     */
    @TableField("sys_ai_token_use_count")
    private Long useCount;

    /**
     * 累计使用 token 数
     */
    @TableField("sys_ai_token_total_tokens")
    private Long totalTokens;

    /**
     * 今日费用
     */
    @TableField("sys_ai_token_today_fee")
    private BigDecimal todayFee;

    /**
     * 今日费用统计日期（yyyy-MM-dd）
     */
    @TableField("sys_ai_token_today_date")
    private String todayDate;

    /**
     * 最后使用时间
     */
    @TableField("sys_ai_token_last_used_time")
    private LocalDateTime lastUsedTime;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_token_delete")
    private Integer delete;

    /**
     * 允许的模型列表（逗号分隔）
     */
    @TableField("sys_ai_token_allowed_models")
    private String allowedModels;

    /**
     * 限流：每分钟最大请求数
     */
    @TableField("sys_ai_token_rate_limit")
    private Integer rateLimit;

    /**
     * 限流计数器
     */
    @TableField("sys_ai_token_rate_count")
    private Integer rateCount;

    /**
     * 限流窗口开始时间
     */
    @TableField("sys_ai_token_rate_window_start")
    private LocalDateTime rateWindowStart;

    /**
     * 备注
     */
    @TableField("sys_ai_token_remark")
    private String remark;

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    /** Create名称 */
    private String createName;

    /**
     * 创建人 ID
     */
    @TableField(fill = FieldFill.INSERT)
    /** CreateBY */
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Update时间 */
    private LocalDateTime updateTime;

    /**
     * 更新人名称
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Update名称 */
    private String updateName;

    /**
     * 更新人 ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** UpdateBY */
    private Integer updateBy;
}


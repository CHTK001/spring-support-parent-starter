package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
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
    private Integer sysAiTokenId;

    /**
     * 令牌名称
     */
    private String sysAiTokenName;

    /**
     * 令牌密钥（明文）
     */
    private String sysAiTokenKey;

    /**
     * 令牌密钥前缀（如 sk-）
     */
    private String sysAiTokenKeyPrefix;

    /**
     * 令牌密钥哈希（用于快速查找）
     */
    private String sysAiTokenKeyHash;

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
    private Integer sysAiTokenGroupId;

    /**
     * 状态：1 启用，0 禁用
     */
    private Integer sysAiTokenStatus;

    /**
     * 过期时间
     */
    private LocalDateTime sysAiTokenExpireTime;

    /**
     * 输入费率（每千 token）
     */
    private BigDecimal sysAiTokenInputFee;

    /**
     * 输出费率（每千 token）
     */
    private BigDecimal sysAiTokenOutputFee;

    /**
     * 累计输入费用
     */
    private BigDecimal sysAiTokenTotalInputFee;

    /**
     * 累计输出费用
     */
    private BigDecimal sysAiTokenTotalOutputFee;

    /**
     * 累计总费用
     */
    private BigDecimal sysAiTokenTotalFee;

    /**
     * 累计使用次数
     */
    private Long sysAiTokenUseCount;

    /**
     * 累计使用 token 数
     */
    private Long sysAiTokenTotalTokens;

    /**
     * 今日费用
     */
    private BigDecimal sysAiTokenTodayFee;

    /**
     * 今日费用统计日期（yyyy-MM-dd）
     */
    private String sysAiTokenTodayDate;

    /**
     * 最后使用时间
     */
    private LocalDateTime sysAiTokenLastUsedTime;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    private Integer sysAiTokenDelete;

    /**
     * 允许的模型列表（逗号分隔）
     */
    private String sysAiTokenAllowedModels;

    /**
     * 限流：每分钟最大请求数
     */
    private Integer sysAiTokenRateLimit;

    /**
     * 限流计数器
     */
    private Integer sysAiTokenRateCount;

    /**
     * 限流窗口开始时间
     */
    private LocalDateTime sysAiTokenRateWindowStart;

    /**
     * 备注
     */
    private String sysAiTokenRemark;

    @TableField(fill = FieldFill.INSERT)
    private String createName;

    @TableField(fill = FieldFill.INSERT)
    private Integer createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateName;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Integer updateBy;
}

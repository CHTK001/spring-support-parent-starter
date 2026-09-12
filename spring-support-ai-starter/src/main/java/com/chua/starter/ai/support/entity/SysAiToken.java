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
   * AI 令牌实体——对应 sys_AI_令牌 表。
 * <p>
   * 每个 令牌 绑定到一个分组，具备费用统计、使用计数、限流和允许模型列表等完整能力。
   * app钥匙 用于身份认证，令牌 用于鉴权+计量。
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("sys_ai_token")
public class SysAiToken {

    /**
     * 令牌主键
     */
    @TableId(value = "sys_ai_token_id", type = IdType.AUTO)
    /** SYSAI令牌标识 */
    private Integer id;

    /**
     * 令牌名称
     */
    @TableField("sys_ai_token_name")
    private String name; // 名称

    /**
     * 令牌密钥（明文）
     */
    @TableField("sys_ai_token_key")
    private String key; // 钥匙

    /**
     * 令牌密钥前缀（如 sk-）
     */
    @TableField("sys_ai_token_key_prefix")
    private String keyPrefix; // 钥匙前缀

    /**
     * 令牌密钥哈希（用于快速查找）
     */
    @TableField("sys_ai_token_key_hash")
    private String keyHash; // 钥匙哈希

    /**
      * 归属用户 标识
     */
    private Integer sysUserId;

    /**
       * 绑定 app钥匙 标识（可选，app钥匙 认证后关联 令牌 计量）
     */
    private Integer sysAiAppKeyId;

    /**
       * 分组 标识——令牌 绑定的分组
     */
    @TableField("sys_ai_token_group_id")
    private Integer groupId; // 群体标识

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_token_status")
    private Integer status; // 状态

    /**
     * 过期时间
     */
    @TableField("sys_ai_token_expire_time")
    private LocalDateTime expireTime; // expire时间

    /**
      * 输入费率（每千 令牌）
     */
    @TableField("sys_ai_token_input_fee")
    private BigDecimal inputFee; // 输入fee

    /**
      * 输出费率（每千 令牌）
     */
    @TableField("sys_ai_token_output_fee")
    private BigDecimal outputFee; // 输出fee

    /**
     * 累计输入费用
     */
    @TableField("sys_ai_token_total_input_fee")
    private BigDecimal totalInputFee; // total输入fee

    /**
     * 累计输出费用
     */
    @TableField("sys_ai_token_total_output_fee")
    private BigDecimal totalOutputFee; // total输出fee

    /**
     * 累计总费用
     */
    @TableField("sys_ai_token_total_fee")
    private BigDecimal totalFee; // totalfee

    /**
     * 累计使用次数
     */
    @TableField("sys_ai_token_use_count")
    private Long useCount; // use数量

    /**
      * 累计使用 令牌 数
     */
    @TableField("sys_ai_token_total_tokens")
    private Long totalTokens; // total令牌

    /**
     * 今日费用
     */
    @TableField("sys_ai_token_today_fee")
    private BigDecimal todayFee; // todayfee

    /**
     * 今日费用统计日期（yyyy-MM-dd）
     */
    @TableField("sys_ai_token_today_date")
    private String todayDate; // today日期

    /**
     * 最后使用时间
     */
    @TableField("sys_ai_token_last_used_time")
    private LocalDateTime lastUsedTime; // 最后一个used时间

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_token_delete")
    private Integer delete; // 删除

    /**
     * 允许的模型列表（逗号分隔）
     */
    @TableField("sys_ai_token_allowed_models")
    private String allowedModels; // allowed模型

    /**
     * 限流：每分钟最大请求数
     */
    @TableField("sys_ai_token_rate_limit")
    private Integer rateLimit; // rate限制

    /**
     * 限流计数器
     */
    @TableField("sys_ai_token_rate_count")
    private Integer rateCount; // rate数量

    /**
     * 限流窗口开始时间
     */
    @TableField("sys_ai_token_rate_window_start")
    private LocalDateTime rateWindowStart; // rate窗口启动

    /**
     * 备注
     */
    @TableField("sys_ai_token_remark")
    private String remark; // 评论

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    /** 创建名称 */
    private String createName;

    /**
      * 创建人 标识
     */
    @TableField(fill = FieldFill.INSERT)
    /** 创建by */
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 更新人名称
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新名称 */
    private String updateName;

    /**
      * 更新人 标识
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新by */
    private Integer updateBy;
}


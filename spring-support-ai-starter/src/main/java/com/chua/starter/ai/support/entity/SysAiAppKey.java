package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 应用密钥实体——对应 sys_ai_app_key 表。
 * <p>
 * AppKey 用于 OpenAI 兼容协议的身份认证（Bearer Token / x-ai-app-key Header），
 * 每个 AppKey 绑定到一个分组，通过分组控制可访问的模型范围。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_app_key")
public class SysAiAppKey {

    /**
     * 应用密钥主键 ID
     */
    @TableId(type = IdType.AUTO)
    /** SYSAIAPP密钥ID */
    private Integer id;

    /**
     * 应用 ID
     */
    @TableField("sys_ai_app_key_app_id")
    private String appId;

    /**
     * 访问 Key
     */
    @TableField("sys_ai_app_key_app_key")
    private String appKey;

    /**
     * 访问 Secret
     */
    @TableField("sys_ai_app_key_secret")
    private String secret;

    /**
     * 密钥名称
     */
    @TableField("sys_ai_app_key_name")
    private String name;

    /**
     * 所属用户 ID
     */
    private Integer sysUserId;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_app_key_status")
    private Integer status;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_app_key_delete")
    @TableLogic
    private Integer delete;

    /**
     * 分组 ID——Token/AppKey 绑定的分组
     */
    @TableField("sys_ai_app_key_group_id")
    private Integer groupId;

    /**
     * 关联供应商 ID（面板层字段）
     */
    @TableField("sys_ai_app_key_provider_id")
    private Long providerId;

    /**
     * 限定使用的模型（面板层字段）
     */
    @TableField("sys_ai_app_key_model")
    private String model;

    /**
     * 是否启用（面板层字段，与状态列互斥使用）
     */
    @TableField("sys_ai_app_key_enabled")
    private Boolean enabled;

    /**
     * 过期时间（DATETIME，面板层字段，与 epoch 秒过期列并存）
     */
    @TableField("sys_ai_app_key_expire_datetime")
    private LocalDateTime expireDatetime;

    /**
     * 过期时间戳（秒）
     */
    @TableField("sys_ai_app_key_expire_time")
    private Long expireTime;

    /**
     * 有效天数
     */
    @TableField("sys_ai_app_key_valid_days")
    private Integer validDays;

    /**
     * IP 白名单（逗号分隔）
     */
    @TableField("sys_ai_app_key_ip_whitelist")
    private String ipWhitelist;

    /**
     * 最近使用时间戳（毫秒）
     */
    @TableField("sys_ai_app_key_last_used_time")
    private Long lastUsedTime;

    /**
     * 使用次数
     */
    @TableField("sys_ai_app_key_use_count")
    private Long useCount;

    /**
     * 次数上限（null 表示不限）
     */
    @TableField("sys_ai_app_key_max_use")
    private Long maxUse;

    /**
     * Token 总量上限（null 表示不限），用于控制 AppKey 累计消耗的 Token 总数
     */
    @TableField("sys_ai_app_key_max_tokens")
    private Long maxTokens;

    /**
     * 已使用 Token 累计值，每次对话成功后累加
     */
    @TableField("sys_ai_app_key_total_tokens")
    private Long totalTokens;

    /**
     * 授权范围
     */
    @TableField("sys_ai_app_key_scope")
    private String scope;

    /**
     * 备注说明
     */
    @TableField("sys_ai_app_key_remark")
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


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
    private Integer sysAiAppKeyId;

    /**
     * 应用 ID
     */
    private String sysAiAppKeyAppId;

    /**
     * 访问 Key
     */
    @TableField("sys_ai_app_key_app_key")
    private String sysAiAppKeyAppKey;

    /**
     * 访问 Secret
     */
    @TableField("sys_ai_app_key_secret")
    private String sysAiAppKeySecret;

    /**
     * 密钥名称
     */
    private String sysAiAppKeyName;

    /**
     * 所属用户 ID
     */
    private Integer sysUserId;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_app_key_status")
    private Integer sysAiAppKeyStatus;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_app_key_delete")
    @TableLogic
    private Integer sysAiAppKeyDelete;

    /**
     * 分组 ID——Token/AppKey 绑定的分组
     */
    private Integer sysAiAppKeyGroupId;

    /**
     * 关联供应商 ID（面板层字段）
     */
    @TableField("sys_ai_app_key_provider_id")
    private Long sysAiAppKeyProviderId;

    /**
     * 限定使用的模型（面板层字段）
     */
    @TableField("sys_ai_app_key_model")
    private String sysAiAppKeyModel;

    /**
     * 是否启用（面板层字段，与状态列互斥使用）
     */
    @TableField("sys_ai_app_key_enabled")
    private Boolean sysAiAppKeyEnabled;

    /**
     * 过期时间（DATETIME，面板层字段，与 epoch 秒过期列并存）
     */
    @TableField("sys_ai_app_key_expire_datetime")
    private LocalDateTime sysAiAppKeyExpireDatetime;

    /**
     * 过期时间戳（秒）
     */
    private Long sysAiAppKeyExpireTime;

    /**
     * 有效天数
     */
    private Integer sysAiAppKeyValidDays;

    /**
     * IP 白名单（逗号分隔）
     */
    private String sysAiAppKeyIpWhitelist;

    /**
     * 最近使用时间戳（毫秒）
     */
    private Long sysAiAppKeyLastUsedTime;

    /**
     * 使用次数
     */
    @TableField("sys_ai_app_key_use_count")
    private Long sysAiAppKeyUseCount;

    /**
     * 次数上限（null 表示不限）
     */
    @TableField("sys_ai_app_key_max_use")
    private Long sysAiAppKeyMaxUse;

    /**
     * Token 总量上限（null 表示不限），用于控制 AppKey 累计消耗的 Token 总数
     */
    @TableField("sys_ai_app_key_max_tokens")
    private Long sysAiAppKeyMaxTokens;

    /**
     * 已使用 Token 累计值，每次对话成功后累加
     */
    @TableField("sys_ai_app_key_total_tokens")
    private Long sysAiAppKeyTotalTokens;

    /**
     * 授权范围
     */
    private String sysAiAppKeyScope;

    /**
     * 备注说明
     */
    private String sysAiAppKeyRemark;

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    private String createName;

    /**
     * 创建人 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 更新人名称
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateName;

    /**
     * 更新人 ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Integer updateBy;
}


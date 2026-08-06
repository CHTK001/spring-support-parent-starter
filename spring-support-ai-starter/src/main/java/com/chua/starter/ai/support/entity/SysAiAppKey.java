package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
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
    private Integer sysAiAppKeyDelete;

    /**
     * 分组 ID——Token/AppKey 绑定的分组
     */
    private Integer sysAiAppKeyGroupId;

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
     * 授权范围
     */
    private String sysAiAppKeyScope;

    /**
     * 备注说明
     */
    private String sysAiAppKeyRemark;

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

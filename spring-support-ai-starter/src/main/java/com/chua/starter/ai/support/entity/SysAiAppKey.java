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
* AI 应用密钥实体——对应 sys_AI_app_Key 表。
* <p>
* appKey 用于 打开AI 兼容协议的身份认证（Bearer 令牌 / x-AI-app-Key 头部），
* 每个 appKey 绑定到一个分组，通过分组控制可访问的模型范围。
*
* @author CH
* @since 4.0.0
 */
@Data
@TableName("sys_ai_app_key")
public class SysAiAppKey {

    /**
    * 应用密钥主键 标识
    */
    @TableId(value = "sys_ai_app_key_id", type = IdType.AUTO)
    /** SYSAIAPP密钥标识 */
    private Integer id;

    /**
    * 应用 标识
    */
    @TableField("sys_ai_app_key_app_id")
    private String appId; // appid

    /**
    * 访问 Key
    */
    @TableField("sys_ai_app_key_app_key")
    private String appKey; // appKey

    /**
    * 访问 Secret
    */
    @TableField("sys_ai_app_key_secret")
    private String secret; // secret

    /**
    * 密钥名称
    */
    @TableField("sys_ai_app_key_name")
    private String name; // 名称

    /**
    * 所属用户 标识
    */
    private Integer sysUserId;

    /**
    * 状态：1 启用，0 禁用
    */
    @TableField("sys_ai_app_key_status")
    private Integer status; // 状态

    /**
    * 删除标记：0 未删除，1 已删除
    */
    @TableField("sys_ai_app_key_delete")
    @TableLogic
    private Integer delete; // 删除

    /**
    * 分组 标识——令牌/appKey 绑定的分组
    */
    @TableField("sys_ai_app_key_group_id")
    private Integer groupId; // 群体标识

    /**
    * 关联供应商 标识（面板层字段）
    */
    @TableField("sys_ai_app_key_provider_id")
    private Long providerId; // 提供者标识

    /**
    * 限定使用的模型（面板层字段）
    */
    @TableField("sys_ai_app_key_model")
    private String model; // 模型

    /**
    * 是否启用（面板层字段，与状态列互斥使用）
    */
    @TableField("sys_ai_app_key_enabled")
    private Boolean enabled; // 已启用

    /**
    * 过期时间（DATETIME，面板层字段，与 轮次 秒过期列并存）
    */
    @TableField("sys_ai_app_key_expire_datetime")
    private LocalDateTime expireDatetime; // expiredatetime

    /**
    * 过期时间戳（秒）
    */
    @TableField("sys_ai_app_key_expire_time")
    private Long expireTime; // expire时间

    /**
    * 有效天数
    */
    @TableField("sys_ai_app_key_valid_days")
    private Integer validDays; // validdays

    /**
    * IP 白名单（逗号分隔）
    */
    @TableField("sys_ai_app_key_ip_whitelist")
    private String ipWhitelist; // ipwhitelist

    /**
    * 最近使用时间戳（毫秒）
    */
    @TableField("sys_ai_app_key_last_used_time")
    private Long lastUsedTime; // 最后一个used时间

    /**
    * 使用次数
    */
    @TableField("sys_ai_app_key_use_count")
    private Long useCount; // use数量

    /**
    * 次数上限（空 表示不限）
    */
    @TableField("sys_ai_app_key_max_use")
    private Long maxUse; // 最大use

    /**
    * 令牌 总量上限（空 表示不限），用于控制 appKey 累计消耗的 令牌 总数
    */
    @TableField("sys_ai_app_key_max_tokens")
    private Long maxTokens; // 最大令牌

    /**
    * 已使用 令牌 累计值，每次对话成功后累加
    */
    @TableField("sys_ai_app_key_total_tokens")
    private Long totalTokens; // total令牌

    /**
    * 授权范围
    */
    @TableField("sys_ai_app_key_scope")
    private String scope; // scope

    /**
    * 备注说明
    */
    @TableField("sys_ai_app_key_remark")
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


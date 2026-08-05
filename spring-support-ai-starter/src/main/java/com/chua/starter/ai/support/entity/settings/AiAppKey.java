package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AppKey 表，存储供应商接入凭证与过期策略。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_app_key")
@Schema(name = "AppKey")
public class AiAppKey implements Serializable {

    /**
     * AppKey 主键 ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "AppKey 主键 ID")
    private Long appKeyId;

    /**
     * AppKey 名称
     */
    @TableField("app_key_name")
    @Schema(description = "AppKey 名称")
    private String appKeyName;

    /**
     * AppKey 密钥值（敏感信息）
     */
    @TableField("app_key_value")
    @Schema(description = "AppKey 密钥值")
    private String appKeyValue;

    /**
     * 关联供应商 ID
     */
    @TableField("app_key_provider_id")
    @Schema(description = "关联供应商 ID")
    private Long appKeyProviderId;

    /**
     * 限定使用的模型（可空）
     */
    @TableField("app_key_model")
    @Schema(description = "限定使用的模型")
    private String appKeyModel;

    /**
     * 绑定分组 ID（分组的 key 只能用分组下的模型）
     */
    @TableField("app_key_group_id")
    @Schema(description = "绑定分组 ID")
    private Long appKeyGroupId;

    /**
     * 是否启用
     */
    @TableField("app_key_enabled")
    @Schema(description = "是否启用")
    private Boolean appKeyEnabled;

    /**
     * 已使用次数
     */
    @TableField("app_key_use_count")
    @Schema(description = "已使用次数")
    private Long appKeyUseCount;

    /**
     * 过期时间
     */
    @TableField("app_key_expire_time")
    @Schema(description = "过期时间")
    private LocalDateTime appKeyExpireTime;

    /**
     * 创建时间
     */
    @TableField(value = "app_key_create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime appKeyCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "app_key_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime appKeyUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("app_key_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer appKeyDeleted;
}

package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel("AppKey")
public class AiAppKey implements Serializable {

    /**
     * AppKey 主键 ID
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("AppKey 主键 ID")
    private Long appKeyId;

    /**
     * AppKey 名称
     */
    @TableField("app_key_name")
    @ApiModelProperty("AppKey 名称")
    private String appKeyName;

    /**
     * AppKey 密钥值（敏感信息）
     */
    @TableField("app_key_value")
    @ApiModelProperty("AppKey 密钥值")
    private String appKeyValue;

    /**
     * 关联供应商 ID
     */
    @TableField("app_key_provider_id")
    @ApiModelProperty("关联供应商 ID")
    private Long appKeyProviderId;

    /**
     * 限定使用的模型（可空）
     */
    @TableField("app_key_model")
    @ApiModelProperty("限定使用的模型")
    private String appKeyModel;

    /**
     * 是否启用
     */
    @TableField("app_key_enabled")
    @ApiModelProperty("是否启用")
    private Boolean appKeyEnabled;

    /**
     * 已使用次数
     */
    @TableField("app_key_use_count")
    @ApiModelProperty("已使用次数")
    private Long appKeyUseCount;

    /**
     * 过期时间
     */
    @TableField("app_key_expire_time")
    @ApiModelProperty("过期时间")
    private LocalDateTime appKeyExpireTime;

    /**
     * 创建时间
     */
    @TableField(value = "app_key_create_time", fill = FieldFill.INSERT)
    @ApiModelProperty("创建时间")
    private LocalDateTime appKeyCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "app_key_update_time", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime appKeyUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("app_key_deleted")
    @TableLogic
    @ApiModelProperty("逻辑删除标记")
    private Integer appKeyDeleted;
}
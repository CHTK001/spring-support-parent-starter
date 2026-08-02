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
 * 模型供应商表，存储 AI 提供商的基础信息、接入地址、可用模型与启用状态。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_provider")
@ApiModel("模型供应商")
public class AiProvider implements Serializable {

    /**
     * 供应商主键 ID
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("供应商主键 ID")
    private Long providerId;

    /**
     * 供应商名称（如 OpenAI、Claude）
     */
    @TableField("provider_name")
    @ApiModelProperty("供应商名称")
    private String providerName;

    /**
     * 供应商类型（openai / anthropic / gemini / qwen / custom）
     */
    @TableField("provider_type")
    @ApiModelProperty("供应商类型")
    private String providerType;

    /**
     * 接入 Base URL
     */
    @TableField("provider_base_url")
    @ApiModelProperty("接入 Base URL")
    private String providerBaseUrl;

    /**
     * 提供商 API Key
     */
    @TableField("provider_api_key")
    @ApiModelProperty("API Key")
    private String providerApiKey;

    /**
     * Logo 图片地址
     */
    @TableField("provider_logo")
    @ApiModelProperty("Logo 图片地址")
    private String providerLogo;

    /**
     * 可用模型列表（JSON 字符串）
     */
    @TableField("provider_models")
    @ApiModelProperty("可用模型列表(JSON)")
    private String providerModels;

    /**
     * 是否启用
     */
    @TableField("provider_enabled")
    @ApiModelProperty("是否启用")
    private Boolean providerEnabled;

    /**
     * 创建时间
     */
    @TableField(value = "provider_create_time", fill = FieldFill.INSERT)
    @ApiModelProperty("创建时间")
    private LocalDateTime providerCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "provider_update_time", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime providerUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("provider_deleted")
    @TableLogic
    @ApiModelProperty("逻辑删除标记")
    private Integer providerDeleted;
}
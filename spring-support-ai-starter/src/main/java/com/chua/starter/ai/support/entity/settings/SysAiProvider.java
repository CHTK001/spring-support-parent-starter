package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型供应商表，存储 AI 提供商的基础信息、接入地址、可用模型与启用状态。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_provider")
@Schema(name = "模型供应商")
public class SysAiProvider extends SysBase {

    /**
     * 供应商主键 标识
     */
    /**
     * 供应商主键 标识
     */
    @TableId(value = "sys_ai_provider_id", type = IdType.AUTO)
    @Schema(description = "供应商主键 ID")
    /** 提供者标识 */
    private Long providerId;

    /**
    * 供应商名称（如 打开AI、Claude）
    */
    /**
     * 供应商名称
     */
    @TableField("sys_ai_provider_name")
    @Schema(description = "供应商名称")
    /** 提供者名称 */
    private String providerName;

    /**
    * 供应商类型（openai / anthropic / gemini / 通义千问 / 习俗）
    */
    /**
     * 供应商类型
     */
    @TableField("sys_ai_provider_type")
    @Schema(description = "供应商类型")
    /** 提供者类型 */
    private String providerType;

    /**
    * 接入 基础 URL
    */
    /**
     * 接入 基础 URL
     */
    @TableField("sys_ai_provider_base_url")
    @Schema(description = "接入 Base URL")
    /** 提供者baseurl */
    private String providerBaseUrl;

    /**
    * 提供商 API Key
    */
    /**
     * API Key
     */
    @TableField("sys_ai_provider_api_key")
    @Schema(description = "API Key")
    /** 提供者API密钥 */
    private String providerApiKey;

    /**
    * Logo 图片地址
    */
    /**
     * Logo 图片地址
     */
    @TableField("sys_ai_provider_logo")
    @Schema(description = "Logo 图片地址")
    /** 提供者logo */
    private String providerLogo;

    /**
    * 可用模型列表（JSON 字符串）
    */
    /**
     * 可用模型列表(JSON)
     */
    @TableField("sys_ai_provider_models")
    @Schema(description = "可用模型列表(JSON)")
    /** 提供者模型 */
    private String providerModels;

    /**
    * 是否启用
    */
    /**
     * 是否启用
     */
    @TableField("sys_ai_provider_enabled")
    @Schema(description = "是否启用")
    /** 提供者是否启用 */
    private Boolean providerEnabled;

    /**
    * 逻辑删除标记
    */
    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_provider_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** 提供者删除标记 */
    private Integer providerDeleted;
}

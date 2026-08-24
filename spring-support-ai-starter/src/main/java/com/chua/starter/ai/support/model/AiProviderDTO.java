package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模型供应商 DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "模型供应商 DTO")
public class AiProviderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 供应商主键 ID
     */
    @Schema(description = "供应商主键 ID")
    /** ID */
    private Long id;

    /**
     * 供应商名称
     */
    @Schema(description = "供应商名称")
    /** 名称 */
    private String name;

    /**
     * 供应商类型
     */
    @Schema(description = "供应商类型")
    /** 类型 */
    private String type;

    /**
     * Logo 图片地址
     */
    @Schema(description = "Logo 图片地址")
    /** Logo */
    private String logo;

    /**
     * 接入 Base URL
     */
    @Schema(description = "接入 Base URL")
    /** BaseURL */
    private String baseUrl;

    /**
     * API Key
     */
    @Schema(description = "API Key")
    /** API密钥 */
    private String apiKey;

    /**
     * 可用模型列表
     */
    @Schema(description = "可用模型列表")
    /** Models */
    private List<String> models;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    /** 是否启用 */
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    /** 创建时间AT */
    private String createdAt;
}
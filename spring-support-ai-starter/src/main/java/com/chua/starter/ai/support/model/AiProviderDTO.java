package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模型供应商 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("模型供应商 DTO")
public class AiProviderDTO implements Serializable {

    /**
     * 供应商主键 ID
     */
    @ApiModelProperty("供应商主键 ID")
    private Long id;

    /**
     * 供应商名称
     */
    @ApiModelProperty("供应商名称")
    private String name;

    /**
     * 供应商类型
     */
    @ApiModelProperty("供应商类型")
    private String type;

    /**
     * Logo 图片地址
     */
    @ApiModelProperty("Logo 图片地址")
    private String logo;

    /**
     * 接入 Base URL
     */
    @ApiModelProperty("接入 Base URL")
    private String baseUrl;

    /**
     * 提供商 API Key
     */
    @ApiModelProperty("API Key")
    private String apiKey;

    /**
     * 可用模型列表
     */
    @ApiModelProperty("可用模型列表")
    private List<String> models;

    /**
     * 是否启用
     */
    @ApiModelProperty("是否启用")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private String createdAt;
}

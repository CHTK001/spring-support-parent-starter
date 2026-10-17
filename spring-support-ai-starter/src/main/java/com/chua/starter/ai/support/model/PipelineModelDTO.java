package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 管线成员模型 DTO，前端能力页按 required 拆分必填与非必填两张表。
 *
 * @param modelId    模型主键
 * @param name       模型名称
 * @param provider   供应商名称
 * @param capability 命中该管线的能力
 * @param enabled    是否启用
 * @param required   是否为该管线的必填模型
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "管线成员模型 DTO")
public record PipelineModelDTO(
        @Schema(description = "模型主键") Long modelId,
        @Schema(description = "模型名称") String name,
        @Schema(description = "供应商名称") String provider,
        @Schema(description = "命中该管线的能力") String capability,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "是否为该管线的必填模型") Boolean required) implements Serializable {

    private static final long serialVersionUID = 1L;
}

package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 管线成员模型 DTO，前端能力页按 required 拆分必填与非必填两张表。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "管线成员模型 DTO")
public class PipelineModelDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "模型主键")
    private Long modelId;

    @Schema(description = "模型名称")
    private String name;

    @Schema(description = "供应商名称")
    private String provider;

    @Schema(description = "命中该管线的能力")
    private String capability;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否为该管线的必填模型")
    private Boolean required;
}

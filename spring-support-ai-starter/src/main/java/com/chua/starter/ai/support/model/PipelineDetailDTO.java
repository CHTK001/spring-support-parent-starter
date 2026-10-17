package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * AI 管线详情 DTO，字段命名与前端能力页面板对齐。
 *
 * @param code                 管线编码
 * @param name                 管线名称
 * @param group                所属分组
 * @param tag                  标签
 * @param required             必填能力列表
 * @param optional             非必填能力列表
 * @param requiredCapabilities 必填能力列表（前端能力页字段名）
 * @param optionalCapabilities 非必填能力列表（前端能力页字段名）
 * @param description          管线说明
 * @param route                前端路由
 * @param configGroup          配置分组
 * @param kind                 条目类型：pipeline / globalBean
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "AI 管线详情 DTO")
public record PipelineDetailDTO(
        @Schema(description = "管线编码") String code,
        @Schema(description = "管线名称") String name,
        @Schema(description = "所属分组") String group,
        @Schema(description = "标签") String tag,
        @Schema(description = "必填能力列表") List<String> required,
        @Schema(description = "非必填能力列表") List<String> optional,
        @Schema(description = "必填能力列表（前端能力页字段名）") List<String> requiredCapabilities,
        @Schema(description = "非必填能力列表（前端能力页字段名）") List<String> optionalCapabilities,
        @Schema(description = "管线说明") String description,
        @Schema(description = "前端路由") String route,
        @Schema(description = "配置分组") String configGroup,
        @Schema(description = "条目类型：pipeline / globalBean") String kind) implements Serializable {

    private static final long serialVersionUID = 1L;
}

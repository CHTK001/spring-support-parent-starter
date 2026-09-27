package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 管线详情 DTO，字段命名与前端能力页面板对齐。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "AI 管线详情 DTO")
public class PipelineDetailDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "管线编码")
    private String code;

    @Schema(description = "管线名称")
    private String name;

    @Schema(description = "所属分组")
    private String group;

    @Schema(description = "标签")
    private String tag;

    @Schema(description = "必填能力列表")
    private List<String> required;

    @Schema(description = "非必填能力列表")
    private List<String> optional;

    @Schema(description = "必填能力列表（前端能力页字段名）")
    private List<String> requiredCapabilities;

    @Schema(description = "非必填能力列表（前端能力页字段名）")
    private List<String> optionalCapabilities;

    @Schema(description = "管线说明")
    private String description;

    @Schema(description = "前端路由")
    private String route;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "条目类型：pipeline / globalBean")
    private String kind;
}

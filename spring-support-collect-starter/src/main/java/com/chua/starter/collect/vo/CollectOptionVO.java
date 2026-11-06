package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 选项出参，用于分类、数据源等下拉框。
 *
 * @param value 选项标识
 * @param label 选项名称
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-选项出参")
public record CollectOptionVO(
        @Schema(description = "选项标识")
        String value,
        @Schema(description = "选项名称")
        String label) {
}

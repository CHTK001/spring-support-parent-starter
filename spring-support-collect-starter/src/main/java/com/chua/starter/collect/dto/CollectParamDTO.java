package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 名称值对。
 *
 * <p>流程节点的同名可重复参数（请求头、查询参数、Cookie、变量赋值）统一用它承载，
 * 避免以 Map 作为接口出入参。</p>
 *
 * @param name        参数名
 * @param value       参数值，支持 ${} 表达式
 * @param description 说明或表头名称，仅用于界面上列提示
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "名称值对")
public record CollectParamDTO(
        @Schema(description = "参数名")
        String name,
        @Schema(description = "参数值")
        String value,
        @Schema(description = "说明")
        String description) {
}

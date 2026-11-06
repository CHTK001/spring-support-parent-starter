package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据表列元数据出参，供流程设计器配置字段映射时选择列。
 *
 * <p>数据来自 {@link java.sql.DatabaseMetaData#getColumns}，因此不依赖任何具体数据库方言；
 * 取不到的注释、精度等一律给空值或默认值，不让元数据缺失变成一次失败的配置。</p>
 *
 * @param name       列名，写库时的目标列
 * @param label      列注释，取不到时为空
 * @param position   列序号，从 1 开始，按表内定义顺序排列
 * @param typeName   数据库原生类型名，例如 {@code varchar(255)}、{@code bigint}
 * @param size       类型精度，例如字符串长度、数字总位数
 * @param nullable   是否可空
 * @param primaryKey 是否主键列，供界面提示「去重/覆盖」可用的自然键
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-表列元数据出参")
public record CollectColumnVO(
        @Schema(description = "列名")
        String name,
        @Schema(description = "列注释")
        String label,
        @Schema(description = "列序号")
        Integer position,
        @Schema(description = "数据库原生类型")
        String typeName,
        @Schema(description = "类型精度")
        Integer size,
        @Schema(description = "是否可空")
        Boolean nullable,
        @Schema(description = "是否主键列")
        Boolean primaryKey) {
}

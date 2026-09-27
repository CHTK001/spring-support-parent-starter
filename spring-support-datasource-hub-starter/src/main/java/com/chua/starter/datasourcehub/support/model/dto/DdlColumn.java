package com.chua.starter.datasourcehub.support.model.dto;

/**
 * 建表列定义（DDL 请求用）。
 *
 * @param name         列名，必须合法标识符
 * @param type         列类型（含长度，如 {@code VARCHAR(255)}），可为空表示按 {@code sourceType} 推断
 * @param nullable     是否可空，可为空表示默认可空
 * @param comment      列注释，可为空
 * @param defaultValue 默认值，可为空
 * @param sourceField  来源字段名（采集侧字段），用于生成"来源→列"的映射说明，可为空
 * @param sourceType   来源字段类型（如 STRING / INTEGER / DECIMAL / BOOLEAN / DATE / DATETIME / JSON），
 *                     仅在 {@code type} 为空时参与类型推断
 * @author CH
 * @since 4.0.0.42
 */
public record DdlColumn(
        String name,
        String type,
        Boolean nullable,
        String comment,
        String defaultValue,
        String sourceField,
        String sourceType) {
}

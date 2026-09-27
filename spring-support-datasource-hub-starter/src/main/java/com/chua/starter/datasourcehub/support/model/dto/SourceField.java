package com.chua.starter.datasourcehub.support.model.dto;

/**
 * 来源字段（字段映射的输入侧）。
 *
 * @param name 字段名
 * @param type 字段类型（STRING / INTEGER / DECIMAL / BOOLEAN / DATE / DATETIME / JSON），
 *             为空时按 STRING 推断
 * @author CH
 * @since 4.0.0.42
 */
public record SourceField(String name, String type) {
}

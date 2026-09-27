package com.chua.starter.monitor.support.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 数据库表字段元数据条目
 *
 * @param name         字段名
 * @param type         字段类型名
 * @param size         字段长度
 * @param nullable     是否可为空
 * @param remark       字段注释
 * @param defaultValue 默认值，JSON 键为 default
 * @param position     字段序号，从 1 开始
 * @param primaryKey   是否主键，JSON 键为 isPrimaryKey
 * @author CH
 * @since 4.0.0.42
 */
public record DbColumnVO(
        String name,
        String type,
        int size,
        boolean nullable,
        String remark,
        @JsonProperty("default") String defaultValue,
        int position,
        @JsonProperty("isPrimaryKey") boolean primaryKey
) {
}

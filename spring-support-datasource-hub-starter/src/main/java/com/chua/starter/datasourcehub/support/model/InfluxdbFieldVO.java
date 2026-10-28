package com.chua.starter.datasourcehub.support.model;

/**
 * InfluxDB 度量字段信息
 *
 * <p>用于数据库管理器的列展示：tag 作为维度列、field 作为度量列。
 * 类型为 InfluxDB 原生类型（integer/float/string/boolean/time/tag）。</p>
 *
 * @param name 字段名
 * @param type 类型（tag / integer / float / string / boolean / time / timestamp）
 * @param tag  是否为标签（true=tag，false=field）
 * @author CH
 * @since 4.0.0.44
 */
public record InfluxdbFieldVO(
        String name,
        String type,
        boolean tag
) {
}

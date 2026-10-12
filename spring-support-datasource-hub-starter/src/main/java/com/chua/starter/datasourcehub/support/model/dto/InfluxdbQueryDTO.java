package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * InfluxDB 查询请求
 *
 * @param flux         查询语句：v2 为 Flux，v3 为 SQL（原样提交给 InfluxDB）
 * @param datasourceId 数据源 ID，为空时取第一个 influxdb 类型数据源
 * @param database     v3 目标数据库，为空时取实例第一个数据库（v2 忽略，语句自带 from(bucket)）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfluxdbQueryDTO(
        String flux,
        Long datasourceId,
        String database
) {
}

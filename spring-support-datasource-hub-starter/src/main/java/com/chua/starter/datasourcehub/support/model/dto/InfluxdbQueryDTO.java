package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * InfluxDB Flux 查询请求
 *
 * @param flux         Flux 语句，原样提交给 InfluxDB
 * @param datasourceId 数据源 ID，为空时取第一个 influxdb 类型数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfluxdbQueryDTO(
        String flux,
        Long datasourceId
) {
}

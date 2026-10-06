package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 写入 InfluxDB 数据点请求（line protocol）。
 *
 * @param bucket       目标 Bucket 名
 * @param precision    时间精度（ns/us/ms/s），空按 ns
 * @param lines        line protocol 文本，一行一个点，换行分隔
 * @param datasourceId 数据源 ID，为空时取第一个 influxdb 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfluxdbWriteDTO(
        String bucket,
        String precision,
        String lines,
        Long datasourceId
) {
}

package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 删除 InfluxDB 测量请求。
 *
 * @param bucket          目标 Bucket 名 / 数据库名
 * @param measurement     度量名（InfluxDB 的「表」），空表示删除操作针对桶内全部数据之外的配置
 * @param predicate       可选的附加谓词（v2 delete 的 predicate 片段；v1/v3 按时间范围拼）
 * @param start           时间范围起点（RFC3339，可空）
 * @param stop            时间范围终点（RFC3339，可空）
 * @param datasourceId    数据源 ID，为空时取第一个 influxdb 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfluxdbMeasurementDeleteDTO(
        String bucket,
        String measurement,
        String predicate,
        String start,
        String stop,
        Long datasourceId
) {
}
package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 新建 / 编辑 InfluxDB Bucket 请求。
 *
 * <p>编辑保留期时只使用 {@code retentionHours}；新建时使用 name/retentionHours/description。</p>
 *
 * @param name           Bucket 名（新建必填）
 * @param retentionHours 保留时长（小时），空或非正表示永久保留
 * @param description    描述，可空
 * @param datasourceId   数据源 ID，为空时取第一个 influxdb 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfluxdbBucketDTO(
        String name,
        Long retentionHours,
        String description,
        Long datasourceId
) {
}

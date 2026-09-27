package com.chua.starter.monitor.support.model;

/**
 * InfluxDB 度量（Measurement）列表行
 *
 * <p>字段数、标签数由 {@code schema.tagKeys/fieldKeys} 聚合得到；最后写入时间只回看固定窗口，
 * 窗口内无数据的度量留空而不是显示为「从未写入」。</p>
 *
 * @param name          度量名
 * @param fieldCount    字段数，统计失败时为 null
 * @param tagCount      标签数，统计失败时为 null
 * @param lastWriteTime 最近写入时间（可读文本），窗口内无数据时为空
 * @author CH
 * @since 4.0.0.42
 */
public record InfluxdbMeasurementVO(
        String name,
        Integer fieldCount,
        Integer tagCount,
        String lastWriteTime
) {
}

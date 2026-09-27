package com.chua.starter.monitor.support.model;

/**
 * 告警趋势点（按小时分桶）
 *
 * @param time  桶标签，跨度不超过 {@code DAY_SPAN_HOURS} 时为 HH:00，否则为 MM-dd HH:00
 * @param count 该桶内的告警事件数
 * @author CH
 * @since 4.0.0.42
 */
public record AlarmTrendPointVO(String time, long count) {
}

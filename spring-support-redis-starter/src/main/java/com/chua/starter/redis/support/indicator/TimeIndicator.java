package com.chua.starter.redis.support.indicator;

/**
 * 时间指标数据类
 * <p>
 * 用于表示时间序列数据点，包含时间戳和对应的值。
 * </p>
 *
 * @param indicator 指标名称
 * @param timestamp 时间戳
 * @param value     指标值
 * @since 2024/7/4
 * @author CH
 */
public record TimeIndicator(
        String indicator,
        long timestamp,
        double value) {
}

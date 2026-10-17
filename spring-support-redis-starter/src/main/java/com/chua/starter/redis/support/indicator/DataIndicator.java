package com.chua.starter.redis.support.indicator;

/**
 * 数据指标类
 * <p>
 * 用于表示通用数据指标，包含时间戳、值和持久化标志。
 * </p>
 *
 * @param indicator   指标名称
 * @param timestamp   时间戳
 * @param value       指标值（字符串形式）
 * @param persistence 是否持久化
 * @since 2024/7/4
 * @author CH
 */
public record DataIndicator(
        String indicator,
        long timestamp,
        String value,
        boolean persistence) {
}

package com.chua.starter.monitor.support.model;

/**
 * Redis 哈希字段
 *
 * @param field 字段名
 * @param value 字段值
 * @author CH
 * @since 4.0.0.42
 */
public record RedisHashFieldVO(
        String field,
        String value
) {
}

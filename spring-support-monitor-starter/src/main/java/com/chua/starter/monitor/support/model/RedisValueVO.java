package com.chua.starter.monitor.support.model;

/**
 * Redis 无下标元素（集合成员、列表追加值）
 *
 * @param value 元素值
 * @author CH
 * @since 4.0.0.42
 */
public record RedisValueVO(
        String value
) {
}

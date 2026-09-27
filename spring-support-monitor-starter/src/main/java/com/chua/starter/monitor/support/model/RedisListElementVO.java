package com.chua.starter.monitor.support.model;

/**
 * Redis 列表元素
 *
 * @param index 下标，覆盖元素时必填；追加元素时忽略
 * @param value 元素值
 * @author CH
 * @since 4.0.0.42
 */
public record RedisListElementVO(
        Integer index,
        String value
) {
}

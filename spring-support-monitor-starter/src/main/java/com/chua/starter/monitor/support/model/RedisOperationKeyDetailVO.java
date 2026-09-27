package com.chua.starter.monitor.support.model;

/**
 * 监控任务侧 Redis 键详情
 *
 * @param key   键名
 * @param value 键值，键不存在时为空串
 * @param ttl   剩余过期秒数，-1 表示永不过期
 * @param type  键类型
 * @param size  值长度
 * @author CH
 * @since 4.0.0.42
 */
public record RedisOperationKeyDetailVO(
        String key,
        String value,
        Long ttl,
        String type,
        int size
) {
}

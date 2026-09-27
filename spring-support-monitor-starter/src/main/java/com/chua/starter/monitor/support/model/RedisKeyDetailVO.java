package com.chua.starter.monitor.support.model;

/**
 * Redis 键详情
 *
 * <p>value 的类型随键类型变化：STRING 为字符串，LIST/SET 为数组，HASH 为对象，
 * ZSET 为 {@link RedisMemberScoreVO} 数组，故保留 Object 承载。</p>
 *
 * @param key   键名
 * @param type  键类型（STRING/LIST/SET/HASH/ZSET/none）
 * @param ttl   剩余过期秒数，-1 表示永不过期
 * @param value 键值
 * @param size  元素数量，STRING 类型为字符长度
 * @author CH
 * @since 4.0.0.42
 */
public record RedisKeyDetailVO(
        String key,
        String type,
        long ttl,
        Object value,
        Long size
) {
}

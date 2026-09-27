package com.chua.starter.monitor.support.model;

/**
 * Redis 键浏览器列表项（扫描结果中的单键摘要）
 *
 * @param key  键名
 * @param type 键类型（STRING/HASH/LIST/SET/ZSET/STREAM）
 * @param ttl  剩余过期秒数，-1 表示永不过期，-2 表示键已不存在
 * @author CH
 * @since 4.0.0.42
 */
public record RedisManageKeyVO(
        String key,
        String type,
        long ttl
) {
}

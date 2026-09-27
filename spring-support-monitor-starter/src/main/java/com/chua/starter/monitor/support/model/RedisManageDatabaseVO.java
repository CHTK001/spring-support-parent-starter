package com.chua.starter.monitor.support.model;

/**
 * Redis 单个逻辑库（dbN）的键统计
 *
 * @param db      库序号（0-15）
 * @param keys    库内键总数
 * @param expires 带过期时间的键数量
 * @author CH
 * @since 4.0.0.42
 */
public record RedisManageDatabaseVO(
        int db,
        long keys,
        long expires
) {
}

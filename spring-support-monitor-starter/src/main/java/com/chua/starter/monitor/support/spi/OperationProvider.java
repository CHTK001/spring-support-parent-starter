package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.enums.ServiceType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 操作 Provider SPI
 * <p>
 * 提供对被监控实例的运行时操作(Redis key CRUD / 数据库 SQL 等),
 * 不参与采集调度, 仅响应外部请求。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
public interface OperationProvider {

    /**
     * 该 Provider 处理的服务类型
     *
     * @return 服务类型
     */
    ServiceType serviceType();

    /**
     * 扫描 Redis key
     *
     * @param ctx 操作上下文
     * @param pattern 匹配模式
     * @param cursor 游标, null 表示从头开始
     * @param count 期望返回条数
     * @return 响应式结果, 包含 keys 与下一次 cursor
     */
    default Mono<ScanResult> scanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 scanKeys"));
    }

    /**
     * 读取单个 key 的值
     *
     * @param ctx 操作上下文
     * @param key 目标 key
     * @return 响应式值
     */
    default Mono<String> getValue(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 getValue"));
    }

    /**
     * 设置 key 的值
     *
     * @param ctx 操作上下文
     * @param key 目标 key
     * @param value 值
     * @param ttl 过期时间, null 表示不过期
     * @return 响应式结果
     */
    default Mono<Boolean> setValue(CollectContext ctx, String key, String value, Duration ttl) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 setValue"));
    }

    /**
     * 删除 keys
     *
     * @param ctx 操作上下文
     * @param keys 目标 keys
     * @return 响应式删除条数
     */
    default Mono<Long> deleteKeys(CollectContext ctx, List<String> keys) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 deleteKeys"));
    }

    /**
     * 设置 key 过期时间
     *
     * @param ctx 操作上下文
     * @param key 目标 key
     * @param ttl 过期时间
     * @return 响应式结果
     */
    default Mono<Boolean> expire(CollectContext ctx, String key, Duration ttl) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 expire"));
    }

    /**
     * 获取 key 的剩余存活时间
     *
     * @param ctx 操作上下文
     * @param key 目标 key
     * @return TTL（秒），-1 表示永久，-2 表示 key 不存在
     */
    default Mono<Long> ttl(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 ttl"));
    }

    /**
     * 获取 key 的类型
     *
     * @param ctx 操作上下文
     * @param key 目标 key
     * @return key 类型（STRING, LIST, HASH, SET, ZSET 等）
     */
    default Mono<String> type(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 type"));
    }

    /**
     * 扫描结果
     *
     * @param keys 本次返回的 keys
     * @param nextCursor 下一次游标, null 表示已结束
     */
    public record ScanResult(List<String> keys, String nextCursor) {
    }
}
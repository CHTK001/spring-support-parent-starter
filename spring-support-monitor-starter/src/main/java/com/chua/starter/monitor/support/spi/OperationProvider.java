package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.enums.ServiceType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 操作 提供者 SPI
 * <p>
 * 提供对被监控实例的运行时操作(Redis 键 CRUD / 数据库 SQL 等),
 * 不参与采集调度, 仅响应外部请求。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
public interface OperationProvider {

    /**
     * 该 提供者 处理的服务类型
     *
     * @return 服务类型
     */
    ServiceType serviceType();

    /**
     * 扫描 Redis 键
     *
     * @param ctx 操作上下文
     * @param pattern 匹配模式
     * @param cursor 游标, 空 表示从头开始
     * @param count 期望返回条数
     * @return 响应式结果, 包含 键 与下一次 Cursor
     */
    default Mono<ScanResult> scanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 scanKeys"));
    }

    /**
     * 读取单个 键 的值
     *
     * @param ctx 操作上下文
     * @param key 目标 键
     * @return 响应式值
     */
    default Mono<String> getValue(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 getValue"));
    }

    /**
     * 设置 键 的值
     *
     * @param ctx 操作上下文
     * @param key 目标 键
     * @param value 值
     * @param ttl 过期时间, 空 表示不过期
     * @return 响应式结果
     */
    default Mono<Boolean> setValue(CollectContext ctx, String key, String value, Duration ttl) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 setValue"));
    }

    /**
     * 删除 键
     *
     * @param ctx 操作上下文
     * @param keys 目标 键
     * @return 响应式删除条数
     */
    default Mono<Long> deleteKeys(CollectContext ctx, List<String> keys) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 deleteKeys"));
    }

    /**
     * 设置 键 过期时间
     *
     * @param ctx 操作上下文
     * @param key 目标 键
     * @param ttl 过期时间
     * @return 响应式结果
     */
    default Mono<Boolean> expire(CollectContext ctx, String key, Duration ttl) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 expire"));
    }

    /**
     * 获取 键 的剩余存活时间
     *
     * @param ctx 操作上下文
     * @param key 目标 键
     * @return TTL（秒），-1 表示永久，-2 表示 键 不存在
     */
    default Mono<Long> ttl(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 ttl"));
    }

    /**
     * 获取 键 的类型
     *
     * @param ctx 操作上下文
     * @param key 目标 键
     * @return key 类型（字符串, 列表, 哈希, 设置, ZSET 等）
     */
    default Mono<String> type(CollectContext ctx, String key) {
        return Mono.error(new UnsupportedOperationException("该 Provider 不支持 type"));
    }

    /**
     * 扫描结果
     *
     * @param keys 本次返回的 键
     * @param nextCursor 下一次游标, 空 表示已结束
     * @return 扫描结果的结果
     */
    public record ScanResult(List<String> keys, String nextCursor) {

        /**
         * 规范构造器：非空时对 键 列表做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。本 record 是对外 SPI：
         * 调用方 {@code MonitorController} 两处显式判空 {@code keys() != null}，
         * 说明 null 是被允许的状态；外部实现也可能给出含 null 元素的列表，
         * 故保留 null 语义并采用可含 null 元素的不可变包装。</p>
         *
         * @param keys 本次返回的 键，可为 null
         */
        public ScanResult {
            keys = keys == null ? null : Collections.unmodifiableList(new ArrayList<>(keys));
        }
    }
}

package com.chua.starter.strategy.support;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 策略模块 Redis 能力抽象。
 * <p>
 * 核心策略组件只依赖这个接口，从而避免在未引入 Redis 依赖时
 * 因为类签名中出现 Spring Data Redis 类型而触发类加载失败。
 * </p>
 *
 * @author CH
 * @since 2026-03-26
 */
public interface StrategyRedisSupport {

    /**
     * Redis key/value 能力是否可用。
     *
     * @return true 表示可用
     */
    boolean isAvailable();

    /**
     * 设置键值，键不存在时才写入。
     *
     * @param key 键
     * @param value 值
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true 表示写入成功
     */
    boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit);

    /**
     * 删除指定键。
     *
     * @param key 键
     */
    void delete(String key);

    /**
     * 仅当 value 匹配时删除 key。
     *
     * @param key 键
     * @param expectedValue 期望值
     * @return 删除数量
     */
    long deleteIfValueMatches(String key, String expectedValue);

    /**
     * 发布消息。
     *
     * @param channel 频道
     * @param payload 消息
     */
    void publish(String channel, String payload);

    /**
     * 订阅消息。
     *
     * @param channel 频道
     * @param consumer 消息处理器
     * @return true 表示订阅已建立
     */
    boolean subscribe(String channel, Consumer<String> consumer);
}

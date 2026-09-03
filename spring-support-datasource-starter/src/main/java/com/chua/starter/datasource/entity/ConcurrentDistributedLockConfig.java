package com.chua.starter.datasource.entity;

import lombok.Data;

/**
 * 分布式锁配置实体，持久化到 SQLite 的 concurrent_distributed_lock 表。
 * <p>
 * 字段与注解 {@code @DistributedLock} 属性对应，通过 actuator 端点增删查改后
 * 清除 {@link com.chua.common.support.concurrent.lock.LockFlow} 缓存，
 * 下次调用时按新配置重建锁提供者。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ConcurrentDistributedLockConfig {

    /** 锁名称，对应 @DistributedLock(name = "...")，也是缓存键 */
    private String name;

    /** 锁类型标识：object（内置 ReentrantLock）、redis、redisson 等，默认 object */
    private String lockType;

    /** 是否公平锁，true 按请求顺序获取锁，默认 false */
    private boolean fair;

    /** 等待锁的最长时间（毫秒），0 表示立即返回 */
    private long waitTimeMs;

    /** 锁租约时间（毫秒），-1 表示永不过期；redis 锁常用此值防止死锁 */
    private long leaseTimeMs;

    /** 是否启用；false 时该锁配置不加载到运行时缓存 */
    private boolean enabled;

    /** 记录创建时间（SQLite datetime('now')） */
    private long createdAt;

    /** 记录更新时间（SQLite datetime('now')） */
    private long updatedAt;
}

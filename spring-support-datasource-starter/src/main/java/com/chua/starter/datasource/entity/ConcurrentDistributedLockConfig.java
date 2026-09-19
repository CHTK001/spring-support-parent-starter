package com.chua.starter.datasource.entity;

import lombok.Data;

/**
 * 分布式锁配置实体，持久化到 concurrent_distributed_lock 表。
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

    /**
     * 锁名称，对应 @distributed锁(名称 = "...")，也是缓存键
    */
    private String name;

    /**
     * 锁类型标识：对象（内置 reentrant锁）、Redis、redisson 等，默认 对象
    */
    private String lockType;

    /**
     * 是否公平锁，true 按请求顺序获取锁，默认 false
    */
    private boolean fair;

    /**
     * 等待锁的最长时间（毫秒），0 表示立即返回
    */
    private long waitTimeMs;

    /**
     * 锁租约时间（毫秒），-1 表示永不过期；Redis 锁常用此值防止死锁
    */
    private long leaseTimeMs;

    /**
     * 是否启用；false 时该锁配置不加载到运行时缓存
    */
    private boolean enabled;

    /**
     * 记录创建时间（列 created_at，由数据库默认值写入，仓储读回时不回填）
    */
    private long createdAt;

    /**
     * 记录更新时间（列 updated_at，保存时写 CURRENT_TIMESTAMP，仓储读回时不回填）
    */
    private long updatedAt;
}

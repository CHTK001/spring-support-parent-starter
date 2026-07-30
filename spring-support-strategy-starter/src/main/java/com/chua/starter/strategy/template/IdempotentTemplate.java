package com.chua.starter.strategy.template;

import java.util.concurrent.TimeUnit;

/**
 * 幂等性操作模板（非注解式编程 API）。
 * <p>
 * 提供 {@link #tryAcquire} / {@link #complete} / {@link #release} 三段式编程接口，
 * 适合在无法使用 {@link com.chua.starter.strategy.annotation.Idempotent @Idempotent} 注解的场景下使用（如 Service 内部方法）。
 * </p>
 *
 * 使用示例：
 * <pre>{@code
 *   String key = "biz:" + merchantId + ":" + bizNo;
 *   if (!idempotentTemplate.tryAcquire(key, 10, TimeUnit.MINUTES)) {
 *       // 重复请求，直接返回已有结果
 *       return existing;
 *   }
 *   try {
 *       // 业务逻辑
 *       return result;
 *   } finally {
 *       idempotentTemplate.release(key);
 *   }
 * }</pre>
 *
 * @author CH
 * @since 2026-05-07
 */
public interface IdempotentTemplate {

    /**
     * 尝试获取幂等锁。
     *
     * @param key     幂等键
     * @param timeout 过期时间（到达后自动释放，允许重试）
     * @param unit    时间单位
     * @return true = 首次请求，可以继续执行；false = 重复请求
     */
    boolean tryAcquire(String key, long timeout, TimeUnit unit);

    /**
     * 标记幂等操作完成。
     *
     * @param key             幂等键
     * @param releaseOnFinish true 表示立即释放；false 则等待过期自动释放
     */
    void complete(String key, boolean releaseOnFinish);

    /**
     * 强制释放幂等锁（通常用于异常回滚）。
     *
     * @param key 幂等键
     */
    void release(String key);
}

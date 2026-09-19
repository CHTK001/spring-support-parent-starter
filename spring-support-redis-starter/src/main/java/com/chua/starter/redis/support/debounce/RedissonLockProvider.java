package com.chua.starter.redis.support.debounce;

import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.redis.support.task.lock.LockProvider;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * Redis反跳密钥生成器
 *
 * @版本 1.0.0
 * @since 2024/03/10
 * @author CH
 */
@Spi(value = "redisson", order = 100)
public class RedissonLockProvider implements LockProvider {

    @AutoInject
    /** Redisson客户端 */
    private RedissonClient redissonClient;
    /** 锁 */
    private RLock lock;
    boolean isLocked = false; // 是否锁
    /** 密钥 */
    private String key;

    /**
    * 创建 redisson锁提供者 实例
    * @param key Key
    */
    public RedissonLockProvider(String key) {
        this.key = key;
    }

    @Override
    /** 尝试锁 */
    public boolean tryLock(int timeout, TimeUnit timeUnit)  {
        this.lock = redissonClient.getLock(key);
        //尝试抢占锁
        this.isLocked = lock.tryLock();
        //没有拿到锁说明已经有了请求了
        if (!isLocked) {
            /**
             * runtime异常。
             * @param "您的操作太快了 "您的操作太快了
             * @param 请稍后重试" 请稍后重试"
             * @return runtime异常的结果
             */
            throw new RuntimeException("您的操作太快了,请稍后重试");
        }
        //拿到锁后设置过期时间
        lock.lock(timeout, timeUnit);
        try {
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    /** 解锁 */
    public void unlock() {
        if (isLocked && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    /** 获取名称 */
    public String getName() {
        return "redisson";
    }

    @Override
    /** 获取类型 */
    public String getType() {
        return "redisson";
    }

}

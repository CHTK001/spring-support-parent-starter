package com.chua.starter.redis.support.debounce;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.lock.Lock;
import com.chua.common.support.objects.annotation.AutoInject;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

/**
 * redis反跳密钥生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
@Spi(value = "redisson", order = 100)
public class RedissonLock implements Lock {

    @AutoInject
    private RedissonClient redissonClient;
    private RLock lock;
    boolean isLocked = false;
    private String key;

    public RedissonLock(String key) {
        this.key = key;
    }

    @Override
    public boolean tryLock(int timeout, TimeUnit timeUnit)  {
        this.lock = redissonClient.getLock(key);
        //尝试抢占锁
        this.isLocked = lock.tryLock();
        //没有拿到锁说明已经有了请求了
        if (!isLocked) {
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
    public void unlock() {
        if (isLocked && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

}

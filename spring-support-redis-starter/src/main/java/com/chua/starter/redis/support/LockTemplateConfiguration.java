package com.chua.starter.redis.support;

import com.chua.starter.redis.support.lock.RedissonLockTemplate;
import com.chua.starter.redis.support.lock.NoopLockTemplate;
import com.chua.starter.redis.support.template.LockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

/**
 * 锁模板。
 *
 * <p>基于 Redisson 的分布式锁自动配置：存在 {@link RedissonClient} 时注册
 * {@link RedissonLockTemplate}，否则回退 {@link NoopLockTemplate}。</p>
 *
 * @since 2022/08/01
 * @author CH
 */
@Slf4j
@ConditionalOnClass(name = "com.chua.starter.redis.support.template.LockTemplate")
public class LockTemplateConfiguration {

    @Autowired(required = false)
    /** Redisson客户端 */
    private RedissonClient redissonClient;

    /**
     * 基于 Redisson 的分布式锁模板。
     *
     * <p>注册名为 {@code customLockTemplate} 的锁模板 Bean；当容器中存在
     * {@link RedissonClient} 时返回 {@link RedissonLockTemplate}，否则回退
     * {@link NoopLockTemplate}（不持锁，仅用于无 Redis 环境下的降级）。</p>
     *
     * @return 分布式锁模板实例
     */
    @Bean("customLockTemplate")
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate() {
        if (redissonClient == null) {
            log.warn(">>>>> 未检测到 RedissonClient，使用 NoopLockTemplate");
            return new NoopLockTemplate();
        }
        log.info(">>>>> 创建基于Redisson的分布式锁模板");
        return new RedissonLockTemplate(redissonClient);
    }

}

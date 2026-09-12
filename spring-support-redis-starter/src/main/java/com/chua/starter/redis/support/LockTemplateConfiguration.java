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
 * 锁模板
 *
 * @since 2022/08/01
 * @author CH
 **/
@Slf4j
@ConditionalOnClass(name = "com.chua.starter.redis.support.template.LockTemplate")
public class LockTemplateConfiguration {

    @Autowired(required = false)
    /** Redisson客户端 */
    private RedissonClient redissonClient;

    /**
     * 基于 Redisson 的分布式锁模板
     *
     * @param redissonClient Redisson 客户端
     * @return 分布式锁模板
     */
    @Bean("customLockTemplate")
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate() {
        if (redissonClient == null) {
            log.warn(">>>>> 未检测到 RedissonClient，使用 NoopLockTemplate");
            /**
             * noop锁template。
             * @return noop锁template的结果
             */
            return new NoopLockTemplate();
        }
        log.info(">>>>> 创建基于Redisson的分布式锁模板");
        /**
         * redisson锁template。
         * @param redissonClient redisson客户端
         * @return redisson锁template的结果
         */
        return new RedissonLockTemplate(redissonClient);
    }

}

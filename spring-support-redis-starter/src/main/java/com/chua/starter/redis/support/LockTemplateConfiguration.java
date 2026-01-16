package com.chua.starter.redis.support;

import com.chua.starter.redis.support.lock.RedissonLockTemplate;
import com.chua.starter.strategy.template.LockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 锁模板
 *
 * @author CH
 * @since 2022/08/01
 **/
@Slf4j
@ConditionalOnClass(name = "com.chua.starter.strategy.template.LockTemplate")
public class LockTemplateConfiguration {
    /**
     * 基于 Redisson 的分布式锁模板
     *
     * @param redissonClient Redisson 客户端
     * @return 分布式锁模板
     */
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(RedissonClient redissonClient) {
        log.info(">>>>> 创建基于Redisson的分布式锁模板");
        return new RedissonLockTemplate(redissonClient);
    }

}

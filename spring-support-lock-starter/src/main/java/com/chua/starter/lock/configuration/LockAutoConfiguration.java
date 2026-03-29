package com.chua.starter.lock.configuration;

import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.ReentrantLockProvider;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.task.layer.idempotent.IdempotentProvider;
import com.chua.common.support.task.layer.idempotent.LocalIdempotentProvider;
import com.chua.starter.lock.annotation.Idempotent;
import com.chua.starter.lock.aspect.IdempotentAspect;
import com.chua.starter.lock.aspect.LockedAspect;
import com.chua.starter.lock.aspect.StrategyDistributedLockAspect;
import com.chua.starter.lock.aspect.StrategyIdempotentAspect;
import com.chua.starter.lock.properties.LockProperties;
import com.chua.starter.lock.provider.ReadLockProvider;
import com.chua.starter.lock.provider.StripedLockProvider;
import com.chua.starter.lock.provider.WriteLockProvider;
import com.chua.starter.lock.support.LockProviderFactory;
import com.chua.starter.lock.support.StrategyAnnotationCompatibilityMarker;
import com.chua.starter.lock.support.StoredResultCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 锁模块自动配置。
 *
 * @author CH
 * @since 2026-03-28
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(LockProperties.class)
@ConditionalOnProperty(prefix = LockProperties.PRE, name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LockAutoConfiguration.class);

    @Bean
    public SmartInitializingSingleton lockProviderInitializer(LockProperties lockProperties) {
        return () -> {
            if (!lockProperties.isRegisterAdditionalProviders()) {
                return;
            }
            ServiceProvider<LockProvider> provider = ServiceProvider.of(LockProvider.class);
            provider.register("read", ReadLockProvider.class);
            provider.register("write", WriteLockProvider.class);
            provider.register("striped", StripedLockProvider.class);
            if (!provider.isSupport("local")) {
                provider.register("local", ReentrantLockProvider.class);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public LockProviderFactory lockProviderFactory() {
        return new LockProviderFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockedAspect lockedAspect(LockProviderFactory lockProviderFactory) {
        return new LockedAspect(lockProviderFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public StoredResultCodec storedResultCodec(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new StoredResultCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = LockProperties.PRE + ".idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotentProvider idempotentProvider(LockProperties lockProperties) {
        if ("redis".equalsIgnoreCase(lockProperties.getIdempotent().getProvider())) {
            log.warn("plugin.lock.idempotent.provider=redis 但当前上下文没有可用的 Redis 适配，回退为本地幂等实现");
        }
        return new LocalIdempotentProvider(lockProperties.getIdempotent().getLocalCleanIntervalSeconds());
    }

    @Bean
    @ConditionalOnClass(Idempotent.class)
    @ConditionalOnBean(IdempotentProvider.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = LockProperties.PRE + ".idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotentAspect idempotentAspect(IdempotentProvider idempotentProvider, LockProperties lockProperties) {
        return new IdempotentAspect(idempotentProvider, lockProperties);
    }

    @Bean(name = "lockStrategyAnnotationCompatibilityMarker")
    @ConditionalOnProperty(prefix = LockProperties.PRE + ".compatibility", name = "strategy-annotations", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = {
            "com.chua.starter.strategy.annotation.DistributedLock",
            "com.chua.starter.strategy.annotation.Idempotent"
    })
    public StrategyAnnotationCompatibilityMarker lockStrategyAnnotationCompatibilityMarker() {
        return new StrategyAnnotationCompatibilityMarker();
    }

    @Bean
    @ConditionalOnBean(name = "lockStrategyAnnotationCompatibilityMarker")
    @ConditionalOnMissingBean
    public StrategyDistributedLockAspect strategyDistributedLockAspect(
            LockProviderFactory lockProviderFactory,
            LockProperties lockProperties) {
        return new StrategyDistributedLockAspect(lockProviderFactory, lockProperties);
    }

    @Bean
    @ConditionalOnBean(value = IdempotentProvider.class, name = "lockStrategyAnnotationCompatibilityMarker")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = LockProperties.PRE + ".idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StrategyIdempotentAspect strategyIdempotentAspect(
            IdempotentProvider idempotentProvider,
            LockProperties lockProperties) {
        return new StrategyIdempotentAspect(idempotentProvider, lockProperties);
    }
}

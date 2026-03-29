package com.chua.starter.lock;

import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;
import com.chua.common.support.task.layer.idempotent.IdempotentProvider;
import com.chua.common.support.task.layer.idempotent.LocalIdempotentProvider;
import com.chua.starter.lock.aspect.IdempotentAspect;
import com.chua.starter.lock.aspect.LockedAspect;
import com.chua.starter.lock.aspect.StrategyDistributedLockAspect;
import com.chua.starter.lock.aspect.StrategyIdempotentAspect;
import com.chua.starter.lock.configuration.LockAutoConfiguration;
import com.chua.starter.lock.support.LockProviderFactory;
import com.chua.starter.lock.provider.ReadLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));

    @Test
    void shouldRegisterCoreBeansAndAdditionalProviders() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LockedAspect.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
            assertThat(context).hasSingleBean(IdempotentProvider.class);
            assertThat(context).hasSingleBean(LockProviderFactory.class);
            assertThat(context).hasBean("lockStrategyAnnotationCompatibilityMarker");
            assertThat(context).hasSingleBean(StrategyDistributedLockAspect.class);
            assertThat(context).hasSingleBean(StrategyIdempotentAspect.class);
            assertThat(context.getBean(IdempotentProvider.class)).isInstanceOf(LocalIdempotentProvider.class);

            LockProviderFactory lockProviderFactory = context.getBean(LockProviderFactory.class);
            LockProvider lockProvider = lockProviderFactory.createLock("unit-test-read-lock",
                    LockSetting.builder().lockType("read").build());
            assertThat(lockProvider).isInstanceOf(ReadLockProvider.class);
        });
    }

    @Test
    void shouldAllowTurningOffStrategyCompatibility() {
        contextRunner
                .withPropertyValues("plugin.lock.compatibility.strategy-annotations=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("lockStrategyAnnotationCompatibilityMarker");
                    assertThat(context).doesNotHaveBean(StrategyDistributedLockAspect.class);
                    assertThat(context).doesNotHaveBean(StrategyIdempotentAspect.class);
                });
    }
}

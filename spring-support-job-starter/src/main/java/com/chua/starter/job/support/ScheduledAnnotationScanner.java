package com.chua.starter.job.support;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.task.scheduler.SchedulerProvider;
import com.chua.common.support.task.scheduler.Scheduled;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.common.support.task.scheduler.trigger.CronTrigger;
import com.chua.common.support.task.scheduler.trigger.FixedDelayTrigger;
import com.chua.common.support.task.scheduler.trigger.FixedRateTrigger;
import com.chua.common.support.task.scheduler.trigger.OnceExecuteTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @Scheduled 注解扫描与自动注册
 *
 * 解析 {@link Scheduled} 注解，构建 Trigger，并将无参方法封装为 Runnable 注册到 SPI 选择的 SchedulerProvider。
 */
@Slf4j
public class ScheduledAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton {

    private volatile SchedulerProvider provider;

    @Override
    public void afterSingletonsInstantiated() {
        try {
            // 选择优先顺序：quartz -> jdbc -> memory（可按需调整或外置配置）
            this.provider = ServiceProvider.of(SchedulerProvider.class)
                    .builder()
                    .name("quartz", "jdbc", "memory")
                    .enableFallback(true)
                    .build();
            if (provider != null) {
                provider.start();
                log.info("[ScheduledScanner] using provider: {} ({})", provider.getName(), provider.getDescription());
            } else {
                log.warn("[ScheduledScanner] no SchedulerProvider available via SPI");
            }
        } catch (Throwable e) {
            log.warn("[ScheduledScanner] init provider failed: {}", e.getMessage());
        }
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        Map<Method, Scheduled> methods = null;
        try {
            methods = MethodIntrospector.selectMethods(
                    bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Scheduled>) m -> AnnotatedElementUtils.findMergedAnnotation(m, Scheduled.class)
            );
        } catch (Throwable ex) {
            log.debug("[ScheduledScanner] scan @Scheduled failed for bean {}: {}", beanName, ex.getMessage());
        }
        if (methods == null || methods.isEmpty() || provider == null) {
            return bean;
        }

        for (Map.Entry<Method, Scheduled> e : methods.entrySet()) {
            Method method = e.getKey();
            Scheduled ann = e.getValue();
            if (method.getParameterCount() != 0) {
                log.warn("[ScheduledScanner] skip non-zero-arg method: {}#{}", bean.getClass().getSimpleName(), method.getName());
                continue;
            }
            String name = StringUtils.hasText(ann.name()) ? ann.name() : bean.getClass().getSimpleName() + "#" + method.getName();
            Trigger trigger = toTrigger(ann);
            if (trigger == null) {
                log.warn("[ScheduledScanner] unresolved trigger for {}", name);
                continue;
            }
            method.setAccessible(true);
            Runnable task = () -> {
                try { method.invoke(bean); } catch (Throwable ex) { log.error("[Scheduled] task {} error", name, ex); }
            };
            provider.scheduleTask(name, task, trigger);
            log.info("[ScheduledScanner] registered task: {} -> {}", name, trigger.getName());
        }
        return bean;
    }

    private static Trigger toTrigger(Scheduled s) {
        if (StringUtils.hasText(s.value())) {
            return new CronTrigger(s.value());
        }
        if (StringUtils.hasText(s.strategy())) {
            String strategy = s.strategy();
            try {
                if (strategy.startsWith("fixedRate=")) {
                    long period = Long.parseLong(strategy.substring("fixedRate=".length()));
                    return new FixedRateTrigger(0, period);
                }
                if (strategy.startsWith("fixedDelay=")) {
                    long delay = Long.parseLong(strategy.substring("fixedDelay=".length()));
                    return new FixedDelayTrigger(0, delay);
                }
                if (strategy.startsWith("once=")) {
                    long delay = Long.parseLong(strategy.substring("once=".length()));
                    return new OnceExecuteTrigger(delay);
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }
}

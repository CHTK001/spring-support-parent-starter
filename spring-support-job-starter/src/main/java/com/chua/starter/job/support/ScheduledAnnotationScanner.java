package com.chua.starter.job.support;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.handler.BeanJobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import com.chua.common.support.task.scheduler.SchedulerProvider;
import com.chua.common.support.task.scheduler.Scheduled;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.common.support.task.scheduler.trigger.CronTrigger;
import com.chua.common.support.task.scheduler.trigger.FixedDelayTrigger;
import com.chua.common.support.task.scheduler.trigger.FixedRateTrigger;
import com.chua.common.support.task.scheduler.trigger.OnceExecuteTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Scheduled 注解扫描与自动注册
 *
 * 解析 {@link Scheduled} 注解，构建 Trigger，并将无参方法封装为 Runnable 注册到 SPI 选择的 SchedulerProvider。
 */
@Slf4j
public class ScheduledAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton, EmbeddedValueResolverAware {

    private volatile SchedulerProvider provider;
    private final Map<String, ScheduledDefinition> discoveredDefinitions = new LinkedHashMap<>();
    private StringValueResolver embeddedValueResolver;

    @org.springframework.beans.factory.annotation.Autowired
    private JobProperties jobProperties;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<JobDynamicConfigService> jobDynamicConfigServiceProvider;

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

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
        processCollectedDefinitions();
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        collectCustomScheduled(bean);
        collectSpringScheduled(bean);
        return bean;
    }

    private void collectCustomScheduled(Object bean) {
        Map<Method, Scheduled> methods = null;
        try {
            methods = MethodIntrospector.selectMethods(
                    bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Scheduled>) m -> AnnotatedElementUtils.findMergedAnnotation(m, Scheduled.class)
            );
        } catch (Throwable ex) {
            log.debug("[ScheduledScanner] scan custom @Scheduled failed for bean {}: {}", bean.getClass().getSimpleName(), ex.getMessage());
        }
        if (methods == null || methods.isEmpty()) {
            return;
        }
        for (Map.Entry<Method, Scheduled> entry : methods.entrySet()) {
            Method method = entry.getKey();
            Scheduled ann = entry.getValue();
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = StringUtils.hasText(ann.name()) ? ann.name() : bean.getClass().getSimpleName() + "#" + method.getName();
            Trigger trigger = toTrigger(ann);
            registerBeanHandler(name, bean, method);
            if (trigger != null) {
                discoveredDefinitions.put(name, ScheduledDefinition.fromCustom(name, bean, method, ann, trigger, this::resolveStringValue));
            }
        }
    }

    private void collectSpringScheduled(Object bean) {
        Map<Method, org.springframework.scheduling.annotation.Scheduled> methods = null;
        try {
            methods = MethodIntrospector.selectMethods(
                    bean.getClass(),
                    (MethodIntrospector.MetadataLookup<org.springframework.scheduling.annotation.Scheduled>) m ->
                            AnnotatedElementUtils.findMergedAnnotation(m, org.springframework.scheduling.annotation.Scheduled.class)
            );
        } catch (Throwable ex) {
            log.debug("[ScheduledScanner] scan spring @Scheduled failed for bean {}: {}", bean.getClass().getSimpleName(), ex.getMessage());
        }
        if (methods == null || methods.isEmpty()) {
            return;
        }
        for (Map.Entry<Method, org.springframework.scheduling.annotation.Scheduled> entry : methods.entrySet()) {
            Method method = entry.getKey();
            org.springframework.scheduling.annotation.Scheduled ann = entry.getValue();
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = bean.getClass().getSimpleName() + "#" + method.getName();
            Trigger trigger = toTrigger(ann);
            registerBeanHandler(name, bean, method);
            if (trigger != null) {
                discoveredDefinitions.put(name, ScheduledDefinition.fromSpring(name, bean, method, ann, trigger, this::resolveStringValue));
            }
        }
    }

    private void processCollectedDefinitions() {
        if (discoveredDefinitions.isEmpty()) {
            return;
        }
        if (shouldSyncToConfigTable()) {
            JobDynamicConfigService service = jobDynamicConfigServiceProvider.getIfAvailable();
            if (service == null) {
                log.warn("[ScheduledScanner] JobDynamicConfigService 不存在，跳过 @Scheduled 自动入表");
                return;
            }
            AnnotationSyncMode mode = jobProperties.getScheduledAnnotationSyncMode();
            for (ScheduledDefinition definition : discoveredDefinitions.values()) {
                try {
                    syncScheduledTask(service, mode, definition);
                } catch (Exception e) {
                    log.error("[ScheduledScanner] @Scheduled 自动入表失败: {}", definition.name, e);
                }
            }
            return;
        }
        if (provider == null) {
            log.warn("[ScheduledScanner] provider 不可用，跳过 @Scheduled 直接调度");
            return;
        }
        for (ScheduledDefinition definition : discoveredDefinitions.values()) {
            provider.scheduleTask(definition.name, definition.runnable, definition.trigger);
            log.info("[ScheduledScanner] registered task: {} -> {}", definition.name, definition.trigger.getName());
        }
    }

    private boolean shouldSyncToConfigTable() {
        return jobProperties != null
                && jobProperties.isConfigTableEnabled()
                && jobProperties.getScheduledAnnotationSyncMode() != null
                && jobProperties.getScheduledAnnotationSyncMode() != AnnotationSyncMode.NONE;
    }

    private void syncScheduledTask(JobDynamicConfigService service, AnnotationSyncMode mode, ScheduledDefinition definition) {
        SysJob existing = service.getJobByName(definition.name);
        if (mode == AnnotationSyncMode.CREATE && existing != null) {
            return;
        }
        SysJob target = existing != null ? existing : new SysJob();
        target.setJobName(definition.name);
        target.setJobScheduleType(definition.scheduleType);
        target.setJobScheduleTime(definition.scheduleTime);
        target.setJobDesc(definition.beanClass.getSimpleName() + "#" + definition.method.getName());
        target.setJobExecuteBean(definition.name);
        target.setJobGlueType("BEAN");
        target.setJobGlueUpdatetime(new java.util.Date());
        if (target.getJobFailRetry() == null) {
            target.setJobFailRetry(0);
        }
        if (target.getJobExecuteTimeout() == null) {
            target.setJobExecuteTimeout(0);
        }
        if (!StringUtils.hasText(target.getJobExecuteMisfireStrategy())) {
            target.setJobExecuteMisfireStrategy("DO_NOTHING");
        }
        target.setJobTriggerStatus(1);
        if (existing == null) {
            service.createJob(target);
            service.startJob(target.getJobId());
        } else if (mode == AnnotationSyncMode.UPDATE) {
            service.updateJob(target);
            service.startJob(target.getJobId());
        }
    }

    private void registerBeanHandler(String name, Object bean, Method method) {
        if (JobHandlerFactory.getInstance().get(name) != null) {
            return;
        }
        method.setAccessible(true);
        JobHandlerFactory.getInstance().register(name, new BeanJobHandler(bean, method, null, null));
    }

    private Trigger toTrigger(Scheduled s) {
        String cron = resolveStringValue(s.value());
        if (StringUtils.hasText(cron)) {
            return new CronTrigger(cron);
        }
        String strategy = resolveStringValue(s.strategy());
        if (StringUtils.hasText(strategy)) {
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

    private Trigger toTrigger(org.springframework.scheduling.annotation.Scheduled s) {
        String cron = resolveStringValue(s.cron());
        if (StringUtils.hasText(cron)) {
            return new CronTrigger(cron);
        }
        if (s.fixedRate() > 0) {
            return new FixedRateTrigger(s.initialDelay(), s.fixedRate());
        }
        if (s.fixedDelay() > 0) {
            return new FixedDelayTrigger(s.initialDelay(), s.fixedDelay());
        }
        String fixedRateString = resolveStringValue(s.fixedRateString());
        if (StringUtils.hasText(fixedRateString)) {
            try {
                long period = Long.parseLong(fixedRateString);
                return new FixedRateTrigger(resolveInitialDelay(s), period);
            } catch (Exception ignore) {
            }
        }
        String fixedDelayString = resolveStringValue(s.fixedDelayString());
        if (StringUtils.hasText(fixedDelayString)) {
            try {
                long delay = Long.parseLong(fixedDelayString);
                return new FixedDelayTrigger(resolveInitialDelay(s), delay);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private long resolveInitialDelay(org.springframework.scheduling.annotation.Scheduled s) {
        if (s.initialDelay() > 0) {
            return s.initialDelay();
        }
        String initialDelayString = resolveStringValue(s.initialDelayString());
        if (StringUtils.hasText(initialDelayString)) {
            try {
                return Long.parseLong(initialDelayString);
            } catch (Exception ignore) {
            }
        }
        return 0L;
    }

    private String resolveStringValue(String value) {
        if (!StringUtils.hasText(value) || embeddedValueResolver == null) {
            return value;
        }
        String resolved = embeddedValueResolver.resolveStringValue(value);
        return resolved == null ? value : resolved;
    }

    private record ScheduledDefinition(String name,
                                       String scheduleType,
                                       String scheduleTime,
                                       Trigger trigger,
                                       Runnable runnable,
                                       Object bean,
                                       Method method,
                                       Class<?> beanClass) {

        private static ScheduledDefinition fromCustom(String name, Object bean, Method method, Scheduled ann, Trigger trigger,
                                                      java.util.function.Function<String, String> valueResolver) {
            return new ScheduledDefinition(name, customScheduleTypeOf(ann), customScheduleTimeOf(ann, valueResolver), trigger, runnableOf(name, bean, method), bean, method, bean.getClass());
        }

        private static ScheduledDefinition fromSpring(String name, Object bean, Method method, org.springframework.scheduling.annotation.Scheduled ann, Trigger trigger,
                                                      java.util.function.Function<String, String> valueResolver) {
            return new ScheduledDefinition(name, springScheduleTypeOf(ann), springScheduleTimeOf(ann, valueResolver), trigger, runnableOf(name, bean, method), bean, method, bean.getClass());
        }

        private static Runnable runnableOf(String name, Object bean, Method method) {
            return () -> {
                try {
                    method.invoke(bean);
                } catch (Throwable ex) {
                    log.error("[Scheduled] task {} error", name, ex);
                }
            };
        }

        private static String customScheduleTypeOf(Scheduled ann) {
            if (StringUtils.hasText(ann.value())) {
                return "cron";
            }
            if (StringUtils.hasText(ann.strategy())) {
                String strategy = ann.strategy();
                if (strategy.startsWith("fixedRate=")) {
                    return "fixed_ms";
                }
                if (strategy.startsWith("fixedDelay=")) {
                    return "fixed";
                }
                if (strategy.startsWith("once=")) {
                    return "once";
                }
            }
            return "cron";
        }

        private static String customScheduleTimeOf(Scheduled ann, java.util.function.Function<String, String> valueResolver) {
            if (StringUtils.hasText(ann.value())) {
                return valueResolver.apply(ann.value());
            }
            if (StringUtils.hasText(ann.strategy())) {
                String strategy = valueResolver.apply(ann.strategy());
                int idx = strategy.indexOf('=');
                return idx > -1 ? strategy.substring(idx + 1) : strategy;
            }
            return "";
        }

        private static String springScheduleTypeOf(org.springframework.scheduling.annotation.Scheduled ann) {
            if (StringUtils.hasText(ann.cron())) {
                return "cron";
            }
            if (ann.fixedRate() > 0 || StringUtils.hasText(ann.fixedRateString())) {
                return "fixed_ms";
            }
            if (ann.fixedDelay() > 0 || StringUtils.hasText(ann.fixedDelayString())) {
                return "fixed";
            }
            return "cron";
        }

        private static String springScheduleTimeOf(org.springframework.scheduling.annotation.Scheduled ann,
                                                   java.util.function.Function<String, String> valueResolver) {
            if (StringUtils.hasText(ann.cron())) {
                return valueResolver.apply(ann.cron());
            }
            if (ann.fixedRate() > 0) {
                return String.valueOf(ann.fixedRate());
            }
            if (StringUtils.hasText(ann.fixedRateString())) {
                return valueResolver.apply(ann.fixedRateString());
            }
            if (ann.fixedDelay() > 0) {
                return String.valueOf(ann.fixedDelay());
            }
            if (StringUtils.hasText(ann.fixedDelayString())) {
                return valueResolver.apply(ann.fixedDelayString());
            }
            return "";
        }
    }
}

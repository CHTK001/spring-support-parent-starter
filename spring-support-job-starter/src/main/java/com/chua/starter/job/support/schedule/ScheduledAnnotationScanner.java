package com.chua.starter.job.support.schedule;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring 原生 {@link Scheduled} 注解扫描器。
 *
 * <p>注册到当前 Spring 上下文的 {@link TaskScheduler} 上, 由其真实执行。
 * 本模块仅做扫描, 不替换定时器实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class ScheduledAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton {

    /**
     * Spring 任务调度器
     */
    private final TaskScheduler taskScheduler;

    /**
     * 应用环境，用于解析 {@code @Scheduled} 里的 {@code ${...}} 占位符。
     *
     * <p>不可为 null：拿不到环境就退化成按字面量注册，会重演
     * {@code ${...} 被 CronExpression 直接解析而启动失败}的老问题。</p>
     */
    private final Environment environment;

    /**
     * 已注册任务计数
     */
    private final AtomicInteger registeredCount = new AtomicInteger(0);

    /**
     * 构造扫描器。
     *
     * @param taskScheduler Spring 任务调度器
     * @param environment   应用环境，用于解析占位符；为 null 时降级为字面量注册
     */
    public ScheduledAnnotationScanner(TaskScheduler taskScheduler, Environment environment) {
        this.taskScheduler = taskScheduler;
        this.environment = environment;
    }

    @Override
    /**
     * Post处理之后initialization
    */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = ClassUtils.getUserClass(bean);
        java.util.Map<Method, Scheduled> annotated = null;
        try {
            annotated = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<Scheduled>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class));
        } catch (Throwable ex) {
            log.error("[job] 扫描 @Scheduled 失败, bean={}", beanName, ex);
        }
        if (annotated == null || annotated.isEmpty()) {
            return bean;
        }
        for (java.util.Map.Entry<Method, Scheduled> entry : annotated.entrySet()) {
            /**
             * 调度。
             * @param targetClass 目标类
             * @param bean Bean
             * @param entry.getKey( entry.获取键(
             */
            schedule(targetClass, bean, entry.getKey(), entry.getValue());
        }
        return bean;
    }

    @Override
    /**
     * 之后单例instantiated
    */
    public void afterSingletonsInstantiated() {
        log.info("[job] @Scheduled 扫描完成, 共注册 {} 个定时任务", registeredCount.get());
    }

    /**
     * 将方法注册为定时任务。
     *
     * @param targetClass 业务类
     * @param bean        业务 Bean
     * @param method      方法
     * @param ann         Spring {@link Scheduled} 注解
     */
    private void schedule(Class<?> targetClass, Object bean, Method method, Scheduled ann) {
        if (method.getParameterCount() > 0) {
            throw new IllegalStateException("@Scheduled 方法必须无参, target="
                    + targetClass.getName() + "#" + method.getName());
        }
        // [P3C 1.10] ReflectUtils.invoke 内部使用 privateLookupIn，无需 setAccessible
        String beanName = targetClass.getSimpleName();
        String methodName = method.getName();
        Runnable task = () -> {
            try {
                ReflectUtils.invoke(bean, method.getName(), method.getReturnType());
            } catch (Throwable ex) {
                log.error("[job] @Scheduled 执行失败 target={}#{}", beanName, methodName, ex);
            }
        };

        String name = beanName + "#" + methodName;
        try {
            // @Scheduled 的值允许写 ${...}（各 starter 普遍把 cron 做成可配置项）。
            // Spring 原生 ScheduledAnnotationBeanPostProcessor 会先解析占位符，
            // 但本扫描器拿到的是注解字面量，不解析就会把 "${xxx.cron:...}" 原样丢给
            // CronExpression，抛 IllegalArgumentException 并连带整个容器启动失败。
            String cron = resolve(ann.cron());
            if (StringUtilsHasText(cron)) {
                String zone = resolve(ann.zone());
                CronTrigger cronTrigger = StringUtilsHasText(zone)
                        ? new CronTrigger(cron, TimeZone.getTimeZone(zone)) : new CronTrigger(cron);
                taskScheduler.schedule(task, cronTrigger);
            } else {
                long periodMs = resolvePeriod(ann.fixedRateString(), ann.fixedRate());
                if (periodMs > 0) {
                    taskScheduler.schedule(task, new PeriodicTrigger(Duration.ofMillis(periodMs)));
                } else {
                    periodMs = resolvePeriod(ann.fixedDelayString(), ann.fixedDelay());
                    if (periodMs <= 0) {
                        log.warn("[job] @Scheduled 缺少有效配置, 跳过 name={}", name);
                        return;
                    }
                    PeriodicTrigger trigger = new PeriodicTrigger(periodMs, TimeUnit.MILLISECONDS);
                    long initialMs = resolvePeriod(ann.initialDelayString(), ann.initialDelay());
                    if (initialMs > 0) {
                        trigger.setInitialDelay(initialMs);
                    }
                    taskScheduler.schedule(task, trigger);
                }
            }
        } catch (RuntimeException ex) {
            // 单个 @Scheduled 配错不该拖垮整个应用：跳过该任务并留下可检索的日志。
            log.error("[job] @Scheduled 配置非法, 已跳过 name={}, 原因={}", name, ex.getMessage(), ex);
            return;
        }
        registeredCount.incrementAndGet();
        log.info("[job] 注册 @Scheduled name={}", name);
    }

    /**
     * 解析 {@code ${...}} 占位符，非严格模式（解析不了就保留原样，与 Spring 默认一致）。
     *
     * @param value 原始值，可为 null
     * @return 解析后的值；入参为空或无环境时原样返回
     */
    private String resolve(String value) {
        if (value == null || value.isBlank() || environment == null) {
            return value;
        }
        return environment.resolvePlaceholders(value);
    }

    /**
     * 解析周期值：优先取字符串形态（可带占位符），否则用长整型。
     *
     * @param stringValue 字符串形态，可能为空或带占位符
     * @param longValue   长整型形态，字符串形态为空时的兜底
     * @return 解析后的毫秒数；无法解析时返回 -1
     */
    private long resolvePeriod(String stringValue, long longValue) {
        if (StringUtilsHasText(stringValue)) {
            String resolved = resolve(stringValue).trim();
            if (resolved.startsWith("P") || resolved.startsWith("p") || resolved.startsWith("-P")
                    || resolved.startsWith("-p") || resolved.startsWith("PT") || resolved.startsWith("pt")) {
                return Duration.parse(resolved).toMillis();
            }
            try {
                return Long.parseLong(resolved);
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return longValue;
    }

    /**
     * 文本非空判定。
     *
     * @param value 文本
     * @return 非空返回 true
     */
    private static boolean StringUtilsHasText(String value) {
        return value != null && !value.isBlank();
    }
}

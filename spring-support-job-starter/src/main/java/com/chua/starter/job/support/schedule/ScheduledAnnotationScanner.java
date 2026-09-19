package com.chua.starter.job.support.schedule;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.time.Duration;
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
     * 已注册任务计数
     */
    private final AtomicInteger registeredCount = new AtomicInteger(0);

    /**
     * 构造扫描器。
     *
     * @param taskScheduler Spring 任务调度器
     */
    public ScheduledAnnotationScanner(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    /** Post处理之后initialization */
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
    /** 之后单例instantiated */
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
        if (StringUtilsHasText(ann.cron())) {
            taskScheduler.schedule(task, new CronTrigger(ann.cron()));
        } else if (ann.fixedRate() > 0) {
            taskScheduler.schedule(task, new PeriodicTrigger(Duration.ofMillis(ann.fixedRate())));
        } else if (ann.fixedDelay() > 0) {
            long delayMs = ann.fixedDelay();
            long initialMs = Math.max(0L, ann.initialDelay());
            PeriodicTrigger trigger = new PeriodicTrigger(delayMs, TimeUnit.MILLISECONDS);
            trigger.setInitialDelay(initialMs);
            taskScheduler.schedule(task, trigger);
        } else {
            log.warn("[job] @Scheduled 缺少有效配置, 跳过 name={}", name);
            return;
        }
        registeredCount.incrementAndGet();
        log.info("[job] 注册 @Scheduled name={}", name);
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

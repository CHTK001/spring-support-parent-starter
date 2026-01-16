package com.chua.starter.strategy.warmup;

import com.chua.starter.strategy.annotation.Warmup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预热执行器
 * <p>
 * 扫描带有@Warmup注解的方法和Bean，在应用启动完成后执行预热。
 * 支持同步/异步执行、超时控制、优先级排序等特性。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
public class WarmupExecutor implements BeanPostProcessor, ApplicationListener<ApplicationReadyEvent>,
        ApplicationContextAware {

    private static final int WARMUP_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private final List<WarmupTask> warmupTasks = Collections.synchronizedList(new ArrayList<>());

    private ApplicationContext applicationContext;

    private final AtomicInteger successCount = new AtomicInteger(0);

    private final AtomicInteger failCount = new AtomicInteger(0);

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        // 检查类级别的@Warmup注解
        Warmup classWarmup = AnnotationUtils.findAnnotation(beanClass, Warmup.class);
        if (classWarmup != null) {
            // 类级别注解，预热所有public方法
            ReflectionUtils.doWithMethods(beanClass, method -> {
                if (method.getDeclaringClass() != Object.class && method.getParameterCount() == 0) {
                    registerWarmupTask(bean, method, classWarmup);
                }
            }, method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        }

        // 检查方法级别的@Warmup注解
        ReflectionUtils.doWithMethods(beanClass, method -> {
            Warmup methodWarmup = AnnotationUtils.findAnnotation(method, Warmup.class);
            if (methodWarmup != null) {
                registerWarmupTask(bean, method, methodWarmup);
            }
        });

        return bean;
    }

    /**
     * 注册预热任务
     *
     * @param bean   目标Bean
     * @param method 目标方法
     * @param warmup 预热注解
     */
    private void registerWarmupTask(Object bean, Method method, Warmup warmup) {
        String taskName = StringUtils.hasText(warmup.name())
                ? warmup.name()
                : bean.getClass().getSimpleName() + "#" + method.getName();

        WarmupParameterProvider provider = null;
        if (StringUtils.hasText(warmup.parameterProvider())) {
            try {
                provider = applicationContext.getBean(warmup.parameterProvider(), WarmupParameterProvider.class);
            } catch (BeansException e) {
                log.warn("预热任务[{}]参数提供者[{}]未找到", taskName, warmup.parameterProvider());
            }
        }

        WarmupTask task = new WarmupTask(
                taskName,
                bean,
                method,
                warmup.order(),
                warmup.async(),
                warmup.timeout(),
                warmup.failOnError(),
                warmup.iterations(),
                provider,
                warmup.description()
        );

        warmupTasks.add(task);
        log.debug("注册预热任务: {}", taskName);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (warmupTasks.isEmpty()) {
            log.info("无预热任务需要执行");
            return;
        }

        log.info("开始执行预热，共{}个任务", warmupTasks.size());
        long startTime = System.currentTimeMillis();

        // 按优先级排序
        Collections.sort(warmupTasks);

        // 分离同步和异步任务
        List<WarmupTask> syncTasks = warmupTasks.stream().filter(t -> !t.async()).toList();
        List<WarmupTask> asyncTasks = warmupTasks.stream().filter(WarmupTask::async).toList();

        // 执行同步任务
        for (WarmupTask task : syncTasks) {
            executeWarmupTask(task);
        }

        // 异步执行异步任务
        if (!asyncTasks.isEmpty()) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<CompletableFuture<Void>> futures = asyncTasks.stream()
                    .map(task -> CompletableFuture.runAsync(() -> executeWarmupTask(task), executor))
                    .toList();

            // 等待所有异步任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("预热完成，耗时{}ms，成功{}个，失败{}个",
                duration, successCount.get(), failCount.get());
    }

    /**
     * 执行单个预热任务
     *
     * @param task 预热任务
     */
    private void executeWarmupTask(WarmupTask task) {
        String taskName = task.name();
        long timeout = task.timeout();

        log.info("执行预热任务: {} ({}次迭代)", taskName, task.iterations());

        try {
            Method method = task.method();
            method.setAccessible(true);

            // 准备参数
            Object[] args = prepareArguments(task);

            for (int i = 0; i < task.iterations(); i++) {
                if (timeout > 0) {
                    // 带超时执行
                    executeWithTimeout(task, args, timeout);
                } else {
                    // 直接执行
                    method.invoke(task.bean(), args);
                }
            }

            successCount.incrementAndGet();
            log.info("预热任务[{}]执行成功", taskName);

        } catch (TimeoutException e) {
            failCount.incrementAndGet();
            String msg = "预热任务[" + taskName + "]执行超时";
            log.error(msg);
            if (task.failOnError()) {
                throw new RuntimeException(msg, e);
            }
        } catch (Exception e) {
            failCount.incrementAndGet();
            String msg = "预热任务[" + taskName + "]执行失败";
            log.error(msg, e);
            if (task.failOnError()) {
                throw new RuntimeException(msg, e);
            }
        }
    }

    /**
     * 准备方法参数
     *
     * @param task 预热任务
     * @return 参数数组
     */
    private Object[] prepareArguments(WarmupTask task) {
        Method method = task.method();
        Class<?>[] paramTypes = method.getParameterTypes();

        if (paramTypes.length == 0) {
            return new Object[0];
        }

        // 使用参数提供者
        if (task.parameterProvider() != null) {
            return task.parameterProvider().provideParameters(method.getName(), paramTypes);
        }

        // 使用默认值
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = getDefaultValue(paramTypes[i]);
        }
        return args;
    }

    /**
     * 获取类型的默认值
     *
     * @param type 类型
     * @return 默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) 0;
        }
        if (type == short.class || type == Short.class) {
            return (short) 0;
        }
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type == float.class || type == Float.class) {
            return 0.0f;
        }
        if (type == double.class || type == Double.class) {
            return 0.0d;
        }
        if (type == char.class || type == Character.class) {
            return '\0';
        }
        if (type == String.class) {
            return "";
        }
        return null;
    }

    /**
     * 带超时执行
     *
     * @param task    预热任务
     * @param args    参数
     * @param timeout 超时时间（毫秒）
     * @throws Exception 执行异常
     */
    private void executeWithTimeout(WarmupTask task, Object[] args, long timeout) throws Exception {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                task.method().invoke(task.bean(), args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("执行超时");
        }
    }

    /**
     * 获取预热统计信息
     *
     * @return 统计信息
     */
    public WarmupStatistics getStatistics() {
        return new WarmupStatistics(
                warmupTasks.size(),
                successCount.get(),
                failCount.get()
        );
    }

    /**
     * 预热统计信息
     *
     * @param total   总任务数
     * @param success 成功数
     * @param fail    失败数
     */
    public record WarmupStatistics(int total, int success, int fail) {
    }
}

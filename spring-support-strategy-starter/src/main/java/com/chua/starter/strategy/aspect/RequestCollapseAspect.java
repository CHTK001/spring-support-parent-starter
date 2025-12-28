package com.chua.starter.strategy.aspect;

import com.chua.starter.strategy.annotation.RequestCollapse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 请求合并切面
 * <p>
 * 实现请求合并功能，将多个单独的请求合并为批量请求执行。
 * 使用虚拟线程处理批量任务，提高并发性能。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
public class RequestCollapseAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 合并器实例缓存
     */
    private final Map<String, RequestCollapser<?>> collapsers = new ConcurrentHashMap<>();

    /**
     * 调度器，用于处理窗口超时
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "request-collapse-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    @Around("@annotation(requestCollapse)")
    public Object around(ProceedingJoinPoint joinPoint, RequestCollapse requestCollapse) throws Throwable {
        if (!requestCollapse.enabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 解析请求key
        Object requestKey = parseRequestKey(joinPoint, requestCollapse, method);

        // 获取或创建合并器
        RequestCollapser<Object> collapser = getOrCreateCollapser(joinPoint, requestCollapse, method);

        // 提交请求并等待结果
        try {
            CompletableFuture<Object> future = collapser.submit(requestKey);
            return future.get(requestCollapse.timeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("请求合并超时: collapser={}, key={}", requestCollapse.name(), requestKey);
            throw new RuntimeException("请求合并超时", e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    /**
     * 解析请求key
     */
    private Object parseRequestKey(ProceedingJoinPoint joinPoint, RequestCollapse requestCollapse, Method method) {
        String keyExpression = requestCollapse.keyExpression();

        EvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), parameterNameDiscoverer);

        return parser.parseExpression(keyExpression).getValue(context);
    }

    /**
     * 获取或创建合并器
     */
    @SuppressWarnings("unchecked")
    private RequestCollapser<Object> getOrCreateCollapser(ProceedingJoinPoint joinPoint,
                                                          RequestCollapse requestCollapse,
                                                          Method method) {
        String collapserName = requestCollapse.name();

        return (RequestCollapser<Object>) collapsers.computeIfAbsent(collapserName, name -> {
            log.info("创建请求合并器: name={}, windowTime={}ms, maxBatchSize={}",
                    name, requestCollapse.windowTime(), requestCollapse.maxBatchSize());

            return new RequestCollapser<>(
                    name,
                    requestCollapse.windowTime(),
                    requestCollapse.maxBatchSize(),
                    keys -> executeBatch(joinPoint, requestCollapse, method, keys),
                    scheduler
            );
        });
    }

    /**
     * 执行批量请求
     */
    @SuppressWarnings("unchecked")
    private Map<Object, Object> executeBatch(ProceedingJoinPoint joinPoint, RequestCollapse requestCollapse,
                                              Method singleMethod, List<Object> keys) {
        String batchMethodName = requestCollapse.batchMethod();
        Object target = joinPoint.getTarget();
        Class<?> targetClass = target.getClass();

        try {
            // 查找批量方法
            Method batchMethod = findBatchMethod(targetClass, batchMethodName);

            if (batchMethod == null) {
                throw new RuntimeException("批量方法不存在: " + batchMethodName);
            }

            batchMethod.setAccessible(true);
            Object result = batchMethod.invoke(target, keys);

            if (result instanceof Map) {
                return (Map<Object, Object>) result;
            }

            throw new RuntimeException("批量方法返回类型必须为Map");

        } catch (Exception e) {
            log.error("执行批量请求失败: method={}", batchMethodName, e);
            throw new RuntimeException("执行批量请求失败", e);
        }
    }

    /**
     * 查找批量方法
     */
    private Method findBatchMethod(Class<?> targetClass, String methodName) {
        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 1 && List.class.isAssignableFrom(paramTypes[0])) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 请求合并器
     *
     * @param <T> 结果类型
     */
    private static class RequestCollapser<T> {
        private final String name;
        private final long windowTime;
        private final int maxBatchSize;
        private final BatchExecutor<T> batchExecutor;
        private final ScheduledExecutorService scheduler;

        private final List<PendingRequest<T>> pendingRequests = new ArrayList<>();
        private final Object lock = new Object();
        private volatile boolean windowOpen = false;

        RequestCollapser(String name, long windowTime, int maxBatchSize,
                         BatchExecutor<T> batchExecutor, ScheduledExecutorService scheduler) {
            this.name = name;
            this.windowTime = windowTime;
            this.maxBatchSize = maxBatchSize;
            this.batchExecutor = batchExecutor;
            this.scheduler = scheduler;
        }

        /**
         * 提交请求
         */
        CompletableFuture<T> submit(Object key) {
            CompletableFuture<T> future = new CompletableFuture<>();
            PendingRequest<T> request = new PendingRequest<>(key, future);

            synchronized (lock) {
                pendingRequests.add(request);

                // 如果达到最大批量大小，立即执行
                if (pendingRequests.size() >= maxBatchSize) {
                    executeBatch();
                } else if (!windowOpen) {
                    // 开启时间窗口
                    windowOpen = true;
                    scheduler.schedule(this::onWindowTimeout, windowTime, TimeUnit.MILLISECONDS);
                }
            }

            return future;
        }

        /**
         * 窗口超时回调
         */
        private void onWindowTimeout() {
            synchronized (lock) {
                if (!pendingRequests.isEmpty()) {
                    executeBatch();
                }
                windowOpen = false;
            }
        }

        /**
         * 执行批量请求
         */
        @SuppressWarnings("unchecked")
        private void executeBatch() {
            List<PendingRequest<T>> batch;
            synchronized (lock) {
                batch = new ArrayList<>(pendingRequests);
                pendingRequests.clear();
                windowOpen = false;
            }

            if (batch.isEmpty()) {
                return;
            }

            // 使用虚拟线程执行批量请求
            Thread.startVirtualThread(() -> {
                try {
                    List<Object> keys = batch.stream().map(r -> r.key).toList();
                    Map<Object, T> results = (Map<Object, T>) batchExecutor.execute(keys);

                    // 分发结果
                    for (PendingRequest<T> request : batch) {
                        T result = results.get(request.key);
                        request.future.complete(result);
                    }

                } catch (Exception e) {
                    // 批量请求失败，所有请求都失败
                    for (PendingRequest<T> request : batch) {
                        request.future.completeExceptionally(e);
                    }
                }
            });
        }
    }

    /**
     * 待处理请求
     */
    private record PendingRequest<T>(Object key, CompletableFuture<T> future) {
    }

    /**
     * 批量执行器接口
     */
    @FunctionalInterface
    private interface BatchExecutor<T> {
        Map<Object, T> execute(List<Object> keys);
    }
}

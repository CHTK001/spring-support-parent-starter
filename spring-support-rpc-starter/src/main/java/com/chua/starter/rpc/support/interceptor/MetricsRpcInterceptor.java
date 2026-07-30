package com.chua.starter.rpc.support.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC性能监控拦截器
 * <p>
 * 统计RPC调用的性能指标
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class MetricsRpcInterceptor implements RpcInterceptor {

    private final ConcurrentHashMap<String, AtomicLong> callCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> successCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> failureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> startTimes = new ThreadLocal<>();

    @Override
    public boolean preHandle(Method method, Object[] args) {
        startTimes.set(System.currentTimeMillis());
        String methodKey = getMethodKey(method);
        callCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        return true;
    }

    @Override
    public Object postHandle(Method method, Object[] args, Object result) {
        String methodKey = getMethodKey(method);
        successCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        recordDuration(methodKey);
        return result;
    }

    @Override
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        String methodKey = getMethodKey(method);
        failureCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        recordDuration(methodKey);
        throw error;
    }

    /**
     * 记录耗时
     */
    private void recordDuration(String methodKey) {
        Long startTime = startTimes.get();
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            totalDurations.computeIfAbsent(methodKey, k -> new AtomicLong()).addAndGet(duration);
            startTimes.remove();

            if (duration > 1000) {
                log.warn("[RPC] 调用耗时过长: method={}, duration={}ms", methodKey, duration);
            }
        }
    }

    /**
     * 获取方法键
     */
    private String getMethodKey(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * 获取调用次数
     */
    public long getCallCount(String methodKey) {
        AtomicLong counter = callCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取成功次数
     */
    public long getSuccessCount(String methodKey) {
        AtomicLong counter = successCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取失败次数
     */
    public long getFailureCount(String methodKey) {
        AtomicLong counter = failureCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取平均耗时
     */
    public long getAverageDuration(String methodKey) {
        AtomicLong total = totalDurations.get(methodKey);
        AtomicLong count = callCounters.get(methodKey);
        if (total == null || count == null || count.get() == 0) {
            return 0;
        }
        return total.get() / count.get();
    }

    /**
     * 重置统计
     */
    public void reset() {
        callCounters.clear();
        successCounters.clear();
        failureCounters.clear();
        totalDurations.clear();
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}

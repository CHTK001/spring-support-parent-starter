package com.chua.starter.rpc.support.interceptor;

import com.chua.common.support.network.rpc.RpcMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
* RPC 性能监控拦截器。
*
* <p>统计 RPC 调用的性能指标：按方法累计调用数、成功/失败数、总耗时，
* 并记录最大/最小/最近一次耗时、最近调用时间戳与最近一次调用结果（含错误信息）。</p>
*
* @since 2025-03-20
* @author CH
 */
@Slf4j
@Component
public class MetricsRpcInterceptor implements RpcInterceptor {

    /** 最近一次结果：成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    /** 最近一次结果：失败 */
    private static final String RESULT_FAILURE = "FAILURE";
    /** 最近一次结果：尚无调用 */
    private static final String RESULT_NONE = "NONE";
    /** 最小耗时初始哨兵值 */
    private static final long MIN_DURATION_INIT = Long.MAX_VALUE;

    /** 方法调用计数器 */
    private final ConcurrentHashMap<String, AtomicLong> callCounters = new ConcurrentHashMap<>();
    /** 方法成功计数器 */
    private final ConcurrentHashMap<String, AtomicLong> successCounters = new ConcurrentHashMap<>();
    /** 方法失败计数器 */
    private final ConcurrentHashMap<String, AtomicLong> failureCounters = new ConcurrentHashMap<>();
    /** 方法累计总耗时（毫秒） */
    private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    /** 方法最大耗时（毫秒） */
    private final ConcurrentHashMap<String, AtomicLong> maxDurations = new ConcurrentHashMap<>();
    /** 方法最小耗时（毫秒） */
    private final ConcurrentHashMap<String, AtomicLong> minDurations = new ConcurrentHashMap<>();
    /** 方法最近一次耗时（毫秒） */
    private final ConcurrentHashMap<String, AtomicLong> lastDurations = new ConcurrentHashMap<>();
    /** 方法最近一次调用时间戳（毫秒） */
    private final ConcurrentHashMap<String, AtomicLong> lastCallTimes = new ConcurrentHashMap<>();
    /** 方法最近一次调用结果（成功 / 失败 / 无） */
    private final ConcurrentHashMap<String, String> lastResults = new ConcurrentHashMap<>();
    /** 方法最近一次失败的错误信息 */
    private final ConcurrentHashMap<String, String> lastErrors = new ConcurrentHashMap<>();
    /** 开始时间 */
    private final ThreadLocal<Long> startTimes = new ThreadLocal<>();

    @Override
    /** Pre处理 */
    public boolean preHandle(Method method, Object[] args) {
        startTimes.set(System.currentTimeMillis());
        String methodKey = getMethodKey(method);
        callCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        return true;
    }

    @Override
    /** Post处理 */
    public Object postHandle(Method method, Object[] args, Object result) {
        String methodKey = getMethodKey(method);
        successCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        lastResults.put(methodKey, RESULT_SUCCESS);
        /**
        * record持续时间。
        * @param methodKey 方法钥匙
         */
        recordDuration(methodKey);
        return result;
    }

    @Override
    /** 处理异常 */
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        String methodKey = getMethodKey(method);
        failureCounters.computeIfAbsent(methodKey, k -> new AtomicLong()).incrementAndGet();
        lastResults.put(methodKey, RESULT_FAILURE);
        lastErrors.put(methodKey, error != null
                ? (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()) : "null");
        /**
        * record持续时间。
        * @param methodKey 方法钥匙
         */
        recordDuration(methodKey);
        throw error;
    }

    /**
    * 记录耗时与最近一次调用信息。
    *
    * @param methodKey 方法键
     */
    private void recordDuration(String methodKey) {
        Long startTime = startTimes.get();
        if (startTime == null) {
            return;
        }
        startTimes.remove();
        long duration = System.currentTimeMillis() - startTime;
        long now = System.currentTimeMillis();
        totalDurations.computeIfAbsent(methodKey, k -> new AtomicLong()).addAndGet(duration);
        maxDurations.computeIfAbsent(methodKey, k -> new AtomicLong())
                .accumulateAndGet(duration, Math::max);
        minDurations.computeIfAbsent(methodKey, k -> new AtomicLong(MIN_DURATION_INIT))
                .accumulateAndGet(duration, Math::min);
        lastDurations.put(methodKey, new AtomicLong(duration));
        lastCallTimes.put(methodKey, new AtomicLong(now));

        if (duration > 1000) {
            log.warn("[RPC] 调用耗时过长: method={}, duration={}ms", methodKey, duration);
        }
    }

    /**
    * 获取方法键。
    *
    * @param method 方法
    * @return 方法键
     */
    private String getMethodKey(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
    * 获取调用次数。
    *
    * @param methodKey 方法键
    * @return 调用次数
     */
    public long getCallCount(String methodKey) {
        AtomicLong counter = callCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
    * 获取成功次数。
    *
    * @param methodKey 方法键
    * @return 成功次数
     */
    public long getSuccessCount(String methodKey) {
        AtomicLong counter = successCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
    * 获取失败次数。
    *
    * @param methodKey 方法键
    * @return 失败次数
     */
    public long getFailureCount(String methodKey) {
        AtomicLong counter = failureCounters.get(methodKey);
        return counter != null ? counter.get() : 0;
    }

    /**
    * 获取平均耗时。
    *
    * @param methodKey 方法键
    * @return 平均耗时（毫秒）
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
    * 重置统计。
     */
    public void reset() {
        callCounters.clear();
        successCounters.clear();
        failureCounters.clear();
        totalDurations.clear();
        maxDurations.clear();
        minDurations.clear();
        lastDurations.clear();
        lastCallTimes.clear();
        lastResults.clear();
        lastErrors.clear();
    }

    /**
    * 获取全部方法级统计快照（按调用次数降序）。
    *
    * @return 方法级统计列表
     */
    public List<RpcMetrics.MethodStat> getMethodStats() {
        List<RpcMetrics.MethodStat> result = new ArrayList<>(callCounters.size());
        for (String methodKey : callCounters.keySet()) {
            long total = getCallCount(methodKey);
            long minDuration = minDurations.getOrDefault(methodKey, new AtomicLong(MIN_DURATION_INIT)).get();
            if (minDuration == MIN_DURATION_INIT) {
                minDuration = 0;
            }
            result.add(new RpcMetrics.MethodStat(methodKey,
                    total,
                    /**
                    * 获取成功数量。
                    * @param methodKey 方法钥匙
                     */
                    getSuccessCount(methodKey),
                    /**
                    * 获取失败数量。
                    * @param methodKey 方法钥匙
                     */
                    getFailureCount(methodKey),
                    /**
                    * 获取average持续时间。
                    * @param methodKey 方法钥匙
                     */
                    getAverageDuration(methodKey),
                    maxDurations.getOrDefault(methodKey, new AtomicLong()).get(),
                    minDuration,
                    lastDurations.getOrDefault(methodKey, new AtomicLong()).get(),
                    lastCallTimes.getOrDefault(methodKey, new AtomicLong()).get(),
                    lastResults.getOrDefault(methodKey, RESULT_NONE),
                    lastErrors.getOrDefault(methodKey, "")));
        }
        result.sort((a, b) -> Long.compare(b.total(), a.total()));
        return result;
    }

    @Override
    /** 获取订单 */
    public int getOrder() {
        return -100; // 高优先级
    }
}
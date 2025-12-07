package com.chua.starter.common.support.log;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * MDC工具类
 * <p>
 * 用于在线程之间传递MDC上下文。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class ThreadMdcUtil {

    private static final String TRACE_ID = "traceId";

    /**
     * 获取traceId
     *
     * @return traceId
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 设置traceId
     *
     * @param traceId traceId
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    /**
     * 生成traceId
     *
     * @return traceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 包装Runnable
     *
     * @param runnable Runnable
     * @param context  MDC上下文
     * @return 包装后的Runnable
     */
    public static Runnable wrap(Runnable runnable, Map<String, String> context) {
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * 包装Callable
     *
     * @param callable Callable
     * @param context  MDC上下文
     * @param <T>      返回类型
     * @return 包装后的Callable
     */
    public static <T> Callable<T> wrap(Callable<T> callable, Map<String, String> context) {
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }
}

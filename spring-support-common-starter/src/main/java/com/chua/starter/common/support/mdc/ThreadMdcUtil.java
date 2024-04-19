package com.chua.starter.common.support.mdc;

import com.chua.common.support.mdc.TraceContextHolder;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 线程mdc-util
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class ThreadMdcUtil {
    public static void setTraceIdIfAbsent() {
        TraceContextHolder.init();
    }

    public static <T> Callable<T> wrap(final Callable<T> callable, final Map<String, String> context) {
        return () -> {
            if (context == null) {
                TraceContextHolder.clear();
            } else {
                TraceContextHolder.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                return callable.call();
            } finally {
                TraceContextHolder.clear();
            }
        };
    }

    public static Runnable wrap(final Runnable runnable, final Map<String, String> context) {
        return () -> {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                runnable.run();
            } finally {
                TraceContextHolder.clear();
            }
        };
    }
}
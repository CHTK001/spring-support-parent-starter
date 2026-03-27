package com.chua.starter.job.support;

import java.util.function.Supplier;

/**
 * Job 表名上下文。
 * <p>
 * 允许在同一个应用内按线程覆盖 sys_job 系列表的物理表名，
 * 以支持平台侧按业务命名空间操作不同任务表。
 * 该上下文只适用于当前线程内的同步数据库访问，不应跨线程或异步回调继续传递。
 * </p>
 */
public final class JobTableContextHolder {

    private static final ThreadLocal<JobProperties.Table> CONTEXT = new ThreadLocal<>();

    private JobTableContextHolder() {
    }

    public static JobProperties.Table get() {
        return CONTEXT.get();
    }

    public static void set(JobProperties.Table table) {
        CONTEXT.set(table);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 在当前调用栈内临时覆盖 Job 表映射，并在结束后恢复之前的上下文。
     */
    public static <T> T withTables(JobProperties.Table table, Supplier<T> supplier) {
        JobProperties.Table previous = CONTEXT.get();
        try {
            CONTEXT.set(table);
            return supplier.get();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }

    public static void withTables(JobProperties.Table table, Runnable runnable) {
        withTables(table, () -> {
            runnable.run();
            return null;
        });
    }
}

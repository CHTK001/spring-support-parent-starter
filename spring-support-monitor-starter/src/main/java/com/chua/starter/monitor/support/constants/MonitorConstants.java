package com.chua.starter.monitor.support.constants;

/**
 * 监控模块常量定义
 *
 * @since 2026/8/4
 * @author CH
 */
public final class MonitorConstants {

    /**
     * 默认采集周期(毫秒)
     */
    public static final long DEFAULT_COLLECT_INTERVAL_MS = 10_000L;

    /**
     * 默认历史保留天数
     */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    /**
     * 默认任务超时(毫秒)
     */
    public static final int DEFAULT_TASK_TIMEOUT_MS = 5_000;

    /**
     * 并发采集上限
     */
    public static final int COLLECT_CONCURRENCY = 50;

    /**
     * 入库缓冲条数
     */
    public static final int BATCH_INSERT_SIZE = 200;

    /**
     * 背压队列容量
     */
    public static final int BACKPRESSURE_BUFFER = 10_000;

    /**
     * 缓存名前缀
     */
    public static final String CACHE_PREFIX = "monitor:";

    /**
     * 私有构造方法
     */
    private MonitorConstants() {
    }
}

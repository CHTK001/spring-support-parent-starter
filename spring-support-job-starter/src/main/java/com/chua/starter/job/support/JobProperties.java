package com.chua.starter.job.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自研调度模块配置属性。
 *
 * <p>配置前缀 {@code plugin.job},内部拆分为:</p>
 * <ul>
 *   <li>{@link Role} - 三选: SCHEDULER / EXECUTOR / SINGLE</li>
 *   <li>{@link ClusterConfig} - 集群身份与单秒扫描间隔</li>
 *   <li>{@link SyncConfig} - 长链同步,再拆 {@code scheduler} 与 {@code executor}</li>
 *   <li>{@link PoolConfig} - 执行线程池</li>
 * </ul>
 *
 * <h3>三模式拓扑</h3>
 * <ul>
 *   <li>SCHEDULER: 起 Leader 单秒扫描 + 对外暴露长链路</li>
 *   <li>EXECUTOR: 连上 Scheduler, 消费 cmd, 上行 log</li>
 *   <li>SINGLE: 单机模式, 不连别人, 内部 Leader + Bus + Executor 闭环</li>
 * </ul>
 *
 * <h3>完整配置示例</h3>
 * <pre>
 * plugin:
 *   job:
 *     enable: true
 *     role: SINGLE
 *     node-id: ""
 *     cluster:
 *       group: payment
 *       scan-interval-ms: 1000
 *       pre-read-ms: 5000
 *       lease-seconds: 15
 *     sync:
 *       in-memory: false
 *       scheduler:
 *         listen-host: 0.0.0.0
 *         listen-port: 8081
 *       executor:
 *         server-url: ""
 *         reconnect-base-ms: 500
 *         reconnect-max-ms: 30000
 *     pool:
 *       virtual: true
 *       core-size: 16
 *       max-size: 64
 *       queue-size: 128
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = JobProperties.PRE)
public class JobProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.job";

    /**
     * 是否启用调度模块,默认 true,引入即开箱即用
     */
    private boolean enable = true;

    /**
     * 进程角色,默认 SINGLE (单机自闭环,开箱即用)
     */
    private Role role = Role.SINGLE;

    /**
     * 当前节点唯一标识,留空时自动按 hostName-pid 推导
     */
    private String nodeId;

    /**
     * 集群配置
     */
    private final ClusterConfig cluster = new ClusterConfig();

    /**
     * 长链同步配置 (嵌套 scheduler/executor)
     */
    private final SyncConfig sync = new SyncConfig();

    /**
     * 执行线程池配置
     */
    private final PoolConfig pool = new PoolConfig();

    /**
     * 日志配置
     */
    private final LogConfig log = new LogConfig();

    /**
     * 当前角色是否为 SCHEDULER
     *
     * @return 是 SCHEDULER 返回 true
     */
    public boolean isScheduler() {
        return role == Role.SCHEDULER;
    }

    /**
     * 当前角色是否为 EXECUTOR
     *
     * @return 是 EXECUTOR 返回 true
     */
    public boolean isExecutor() {
        return role == Role.EXECUTOR;
    }

    /**
     * 当前角色是否为 SINGLE
     *
     * @return 是 SINGLE 返回 true
     */
    public boolean isSingle() {
        return role == Role.SINGLE;
    }

    /**
     * 进程角色枚举。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Role {

        /**
         * 调度中心模式: Leader 单秒扫描 + 暴露长链同步
         */
        SCHEDULER,

        /**
         * 执行器模式: 连上 Scheduler, 消费 cmd, 上行 log
         */
        EXECUTOR,

        /**
         * 单机模式: 纯进程内闭环, 既不连别人, 也不暴露
         */
        SINGLE
    }

    /**
     * 集群配置。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class ClusterConfig {

        /**
         * 默认预读时间窗 (毫秒)
         */
        public static final long DEFAULT_PRE_READ_MS = 5000L;

        /**
         * 默认 Leader 租约时长 (秒)
         */
        public static final int DEFAULT_LEASE_SECONDS = 15;

        /**
         * 默认扫描间隔 (毫秒)
         */
        public static final long DEFAULT_SCAN_INTERVAL_MS = 1000L;

        /**
         * 命名空间, 同一 group 内 Leader 唯一
         */
        private String group = "default";

        /**
         * 单秒扫描间隔, 默认 1000ms
         */
        private long scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;

        /**
         * 预读时间窗, 默认 5000ms
         */
        private long preReadMs = DEFAULT_PRE_READ_MS;

        /**
         * Leader 抢占租约时长, 默认 15s
         */
        private int leaseSeconds = DEFAULT_LEASE_SECONDS;
    }

    /**
     * 长链同步配置, 内部拆分 scheduler 与 executor。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class SyncConfig {

        /**
         * 是否强制使用内存 SPI 实现 (跳过真实 RSocket/HTTP 选取)。
         * <p>主要用于单元测试和精简部署,默认 false。</p>
         */
        private boolean inMemory = false;

        /**
         * 调度中心侧配置 (role=SCHEDULER 或 SINGLE 时生效, SINGLE 仅占位)
         */
        private final SchedulerConfig scheduler = new SchedulerConfig();

        /**
         * 执行器侧配置 (role=EXECUTOR 时生效)
         */
        private final ExecutorConfig executor = new ExecutorConfig();
    }

    /**
     * Scheduler 长链配置。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class SchedulerConfig {

        /**
         * 默认监听端口
         */
        public static final int DEFAULT_LISTEN_PORT = 8081;

        /**
         * 监听主机
         */
        private String listenHost = "0.0.0.0";

        /**
         * 监听端口
         */
        private int listenPort = DEFAULT_LISTEN_PORT;
    }

    /**
     * Executor 长链配置。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class ExecutorConfig {

        /**
         * 默认重连退避起点 (毫秒)
         */
        public static final int DEFAULT_RECONNECT_BASE_MS = 500;

        /**
         * 默认重连退避上限 (毫秒)
         */
        public static final int DEFAULT_RECONNECT_MAX_MS = 30000;

        /**
         * 调度中心长链 URL, 例如 ws://10.0.0.11:8081
         */
        private String serverUrl;

        /**
         * 重连退避起点
         */
        private int reconnectBaseMs = DEFAULT_RECONNECT_BASE_MS;

        /**
         * 重连退避上限
         */
        private int reconnectMaxMs = DEFAULT_RECONNECT_MAX_MS;
    }

    /**
     * 执行线程池配置。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class PoolConfig {

        /**
         * 默认核心线程数 (对齐常见逻辑处理器数量, 就近吞吐峰值)
         */
        public static final int DEFAULT_CORE_SIZE = 16;

        /**
         * 默认最大线程数 (4×core, 阻塞型突发削峰)
         */
        public static final int DEFAULT_MAX_SIZE = 64;

        /**
         * 默认队列容量 (小队列快速扩容削峰, 避免大队列吞掉 max 弹性)
         */
        public static final int DEFAULT_QUEUE_CAPACITY = 128;

        /**
         * 核心线程数
         */
        private int coreSize = DEFAULT_CORE_SIZE;

        /**
         * 最大线程数
         */
        private int maxSize = DEFAULT_MAX_SIZE;

        /**
         * 任务队列容量
         */
        private int queueSize = DEFAULT_QUEUE_CAPACITY;

        /**
         * 是否启用虚拟线程执行器, 默认开启。
         *
         * <p>开启时使用 {@code Executors.newVirtualThreadPerTaskExecutor()}
         * (每任务一个虚拟线程, 阻塞型任务大幅提升吞吐, 无 max/queue 限制);
         * 关闭时回退到 {@link ThreadPoolExecutor} (core/max/queue 平台线程池)。</p>
         */
        private boolean virtual = true;
    }

    /**
     * 日志配置。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class LogConfig {

        /**
         * 默认日志根目录
         */
        public static final String DEFAULT_BASE_PATH = "./logs/job";

        /**
         * 默认异步队列容量
         */
        public static final int DEFAULT_QUEUE_SIZE = 4096;

        /**
         * 默认日志保留天数
         */
        public static final int DEFAULT_RETENTION_DAYS = 30;

        /**
         * 默认清理 cron
         */
        public static final String DEFAULT_CLEANUP_CRON = "0 0 0 * * ?";

        /**
         * 是否启用文件日志
         */
        private boolean enabled = false;

        /**
         * 日志根目录
         */
        private String basePath = DEFAULT_BASE_PATH;

        /**
         * 是否启用异步写入
         */
        private boolean async = true;

        /**
         * 异步队列容量
         */
        private int queueSize = DEFAULT_QUEUE_SIZE;

        /**
         * 是否启用日志自动清理
         */
        private boolean cleanupEnabled = false;

        /**
         * 日志保留天数 (超过该天数的日志将被清理)
         */
        private int cleanupRetentionDays = DEFAULT_RETENTION_DAYS;

        /**
         * 日志清理定时 cron 表达式
         */
        private String cleanupCron = DEFAULT_CLEANUP_CRON;
    }
}

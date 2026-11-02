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
 *     manage:
 *       enable: false   # 开启后暴露 /v2/job/** 任务治理接口 (仅管理员)
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
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
     * 进程角色,默认 单个 (单机自闭环,开箱即用)
     */
    private Role role = Role.SINGLE;

    /**
     * 当前节点唯一标识,留空时自动按 主机名称-pid 推导
     */
    private String nodeId;

    /**
     * 集群配置
     */
    private final ClusterConfig cluster = new ClusterConfig();

    /**
     * 长链同步配置 (嵌套 调度器/执行器)
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
     * 外部治理接口配置 (HTTP /v2/job/**)
     */
    private final ManageConfig manage = new ManageConfig();

    /**
     * 查询缓存配置。
     *
     * <h3>通过 {@code plugin.job.cache.*} 控制</h3>
     * <ul>
     *   <li><b>关闭（默认）</b>: 每次拍次真实查库（{@code selectDbNow} 取相位 +
     *   {@code selectList} 扫候选行），每拍 2 次查询，行为与历史完全一致；</li>
     *   <li><b>开启</b>: {@code findDue} 的扫描结果连同相位（数据库时间）按 TTL 跨拍次复用，
     *   每拍查询压到 0~1 次，但相位（扫描窗与错过判定）最多滞后 TTL。</li>
     * </ul>
     *
     * <h3>代价说明</h3>
     * <p>开启后每拍 <b>不重新读数据库时钟</b>，所以：</p>
     * <ul>
     *   <li>扫描窗的上界（{@code now + preReadMs}）在 TTL 内不更新；</li>
     *   <li>1 秒内的错过判定（misfire）可能少触发一次；</li>
     *   <li>扫描窗的上界和错过判定（misfire）最多滚后约 TTL 秒；</li>
     *   <li>相位就是滞后为 TTL 的相位，下一拍从每个槽位重新算起时仍自洽。</li>
     * </ul>
     * <p>如果您需要严格的相位精度，请保持关闭；如果您需要尽可能削减扫描读库，请开启。</p>
     *
     * @since 4.0.0.51
     * @author CH
     */
    @Data
    public static class CacheConfig {

        /**
         * 是否启用查询缓存（默认关闭，行为与历史一致）
         */
        private boolean enabled = false;

        /**
         * 扫描结果缓存有效期（毫秒），默认 5000
         *
         * <p>允许阈值与 {@link ClusterConfig#DEFAULT_PRE_READ_MS} 同号：候选窗口越宽，
         * 这个窗口的扫描结果越稳定，越值得复用。</p>
         */
        private long ttlMs = 5000L;
    }

    /**
     * 查询缓存配置
     */
    private final CacheConfig cache = new CacheConfig();

    /**
     * 当前角色是否为 调度器
     *
     * @return 是 调度器 返回 true
     */
    public boolean isScheduler() {
        return role == Role.SCHEDULER;
    }

    /**
     * 当前角色是否为 执行器
     *
     * @return 是 执行器 返回 true
     */
    public boolean isExecutor() {
        return role == Role.EXECUTOR;
    }

    /**
     * 当前角色是否为 单个
     *
     * @return 是 单个 返回 true
     */
    public boolean isSingle() {
        return role == Role.SINGLE;
    }

    /**
     * 进程角色枚举。
     *
     * @since 4.0.0.42
     * @author CH
     */
    public enum Role {

        /**
         * 调度中心模式: Leader 单秒扫描 + 暴露长链同步
         */
        SCHEDULER,

        /**
         * 执行器模式: 连上 调度器, 消费 CMD, 上行 日志
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
     * @since 4.0.0.42
     * @author CH
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
         * 命名空间, 同一 群体 内 Leader 唯一
         *
         * <p>本值是<b>作业归属</b>口径: Leader 只扫描 {@code sys_job_group = group} 的任务行,
         * 与 {@code sys_job.sys_job_group} 对齐。不要拿它当选主租约的键, 见 {@link #electionNamespace}。</p>
         */
        private String group = "default";

    /**
     * 选主实现（SPI 键, 大小写不敏感）: 默认 {@code mysql} 走数据库租约;
     * 注册在 {@code META-INF/extensions/com.chua.starter.job.support.cluster.LeaderElectionResolver}。
     *
     * <p>留空或取不到该键时<b>回落</b>数据库实现, 保证默认路径与历史行为一致 ——
     * 选非 DB 实现的人自带其基础设施（如 Redis），不强制单机用户引入额外中间件。</p>
     */
    private String resolver = "mysql";

        /**
         * 选主租约命名空间, 留空时按 {@link #resolveElectionNamespace(String)} 推导。
         *
         * <p><b>与 {@link #group} 分工的原因</b>: 选主租约在 {@code sys_job_cluster} 里以本值为主键,
         * 同一行只能有一个 Leader。把「作业分组」直接当租约键时, 两个彼此独立、恰好都叫
         * {@code default} 的部署会抢同一条租约行, 互相把对方踢下 Leader, 表现为两边任务都只跑一半拍。
         * 作业分组允许跨部署重名, 选主命名空间不允许, 因此必须是独立配置项。</p>
         *
         * <p>同一集群内所有节点的取值必须一致 (通常都用默认推导), 否则会分裂成两个各自当 Leader 的集群。</p>
         */
        private String electionNamespace;

        /**
         * 推导选主租约命名空间: 显式配置优先, 其次应用名, 最后才回落到作业分组。
         *
         * <p>用应用名做默认是刻意的: 调度集群本就以「应用」为单位,
         * 两个独立部署的应用名通常不同, 天然不会抢同一条租约行; 而同一个应用的多节点
         * 应用名相同, 仍会正确地选出唯一 Leader。</p>
         *
         * @param applicationName Spring 应用名, 可为空
         * @return 选主租约命名空间 (非空)
         */
        public String resolveElectionNamespace(String applicationName) {
            if (electionNamespace != null && !electionNamespace.isBlank()) {
                return electionNamespace.trim();
            }
            if (applicationName != null && !applicationName.isBlank()) {
                return applicationName.trim();
            }
            return group == null || group.isBlank() ? "default" : group.trim();
        }

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
     * 长链同步配置, 内部拆分 调度器 与 执行器。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class SyncConfig {

        /**
         * 兼容旧配置项；设置为 true 时明确拒绝启动，不提供内存同步伪实现。
         */
        private boolean inMemory = false;

        /**
         * 调度中心侧配置 (角色=调度器 或 单个 时生效, 单个 仅占位)
         */
        private final SchedulerConfig scheduler = new SchedulerConfig();

        /**
         * 执行器侧配置 (角色=执行器 时生效)
         */
        private final ExecutorConfig executor = new ExecutorConfig();
    }

    /**
     * 调度器 长链配置。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class SchedulerConfig {

        /**
         * 默认监听端口
         */
        public static final int DEFAULT_LISTEN_PORT = 8081;

        /**
         * 默认长链协议
         *
         * <p>必须与执行器 {@code sync.executor.server-url} 的 scheme 一致，否则两端协议错配：
         * 执行器连得上却永远发不出可解析的注册报文，调度中心的执行器注册表恒为空。</p>
         */
        public static final String DEFAULT_PROTOCOL = "tcp";

        /**
         * 监听主机
         */
        private String listenHost = "0.0.0.0";

        /**
         * 监听端口
         */
        private int listenPort = DEFAULT_LISTEN_PORT;

        /**
         * 长链协议 (tcp / websocket / udp / http / rsocket)
         *
         * <p>按本值优先挑 SyncServer SPI 实现，挑不到再按内置兜底顺序回退。</p>
         */
        private String protocol = DEFAULT_PROTOCOL;
    }

    /**
     * 执行器 长链配置。
     *
     * @since 4.0.0.42
     * @author CH
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
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class PoolConfig {

        /**
         * 默认核心线程数 (对齐常见逻辑处理器数量, 就近吞吐峰值)
         */
        public static final int DEFAULT_CORE_SIZE = 16;

        /**
         * 默认最大线程数 (4×核心, 阻塞型突发削峰)
         */
        public static final int DEFAULT_MAX_SIZE = 64;

        /**
         * 默认队列容量 (小队列快速扩容削峰, 避免大队列吞掉 最大 弹性)
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
         * (每任务一个虚拟线程, 阻塞型任务大幅提升吞吐, 无 最大/队列 限制);
         * 关闭时回退到 {@link ThreadPoolExecutor} (核心/最大/队列 平台线程池)。</p>
         */
        private boolean virtual = true;
    }

    /**
     * 日志配置。
     *
     * @since 4.0.0.42
     * @author CH
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

    /**
     * 外部治理接口配置。
     *
     * <p>只影响 HTTP 出口 {@code /v2/job/**} 是否装配; 进程内治理始终可用
     * ({@link com.chua.starter.job.support.operate.JobOperator}), 不受本开关约束。</p>
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class ManageConfig {

        /**
         * 是否暴露任务治理 HTTP 接口, 默认 false (需应用显式开启)
         */
        private boolean enable = false;
    }
}

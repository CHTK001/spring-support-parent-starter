package com.chua.starter.hotsport.support.storage;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.StorageConfig;
import com.chua.runtime.apm.storage.StorageManager;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import com.chua.runtime.protocol.Endpoint;
import com.chua.runtime.protocol.StatusCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 JDBC 的 APM 存储后端（MySQL / MariaDB）。
 *
 * <p><b>为什么需要它</b>：{@code utils-support-runtime-apm} 自带的
 * {@code inmemory} 重启即归零、{@code sqlite} 只适合单机小体量。生产上 APM 数据
 * 往往要和业务数据放在同一个可运维的数据库里：能 {@code SELECT} 直查、能被备份、
 * 能多实例共享。本类把 {@link ApmStorage} 的读写落到标准 MySQL 协议上。</p>
 *
 * <p><b>怎么接入</b>：{@code StorageManager.resolve()} 在找不到内置类型时会先查
 * 注册表，因此只需在初始化前 {@link #register()} 一次，然后把
 * {@code plugin.hotsport.apm.storage.type} 配成 {@code mysql} 即可。</p>
 *
 * <p><b>为什么不会拖垮宿主</b>：</p>
 * <ul>
 *   <li>建表 / 连通性校验失败会抛异常，由 {@code StorageManager.init} 捕获后
 *       降级为 {@code NoopStorage}，业务进程照常启动；</li>
 *   <li>写入默认走有界队列 + 单写线程批量提交，队列满时丢弃并计数，
 *       绝不让业务线程等待数据库；</li>
 *   <li>任何读写异常都只记日志，不向上抛给采集链路。</li>
 * </ul>
 *
 * <p><b>与 sqlite 实现对齐的语义</b>：表结构、过滤条件（时间区间 / traceId /
 * 主机 / 协议 / 软件 / 状态 / 仅错误）、排序与分页规则、依赖边的累加合并
 * （{@code call_count + excluded.call_count}）、按保留期清理与按容量淘汰。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class JdbcStorage implements ApmStorage {

    /**
     * 存储类型名。{@code apm.storage.type} 必须与它完全一致才会命中本实现。
     */
    public static final String TYPE = "mysql";

    /**
     * JDBC URL 配置键。
     */
    public static final String KEY_URL = "apm.storage.url";

    /**
     * 数据库用户名配置键。
     */
    public static final String KEY_USER = "apm.storage.username";

    /**
     * 数据库密码配置键。
     */
    public static final String KEY_PASSWORD = "apm.storage.password";

    /**
     * 连接池大小配置键。
     */
    public static final String KEY_POOL_SIZE = "apm.storage.pool.size";

    /**
     * 单批写入条数配置键。
     */
    public static final String KEY_BATCH_SIZE = "apm.storage.batch.size";

    /**
     * 保留期配置键（毫秒）。
     */
    private static final String KEY_RETENTION = "apm.storage.retention.ms";

    /**
     * 容量配置键（单表行数上限）。
     */
    private static final String KEY_CAPACITY = "apm.storage.capacity";

    /**
     * 异步写开关配置键。
     */
    private static final String KEY_ASYNC = "apm.storage.async";

    /**
     * 自维护周期配置键（毫秒）。
     */
    private static final String KEY_MAINTENANCE = "apm.storage.maintenance.interval.ms";

    /**
     * 停机落盘等待时长配置键（毫秒）。
     */
    private static final String KEY_FLUSH = "apm.storage.flush.timeout.ms";

    /**
     * 默认保留期：7 天。
     */
    private static final long DEFAULT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * 默认单表容量。
     */
    private static final int DEFAULT_CAPACITY = 100000;

    /**
     * 默认自维护周期。
     */
    private static final long DEFAULT_MAINTENANCE_MS = 60_000L;

    /**
     * 默认停机等待时长。
     */
    private static final long DEFAULT_FLUSH_MS = 5_000L;

    /**
     * 统计缓存时长：一帧一次 COUNT(*) 会白烧数据库。
     */
    private static final long STATS_TTL_MS = 1_000L;

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(JdbcStorage.class);

    /**
     * JSON 序列化（依赖边端点、传输属性）。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 传输表列顺序（与 DDL 一致）。
     */
    private static final String[] TRANSMISSION_COLUMNS = {
            "id", "trace_id", "span_id", "parent_span_id",
            "source_protocol", "source_software", "source_host", "source_port", "source_path",
            "target_protocol", "target_software", "target_host", "target_port", "target_path",
            "protocol", "software", "operation", "status", "status_code",
            "start_time", "end_time", "duration", "bytes_out", "bytes_in",
            "error_type", "error_message", "attributes"
    };

    /**
     * 依赖表列顺序。
     */
    private static final String[] DEPENDENCY_COLUMNS = {
            "edge_id", "source", "target", "protocol", "software",
            "call_count", "total_duration", "error_count", "last_error", "last_call_time"
    };

    /**
     * 泄漏表列顺序。
     */
    private static final String[] LEAK_COLUMNS = {
            "id", "handle_id", "kind", "name", "thread", "created_at", "closed_at", "stack_trace"
    };

    /**
     * 日志表列顺序。
     */
    private static final String[] LOG_COLUMNS = {
            "id", "timestamp", "level", "logger", "class_name", "method_name", "message", "trace_id"
    };

    /**
     * 建表语句。全部 {@code IF NOT EXISTS}，重复启动安全。
     */
    private static final String[] DDL = {
            "CREATE TABLE IF NOT EXISTS transmissions ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "trace_id VARCHAR(128),"
                    + "span_id VARCHAR(128),"
                    + "parent_span_id VARCHAR(128),"
                    + "source_protocol VARCHAR(32),"
                    + "source_software VARCHAR(64),"
                    + "source_host VARCHAR(255),"
                    + "source_port INT DEFAULT 0,"
                    + "source_path VARCHAR(1024),"
                    + "target_protocol VARCHAR(32),"
                    + "target_software VARCHAR(64),"
                    + "target_host VARCHAR(255),"
                    + "target_port INT DEFAULT 0,"
                    + "target_path VARCHAR(1024),"
                    + "protocol VARCHAR(32),"
                    + "software VARCHAR(64),"
                    + "operation VARCHAR(512),"
                    + "status VARCHAR(32),"
                    + "status_code INT DEFAULT 0,"
                    + "start_time BIGINT DEFAULT 0,"
                    + "end_time BIGINT DEFAULT 0,"
                    + "duration BIGINT DEFAULT 0,"
                    + "bytes_out BIGINT DEFAULT 0,"
                    + "bytes_in BIGINT DEFAULT 0,"
                    + "error_type VARCHAR(255),"
                    + "error_message TEXT,"
                    + "attributes MEDIUMTEXT,"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_trans_start (start_time),"
                    + "KEY idx_trans_trace (trace_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS dependencies ("
                    + "edge_id VARCHAR(255) NOT NULL,"
                    + "source MEDIUMTEXT,"
                    + "target MEDIUMTEXT,"
                    + "protocol VARCHAR(32),"
                    + "software VARCHAR(64),"
                    + "call_count BIGINT DEFAULT 0,"
                    + "total_duration BIGINT DEFAULT 0,"
                    + "error_count BIGINT DEFAULT 0,"
                    + "last_error TEXT,"
                    + "last_call_time BIGINT DEFAULT 0,"
                    + "PRIMARY KEY (edge_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS leaks ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "handle_id VARCHAR(255),"
                    + "kind VARCHAR(64),"
                    + "name VARCHAR(512),"
                    + "thread VARCHAR(255),"
                    + "created_at BIGINT DEFAULT 0,"
                    + "closed_at BIGINT DEFAULT 0,"
                    + "stack_trace MEDIUMTEXT,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_leak_handle (handle_id),"
                    + "KEY idx_leak_created (created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS `logs` ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "`timestamp` BIGINT DEFAULT 0,"
                    + "`level` VARCHAR(32),"
                    + "`logger` VARCHAR(255),"
                    + "class_name VARCHAR(255),"
                    + "method_name VARCHAR(255),"
                    + "message MEDIUMTEXT,"
                    + "trace_id VARCHAR(128),"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_log_ts (`timestamp`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    };

    /**
     * 传输写入语句。
     */
    private static final String INSERT_TRANSMISSION = "INSERT INTO transmissions ("
            + "trace_id, span_id, parent_span_id, source_protocol, source_software, source_host,"
            + "source_port, source_path, target_protocol, target_software, target_host, target_port,"
            + "target_path, protocol, software, operation, status, status_code, start_time, end_time,"
            + "duration, bytes_out, bytes_in, error_type, error_message, attributes"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * 依赖写入语句：同一条边累加计数，与 sqlite 实现语义一致。
     */
    private static final String UPSERT_DEPENDENCY = "INSERT INTO dependencies ("
            + "edge_id, source, target, protocol, software, call_count, total_duration,"
            + "error_count, last_error, last_call_time"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
            + "call_count = call_count + VALUES(call_count),"
            + "total_duration = total_duration + VALUES(total_duration),"
            + "error_count = error_count + VALUES(error_count),"
            + "last_error = COALESCE(VALUES(last_error), last_error),"
            + "last_call_time = VALUES(last_call_time)";

    /**
     * 泄漏写入语句：按 handle_id 幂等覆盖（关闭时间会从 0 变成真实值）。
     */
    private static final String UPSERT_LEAK = "INSERT INTO leaks ("
            + "handle_id, kind, name, thread, created_at, closed_at, stack_trace"
            + ") VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
            + "kind = VALUES(kind), name = VALUES(name), thread = VALUES(thread),"
            + "closed_at = VALUES(closed_at), stack_trace = VALUES(stack_trace)";

    /**
     * 日志写入语句。
     */
    private static final String INSERT_LOG = "INSERT INTO `logs` ("
            + "`timestamp`, `level`, `logger`, class_name, method_name, message, trace_id"
            + ") VALUES (?,?,?,?,?,?,?)";

    /**
     * JDBC URL。
     */
    private volatile String url = "";

    /**
     * 数据库用户名。
     */
    private volatile String user = "";

    /**
     * 数据库密码。
     */
    private volatile String password = "";

    /**
     * 保留期（毫秒）。
     */
    private volatile long retentionMs = DEFAULT_RETENTION_MS;

    /**
     * 单表容量上限。
     */
    private volatile int capacity = DEFAULT_CAPACITY;

    /**
     * 自维护周期（毫秒）。
     */
    private volatile long maintenanceIntervalMs = DEFAULT_MAINTENANCE_MS;

    /**
     * 停机等待时长（毫秒）。
     */
    private volatile long flushTimeoutMs = DEFAULT_FLUSH_MS;

    /**
     * 是否异步批量写。
     */
    private volatile boolean async = true;

    /**
     * 是否已启动。
     */
    private volatile boolean running;

    /**
     * 连接池大小。
     */
    private volatile int poolSize = 4;

    /**
     * 单批条数。
     */
    private volatile int batchSize = 200;

    /**
     * 空闲连接。
     */
    private final BlockingQueue<Connection> idle = new LinkedBlockingQueue<>();

    /**
     * 待写队列（有界，满了丢弃）。
     */
    private final BlockingQueue<Op> queue = new ArrayBlockingQueue<>(20000);

    /**
     * 写线程。
     */
    private volatile Thread writer;

    /**
     * 自维护线程。
     */
    private volatile ScheduledExecutorService maintenance;

    /**
     * 已落盘条数。
     */
    private final AtomicLong flushed = new AtomicLong();

    /**
     * 丢弃条数（队列满 / 写失败）。
     */
    private final AtomicLong dropped = new AtomicLong();

    /**
     * 容量淘汰条数。
     */
    private final AtomicLong evicted = new AtomicLong();

    /**
     * 最近一次自维护时间。
     */
    private volatile long lastMaintenanceAt;

    /**
     * 统计缓存。
     */
    private volatile Map<String, Long> statsCache = Map.of();

    /**
     * 统计缓存生成时间。
     */
    private volatile long statsAt;

    /**
     * 库体积缓存。
     */
    private volatile long dbBytesCache;

    /**
     * 库体积缓存时间。
     */
    private volatile long dbBytesAt;

    /**
     * 注册本实现，供 {@code StorageManager} 按类型名解析。
     *
     * <p>必须在 APM 引擎初始化（{@code StorageManager.init}）之前调用。
     * 重复调用是安全的：注册表按 {@link #name()} 覆盖。</p>
     */
    public static void register() {
        try {
            StorageManager.register(new JdbcStorage());
        } catch (Throwable t) {
            log.warn("[APM-JDBC] 注册存储类型失败: {}", t.getMessage());
        }
    }

    /**
     * 存储类型名。
     *
     * @return 固定为 {@code mysql}
     */
    @Override
    public String name() {
        return TYPE;
    }

    /**
     * 启动存储：校验连通性、建表、起写线程与自维护线程。
     *
     * @param config 存储配置
     */
    @Override
    public void start(StorageConfig config) {
        stop();
        if (config == null) {
            throw new IllegalArgumentException("存储配置为空");
        }
        String jdbcUrl = trimToNull(config.get(KEY_URL));
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("apm.storage.url 未配置（type=mysql 时必须给出 JDBC URL）");
        }
        this.url = jdbcUrl;
        this.user = config.get(KEY_USER, "");
        this.password = config.get(KEY_PASSWORD, "");
        this.retentionMs = Math.max(1000L, config.getLong(KEY_RETENTION, DEFAULT_RETENTION_MS));
        this.capacity = Math.max(1, config.getInt(KEY_CAPACITY, DEFAULT_CAPACITY));
        this.maintenanceIntervalMs = Math.max(5000L, config.getLong(KEY_MAINTENANCE, DEFAULT_MAINTENANCE_MS));
        this.flushTimeoutMs = Math.max(500L, config.getLong(KEY_FLUSH, DEFAULT_FLUSH_MS));
        this.async = config.getBoolean(KEY_ASYNC, true);
        this.poolSize = Math.max(2, config.getInt(KEY_POOL_SIZE, 4));
        this.batchSize = Math.max(1, config.getInt(KEY_BATCH_SIZE, 200));

        loadDriver(jdbcUrl);
        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            for (String ddl : DDL) {
                st.execute(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 MySQL 存储失败: " + e.getMessage(), e);
        }
        try {
            for (int i = 0; i < poolSize; i++) {
                idle.add(openConnection());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 MySQL 连接池失败: " + e.getMessage(), e);
        }

        running = true;
        if (async) {
            Thread t = new Thread(this::writerLoop, "apm-jdbc-writer");
            t.setDaemon(true);
            this.writer = t;
            t.start();
        }
        ScheduledExecutorService m = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "apm-jdbc-maintenance");
            t.setDaemon(true);
            return t;
        });
        this.maintenance = m;
        m.scheduleWithFixedDelay(this::maintain, maintenanceIntervalMs, maintenanceIntervalMs,
                TimeUnit.MILLISECONDS);
        log.info("[APM-JDBC] 存储已启动: {}（池={}, 异步={}, 保留={}ms, 容量={}）",
                maskUrl(url), poolSize, async, retentionMs, capacity);
    }

    /**
     * 停止存储：先落盘队列、再关线程与连接。
     */
    @Override
    public void stop() {
        running = false;
        Thread w = this.writer;
        this.writer = null;
        if (w != null) {
            w.interrupt();
            try {
                w.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueue();
        ScheduledExecutorService m = this.maintenance;
        this.maintenance = null;
        if (m != null) {
            m.shutdownNow();
        }
        Connection c;
        while ((c = idle.poll()) != null) {
            closeQuietly(c);
        }
    }

    /**
     * 写入一条传输记录。
     *
     * @param event 传输事件
     */
    @Override
    public void appendTransmission(TransmissionEvent event) {
        if (event != null) {
            submit(new Op(Kind.TRANSMISSION, event));
        }
    }

    /**
     * 写入（合并）一条依赖边。
     *
     * @param edge 依赖边
     */
    @Override
    public void appendDependency(DependencyEdge edge) {
        if (edge != null && edge.getSource() != null && edge.getTarget() != null) {
            submit(new Op(Kind.DEPENDENCY, edge));
        }
    }

    /**
     * 写入（覆盖）一条资源泄漏记录。
     *
     * @param record 泄漏记录
     */
    @Override
    public void appendLeak(LeakRecord record) {
        if (record != null) {
            submit(new Op(Kind.LEAK, record));
        }
    }

    /**
     * 写入一条日志记录。
     *
     * @param record 日志记录
     */
    @Override
    public void appendLog(LogRecord record) {
        if (record != null) {
            submit(new Op(Kind.LOG, record));
        }
    }

    /**
     * 查询传输记录。
     *
     * @param query 查询条件
     * @return 传输记录（按开始时间倒序）
     */
    @Override
    public List<TransmissionEvent> queryTransmissions(Query query) {
        Query q = query == null ? new Query() : query;
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", TRANSMISSION_COLUMNS))
                .append(" FROM transmissions WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendTimeFilter(sql, params, "start_time", q);
        appendLike(sql, params, "trace_id", q.getTraceId());
        appendLike(sql, params, "source_host", q.getSourceHost());
        appendLike(sql, params, "target_host", q.getTargetHost());
        appendLike(sql, params, "protocol", q.getProtocol());
        appendLike(sql, params, "software", q.getSoftware());
        appendLike(sql, params, "status", q.getStatus());
        if (q.isErrorOnly()) {
            sql.append(" AND status = 'ERROR'");
        }
        sql.append(" ORDER BY start_time DESC LIMIT ").append(limit(q))
                .append(" OFFSET ").append(Math.max(0, q.getOffset()));
        List<TransmissionEvent> result = new ArrayList<>();
        eachRow(sql.toString(), params, rs -> result.add(mapTransmission(rs)));
        return result;
    }

    /**
     * 查询依赖边。
     *
     * @param query 查询条件
     * @return 依赖边
     */
    @Override
    public List<DependencyEdge> queryDependencies(Query query) {
        Query q = query == null ? new Query() : query;
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", DEPENDENCY_COLUMNS))
                .append(" FROM dependencies WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendTimeFilter(sql, params, "last_call_time", q);
        appendLike(sql, params, "source", q.getSourceHost());
        appendLike(sql, params, "target", q.getTargetHost());
        appendLike(sql, params, "protocol", q.getProtocol());
        appendLike(sql, params, "software", q.getSoftware());
        List<DependencyEdge> result = new ArrayList<>();
        eachRow(sql.toString(), params, rs -> result.add(mapDependency(rs)));
        return result;
    }

    /**
     * 查询资源泄漏记录。
     *
     * @param query 查询条件
     * @return 泄漏记录（按创建时间倒序）
     */
    @Override
    public List<LeakRecord> queryLeaks(Query query) {
        Query q = query == null ? new Query() : query;
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", LEAK_COLUMNS))
                .append(" FROM leaks WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendTimeFilter(sql, params, "created_at", q);
        sql.append(" ORDER BY created_at DESC LIMIT ").append(limit(q))
                .append(" OFFSET ").append(Math.max(0, q.getOffset()));
        List<LeakRecord> result = new ArrayList<>();
        eachRow(sql.toString(), params, rs -> result.add(mapLeak(rs)));
        return result;
    }

    /**
     * 查询日志记录。
     *
     * @param query 查询条件
     * @return 日志记录（按时间倒序）
     */
    @Override
    public List<LogRecord> queryLogs(Query query) {
        Query q = query == null ? new Query() : query;
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", LOG_COLUMNS))
                .append(" FROM `logs` WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendTimeFilter(sql, params, "`timestamp`", q);
        appendLike(sql, params, "trace_id", q.getTraceId());
        sql.append(" ORDER BY `timestamp` DESC LIMIT ").append(limit(q))
                .append(" OFFSET ").append(Math.max(0, q.getOffset()));
        List<LogRecord> result = new ArrayList<>();
        eachRow(sql.toString(), params, rs -> result.add(mapLog(rs)));
        return result;
    }

    /**
     * 各表行数统计（1 秒缓存，避免每帧都打 COUNT(*)）。
     *
     * @return 表名 → 行数
     */
    @Override
    public Map<String, Long> stats() {
        long now = System.currentTimeMillis();
        if (now - statsAt < STATS_TTL_MS && !statsCache.isEmpty()) {
            return new LinkedHashMap<>(statsCache);
        }
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("transmissions", count("transmissions"));
        map.put("dependencies", count("dependencies"));
        map.put("leaks", count("leaks"));
        map.put("logs", count("`logs`"));
        statsCache = map;
        statsAt = now;
        return new LinkedHashMap<>(map);
    }

    /**
     * 按保留期清理过期数据。
     *
     * @param ttlMs 保留期（毫秒）
     * @return 删除行数
     */
    @Override
    public long cleanup(long ttlMs) {
        long cutoff = System.currentTimeMillis() - Math.max(0L, ttlMs);
        long removed = 0L;
        removed += execute("DELETE FROM transmissions WHERE start_time < ?", cutoff);
        removed += execute("DELETE FROM `logs` WHERE `timestamp` < ?", cutoff);
        removed += execute("DELETE FROM leaks WHERE closed_at > 0 AND closed_at < ?", cutoff);
        return removed;
    }

    /**
     * 等待队列落盘。
     *
     * @param timeoutMs 最长等待毫秒
     * @return 队列是否已清空
     */
    @Override
    public boolean flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (queue.isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return queue.isEmpty();
            }
        }
        return queue.isEmpty();
    }

    /**
     * 数据库当前是否可用。
     *
     * @return 可用返回 true
     */
    @Override
    public boolean available() {
        if (!running) {
            return false;
        }
        Connection c = null;
        try {
            c = borrow(1000L);
            return c != null && c.isValid(1);
        } catch (Throwable t) {
            return false;
        } finally {
            giveBack(c);
        }
    }

    /**
     * 运行时描述（运维面板展示）。
     *
     * @return 描述信息
     */
    @Override
    public Map<String, Object> describe() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", TYPE);
        d.put("url", maskUrl(url));
        d.put("available", available());
        d.put("poolSize", poolSize);
        d.put("async", async);
        d.put("pending", queue.size());
        d.put("flushed", flushed.get());
        d.put("dropped", dropped.get());
        d.put("evicted", evicted.get());
        d.put("retentionMs", retentionMs);
        d.put("capacity", capacity);
        d.put("maintenanceIntervalMs", maintenanceIntervalMs);
        d.put("maintenanceActive", maintenance != null);
        d.put("lastMaintenanceAt", lastMaintenanceAt);
        d.put("dbBytes", dbBytes());
        return d;
    }

    // ==================================================================
    // 写入链路
    // ==================================================================

    /**
     * 提交一条写操作：异步进队列，同步直接落库。
     *
     * @param op 写操作
     */
    private void submit(Op op) {
        if (!running) {
            return;
        }
        if (!async) {
            executeBatch(List.of(op));
            return;
        }
        if (!queue.offer(op)) {
            dropped.incrementAndGet();
        }
    }

    /**
     * 写线程主循环：批量取、批量提交。
     */
    private void writerLoop() {
        while (running || !queue.isEmpty()) {
            Op first;
            try {
                first = queue.poll(200L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (first == null) {
                continue;
            }
            List<Op> batch = new ArrayList<>(Math.min(batchSize, queue.size() + 1));
            batch.add(first);
            queue.drainTo(batch, batchSize - 1);
            executeBatch(batch);
        }
    }

    /**
     * 停机时把剩余队列写完。
     */
    private void drainQueue() {
        while (!queue.isEmpty()) {
            List<Op> batch = new ArrayList<>(Math.min(batchSize, queue.size()));
            queue.drainTo(batch, batchSize);
            if (!batch.isEmpty()) {
                executeBatch(batch);
            }
        }
    }

    /**
     * 一个事务内按类型批量写入。
     *
     * @param ops 待写操作
     */
    private void executeBatch(List<Op> ops) {
        if (ops.isEmpty()) {
            return;
        }
        Connection c = null;
        try {
            c = borrow(Math.max(1000L, flushTimeoutMs));
            if (c == null) {
                dropped.addAndGet(ops.size());
                return;
            }
            c.setAutoCommit(false);
            Map<Kind, List<Op>> grouped = new java.util.EnumMap<>(Kind.class);
            for (Op op : ops) {
                grouped.computeIfAbsent(op.kind, k -> new ArrayList<>()).add(op);
            }
            for (Map.Entry<Kind, List<Op>> e : grouped.entrySet()) {
                writeGroup(c, e.getKey(), e.getValue());
            }
            c.commit();
            flushed.addAndGet(ops.size());
        } catch (Throwable t) {
            dropped.addAndGet(ops.size());
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // 回滚失败只能丢弃本批，下一次连接会重建
                }
            }
            log.warn("[APM-JDBC] 批量写入失败（丢弃 {} 条）: {}", ops.size(), t.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // 连接即将归还，自动提交状态由下次借用时的校验兜底
                }
            }
            giveBack(c);
        }
    }

    /**
     * 按类型执行一批写入。
     *
     * @param c    连接
     * @param kind 类型
     * @param ops  操作
     * @throws SQLException 写失败
     */
    private void writeGroup(Connection c, Kind kind, List<Op> ops) throws SQLException {
        switch (kind) {
            case TRANSMISSION -> {
                try (PreparedStatement ps = c.prepareStatement(INSERT_TRANSMISSION)) {
                    for (Op op : ops) {
                        bindTransmission(ps, (TransmissionEvent) op.payload);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            case DEPENDENCY -> {
                try (PreparedStatement ps = c.prepareStatement(UPSERT_DEPENDENCY)) {
                    for (Op op : ops) {
                        bindDependency(ps, (DependencyEdge) op.payload);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            case LEAK -> {
                try (PreparedStatement ps = c.prepareStatement(UPSERT_LEAK)) {
                    for (Op op : ops) {
                        bindLeak(ps, (LeakRecord) op.payload);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            case LOG -> {
                try (PreparedStatement ps = c.prepareStatement(INSERT_LOG)) {
                    for (Op op : ops) {
                        bindLog(ps, (LogRecord) op.payload);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            default -> {
                // 未知类型直接忽略
            }
        }
    }

    /**
     * 绑定传输记录参数。
     *
     * @param ps    语句
     * @param event 传输事件
     * @throws SQLException 绑定失败
     */
    private void bindTransmission(PreparedStatement ps, TransmissionEvent event) throws SQLException {
        int i = 1;
        ps.setString(i++, cut(event.getTraceId(), 128));
        ps.setString(i++, cut(event.getSpanId(), 128));
        ps.setString(i++, cut(event.getParentSpanId(), 128));
        ps.setString(i++, cut(event.getSourceProtocol(), 32));
        ps.setString(i++, cut(event.getSourceSoftware(), 64));
        ps.setString(i++, cut(event.getSourceHost(), 255));
        ps.setInt(i++, event.getSourcePort());
        ps.setString(i++, cut(event.getSourcePath(), 1024));
        ps.setString(i++, cut(event.getTargetProtocol(), 32));
        ps.setString(i++, cut(event.getTargetSoftware(), 64));
        ps.setString(i++, cut(event.getTargetHost(), 255));
        ps.setInt(i++, event.getTargetPort());
        ps.setString(i++, cut(event.getTargetPath(), 1024));
        ps.setString(i++, cut(event.getProtocol(), 32));
        ps.setString(i++, cut(event.getSoftware(), 64));
        ps.setString(i++, cut(event.getOperation(), 512));
        ps.setString(i++, event.getStatus() == null ? null : event.getStatus().name());
        ps.setInt(i++, event.getStatusCode());
        ps.setLong(i++, event.getStartTime());
        ps.setLong(i++, event.getEndTime());
        ps.setLong(i++, event.getDuration());
        ps.setLong(i++, event.getBytesOut());
        ps.setLong(i++, event.getBytesIn());
        ps.setString(i++, cut(event.getErrorType(), 255));
        ps.setString(i++, cut(event.getErrorMessage(), 16000));
        ps.setString(i, attributesToJson(event.getAttributes()));
    }

    /**
     * 绑定依赖边参数。
     *
     * @param ps   语句
     * @param edge 依赖边
     * @throws SQLException 绑定失败
     */
    private void bindDependency(PreparedStatement ps, DependencyEdge edge) throws SQLException {
        int i = 1;
        ps.setString(i++, cut(edge.edgeId(), 255));
        ps.setString(i++, toJson(edge.getSource()));
        ps.setString(i++, toJson(edge.getTarget()));
        ps.setString(i++, edge.getProtocol() == null ? null : edge.getProtocol().name());
        ps.setString(i++, edge.getSoftware() == null ? null : edge.getSoftware().name());
        ps.setLong(i++, edge.getCallCount());
        ps.setLong(i++, edge.getTotalDuration());
        ps.setLong(i++, edge.getErrorCount());
        ps.setString(i++, cut(edge.getLastError(), 16000));
        ps.setLong(i, edge.getLastCallTime());
    }

    /**
     * 绑定泄漏记录参数。
     *
     * @param ps     语句
     * @param record 泄漏记录
     * @throws SQLException 绑定失败
     */
    private void bindLeak(PreparedStatement ps, LeakRecord record) throws SQLException {
        int i = 1;
        ps.setString(i++, cut(record.getHandleId(), 255));
        ps.setString(i++, cut(record.getKind(), 64));
        ps.setString(i++, cut(record.getName(), 512));
        ps.setString(i++, cut(record.getThread(), 255));
        ps.setLong(i++, record.getCreatedAt());
        ps.setLong(i++, record.getClosedAt());
        ps.setString(i, cut(record.getStackTrace(), 60000));
    }

    /**
     * 绑定日志记录参数。
     *
     * @param ps     语句
     * @param record 日志记录
     * @throws SQLException 绑定失败
     */
    private void bindLog(PreparedStatement ps, LogRecord record) throws SQLException {
        int i = 1;
        ps.setLong(i++, record.getTimestamp());
        ps.setString(i++, cut(record.getLevel(), 32));
        ps.setString(i++, cut(record.getLogger(), 255));
        ps.setString(i++, cut(record.getClassName(), 255));
        ps.setString(i++, cut(record.getMethodName(), 255));
        ps.setString(i++, cut(record.getMessage(), 60000));
        ps.setString(i, cut(record.getTraceId(), 128));
    }

    // ==================================================================
    // 查询链路
    // ==================================================================

    /**
     * 追加时间区间过滤。
     *
     * @param sql    语句
     * @param params 参数
     * @param column 列名
     * @param q      查询条件
     */
    private static void appendTimeFilter(StringBuilder sql, List<Object> params, String column, Query q) {
        if (q.getStartTime() != null) {
            sql.append(" AND ").append(column).append(" >= ?");
            params.add(q.getStartTime());
        }
        if (q.getEndTime() != null) {
            sql.append(" AND ").append(column).append(" <= ?");
            params.add(q.getEndTime());
        }
    }

    /**
     * 追加包含匹配过滤（与 sqlite 实现一致：{@code LIKE '%值%'}）。
     *
     * @param sql    语句
     * @param params 参数
     * @param column 列名
     * @param value  值；空则不追加
     */
    private static void appendLike(StringBuilder sql, List<Object> params, String column, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" LIKE ?");
        params.add("%" + value + "%");
    }

    /**
     * 取有效 LIMIT（0 视为「未限制」，兜底 1000，避免误查全表）。
     *
     * @param q 查询条件
     * @return 行数上限
     */
    private static int limit(Query q) {
        int limit = q.getLimit();
        return limit > 0 ? limit : 1000;
    }

    /**
     * 遍历结果集。
     *
     * @param sql    语句
     * @param params 参数
     * @param row    行处理
     */
    private void eachRow(String sql, List<Object> params, RowHandler row) {
        if (!running) {
            return;
        }
        Connection c = null;
        try {
            c = borrow(3000L);
            if (c == null) {
                return;
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        row.accept(rs);
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("[APM-JDBC] 查询失败: {}", t.getMessage());
        } finally {
            giveBack(c);
        }
    }

    /**
     * 映射传输记录。
     *
     * @param rs 结果集
     * @return 传输事件
     * @throws SQLException 读取失败
     */
    private TransmissionEvent mapTransmission(ResultSet rs) throws SQLException {
        TransmissionEvent e = new TransmissionEvent();
        e.setId(rs.getLong("id"));
        e.setTraceId(rs.getString("trace_id"));
        e.setSpanId(rs.getString("span_id"));
        e.setParentSpanId(rs.getString("parent_span_id"));
        e.setSourceProtocol(rs.getString("source_protocol"));
        e.setSourceSoftware(rs.getString("source_software"));
        e.setSourceHost(rs.getString("source_host"));
        e.setSourcePort(rs.getInt("source_port"));
        e.setSourcePath(rs.getString("source_path"));
        e.setTargetProtocol(rs.getString("target_protocol"));
        e.setTargetSoftware(rs.getString("target_software"));
        e.setTargetHost(rs.getString("target_host"));
        e.setTargetPort(rs.getInt("target_port"));
        e.setTargetPath(rs.getString("target_path"));
        e.setProtocol(rs.getString("protocol"));
        e.setSoftware(rs.getString("software"));
        e.setOperation(rs.getString("operation"));
        e.setStatus(parseStatus(rs.getString("status")));
        e.setStatusCode(rs.getInt("status_code"));
        e.setStartTime(rs.getLong("start_time"));
        e.setEndTime(rs.getLong("end_time"));
        e.setDuration(rs.getLong("duration"));
        e.setBytesOut(rs.getLong("bytes_out"));
        e.setBytesIn(rs.getLong("bytes_in"));
        e.setErrorType(rs.getString("error_type"));
        e.setErrorMessage(rs.getString("error_message"));
        e.setAttributes(attributesFromJson(rs.getString("attributes")));
        return e;
    }

    /**
     * 映射依赖边。
     *
     * @param rs 结果集
     * @return 依赖边
     * @throws SQLException 读取失败
     */
    private DependencyEdge mapDependency(ResultSet rs) throws SQLException {
        DependencyEdge d = new DependencyEdge();
        d.setSource(parseEndpoint(rs.getString("source")));
        d.setTarget(parseEndpoint(rs.getString("target")));
        d.setProtocol(parseEnum(com.chua.runtime.protocol.Protocol.class, rs.getString("protocol")));
        d.setSoftware(parseEnum(com.chua.runtime.protocol.Software.class, rs.getString("software")));
        d.setCallCount(rs.getLong("call_count"));
        d.setTotalDuration(rs.getLong("total_duration"));
        d.setErrorCount(rs.getLong("error_count"));
        d.setLastError(rs.getString("last_error"));
        d.setLastCallTime(rs.getLong("last_call_time"));
        return d;
    }

    /**
     * 映射泄漏记录。
     *
     * @param rs 结果集
     * @return 泄漏记录
     * @throws SQLException 读取失败
     */
    private LeakRecord mapLeak(ResultSet rs) throws SQLException {
        LeakRecord r = new LeakRecord();
        r.setId(rs.getLong("id"));
        r.setHandleId(rs.getString("handle_id"));
        r.setKind(rs.getString("kind"));
        r.setName(rs.getString("name"));
        r.setThread(rs.getString("thread"));
        r.setCreatedAt(rs.getLong("created_at"));
        r.setClosedAt(rs.getLong("closed_at"));
        r.setStackTrace(rs.getString("stack_trace"));
        return r;
    }

    /**
     * 映射日志记录。
     *
     * @param rs 结果集
     * @return 日志记录
     * @throws SQLException 读取失败
     */
    private LogRecord mapLog(ResultSet rs) throws SQLException {
        LogRecord r = new LogRecord();
        r.setId(rs.getLong("id"));
        r.setTimestamp(rs.getLong("timestamp"));
        r.setLevel(rs.getString("level"));
        r.setLogger(rs.getString("logger"));
        r.setClassName(rs.getString("class_name"));
        r.setMethodName(rs.getString("method_name"));
        r.setMessage(rs.getString("message"));
        r.setTraceId(rs.getString("trace_id"));
        return r;
    }

    /**
     * 解析端点 JSON；失败时退化为「主机名 = 原始字符串」。
     *
     * @param json JSON
     * @return 端点
     */
    private static Endpoint parseEndpoint(String json) {
        if (json == null || json.isEmpty()) {
            return new Endpoint();
        }
        try {
            return JSON.readValue(json, Endpoint.class);
        } catch (Exception e) {
            Endpoint ep = new Endpoint();
            ep.setHost(json);
            return ep;
        }
    }

    /**
     * 解析枚举值（大小写不敏感），非法值返回 null。
     *
     * @param type  枚举类型
     * @param value 文本
     * @param <E>   枚举
     * @return 枚举值或 null
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析状态码；未知值归为 {@code UNSET}。
     *
     * @param value 文本
     * @return 状态码
     */
    private static StatusCode parseStatus(String value) {
        StatusCode code = parseEnum(StatusCode.class, value);
        return code == null ? StatusCode.UNSET : code;
    }

    /**
     * 端点序列化。
     *
     * @param endpoint 端点
     * @return JSON；失败返回 null
     */
    private static String toJson(Object endpoint) {
        try {
            return JSON.writeValueAsString(endpoint);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 属性序列化。
     *
     * @param attributes 属性
     * @return JSON；空返回 null
     */
    private static String attributesToJson(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(attributes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 属性反序列化（兼容 sqlite 实现的 {@code #b64:} 前缀）。
     *
     * @param value 存储值
     * @return 属性；失败返回空 Map
     */
    private static Map<String, String> attributesFromJson(String value) {
        if (value == null || value.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            String json = value;
            if (json.startsWith("#b64:")) {
                json = new String(Base64.getDecoder().decode(json.substring(5)), StandardCharsets.UTF_8);
            }
            Map<String, String> map = JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    // ==================================================================
    // 维护与连接
    // ==================================================================

    /**
     * 自维护：保留期清理 + 容量淘汰。
     */
    private void maintain() {
        try {
            long removed = cleanup(retentionMs);
            long evictedRows = enforceCapacity();
            lastMaintenanceAt = System.currentTimeMillis();
            if (removed + evictedRows > 0) {
                log.info("[APM-JDBC] 自维护完成：过期删除 {} 行，容量淘汰 {} 行", removed, evictedRows);
            }
        } catch (Throwable t) {
            log.warn("[APM-JDBC] 自维护失败: {}", t.getMessage());
        }
    }

    /**
     * 按容量淘汰最旧数据（传输与日志各一份）。
     *
     * @return 淘汰行数
     */
    private long enforceCapacity() {
        long removed = 0L;
        Long minKeep = queryLong("SELECT id FROM transmissions ORDER BY id DESC LIMIT 1 OFFSET ?", capacity);
        if (minKeep != null) {
            removed += execute("DELETE FROM transmissions WHERE id < ?", minKeep);
        }
        Long minKeepLog = queryLong("SELECT id FROM `logs` ORDER BY id DESC LIMIT 1 OFFSET ?", capacity);
        if (minKeepLog != null) {
            removed += execute("DELETE FROM `logs` WHERE id < ?", minKeepLog);
        }
        if (removed > 0) {
            evicted.addAndGet(removed);
        }
        return removed;
    }

    /**
     * 执行一条计数查询。
     *
     * @param table 表名（含反引号时原样使用）
     * @return 行数；失败返回 0
     */
    private long count(String table) {
        Long value = queryLong("SELECT COUNT(*) FROM " + table);
        return value == null ? 0L : value;
    }

    /**
     * 执行单值查询。
     *
     * @param sql    语句
     * @param params 参数
     * @return 值；无行或失败返回 null
     */
    private Long queryLong(String sql, Object... params) {
        Connection c = null;
        try {
            c = borrow(3000L);
            if (c == null) {
                return null;
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : null;
                }
            }
        } catch (Throwable t) {
            log.warn("[APM-JDBC] 查询失败: {}", t.getMessage());
            return null;
        } finally {
            giveBack(c);
        }
    }

    /**
     * 执行一条写语句。
     *
     * @param sql    语句
     * @param params 参数
     * @return 影响行数；失败返回 0
     */
    private long execute(String sql, Object... params) {
        Connection c = null;
        try {
            c = borrow(3000L);
            if (c == null) {
                return 0L;
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                return ps.executeUpdate();
            }
        } catch (Throwable t) {
            log.warn("[APM-JDBC] 执行失败: {}", t.getMessage());
            return 0L;
        } finally {
            giveBack(c);
        }
    }

    /**
     * 数据库体积（information_schema，5 秒缓存）。
     *
     * @return 字节数；失败返回 0
     */
    private long dbBytes() {
        long now = System.currentTimeMillis();
        if (now - dbBytesAt < 5000L) {
            return dbBytesCache;
        }
        Long value = queryLong("SELECT COALESCE(SUM(data_length + index_length), 0) "
                + "FROM information_schema.tables WHERE table_schema = DATABASE()");
        dbBytesCache = value == null ? 0L : value;
        dbBytesAt = now;
        return dbBytesCache;
    }

    /**
     * 借出一个连接。
     *
     * @param timeoutMs 等待毫秒
     * @return 连接；超时返回 null
     */
    private Connection borrow(long timeoutMs) {
        Connection c;
        try {
            c = idle.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (c == null) {
            return null;
        }
        try {
            if (c.isClosed() || !c.isValid(1)) {
                closeQuietly(c);
                c = openConnection();
            }
            return c;
        } catch (Throwable t) {
            closeQuietly(c);
            log.warn("[APM-JDBC] 连接不可用，重建失败: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 归还连接。
     *
     * @param c 连接
     */
    private void giveBack(Connection c) {
        if (c == null) {
            return;
        }
        if (!idle.offer(c)) {
            closeQuietly(c);
        }
    }

    /**
     * 建立连接。
     *
     * @return 连接
     * @throws SQLException 连接失败
     */
    private Connection openConnection() throws SQLException {
        Properties props = new Properties();
        if (user != null && !user.isEmpty()) {
            props.setProperty("user", user);
        }
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");
        props.setProperty("rewriteBatchedStatements", "true");
        props.setProperty("useSSL", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("characterEncoding", "UTF-8");
        return DriverManager.getConnection(url, props);
    }

    /**
     * 按 URL 前缀加载驱动（驱动为可选依赖，缺失时给出明确提示）。
     *
     * @param jdbcUrl JDBC URL
     */
    private static void loadDriver(String jdbcUrl) {
        String driver;
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) {
            driver = "com.mysql.cj.jdbc.Driver";
        } else if (lower.startsWith("jdbc:mariadb:")) {
            driver = "org.mariadb.jdbc.Driver";
        } else if (lower.startsWith("jdbc:postgresql:")) {
            driver = "org.postgresql.Driver";
        } else {
            driver = null;
        }
        if (driver == null) {
            throw new IllegalArgumentException("不支持的 JDBC URL（需要 jdbc:mysql / jdbc:mariadb）: " + jdbcUrl);
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("缺少 JDBC 驱动 " + driver
                    + "，请把对应驱动加入运行时 classpath（该依赖在 starter 中是 optional）", e);
        }
    }

    /**
     * 关闭连接。
     *
     * @param c 连接
     */
    private static void closeQuietly(Connection c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (SQLException ignored) {
            // 关闭失败无需处理，连接最终由 GC 回收
        }
    }

    /**
     * 截断字符串到列宽，避免严格模式下整批插入失败。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断后的值
     */
    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 去掉首尾空白，空串归 null。
     *
     * @param value 原值
     * @return 结果
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 隐藏 URL 中的密码（日志/面板可见）。
     *
     * @param jdbcUrl 原始 URL
     * @return 脱敏后的 URL
     */
    private static String maskUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return "";
        }
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    /**
     * 写操作类型。
     */
    private enum Kind {
        /**
         * 传输记录。
         */
        TRANSMISSION,
        /**
         * 依赖边。
         */
        DEPENDENCY,
        /**
         * 资源泄漏。
         */
        LEAK,
        /**
         * 日志。
         */
        LOG
    }

    /**
     * 一条待写操作。
     */
    private static final class Op {

        /**
         * 类型。
         */
        private final Kind kind;

        /**
         * 载荷。
         */
        private final Object payload;

        /**
         * 构造。
         *
         * @param kind    类型
         * @param payload 载荷
         */
        private Op(Kind kind, Object payload) {
            this.kind = kind;
            this.payload = payload;
        }
    }

    /**
     * 结果集行处理。
     */
    @FunctionalInterface
    private interface RowHandler {

        /**
         * 处理一行。
         *
         * @param rs 结果集
         * @throws SQLException 读取失败
         */
        void accept(ResultSet rs) throws SQLException;
    }
}

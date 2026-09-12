package com.chua.starter.datasource.monitor;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.collection.CircularLinkedSet;
import com.chua.common.support.collection.OverflowPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL 监控门面
 *
 * <p>负责数据源包装、SQL 事件发布与统计聚合。
 * 设计要点：</p>
 * <ul>
 *   <li><b>静态单例 + Spring Bean</b>：{@link DataSourceContextSupport#addDatasource} 在
 *       Spring 容器初始化早期（BDRPP 阶段）即被调用，因此通过静态实例访问；
 *       自动配置创建 Spring Bean 后注册到静态引用</li>
 *   <li><b>幂等包装</b>：同一数据源只包装一次，并通过反射穿透 Seata
 *       {@code DataSourceProxy} 检测内部是否已包装，避免多层嵌套</li>
 *   <li><b>数据源无关</b>：通过 JDBC 代理拦截 execute 方法，不依赖 druid/p6spy/mybatis</li>
 *   <li><b>事务安全</b>：只包装 {@code getConnection()} 返回的连接，连接池内部事务不受影响</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DataSourceSqlMonitor {

    /**
     * 全局静态实例（由自动配置初始化）
     */
    private static volatile DataSourceSqlMonitor INSTANCE;

    /**
     * 是否启用（由配置解析，未显式设置时按环境自动切换）
     */
    private volatile boolean enabled = true;

    /**
     * 事件发布器
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 自定义监听器
     */
    private final List<SqlExecutionListener> listeners;

    /**
     * 监控配置
     */
    private final SqlMonitorProperties properties;

    /**
      * 数据源统计缓存：数据源名称 → 统计
     */
    private final Map<String, SqlStatistics> statisticsMap = new ConcurrentHashMap<>();

    /**
     * 已跟踪数据源名称（有界去重，按策略淘汰）
     */
    private final CircularLinkedSet<String> trackedDataSources;

    /**
     * 已包装数据源缓存，保证幂等
     */
    private final Map<DataSource, DataSource> wrappedCache = new ConcurrentHashMap<>();

    /**
      * 已包装数据源 钥匙 集合（有界去重，按策略淘汰）
     */
    private final CircularLinkedSet<DataSource> trackedWrappedKeys;

    /**
     * 监控构造器
     *
     * @param eventPublisher 事件发布器
     * @param listeners      自定义监听器
     * @param properties     监控配置
     */
    public DataSourceSqlMonitor(ApplicationEventPublisher eventPublisher,
                                List<SqlExecutionListener> listeners,
                                SqlMonitorProperties properties) {
        this.eventPublisher = eventPublisher;
        this.listeners = listeners;
        this.properties = properties;
        this.trackedDataSources = CircularLinkedSet.of(properties.getMaxTrackedDataSources(), properties.getOverflowPolicy());
        this.trackedWrappedKeys = CircularLinkedSet.of(properties.getMaxTrackedDataSources(), OverflowPolicy.REJECT);
    }

    /**
     * 初始化静态实例
     *
     * @param monitor 监控实例
     */
    public static void initialize(DataSourceSqlMonitor monitor) {
        INSTANCE = monitor;
    }

    /**
     * 获取静态实例
     *
     * @return 监控实例，未初始化时返回 空
     */
    public static DataSourceSqlMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * 注册数据源入口的包装方法（供 {@link DataSourceContextSupport#addDatasource} 调用）
     *
     * @param dataSourceName 数据源名称
     * @param dataSource     数据源
     * @return 包装后的数据源（监控未启用或数据源为 空 时返回原对象）
     */
    public static DataSource wrapIfEnabled(String dataSourceName, DataSource dataSource) {
        DataSourceSqlMonitor instance = INSTANCE;
        if (instance == null || !instance.isEnabled() || dataSource == null) {
            return dataSource;
        }
        return instance.wrap(dataSource, dataSourceName);
    }

    /**
     * 设置启用状态
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 补包装所有已注册的数据源
     * <p>解决 {@link DataSourceInspect}（BDRPP）在容器早期注册数据源时监控尚未初始化的时序问题。
     * 幂等：已包装的数据源保持不变，分组注册表同步替换为包装后的数据源。</p>
     */
    public static void wrapExistingDataSources() {
        DataSourceSqlMonitor instance = INSTANCE;
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        for (Map.Entry<String, DataSource> entry : com.chua.starter.datasource.support.DataSourceContextSupport.getAllDataSources().entrySet()) {
            String name = entry.getKey();
            DataSource dataSource = entry.getValue();
            if (dataSource instanceof MonitoringDataSource) {
                continue;
            }
            DataSource wrapped = instance.wrap(dataSource, name);
            if (wrapped != dataSource) {
 // 重新注册以替换 Target_数据_源 与分组注册表中的引用
                com.chua.starter.datasource.support.DataSourceContextSupport.addDatasource(name, wrapped);
            }
        }
    }

    /**
     * 包装数据源
     * <p>幂等：若数据源已包装（或内部已含监控包装）则原样返回，防止 Seata 替换等场景多层嵌套。</p>
     *
     * @param dataSource     数据源
     * @param dataSourceName 数据源名称
     * @return 包装后的数据源
     */
    public DataSource wrap(DataSource dataSource, String dataSourceName) {
        if (dataSource instanceof MonitoringDataSource) {
            return dataSource;
        }
 // 穿透 Seata 数据源代理 等包装器检测，避免嵌套
        if (isMonitored(dataSource)) {
            return dataSource;
        }
        DataSource cached = wrappedCache.get(dataSource);
        if (cached != null) {
            return cached;
        }
        MonitoringDataSource wrapped = new MonitoringDataSource(dataSource, dataSourceName, this);
        // 有界集合控制缓存规模，防止数据源实例动态增长导致内存膨胀
        synchronized (trackedWrappedKeys) {
            // 同步块内二次查询，防止并发下同一数据源生成多个包装实例
            cached = wrappedCache.get(dataSource);
            if (cached != null) {
                return cached;
            }
            if (trackedWrappedKeys.add(dataSource)) {
                wrappedCache.put(dataSource, wrapped);
            }
 // 容量满被淘汰的 钥匙 需同步清理缓存，避免 wrapped缓存 无限增长
            DataSource evicted = trackedWrappedKeys.lastEvicted();
            if (evicted != null) {
                wrappedCache.remove(evicted);
            }
            return wrapped;
        }
    }

    /**
     * 记录一次 SQL 执行
     * <p>内部统一聚合统计，并按配置发布事件或日志。</p>
     *
     * @param dataSourceName 数据源名称
     * @param sql            SQL 文本
     * @param costMs         执行耗时（毫秒）
     * @param success        是否成功
     * @param errorMessage   失败原因（失败时）
     */
    public void recordExecution(String dataSourceName, String sql, long costMs, boolean success, String errorMessage) {
        boolean slowSql = costMs >= properties.getSlowSqlThresholdMs();
        String sqlType = resolveSqlType(sql);

        // 聚合统计（有界集合按策略控制数据源数量，防止内存无限膨胀）
        SqlStatistics statistics = statisticsMap.get(dataSourceName);
        if (statistics == null) {
            synchronized (trackedDataSources) {
                statistics = statisticsMap.get(dataSourceName);
                if (statistics == null) {
                    if (trackedDataSources.add(dataSourceName)) {
                        statistics = statisticsMap.computeIfAbsent(dataSourceName, SqlStatistics::new);
 // 容量满被淘汰的数据源名需同步清理统计，避免 statistics映射 无限增长
                        String evicted = trackedDataSources.lastEvicted();
                        if (evicted != null) {
                            statisticsMap.remove(evicted);
                        }
                    } else if (log.isDebugEnabled()) {
                        log.debug("[SqlMonitor] 数据源统计已达上限({}), 跳过数据源 '{}' 的统计聚合",
                                properties.getMaxTrackedDataSources(), dataSourceName);
                    }
                }
            }
        }
        if (statistics != null) {
            statistics.record(costMs, success, slowSql);
        }

        // 慢 SQL 日志
        if (slowSql) {
            log.warn("[SqlMonitor] 慢SQL: 数据源={}, 耗时={}ms, 类型={}, SQL={}",
                    dataSourceName, costMs, sqlType, truncate(sql));
        }

        if (!properties.isRecordSql()) {
            sql = null;
        }

        // 自定义监听器
        if (listeners != null && !listeners.isEmpty()) {
            SqlExecutionEvent event = new SqlExecutionEvent(this, dataSourceName, sql, sqlType,
                    costMs, slowSql, success, errorMessage);
            for (SqlExecutionListener listener : listeners) {
                try {
                    listener.onSqlExecuted(event);
                } catch (Exception e) {
                    log.warn("[SqlMonitor] 监听器回调异常: {}", listener.getClass().getName(), e);
                }
            }
        }

        // Spring 事件
        if (properties.isPublishEvent() && eventPublisher != null) {
            SqlExecutionEvent event = new SqlExecutionEvent(this, dataSourceName, sql, sqlType,
                    costMs, slowSql, success, errorMessage);
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 获取某数据源的统计
     *
     * @param dataSourceName 数据源名称
     * @return 统计信息，不存在时返回 空
     */
    public SqlStatistics getStatistics(String dataSourceName) {
        return statisticsMap.get(dataSourceName);
    }

    /**
     * 获取全部统计
     *
     * @return 全部统计信息
     */
    public Map<String, SqlStatistics> getAllStatistics() {
        return statisticsMap;
    }

    /**
     * 重置统计
     */
    public void resetStatistics() {
        statisticsMap.values().forEach(SqlStatistics::reset);
    }

    /**
     * 解析 SQL 类型
     *
     * @param sql SQL 文本
     * @return select/insert/update/delete/other
     */
    private String resolveSqlType(String sql) {
        if (sql == null) {
            return "other";
        }
        String normalized = sql.trim().toLowerCase();
        if (normalized.startsWith("select") || normalized.startsWith("with")) {
            return "select";
        }
        if (normalized.startsWith("insert")) {
            return "insert";
        }
        if (normalized.startsWith("update")) {
            return "update";
        }
        if (normalized.startsWith("delete")) {
            return "delete";
        }
        return "other";
    }

    /**
     * 截断超长 SQL
     *
     * @param sql SQL 文本
     * @return 截断后的 SQL
     */
    private String truncate(String sql) {
        if (sql == null) {
            return null;
        }
        if (sql.length() <= properties.getMaxSqlLength()) {
            return sql;
        }
        return sql.substring(0, properties.getMaxSqlLength()) + "...";
    }

    /**
     * 检测数据源内部是否已被监控包装（供幂等判断）
     * <p>递归穿透 {@code getTargetDataSource()}（Seata DataSourceProxy 等）检查包装链。</p>
     *
     * @param dataSource 数据源
     * @return 是否已被监控包装
     */
    public static boolean isMonitored(DataSource dataSource) {
        if (dataSource instanceof MonitoringDataSource) {
            return true;
        }
        try {
            Object target = ReflectUtils.invoke(dataSource, "getTargetDataSource", Object.class);
            if (target instanceof DataSource targetDs) {
                return isMonitored(targetDs);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}

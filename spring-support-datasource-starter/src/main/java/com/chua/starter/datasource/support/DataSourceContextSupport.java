package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.datasource.support.DynamicDataSource.TARGET_DATA_SOURCES;

/**
 * 数据源管理器
 *
 * @author CH
 */
@Slf4j
public class DataSourceContextSupport {

    public static final Map<Object, Object> DATA_SOURCE_MAP = new ConcurrentHashMap<>();

    /**
     * 默认数据源
     */
    public static final String DEFAULT_DATASOURCE = "master";
    /**
     * 本地安全处理数据源
     */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /** 数据源上下文栈（用于 @DSTransactional 嵌套切换） */
    /** Ds_stack */
    private static final ThreadLocal<Deque<String>> DS_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 设置数据源名
     *
     * @param dbType 数据类型
     */
    public static void setDbType(String dbType) {
        CONTEXT_HOLDER.set(dbType);
        // 当 @DSTransactional 激活时，自动追踪已使用的数据源
        MultiDSTransactionContext ctx = MultiDSTransactionContext.getCurrent();
        if (ctx != null) {
            ctx.pushDataSource(dbType);
        }
    }

    /**
     * 推入数据源上下文栈（用于嵌套切换时的上下文恢复）
     *
     * @param dsName 数据源名称
     */
    public static void pushDbType(String dsName) {
        DS_STACK.get().push(dsName);
    }

    /**
     * 弹出数据源上下文栈，恢复上一级上下文
     *
     * @return 上一级数据源名称（栈空时返回 null）
     */
    public static String popDbType() {
        Deque<String> stack = DS_STACK.get();
        stack.poll(); // 移除当前
        String previous = stack.peek(); // 获取上一级
        if (previous == null) {
            DS_STACK.remove();
        }
        return previous;
    }

    /**
     * 添加数据源
     * <p>若 SQL 监听已启用，注册前会统一包装数据源（幂等），实现纯 JDBC 层 SQL 监控。</p>
     *
     * @param name       名称
     * @param dataSource 添加数据源
     */
    public static void addDatasource(String name, DataSource dataSource) {
        DataSource target = com.chua.starter.datasource.monitor.DataSourceSqlMonitor.wrapIfEnabled(name, dataSource);
        TARGET_DATA_SOURCES.put(name, target);
        // 自动检测并注册数据源分组（如 slave_1 → slave 组）
        DataSourceGroupRegistry.register(name, target);
    }

    /**
     * 是否存在数据源
     *
     * @param dbType 数据源名称
     * @return 是否存在数据源
     */
    public static boolean hasDbType(String dbType) {
        return TARGET_DATA_SOURCES.containsKey(dbType);
    }

    /**
     * 获取数据源
     *
     * @param name 名称
     * @return dataSource 数据源
     */
    public static DataSource getDatasource(String name) {
        return (DataSource) TARGET_DATA_SOURCES.get(name);
    }

    /**
     * 获取所有数据源
     *
     * @return 所有数据源Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, DataSource> getAllDataSources() {
        Map<String, DataSource> result = new ConcurrentHashMap<>();
        for (Map.Entry<Object, Object> entry : TARGET_DATA_SOURCES.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof DataSource) {
                result.put((String) entry.getKey(), (DataSource) entry.getValue());
            }
        }
        return result;
    }

    /**
     * 获取数据源名
     *
     * @return 获取数据源名
     */
    public static String getDbType() {
        return (CONTEXT_HOLDER.get());
    }

    /**
     * 清除数据源名
     */
    public static void clearDbType() {
        CONTEXT_HOLDER.remove();
    }
}

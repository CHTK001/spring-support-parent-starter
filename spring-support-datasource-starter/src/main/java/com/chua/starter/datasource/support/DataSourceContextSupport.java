package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
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

    /**
     * 设置数据源名
     *
     * @param dbType 数据类型
     */
    public static void setDbType(String dbType) {
        CONTEXT_HOLDER.set(dbType);
    }

    /**
     * 添加数据源
     *
     * @param name       名称
     * @param dataSource 添加数据源
     */
    public static void addDatasource(String name, DataSource dataSource) {
        TARGET_DATA_SOURCES.put(name, dataSource);
    }

    /**
     * 是否存在數據源
     *
     * @param dbType 数据源名称
     * @return 是否存在數據源
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

package com.chua.starter.datasource.support;

import com.chua.common.support.lang.document.DocumentData;
import com.chua.common.support.lang.document.DocumentParser;
import com.chua.common.support.lang.document.TableData;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.datasource.properties.DocumentProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据源文档服务
 * 提供两种取数据方式：
 * <ul>
 *     <li>{@link #apply()} —— 强制重新读取数据库元数据并刷新缓存</li>
 *     <li>{@link #preview()} —— 优先返回缓存，缓存为空才重新读取</li>
 * </ul>
 * 通过复用 {@link DocumentParser} SPI（{@code DatabaseDocumentParser}），
 * 无三方模板引擎依赖。
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class DataSourceDocumentService {

    /**
     * 数据源
     */
    private final DataSource dataSource;

    /**
     * 文档配置
     */
    private final DocumentProperties properties;

    /**
     * 缓存：上一次解析结果
     */
    private volatile DocumentData cached;

    /**
     * 缓存生成时间戳
     */
    private volatile long cachedAt;

    /**
     * 防止并发重复解析的写锁
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建 数据源文档服务 实例
     * @param dataSource 数据源
     * @param properties 文档属性
     */
    public DataSourceDocumentService(DataSource dataSource, DocumentProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * 强制重新读取数据库结构（每次拿最新）
     * <p>用于 {@code /apply} 接口</p>
     *
     * @return 文档数据
     */
    public DocumentData apply() {
        lock.lock();
        try {
            DocumentData data = doParse();
            this.cached = data;
            this.cachedAt = System.currentTimeMillis();
            log.info("数据库文档已刷新: {} 张表, {} 个 schema",
                    data.getTables() == null ? 0 : data.getTables().size(),
                    data.getDatabaseName());
            return data;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 优先返回缓存；缓存不存在则生成
     * <p>用于 {@code /preview} 接口</p>
     *
     * @return 文档数据
     */
    public DocumentData preview() {
        if (properties.isCacheable() && cached != null) {
            log.debug("命中数据库文档缓存: cachedAt={}", cachedAt);
            return cached;
        }
        return apply();
    }

    /**
     * 上一次刷新时间戳
     * @return 获取缓存at的结果
     */
    public long getCachedAt() {
        return cachedAt;
    }

    /**
     * 当前是否已有缓存
     * @return 是否包含缓存的结果
     */
    public boolean hasCache() {
        return cached != null;
    }

    // ====================== 私有 ======================

    /**
     * 执行解析
     *
     * @return 执行解析的结果
     */
    private DocumentData doParse() {
        DocumentParser parser;
        try {
            parser = DocumentParser.create("database");
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "未找到 DocumentParser(database) 实现，请确认 classpath 中已引入 utils-support-datasource-starter", t);
        }
        if (parser == null) {
            throw new IllegalStateException(
                    "未找到 DocumentParser(database) 实现，请确认 classpath 中已引入 utils-support-datasource-starter");
        }

        ConnectionInfo conn = readConnectionInfo();

 // 复制透传 期权，并补上 模式/版本/title
        java.util.Map<String, Object> opts = new java.util.LinkedHashMap<>();
        if (properties.getOptions() != null) {
            opts.putAll(properties.getOptions());
        }
        if (properties.getIncludeSchemas() != null && !properties.getIncludeSchemas().isBlank()) {
            opts.put("schemas", properties.getIncludeSchemas());
        }
        if (properties.getVersion() != null && !properties.getVersion().isBlank()) {
            opts.put("version", properties.getVersion());
        }

        com.chua.common.support.lang.document.DocumentConfig config =
                com.chua.common.support.lang.document.DocumentConfig.builder()
                        .type("database")
                        .url(conn.url)
                        .username(conn.username)
                        .password(conn.password)
 // 不传 driver类，让 driver管理器 自动发现（获取driver名称 返回的是人话名带空格）
                        .options(opts)
                        .build();

        DocumentData data = parser.parse(config);

        // 覆盖标题（如有自定义）
        if (properties.getTitle() != null && !properties.getTitle().isBlank()) {
            data.setTitle(properties.getTitle());
        }
        if (properties.getVersion() != null && !properties.getVersion().isBlank()) {
            data.setVersion(properties.getVersion());
        }

        // 表过滤
        data.setTables(filterTables(data.getTables()));
        return data;
    }

    /**
     * 按白/黑名单过滤表
     * @param source 源
     * @return 过滤器tables的结果
     */
    private List<TableData> filterTables(List<TableData> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<TablePatternMatcher.Matcher> includes = TablePatternMatcher.build(properties.getIncludeTables());
        List<TablePatternMatcher.Matcher> excludes = TablePatternMatcher.build(properties.getExcludeTables());

        List<TableData> result = new ArrayList<>(source.size());
        for (TableData t : source) {
            String name = t.getTableName();
            if (name == null) {
                continue;
            }
            if (!includes.isEmpty() && !TablePatternMatcher.anyMatch(includes, name)) {
                continue;
            }
            if (!excludes.isEmpty() && TablePatternMatcher.anyMatch(excludes, name)) {
                continue;
            }
            result.add(t);
        }
        return result;
    }

    /**
     * 从 数据源 读取连接信息（url/用户名/密码）
     * <p>注：JDBC 不暴露密码，通过反射读取常见连接池字段；拿不到也不致命，
     * 因为用户可能使用 XA 包装、分库分表 路由等，url 本身已足够解析元数据。</p>
     * @return 读取connection信息的结果
     */
    private ConnectionInfo readConnectionInfo() {
        ConnectionInfo info = new ConnectionInfo();
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection conn = dataSource.getConnection()) {
            info.url = conn.getMetaData().getURL();
            info.username = conn.getMetaData().getUserName();
            info.password = readPassword();
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param 连接信息" 连接信息"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("无法读取 DataSource 连接信息", e);
        }
        return info;
    }

    /**
     * 通过反射常见字段读取数据库密码
     * @return 读取密码的结果
     */
    private String readPassword() {
        if (!(dataSource instanceof javax.sql.DataSource ds)) {
            return null;
        }
        String[] poolTypes = {
                "com.zaxxer.hikari.HikariDataSource",
                "com.alibaba.druid.pool.DruidDataSource",
                "org.apache.tomcat.jdbc.pool.DataSource",
                "org.apache.commons.dbcp2.BasicDataSource",
                "com.mchange.v2.c3p0.ComboPooledDataSource"
        };
        for (String type : poolTypes) {
            try {
                Class<?> poolClass = ReflectUtils.forName(type);
                if (ds.isWrapperFor(poolClass)) {
                    Object raw = ds.unwrap(poolClass);
                    try {
                        java.lang.reflect.Field f = findField(raw.getClass(), "password", "pass");
                        if (f != null) {
                            f.setAccessible(true);
                            Object val = f.get(raw);
                            return val == null ? null : val.toString();
                        }
                    } catch (Throwable e) {
                        log.debug("[datasource] 操作失败，已忽略: {}", e.getMessage());
                    }
                }
            } catch (Throwable e) {
                log.debug("[datasource] 操作失败，已忽略: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 查找字段
     *
     * @param clazz clazz
     * @param names 名称
     * @return find字段的结果
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String... names) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                for (String n : names) {
                    if (f.getName().equalsIgnoreCase(n)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 连接信息封装
     */
    private static final class ConnectionInfo {
        String url;
        String username;
        String password;
    }
}

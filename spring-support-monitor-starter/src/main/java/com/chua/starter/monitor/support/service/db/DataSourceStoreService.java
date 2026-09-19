package com.chua.starter.monitor.support.service.db;

import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据源配置存储服务。
 *
 * <p>接管原 {@code MonitorController} 中的静态 {@code ConcurrentHashMap} 内存存储,
 * 提供数据源配置的增删改查与 JDBC 连接构建/测试能力。
 * 内存存储为简化实现,应用重启即丢失;后续可替换为数据库持久化。</p>
 *
 * <p>连接构建支持按 {@link DataSourceConfig#type()} 分派:
 * {@code jdbc} 类型优先使用完整 {@code url},否则按 {@code host/port/database} 拼接 MySQL URL;
 * 其余类型(redis/influxdb/neo4j/nacos/zookeeper)暂不支持 JDBC 直连,抛 {@link UnsupportedOperationException}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class DataSourceStoreService {

    /**
     * 支持的数据源类型列表。
    */
    private static final List<String> DS_TYPES = List.of("jdbc", "redis", "influxdb", "neo4j", "nacos", "zookeeper");

    /**
     * JDBC 数据源默认端口(MySQL)。
    */
    private static final int JDBC_DEFAULT_PORT = 3306;

    /**
     * 数据源配置内存存储(key 为数据源 id)。
    */
    private final ConcurrentHashMap<Long, DataSourceConfig> dataSourceStore = new ConcurrentHashMap<>();

    /**
     * 数据源 id 生成器,从 1000 起递增。
    */
    private final AtomicLong idGenerator = new AtomicLong(1000);

    /**
     * 默认主数据源提供者(应用自身连接)。
    */
    private final ObjectProvider<DataSource> defaultDataSourceProvider;

    /**
     * 构造数据源配置存储服务。
     *
     * @param defaultDataSourceProvider 默认主数据源提供者,可为空(未配置主数据源时)
     */
    public DataSourceStoreService(ObjectProvider<DataSource> defaultDataSourceProvider) {
        this.defaultDataSourceProvider = defaultDataSourceProvider;
    }

    /**
     * 获取所有支持的数据源类型。
     *
     * @return 数据源类型列表(不可变)
     */
    public List<String> supportedTypes() {
        return DS_TYPES;
    }

    /**
     * 列出全部数据源配置。
     *
     * @return 数据源配置列表(副本),无数据时为空列表,不为 null
     */
    public List<DataSourceConfig> listAll() {
        return List.copyOf(dataSourceStore.values());
    }

    /**
     * 根据 id 获取数据源配置。
     *
     * @param id 数据源 id,不能为 null
     * @return 配置存在时返回结果,否则返回错误码"数据源不存在"
     */
    public ReturnResult<DataSourceConfig> get(Long id) {
        if (id == null) {
            return ReturnResult.error("数据源 id 不能为空");
        }
        DataSourceConfig config = dataSourceStore.get(id);
        if (config == null) {
            return ReturnResult.error("数据源不存在");
        }
        return ReturnResult.ok(config);
    }

    /**
     * 创建数据源配置。
     *
     * <p>{@code id} 由本服务分配并回写;{@code createTime} 由本服务写入当前时间。</p>
     *
     * @param config 数据源配置,{@code type} 不能为空且须在支持列表内
     * @return 创建成功的配置(含分配的 id),参数非法时返回错误
     */
    public ReturnResult<DataSourceConfig> create(DataSourceConfig config) {
        if (config == null) {
            return ReturnResult.error("数据源配置不能为空");
        }
        if (config.type() == null || !DS_TYPES.contains(config.type())) {
            return ReturnResult.error("不支持的数据源类型: " + config.type());
        }
        Long id = idGenerator.incrementAndGet();
        DataSourceConfig persisted = new DataSourceConfig(
                id,
                config.name(),
                config.type(),
                config.host(),
                config.port(),
                config.database(),
                config.username(),
                config.password(),
                config.url(),
                LocalDateTime.now().toString(),
                null);
        dataSourceStore.put(id, persisted);
        return ReturnResult.ok(persisted);
    }

    /**
     * 更新数据源配置。
     *
     * @param id 数据源 id,不能为 null
     * @param config 数据源配置,不能为 null
     * @return 更新成功的配置(含原 id 与 updateTime),id 不存在时返回错误
     */
    public ReturnResult<DataSourceConfig> update(Long id, DataSourceConfig config) {
        if (id == null) {
            return ReturnResult.error("数据源 id 不能为空");
        }
        if (config == null) {
            return ReturnResult.error("数据源配置不能为空");
        }
        if (!dataSourceStore.containsKey(id)) {
            return ReturnResult.error("数据源不存在");
        }
        DataSourceConfig updated = new DataSourceConfig(
                id,
                config.name(),
                config.type(),
                config.host(),
                config.port(),
                config.database(),
                config.username(),
                config.password(),
                config.url(),
                null,
                LocalDateTime.now().toString());
        dataSourceStore.put(id, updated);
        return ReturnResult.ok(updated);
    }

    /**
     * 删除数据源配置。
     *
     * @param id 数据源 id,不能为 null
     * @return 成功(恒为 true,重复删除不报错)
     */
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        return dataSourceStore.remove(id) != null;
    }

    /**
     * 测试数据源连接(jdbc 类型真实建立连接并校验可达,非 jdbc 类型暂不支持)。
     *
     * @param id 数据源 id,不能为 null
     * @return 测试结果:{@code success} 表示是否连通,{@code message} 为说明,
     *         {@code type} 为数据源类型;id 不存在或类型不受支持时 success=false
     */
    public Map<String, Object> test(Long id) {
        ReturnResult<DataSourceConfig> result = get(id);
        if (result.getData() == null) {
            return Map.of(
                    "success", false,
                    "message", "数据源不存在",
                    "type", "");
        }
        DataSourceConfig config = result.getData();
        if (!"jdbc".equals(config.type())) {
            return Map.of(
                    "success", false,
                    "message", "暂不支持 " + config.type() + " 类型的连接测试",
                    "type", config.type());
        }
        try (Connection conn = buildConnection(config.id())) {
            boolean valid = conn.isValid(3);
            return Map.of(
                    "success", valid,
                    "message", valid ? "连接测试成功" : "连接不可用",
                    "type", config.type());
        } catch (SQLException e) {
            return Map.of(
                    "success", false,
                    "message", "连接测试失败: " + e.getMessage(),
                    "type", config.type());
        }
    }

    /**
     * 构建 JDBC 连接。
     *
     * <p>连接优先级:数据源 id 为 null/0 时使用默认主数据源;
     * 否则按 {@link DataSourceConfig#url()} 优先、host/port/database 拼接次之。</p>
     *
     * @param datasourceId 数据源 id,null 或 0 表示使用默认主数据源
     * @return JDBC 连接,调用方负责关闭
     * @throws SQLException 连接构建或获取失败
     * @throws UnsupportedOperationException 非 jdbc 类型或不支持的主数据源
     */
    public Connection buildConnection(Long datasourceId) throws SQLException {
        if (datasourceId == null || datasourceId == 0) {
            DataSource defaultDs = defaultDataSourceProvider.getIfAvailable();
            if (defaultDs == null) {
                throw new UnsupportedOperationException("未配置默认数据源");
            }
            return defaultDs.getConnection();
        }
        DataSourceConfig config = dataSourceStore.get(datasourceId);
        if (config == null) {
            throw new SQLException("数据源不存在: " + datasourceId);
        }
        if (!"jdbc".equals(config.type())) {
            throw new UnsupportedOperationException("非 JDBC 数据源类型: " + config.type());
        }
        String url = config.url();
        if (url == null || url.isBlank()) {
            String host = requireText(config.host(), "host");
            int port = config.port() != null ? config.port() : JDBC_DEFAULT_PORT;
            String database = requireText(config.database(), "database");
            url = String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                    host, port, database);
        }
        return DriverManager.getConnection(
                url,
                config.username() != null ? config.username() : "",
                config.password() != null ? config.password() : "");
    }

    /**
     * 获取必填文本字段的非空值。
     *
     * @param value 待校验文本
     * @param fieldName 字段名(用于异常信息)
     * @return 非空文本
     * @throws SQLException 字段为空时
     */
    private String requireText(String value, String fieldName) throws SQLException {
        if (value == null || value.isBlank()) {
            throw new SQLException("数据源配置缺少必填项: " + fieldName);
        }
        return value;
    }
}

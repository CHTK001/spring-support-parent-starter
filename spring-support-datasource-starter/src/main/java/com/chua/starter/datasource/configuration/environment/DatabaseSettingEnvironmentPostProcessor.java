// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasource.configuration.environment;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 启动期数据库配置管理。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>基于现有 datasource 参数直连数据库</li>
 *     <li>缺少 {@code sys_setting}/{@code sys_setting_group} 时自动建表</li>
 *     <li>为通用启动配置补齐默认初始化数据</li>
 *     <li>将 {@code sys_setting_app_inner=1} 的配置注入 Environment</li>
 * </ul>
 *
 * <p>这里只负责数据库配置中心能力，不替代 {@link com.chua.starter.common.support.configuration.environment.ProfileEnvironmentPostProcessor}。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class DatabaseSettingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /**
     * 已启用_Key
    */
    private static final String ENABLED_KEY = "plugin.ops-config.database.enabled";
    /**
     * Setting_table
    */
    private static final String SETTING_TABLE = "sys_setting";
    /**
     * 群体_table
    */
    private static final String GROUP_TABLE = "sys_setting_group";
    /**
     * 财产_源_名称
    */
    private static final String PROPERTY_SOURCE_NAME = "database-startup-config";
    /**
     * Ops_源_前缀
    */
    private static final String OPS_SOURCE_PREFIX = "ops-config-";

    /**
     * 旧库缺失列对应的固定 DDL 注册表。
     *
     * <p>表名与列名均来自本类固定注册项，不接收外部标识符；值定义不属于 DDL 参数绑定范围。</p>
     */
    private static final Map<String, String> SETTING_MISSING_COLUMNS = Map.of(
            "sys_setting_code", "ALTER TABLE sys_setting ADD COLUMN sys_setting_code VARCHAR(255)",
            "sys_setting_default_value", "ALTER TABLE sys_setting ADD COLUMN sys_setting_default_value VARCHAR(255)",
            "sys_setting_is_sensitive", "ALTER TABLE sys_setting ADD COLUMN sys_setting_is_sensitive INT DEFAULT 0",
            "sys_setting_validate", "ALTER TABLE sys_setting ADD COLUMN sys_setting_validate VARCHAR(512)");

    /**
     * 默认_群体
    */
    private static final List<GroupDefinition> DEFAULT_GROUPS = List.of(
            new GroupDefinition("startup-api", "API基础配置", "ri:code-box-line", "启动期 API 通用配置", 10),
            new GroupDefinition("startup-actuator", "Actuator配置", "ri:pulse-line", "启动期 Actuator 通用配置", 20),
            new GroupDefinition("startup-ip", "IP配置", "ri:map-pin-line", "启动期 IP 通用配置", 30),
            new GroupDefinition("startup-jackson", "Jackson配置", "ri:file-code-line", "启动期 Jackson 通用配置", 40),
            new GroupDefinition("startup-mybatis", "MyBatis配置", "ri:database-2-line", "启动期 MyBatis 通用配置", 50),
            new GroupDefinition("startup-swagger", "Swagger配置", "ri:book-open-line", "启动期 Swagger 通用配置", 60)
    );

    /**
     * 默认_settings
    */
    private static final List<SettingDefinition> DEFAULT_SETTINGS = List.of(
            new SettingDefinition("plugin.api.encode.enable", "true", "BOOL", "startup-api", 10, "ops-default/application-api.yml", "开启接口请求体编码"),
            new SettingDefinition("plugin.api.decode.enable", "true", "BOOL", "startup-api", 20, "ops-default/application-api.yml", "开启接口响应体解码"),
            new SettingDefinition("plugin.api.spi.mapping.balance", "com.chua.common.support.lang.robin.Robin", "TEXT", "startup-api", 30, "ops-default/application-api.yml", "balance SPI 默认实现"),
            new SettingDefinition("plugin.api.spi.mapping.robin", "com.chua.common.support.lang.robin.Robin", "TEXT", "startup-api", 40, "ops-default/application-api.yml", "robin SPI 默认实现"),
            new SettingDefinition("plugin.api.spi.mapping.serviceDiscovery", "com.chua.common.support.discovery.ServiceDiscovery", "TEXT", "startup-api", 50, "ops-default/application-api.yml", "服务发现 SPI 默认实现"),
            new SettingDefinition("plugin.api.spi.mapping.fileStoragePlugin", "com.chua.common.support.image.filter.ImageFilter", "TEXT", "startup-api", 60, "ops-default/application-api.yml", "文件存储插件 SPI 默认实现"),
            new SettingDefinition("plugin.api.spi.mapping.fileStorage", "com.chua.common.support.oss.FileStorage", "TEXT", "startup-api", 70, "ops-default/application-api.yml", "文件存储 SPI 默认实现"),
            new SettingDefinition("plugin.api.spi.mapping.ImageSettingProcessor", "com.chua.common.support.protocol.filter.storage.image.ImageSettingProcessor", "TEXT", "startup-api", 80, "ops-default/application-api.yml", "图像处理 SPI 默认实现"),
            new SettingDefinition("spring.threads.virtual.enabled", "true", "BOOL", "startup-api", 90, "ops-default/application-api.yml", "默认启用虚拟线程"),

            new SettingDefinition("management.endpoints.web.exposure.include", "*", "TEXT", "startup-actuator", 10, "ops-default/application-actuator.yml", "Actuator 默认暴露全部端点"),
            new SettingDefinition("management.endpoint.health.show-details", "always", "TEXT", "startup-actuator", 20, "ops-default/application-actuator.yml", "默认显示健康详情"),

            new SettingDefinition("plugin.ip.enable", "true", "BOOL", "startup-ip", 10, "ops-default/application-ip.yml", "启用 IP 插件"),

            new SettingDefinition("spring.jackson.date-format", "yyyy-MM-dd HH:mm:ss", "TEXT", "startup-jackson", 10, "ops-default/application-jackson.yml", "全局时间格式"),
            new SettingDefinition("spring.jackson.time-zone", "GMT+8", "TEXT", "startup-jackson", 20, "ops-default/application-jackson.yml", "全局时区"),
            new SettingDefinition("plugin.jackson.long-to-string", "true", "BOOL", "startup-jackson", 30, "ops-default/application-jackson.yml", "Long 默认转字符串"),

            new SettingDefinition("mybatis-plus.mapper-locations", "classpath*:mapper/**/*.xml", "TEXT", "startup-mybatis", 10, "ops-default/application-mybatis.yml", "Mapper XML 扫描路径"),
            new SettingDefinition("mybatis-plus.global-config.db-config.id-type", "none", "TEXT", "startup-mybatis", 20, "ops-default/application-mybatis.yml", "主键策略"),
            new SettingDefinition("mybatis-plus.global-config.db-config.logic-delete-value", "1", "NUMBER", "startup-mybatis", 30, "ops-default/application-mybatis.yml", "逻辑删除值"),
            new SettingDefinition("mybatis-plus.global-config.db-config.logic-not-delete-value", "0", "NUMBER", "startup-mybatis", 40, "ops-default/application-mybatis.yml", "逻辑未删除值"),
            new SettingDefinition("mybatis-plus.configuration.default-enum-type-handler", "org.apache.ibatis.type.EnumTypeHandler", "TEXT", "startup-mybatis", 50, "ops-default/application-mybatis.yml", "枚举处理器"),
            new SettingDefinition("mybatis-plus.configuration.map-underscore-to-camel-case", "true", "BOOL", "startup-mybatis", 60, "ops-default/application-mybatis.yml", "下划线转驼峰"),
            new SettingDefinition("mybatis-plus.configuration.cache-enabled", "true", "BOOL", "startup-mybatis", 70, "ops-default/application-mybatis.yml", "开启 MyBatis 二级缓存"),
            new SettingDefinition("mybatis-plus.type-handlers-package", "com.chua.starter.mybatis.handler", "TEXT", "startup-mybatis", 80, "ops-default/application-mybatis.yml", "TypeHandler 包路径"),
            new SettingDefinition("plugin.mybatis.open-xml-reload", "true", "BOOL", "startup-mybatis", 90, "ops-default/application-mybatis.yml", "开启 XML 热加载"),
            new SettingDefinition("plugin.mybatis.read-only", "false", "BOOL", "startup-mybatis", 100, "ops-default/application-mybatis.yml", "默认非只读模式"),

            new SettingDefinition("springdoc.group-configs[0].group", "default", "TEXT", "startup-swagger", 10, "ops-default/application-swagger.yml", "Swagger 默认分组编码"),
            new SettingDefinition("springdoc.group-configs[0].paths-to-match", "/**", "TEXT", "startup-swagger", 20, "ops-default/application-swagger.yml", "Swagger 默认路径"),
            new SettingDefinition("springdoc.group-configs[0].packages-to-scan", "com.chua", "TEXT", "startup-swagger", 30, "ops-default/application-swagger.yml", "Swagger 默认扫描包"),
            new SettingDefinition("knife4j.enable", "true", "BOOL", "startup-swagger", 40, "ops-default/application-swagger.yml", "启用 Knife4j"),
            new SettingDefinition("knife4j.setting.language", "zh-CN", "TEXT", "startup-swagger", 50, "ops-default/application-swagger.yml", "Knife4j 默认语言")
    );

    @Override
    /**
     * Post处理环境
     * @param application 参数
     * @param environment 参数
     * @return 返回值
    */
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.parseBoolean(environment.getProperty(ENABLED_KEY, "true"))) {
            log.debug("[数据库配置]已禁用: {}=false", ENABLED_KEY);
            return;
        }

        ConnectionInfo connectionInfo = resolveConnectionInfo(environment);
        if (connectionInfo == null) {
            log.info("[数据库配置]未检测到可用数据源配置，跳过数据库配置中心初始化");
            return;
        }

        /**
         * 尝试。
         * @param openConnection(connectionInfo 打开connection(connection信息
         */
        try (Connection connection = openConnection(connectionInfo)) {
            /**
             * ensure群体table。
             * @param connection connection
             */
            ensureGroupTable(connection);
            /**
             * ensuresettingtable。
             * @param connection connection
             */
            ensureSettingTable(connection);
            /**
             * 参见默认群体。
             * @param connection connection
             */
            seedDefaultGroups(connection);
            /**
             * 参见默认settings。
             * @param connection connection
             */
            seedDefaultSettings(connection);
            /**
             * 注册财产源。
             * @param environment.getPropertySources( 环境.获取财产源(
             */
            registerPropertySource(environment.getPropertySources(), loadStartupSettings(connection));
        } catch (Exception e) {
            log.warn("[数据库配置]初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析connection信息
     *
     * @param environment 环境
     * @return resolveconnection信息的结果
     */
    private ConnectionInfo resolveConnectionInfo(ConfigurableEnvironment environment) {
        String url = environment.getProperty("spring.datasource.url");
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return new ConnectionInfo(
                url,
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("spring.datasource.driver-class-name"));
    }

    /**
     * 打开Connection
     * @param connectionInfo connection信息
     * @return 打开connection的结果
     * @throws Exception 异常
    */
    private Connection openConnection(ConnectionInfo connectionInfo) throws Exception {
        if (StringUtils.hasText(connectionInfo.driverClassName())) {
            ReflectUtils.forName(connectionInfo.driverClassName());
        }
        if (connectionInfo.username() == null) {
            return DriverManager.getConnection(connectionInfo.url());
        }
        return DriverManager.getConnection(
                connectionInfo.url(),
                connectionInfo.username(),
                connectionInfo.password() == null ? "" : connectionInfo.password());
    }

    /**
     * Ensure分组Table
     * @param connection connection
     * @return 返回值
     * @throws SQLException 异常
    */
    private void ensureGroupTable(Connection connection) throws SQLException {
        if (tableExists(connection, GROUP_TABLE)) {
            return;
        }
        /**
         * 尝试。
         * @param connection.createStatement( connection.创建对账单(
         */
        try (Statement statement = connection.createStatement()) {
            statement.execute(createGroupTableSql(connection));
        }
        log.info("[数据库配置]已创建配置分组表 {}", GROUP_TABLE);
    }

    /**
     * ensuresettingtable
     * @param connection connection
     * @return 返回值
     * @throws SQLException 异常
    */
    private void ensureSettingTable(Connection connection) throws SQLException {
        if (tableExists(connection, SETTING_TABLE)) {
            addMissingSettingColumns(connection);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSettingTableSql(connection));
        }
        log.info("[数据库配置]已创建配置表 {}", SETTING_TABLE);
    }

    /**
     * 补齐配置表中缺失的固定注册列，单列失败不阻断启动。
     *
     * @param connection 数据库连接
     * @throws SQLException 读取表结构失败时抛出
     */
    private void addMissingSettingColumns(Connection connection) throws SQLException {
        List<String> existing = columnNames(connection, SETTING_TABLE);
        for (Map.Entry<String, String> entry : SETTING_MISSING_COLUMNS.entrySet()) {
            if (existing.contains(entry.getKey())) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(entry.getValue());
                log.info("[数据库配置]已补齐表 {} 缺失列 {}", SETTING_TABLE, entry.getKey());
            } catch (SQLException e) {
                log.warn("[数据库配置]补齐表 {} 缺失列 {} 失败: {}", SETTING_TABLE, entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 读取表的列名（小写），按 tableExists 同样的大小写候选逐一尝试
     * @param connection connection
     * @param tableName table名称
     * @return 列名列表（小写）
     * @throws SQLException 异常
    */
    private List<String> columnNames(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        List<String> columns = new ArrayList<>();
        for (String candidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
            try (ResultSet resultSet = metaData.getColumns(catalog, schema, candidate, null)) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return columns;
    }

    /**
     * Table是否存在
     * @param connection connection
     * @param tableName table名称
     * @return tableExists的结果
     * @throws SQLException 异常
    */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        for (String candidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
            /**
             * 尝试。
             * @param String[]{"TABLE"} 字符串[]{"TABLE"}
             */
            try (ResultSet resultSet = metaData.getTables(catalog, schema, candidate, new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String current = resultSet.getString("TABLE_NAME");
                    if (tableName.equalsIgnoreCase(current)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 参见默认群体
     * @param connection connection
     * @return 返回值
     * @throws SQLException 异常
    */
    private void seedDefaultGroups(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO sys_setting_group (
                    sys_setting_group_name,
                    sys_setting_group_code,
                    sys_setting_group_icon,
                    sys_setting_group_enable,
                    sys_setting_group_remark,
                    sys_setting_group_sort,
                    sys_setting_group_use_project_interface,
                    create_name,
                    create_time,
                    update_name,
                    update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """;

        int created = 0;
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT COUNT(*) FROM sys_setting_group WHERE sys_setting_group_code = ?");
             PreparedStatement insert = connection.prepareStatement(sql)) {
            for (GroupDefinition definition : DEFAULT_GROUPS) {
                exists.setString(1, definition.code());
                /**
                 * 尝试。
                 * @param exists.executeQuery( exists.执行查询(
                 */
                try (ResultSet resultSet = exists.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        continue;
                    }
                }

                insert.setString(1, definition.name());
                insert.setString(2, definition.code());
                insert.setString(3, definition.icon());
                insert.setBoolean(4, true);
                insert.setString(5, definition.remark());
                insert.setInt(6, definition.sort());
                insert.setBoolean(7, false);
                insert.setString(8, "system");
                insert.setString(9, "system");
                insert.addBatch();
                created++;
            }
            if (created > 0) {
                insert.executeBatch();
            }
        }

        if (created > 0) {
            log.info("[数据库配置]补齐 {} 个默认配置分组", created);
        }
    }

    /**
     * 参见默认settings
     * @param connection connection
     * @return 返回值
     * @throws SQLException 异常
    */
    private void seedDefaultSettings(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO sys_setting (
                    sys_setting_name,
                    sys_setting_value,
                    sys_setting_config,
                    sys_setting_remark,
                    sys_setting_sort,
                    sys_setting_version,
                    sys_setting_status,
                    sys_setting_admin,
                    sys_setting_value_type,
                    sys_setting_group,
                    sys_setting_collect,
                    sys_setting_in_system,
                    sys_setting_app_inner,
                    create_name,
                    create_time,
                    update_name,
                    update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """;

        int created = 0;
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT COUNT(*) FROM sys_setting WHERE sys_setting_group = ? AND sys_setting_name = ?");
             PreparedStatement insert = connection.prepareStatement(sql)) {
            for (SettingDefinition definition : DEFAULT_SETTINGS) {
                exists.setString(1, definition.groupCode());
                exists.setString(2, definition.name());
                /**
                 * 尝试。
                 * @param exists.executeQuery( exists.执行查询(
                 */
                try (ResultSet resultSet = exists.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        continue;
                    }
                }

                insert.setString(1, definition.name());
                insert.setString(2, definition.defaultValue());
                insert.setString(3, definition.config());
                insert.setString(4, definition.remark());
                insert.setInt(5, definition.sort());
                insert.setInt(6, 1);
                insert.setInt(7, 1);
                insert.setInt(8, 0);
                insert.setString(9, definition.valueType());
                insert.setString(10, definition.groupCode());
                insert.setInt(11, 0);
                insert.setInt(12, 1);
                insert.setInt(13, 1);
                insert.setString(14, "system");
                insert.setString(15, "system");
                insert.addBatch();
                created++;
            }
            if (created > 0) {
                insert.executeBatch();
            }
        }

        if (created > 0) {
            log.info("[数据库配置]补齐 {} 个默认启动配置项", created);
        }
    }

    /**
     * 加载startupsettings
     * @param connection connection
     * @return 加载startupsettings的结果
     * @throws SQLException 异常
    */
    private Map<String, Object> loadStartupSettings(Connection connection) throws SQLException {
        String sql = """
                SELECT sys_setting_name, sys_setting_value
                FROM sys_setting
                WHERE sys_setting_status = 1
                  AND sys_setting_app_inner = 1
                ORDER BY sys_setting_group ASC, sys_setting_sort ASC, sys_setting_id ASC
                """;

        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        /**
         * 尝试。
         * @param connection.prepareStatement(sql connection.prepare对账单(SQL
         */
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                settings.put(resultSet.getString("sys_setting_name"), resultSet.getString("sys_setting_value"));
            }
        }
        return settings;
    }

    /**
     * 注册财产源
     *
     * @param propertySources 财产源
     * @param settings settings
     */
    private void registerPropertySource(MutablePropertySources propertySources, Map<String, Object> settings) {
        if (settings.isEmpty() || propertySources.contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, settings);
        String firstOpsSource = findFirstOpsSource(propertySources);
        if (firstOpsSource != null) {
            propertySources.addBefore(firstOpsSource, propertySource);
            return;
        }
        propertySources.addLast(propertySource);
    }

    /**
     * 查找第一个ops源
     *
     * @param propertySources 财产源
     * @return find第一个ops源的结果
     */
    private String findFirstOpsSource(MutablePropertySources propertySources) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource.getName().startsWith(OPS_SOURCE_PREFIX)) {
                return propertySource.getName();
            }
        }
        return null;
    }

    /**
     * 创建分组tablesql
     * @param connection connection
     * @return 创建群体tablesql的结果
     * @throws SQLException 异常
    */
    private String createGroupTableSql(Connection connection) throws SQLException {
        String idColumn = autoIncrementColumn(connection, "sys_setting_group_id");
        return """
                CREATE TABLE IF NOT EXISTS sys_setting_group (
                    %s,
                    /**
                    * VARCHAR。
                    * @param 255 255
                    * @return VARCHAR的结果
                    */
                    sys_setting_group_name VARCHAR(255),
                    /**
                    * VARCHAR。
                    * @param 255 255
                    * @return VARCHAR的结果
                    */
                    sys_setting_group_code VARCHAR(255),
                    /**
                    * VARCHAR。
                    * @param 255 255
                    * @return VARCHAR的结果
                    */
                    sys_setting_group_icon VARCHAR(255),
                    sys_setting_group_enable BOOLEAN,
                    /**
                    * VARCHAR。
                    * @param 1000 1000
                    * @return VARCHAR的结果
                    */
                    sys_setting_group_remark VARCHAR(1000),
                    sys_setting_group_sort INT,
                    sys_setting_group_use_project_interface BOOLEAN,
                    /**
                    * VARCHAR。
                    * @param 255 255
                    * @return VARCHAR的结果
                    */
                    create_name VARCHAR(255),
                    create_by INT,
                    create_time TIMESTAMP,
                    /**
                    * VARCHAR。
                    * @param 255 255
                    * @return VARCHAR的结果
                    */
                    update_name VARCHAR(255),
                    update_by INT,
                    update_time TIMESTAMP,
                    /**
                    * UNIQUE。
                    * @param sys_setting_group_code syssetting群体编码
                    * @return UNIQUE的结果
                    */
                    CONSTRAINT uk_sys_setting_group_code UNIQUE (sys_setting_group_code)
                )
                """.formatted(idColumn);
    }

    /**
     * 创建settingtablesql
     * @param connection connection
     * @return 创建settingtablesql的结果
     * @throws SQLException 异常
    */
    private String createSettingTableSql(Connection connection) throws SQLException {
        String idColumn = autoIncrementColumn(connection, "sys_setting_id");
        return """
                CREATE TABLE IF NOT EXISTS sys_setting (
                    %s,
                    sys_setting_name VARCHAR(255),
                    sys_setting_code VARCHAR(255),
                    sys_setting_value VARCHAR(255),
                    sys_setting_default_value VARCHAR(255),
                    sys_setting_config VARCHAR(255),
                    sys_setting_remark VARCHAR(255),
                    sys_setting_sort INT,
                    sys_setting_version INT,
                    sys_setting_status INT,
                    sys_setting_admin INT,
                    sys_setting_value_type VARCHAR(255),
                    sys_setting_group VARCHAR(255),
                    sys_setting_collect INT,
                    sys_setting_in_system INT,
                    sys_setting_app_inner INT,
                    create_name VARCHAR(255),
                    create_by INT,
                    create_time TIMESTAMP,
                    update_name VARCHAR(255),
                    update_by INT,
                    update_time TIMESTAMP,
                    sys_setting_is_sensitive INT DEFAULT 0,
                    sys_setting_validate VARCHAR(512),
                    CONSTRAINT uk_sys_setting_name_group UNIQUE (sys_setting_name, sys_setting_group)
                )
                """.formatted(idColumn);
    }

    /**
     * autoincrementcolumn
     * @param columnName column名称
     * @param connection connection
     * @return autoIncrementColumn的结果
     * @throws SQLException 异常
    */
    private String autoIncrementColumn(Connection connection, String columnName) throws SQLException {
        String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (database.contains("sqlite")) {
            return columnName + " INTEGER PRIMARY KEY AUTOINCREMENT";
        }
        if (database.contains("postgresql")) {
            return columnName + " SERIAL PRIMARY KEY";
        }
        return columnName + " INT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 110;
    }

    /**
     * connection信息
     *
     * @param url url
     * @param username 用户名
     * @param password 密码
     * @param driverClassName driver类名称
     * @return connection信息的结果
     */
    private record ConnectionInfo(String url, String username, String password, String driverClassName) {
    }

    /**
     * 分组Definition
     *
     * @param code 编码
     * @param name 名称
     * @param icon icon
     * @param remark 评论
     * @param sort 排序
     * @return 群体definition的结果
     */
    private record GroupDefinition(String code, String name, String icon, String remark, int sort) {
    }

    /**
     * settingdefinition
     * @param name 名称
     * @param defaultValue 默认值
     * @param valueType 值类型
     * @param groupCode 群体编码
     * @param sort 排序
     * @param config 配置
     * @param remark 评论
     */
    private record SettingDefinition(
            String name,
            String defaultValue,
            String valueType,
            String groupCode,
            int sort,
            String config,
            String remark
    ) {
    }
}

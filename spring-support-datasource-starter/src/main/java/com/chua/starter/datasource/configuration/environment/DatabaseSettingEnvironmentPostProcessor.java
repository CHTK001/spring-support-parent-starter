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
 */
@Slf4j
public class DatabaseSettingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /** Enabled_key */
    private static final String ENABLED_KEY = "plugin.ops-config.database.enabled";
    /** Setting_table */
    private static final String SETTING_TABLE = "sys_setting";
    /** Group_table */
    private static final String GROUP_TABLE = "sys_setting_group";
    /** Property_source_name */
    private static final String PROPERTY_SOURCE_NAME = "database-startup-config";
    /** Ops_source_prefix */
    private static final String OPS_SOURCE_PREFIX = "ops-config-";

    /** Default_groups */
    private static final List<GroupDefinition> DEFAULT_GROUPS = List.of(
            new GroupDefinition("startup-api", "API基础配置", "ri:code-box-line", "启动期 API 通用配置", 10),
            new GroupDefinition("startup-actuator", "Actuator配置", "ri:pulse-line", "启动期 Actuator 通用配置", 20),
            new GroupDefinition("startup-ip", "IP配置", "ri:map-pin-line", "启动期 IP 通用配置", 30),
            new GroupDefinition("startup-jackson", "Jackson配置", "ri:file-code-line", "启动期 Jackson 通用配置", 40),
            new GroupDefinition("startup-mybatis", "MyBatis配置", "ri:database-2-line", "启动期 MyBatis 通用配置", 50),
            new GroupDefinition("startup-swagger", "Swagger配置", "ri:book-open-line", "启动期 Swagger 通用配置", 60)
    );

    /** Default_settings */
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
    /** Post处理Environment */
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

        try (Connection connection = openConnection(connectionInfo)) {
            ensureGroupTable(connection);
            ensureSettingTable(connection);
            seedDefaultGroups(connection);
            seedDefaultSettings(connection);
            registerPropertySource(environment.getPropertySources(), loadStartupSettings(connection));
        } catch (Exception e) {
            log.warn("[数据库配置]初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 解析ConnectionInfo */
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

    /** 打开Connection */
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

    /** Ensure分组Table */
    private void ensureGroupTable(Connection connection) throws SQLException {
        if (tableExists(connection, GROUP_TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(createGroupTableSql(connection));
        }
        log.info("[数据库配置]已创建配置分组表 {}", GROUP_TABLE);
    }

    /** EnsureSettingTable */
    private void ensureSettingTable(Connection connection) throws SQLException {
        if (tableExists(connection, SETTING_TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSettingTableSql(connection));
        }
        log.info("[数据库配置]已创建配置表 {}", SETTING_TABLE);
    }

    /** Table是否存在 */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        for (String candidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
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

    /** SeedDefaultGroups */
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

    /** SeedDefaultSettings */
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

    /** 加载StartupSettings */
    private Map<String, Object> loadStartupSettings(Connection connection) throws SQLException {
        String sql = """
                SELECT sys_setting_name, sys_setting_value
                FROM sys_setting
                WHERE sys_setting_status = 1
                  AND sys_setting_app_inner = 1
                ORDER BY sys_setting_group ASC, sys_setting_sort ASC, sys_setting_id ASC
                """;

        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                settings.put(resultSet.getString("sys_setting_name"), resultSet.getString("sys_setting_value"));
            }
        }
        return settings;
    }

    /** 注册PropertySource */
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

    /** 查找FirstOpsSource */
    private String findFirstOpsSource(MutablePropertySources propertySources) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource.getName().startsWith(OPS_SOURCE_PREFIX)) {
                return propertySource.getName();
            }
        }
        return null;
    }

    /** 创建分组TableSql */
    private String createGroupTableSql(Connection connection) throws SQLException {
        String idColumn = autoIncrementColumn(connection, "sys_setting_group_id");
        return """
                CREATE TABLE IF NOT EXISTS sys_setting_group (
                    %s,
                    sys_setting_group_name VARCHAR(255),
                    sys_setting_group_code VARCHAR(255),
                    sys_setting_group_icon VARCHAR(255),
                    sys_setting_group_enable BOOLEAN,
                    sys_setting_group_remark VARCHAR(1000),
                    sys_setting_group_sort INT,
                    sys_setting_group_use_project_interface BOOLEAN,
                    create_name VARCHAR(255),
                    create_by INT,
                    create_time TIMESTAMP,
                    update_name VARCHAR(255),
                    update_by INT,
                    update_time TIMESTAMP,
                    CONSTRAINT uk_sys_setting_group_code UNIQUE (sys_setting_group_code)
                )
                """.formatted(idColumn);
    }

    /** 创建SettingTableSql */
    private String createSettingTableSql(Connection connection) throws SQLException {
        String idColumn = autoIncrementColumn(connection, "sys_setting_id");
        return """
                CREATE TABLE IF NOT EXISTS sys_setting (
                    %s,
                    sys_setting_name VARCHAR(255),
                    sys_setting_value VARCHAR(255),
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
                    CONSTRAINT uk_sys_setting_name_group UNIQUE (sys_setting_name, sys_setting_group)
                )
                """.formatted(idColumn);
    }

    /** AutoIncrementColumn */
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
    /** 获取Order */
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 110;
    }

    /** ConnectionInfo */
    private record ConnectionInfo(String url, String username, String password, String driverClassName) {
    }

    /** 分组Definition */
    private record GroupDefinition(String code, String name, String icon, String remark, int sort) {
    }

    /**
     * SettingDefinition
     * @param name name
     * @param defaultValue defaultValue
     * @param valueType valueType
     * @param groupCode groupCode
     * @param sort sort
     * @param config config
     * @param remark remark
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

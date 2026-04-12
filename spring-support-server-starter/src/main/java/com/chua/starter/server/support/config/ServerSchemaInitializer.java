package com.chua.starter.server.support.config;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 服务器模块表结构初始化器。
 *
 * <p>全局 datasource script 在 ONCE 模式下只会在“数据库近乎空白”时执行，
 * 当系统库里已经有其他业务表时，新接入 server 模块的结构脚本可能被整体跳过。
 * 这里补一层模块级自检，缺表时只执行 server 模块 DDL，不写入测试数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerSchemaInitializer {

    private static final String SERVER_DDL_PATH = "db/init/V1.0__init_server.sql";
    private static final String[] REQUIRED_TABLES = {
            "server_host",
            "server_service",
            "server_service_operation_log",
            "server_service_ai_knowledge",
            "server_alert_setting",
            "server_alert_event",
            "server_metrics_history",
            "server_setting"
    };

    private final DataSource dataSource;

    /**
     * 应用启动后检查 server 模块核心表是否齐备，缺失时补建。
     */
    @PostConstruct
    public void initializeSchemaIfNecessary() {
        if (allTablesPresent()) {
            return;
        }
        log.info("检测到 server 模块表结构缺失，开始补建核心表");
        executeServerDdl();
    }

    /**
     * 判断 server 模块核心表是否已经全部存在。
     */
    private boolean allTablesPresent() {
        for (String table : REQUIRED_TABLES) {
            if (!checkTableExists(table)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查单个表是否存在。
     */
    private boolean checkTableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] candidates = {tableName, tableName.toUpperCase()};
            for (String candidate : candidates) {
                try (ResultSet resultSet = metaData.getTables(null, null, candidate, new String[]{"TABLE"})) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("检查 server 模块表失败: " + tableName, e);
        }
        return false;
    }

    /**
     * 只执行结构类 DDL，避免把 soft-test 示例数据注入到所有接入方。
     */
    private void executeServerDdl() {
        ClassPathResource resource = new ClassPathResource(SERVER_DDL_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("未找到服务器模块初始化脚本: " + SERVER_DDL_PATH);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            String ddlScript = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            for (String statement : splitStatements(ddlScript)) {
                String normalized = normalizeStatement(statement);
                if (!normalized.startsWith("CREATE TABLE")) {
                    continue;
                }
                jdbcTemplate.execute(statement);
            }
            if (!allTablesPresent()) {
                throw new IllegalStateException("server 模块表结构补建后仍有缺失");
            }
            log.info("server 模块核心表补建完成");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 server 模块表结构失败", e);
        }
    }

    /**
     * 按分号拆分 SQL 语句。
     */
    private String[] splitStatements(String script) {
        return script.split(";");
    }

    /**
     * 去掉注释与空白后用于识别语句类型。
     */
    private String normalizeStatement(String statement) {
        StringBuilder builder = new StringBuilder();
        for (String line : statement.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            builder.append(trimmed).append(' ');
        }
        return builder.toString().trim().toUpperCase();
    }
}

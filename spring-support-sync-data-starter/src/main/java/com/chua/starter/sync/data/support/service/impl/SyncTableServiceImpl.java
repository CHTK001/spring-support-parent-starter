package com.chua.starter.sync.data.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.sync.SyncTableStatus;
import com.chua.starter.sync.data.support.service.sync.SyncTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步任务表服务实现
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTableServiceImpl implements SyncTableService {

    private final DataSource dataSource;

    /**
     * DDL 脚本路径
     */
    private static final String SYNC_DDL_PATH = "db/sync/V1.0__init_sync_tables.sql";

    /**
     * 同步相关表定义
     */
    private static final String[][] SYNC_TABLES = {
            {"monitor_sync_task", "同步任务表"},
            {"monitor_sync_node", "同步节点表"},
            {"monitor_sync_connection", "同步连线表"},
            {"monitor_sync_task_log", "同步任务执行日志表"},
            {"monitor_sync_task_log_detail", "同步任务实时日志详情表"}
    };

    @Override
    public ReturnResult<SyncTableStatus> initializeTables(boolean force) {
        try {
            SyncTableStatus currentStatus = checkTablesInternal();

            if (currentStatus.isInitialized() && !force) {
                currentStatus.setMessage("同步任务表已存在，无需重复初始化");
                return ReturnResult.ok(currentStatus);
            }

            log.info("开始{}同步任务相关表...", force ? "强制重建" : "创建");

            executeDdlScript();

            SyncTableStatus newStatus = checkTablesInternal();
            newStatus.setMessage(force ? "表已强制重建完成" : "表初始化完成");

            log.info("同步任务相关表{}完成", force ? "重建" : "初始化");
            return ReturnResult.ok(newStatus);

        } catch (Exception e) {
            log.error("初始化同步任务表失败", e);
            return ReturnResult.error("初始化失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTableStatus> checkTableStatus() {
        try {
            SyncTableStatus status = checkTablesInternal();
            status.setMessage(status.isInitialized() ? "所有表已就绪" : "部分表未创建");
            return ReturnResult.ok(status);
        } catch (Exception e) {
            log.error("检查表状态失败", e);
            return ReturnResult.error("检查失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isTableExists() {
        return checkTableExists("monitor_sync_task");
    }

    /**
     * 内部检查表状态
     */
    private SyncTableStatus checkTablesInternal() {
        List<SyncTableStatus.TableInfo> tableInfos = new ArrayList<>();
        boolean allExists = true;

        for (String[] tableInfo : SYNC_TABLES) {
            String tableName = tableInfo[0];
            String description = tableInfo[1];
            boolean exists = checkTableExists(tableName);

            tableInfos.add(SyncTableStatus.TableInfo.builder()
                    .tableName(tableName)
                    .exists(exists)
                    .description(description)
                    .build());

            if (!exists) {
                allExists = false;
            }
        }

        return SyncTableStatus.builder()
                .initialized(allExists)
                .tables(tableInfos)
                .build();
    }

    /**
     * 检查指定表是否存在
     */
    private boolean checkTableExists(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String[] tableNames = {tableName, tableName.toUpperCase()};
            for (String name : tableNames) {
                try (ResultSet rs = metaData.getTables(null, null, name, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("检查表 {} 存在性失败", tableName, e);
            return false;
        }
    }

    /**
     * 执行 DDL 脚本
     */
    private void executeDdlScript() throws Exception {
        ClassPathResource resource = new ClassPathResource(SYNC_DDL_PATH);
        if (!resource.exists()) {
            throw new RuntimeException("DDL 脚本文件不存在: " + SYNC_DDL_PATH);
        }

        String ddlScript;
        try (InputStream is = resource.getInputStream()) {
            ddlScript = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String[] statements = splitSqlStatements(ddlScript);
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                try {
                    jdbcTemplate.execute(trimmed);
                    log.debug("执行 SQL: {}", truncate(trimmed, 100));
                } catch (Exception e) {
                    log.warn("SQL 执行警告: {} - {}", truncate(trimmed, 50), e.getMessage());
                }
            }
        }
    }

    /**
     * 分割 SQL 语句
     */
    private String[] splitSqlStatements(String script) {
        String[] lines = script.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().split(";");
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        str = str.replace("\n", " ").replace("\r", "");
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}

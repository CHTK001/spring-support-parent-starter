package com.chua.starter.job.support;

import com.chua.common.support.data.datasource.meta.TableMetadata;
import com.chua.common.support.data.query.JdbcEngine;
import com.chua.common.support.data.query.ddl.ActionType;
import com.chua.common.support.data.query.ddl.JdbcTableExecutor;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.entity.SysJobLogBackup;
import com.chua.starter.job.support.entity.SysJobLogDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于 JdbcEngine 的 Job 表初始化器。
 * <p>
 * 在启动阶段根据当前解析后的物理表名执行 DDL，支持命名空间前缀表和自定义表名。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class JobSchemaInitializer implements InitializingBean {

    private final JobProperties jobProperties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    @Override
    public void afterPropertiesSet() {
        JobSchemaInitMode initMode = jobProperties.getTableInitMode();
        if (initMode == null || !initMode.isEnabled()) {
            return;
        }
        if (!jobProperties.isConfigTableEnabled()) {
            log.info("[Job] 配置表调度已关闭，跳过物理表初始化");
            return;
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            throw new IllegalStateException("Job 表初始化失败: 未找到可用的数据源");
        }

        JobProperties.Table table = jobProperties.getTable() == null
                ? new JobProperties.Table()
                : jobProperties.getTable().resolvedCopy();

        String databaseProductName = detectDatabaseProductName(dataSource);
        if (isH2Database(databaseProductName)) {
            initializeForH2(dataSource, SysJob.class, table.getJob(), initMode);
            initializeForH2(dataSource, SysJobLog.class, table.getLog(), initMode);
            initializeForH2(dataSource, SysJobLogDetail.class, table.getLogDetail(), initMode);
            initializeForH2(dataSource, SysJobLogBackup.class, table.getLogBackup(), initMode);
            return;
        }

        JdbcTableExecutor executor = new JdbcEngine()
                .setDataSource(dataSource)
                .createTable();

        initialize(executor, dataSource, SysJob.class, table.getJob(), initMode);
        initialize(executor, dataSource, SysJobLog.class, table.getLog(), initMode);
        initialize(executor, dataSource, SysJobLogDetail.class, table.getLogDetail(), initMode);
        initialize(executor, dataSource, SysJobLogBackup.class, table.getLogBackup(), initMode);
    }

    private void initialize(JdbcTableExecutor executor,
                            DataSource dataSource,
                            Class<?> entityType,
                            String tableName,
                            JobSchemaInitMode initMode) {
        TableMetadata metadata = new TableMetadata();
        BeanUtils.copyProperties(TableMetadata.of(entityType), metadata);
        metadata.setName(tableName);

        if (initMode == JobSchemaInitMode.CREATE) {
            createOrSkip(executor, dataSource, metadata, entityType, tableName, initMode);
            return;
        }

        updateOrCreate(executor, dataSource, metadata, entityType, tableName, initMode);
    }

    private void execute(JdbcTableExecutor executor,
                         TableMetadata metadata,
                         ActionType actionType,
                         String errorPrefix) {
        ErrorResult<String> result = executor.doIt(metadata, actionType);
        if (result != null && result.hasError()) {
            throw new IllegalStateException(errorPrefix + ": table=" + metadata.getName() + ", error=" + result.getFirstError());
        }
    }

    private void createOrSkip(JdbcTableExecutor executor,
                              DataSource dataSource,
                              TableMetadata metadata,
                              Class<?> entityType,
                              String tableName,
                              JobSchemaInitMode initMode) {
        ErrorResult<String> result = executor.doIt(metadata, ActionType.CREATE);
        if (result == null || !result.hasError()) {
            upgradeLegacySchema(dataSource, entityType, tableName);
            log.info("[Job] 任务表初始化完成: table={}, mode={}", tableName, initMode);
            return;
        }
        if (isAlreadyExists(result)) {
            upgradeLegacySchema(dataSource, entityType, tableName);
            validateExistingTable(dataSource, metadata);
            log.info("[Job] 任务表已存在，跳过创建: table={}, mode={}", tableName, initMode);
            return;
        }
        throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", error=" + result.getFirstError());
    }

    /**
     * H2 在 MySQL 兼容模式下仍可能无法完成 JdbcEngine 的 identity 列更新。
     * 这里为本地浏览器/演示环境提供一套确定性的兜底 DDL，保证 job 模块可以启动。
     */
    private void initializeForH2(DataSource dataSource,
                                 Class<?> entityType,
                                 String tableName,
                                 JobSchemaInitMode initMode) {
        List<String> ddl = h2SchemaSql(entityType, tableName);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (initMode == JobSchemaInitMode.DROP_CREATE) {
                statement.execute("DROP TABLE IF EXISTS " + tableName);
            }
            for (String sql : ddl) {
                statement.execute(sql);
            }
            log.info("[Job] H2 任务表初始化完成: table={}, mode={}", tableName, initMode);
        } catch (SQLException e) {
            throw new IllegalStateException("Job H2 表初始化失败: table=" + tableName, e);
        }
    }

    private List<String> h2SchemaSql(Class<?> entityType, String tableName) {
        if (SysJob.class == entityType) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "job_id INT AUTO_INCREMENT PRIMARY KEY,"
                            + "job_no VARCHAR(64) NOT NULL,"
                            + "job_name VARCHAR(255),"
                            + "job_schedule_type VARCHAR(50) DEFAULT 'CRON',"
                            + "job_schedule_time VARCHAR(255),"
                            + "job_author VARCHAR(100),"
                            + "job_alarm_email VARCHAR(255),"
                            + "job_trigger_status TINYINT DEFAULT 0,"
                            + "job_desc VARCHAR(500),"
                            + "job_glue_updatetime TIMESTAMP NULL,"
                            + "job_glue_source CLOB,"
                            + "job_glue_type VARCHAR(50) DEFAULT 'BEAN',"
                            + "job_dispatch_mode VARCHAR(20) DEFAULT 'LOCAL',"
                            + "job_remote_executor_address VARCHAR(500),"
                            + "job_storage_mode VARCHAR(20) DEFAULT 'DATABASE',"
                            + "job_fail_retry INT DEFAULT 3,"
                            + "job_retry_interval INT DEFAULT 0,"
                            + "job_execute_timeout INT DEFAULT 0,"
                            + "job_execute_bean VARCHAR(255),"
                            + "job_execute_param VARCHAR(512),"
                            + "job_exception_callback_bean VARCHAR(255),"
                            + "job_retry_callback_bean VARCHAR(255),"
                            + "job_execute_misfire_strategy VARCHAR(50) DEFAULT 'DO_NOTHING',"
                            + "job_trigger_last_time BIGINT DEFAULT 0,"
                            + "job_trigger_next_time BIGINT DEFAULT 0,"
                            + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                            + "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                            + "create_by VARCHAR(50),"
                            + "update_by VARCHAR(50)"
                            + ")",
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_job_no ON " + tableName + " (job_no)",
                    "CREATE INDEX IF NOT EXISTS idx_trigger_status ON " + tableName + " (job_trigger_status)",
                    "CREATE INDEX IF NOT EXISTS idx_trigger_next_time ON " + tableName + " (job_trigger_next_time)",
                    "CREATE INDEX IF NOT EXISTS idx_job_dispatch_mode ON " + tableName + " (job_dispatch_mode)"
            );
        }
        if (SysJobLog.class == entityType) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "job_log_id INT AUTO_INCREMENT PRIMARY KEY,"
                            + "job_log_no VARCHAR(64) NOT NULL,"
                            + "job_id INT,"
                            + "job_no VARCHAR(64),"
                            + "job_log_app VARCHAR(255),"
                            + "job_log_trigger_bean VARCHAR(255),"
                            + "job_log_trigger_type VARCHAR(50),"
                            + "job_log_profile VARCHAR(50),"
                            + "job_log_trigger_time TIMESTAMP NULL,"
                            + "job_log_trigger_date DATE,"
                            + "job_log_trigger_code VARCHAR(20),"
                            + "job_log_execute_code VARCHAR(20) DEFAULT 'PADDING',"
                            + "job_log_cost DECIMAL(10,2),"
                            + "job_log_trigger_msg CLOB,"
                            + "job_log_trigger_param VARCHAR(512),"
                            + "job_log_trigger_address VARCHAR(255),"
                            + "job_log_file_path VARCHAR(500),"
                            + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                            + "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                            + "create_by VARCHAR(50),"
                            + "update_by VARCHAR(50)"
                            + ")",
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_job_log_no ON " + tableName + " (job_log_no)",
                    "CREATE INDEX IF NOT EXISTS idx_trigger_date ON " + tableName + " (job_log_trigger_date)",
                    "CREATE INDEX IF NOT EXISTS idx_trigger_bean ON " + tableName + " (job_log_trigger_bean)",
                    "CREATE INDEX IF NOT EXISTS idx_job_no ON " + tableName + " (job_no)"
            );
        }
        if (SysJobLogDetail.class == entityType) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "job_log_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                            + "job_log_id INT,"
                            + "job_log_no VARCHAR(64),"
                            + "job_id INT,"
                            + "job_no VARCHAR(64),"
                            + "job_log_detail_level VARCHAR(20) DEFAULT 'INFO',"
                            + "job_log_detail_content CLOB,"
                            + "job_log_detail_time TIMESTAMP NULL,"
                            + "job_log_detail_phase VARCHAR(50),"
                            + "job_log_detail_progress INT,"
                            + "job_log_detail_file_path VARCHAR(500),"
                            + "job_log_detail_handler VARCHAR(255),"
                            + "job_log_detail_profile VARCHAR(100),"
                            + "job_log_detail_address VARCHAR(255),"
                            + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                            + ")",
                    "CREATE INDEX IF NOT EXISTS idx_job_log_id ON " + tableName + " (job_log_id)",
                    "CREATE INDEX IF NOT EXISTS idx_job_id ON " + tableName + " (job_id)",
                    "CREATE INDEX IF NOT EXISTS idx_job_no ON " + tableName + " (job_no)",
                    "CREATE INDEX IF NOT EXISTS idx_detail_time ON " + tableName + " (job_log_detail_time)",
                    "CREATE INDEX IF NOT EXISTS idx_level ON " + tableName + " (job_log_detail_level)"
            );
        }
        if (SysJobLogBackup.class == entityType) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "job_log_backup_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                            + "job_log_backup_file_name VARCHAR(255),"
                            + "job_log_backup_file_path VARCHAR(500),"
                            + "job_log_backup_file_size BIGINT,"
                            + "job_log_backup_count BIGINT,"
                            + "job_log_backup_start_date DATE,"
                            + "job_log_backup_end_date DATE,"
                            + "job_log_backup_status VARCHAR(20) DEFAULT 'RUNNING',"
                            + "job_log_backup_type VARCHAR(20) DEFAULT 'MANUAL',"
                            + "job_log_backup_start_time TIMESTAMP NULL,"
                            + "job_log_backup_end_time TIMESTAMP NULL,"
                            + "job_log_backup_cost BIGINT,"
                            + "job_log_backup_message VARCHAR(1000),"
                            + "job_log_backup_compress_type VARCHAR(20) DEFAULT 'ZIP',"
                            + "job_log_backup_cleaned TINYINT DEFAULT 0,"
                            + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                            + ")",
                    "CREATE INDEX IF NOT EXISTS idx_backup_status ON " + tableName + " (job_log_backup_status)",
                    "CREATE INDEX IF NOT EXISTS idx_backup_type ON " + tableName + " (job_log_backup_type)",
                    "CREATE INDEX IF NOT EXISTS idx_backup_start_time ON " + tableName + " (job_log_backup_start_time)"
            );
        }
        throw new IllegalArgumentException("不支持的 Job 实体类型: " + entityType.getName());
    }

    private void updateOrCreate(JdbcTableExecutor executor,
                                DataSource dataSource,
                                TableMetadata metadata,
                                Class<?> entityType,
                                String tableName,
                                JobSchemaInitMode initMode) {
        ErrorResult<String> result = executor.doIt(metadata, ActionType.UPDATE);
        if (result == null || !result.hasError()) {
            upgradeLegacySchema(dataSource, entityType, tableName);
            log.info("[Job] 任务表初始化完成: table={}, mode={}", tableName, initMode);
            return;
        }
        if (isAlreadyExists(result)) {
            upgradeLegacySchema(dataSource, entityType, tableName);
            validateExistingTable(dataSource, metadata);
            log.info("[Job] 任务表已存在，JdbcEngine UPDATE 已退化为结构校验通过: table={}, mode={}", tableName, initMode);
            return;
        }
        if (isTableMissing(result)) {
            execute(executor, metadata, ActionType.CREATE, "Job 表初始化失败");
            upgradeLegacySchema(dataSource, entityType, tableName);
            log.info("[Job] 任务表初始化完成: table={}, mode={}", tableName, initMode);
            return;
        }
        throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", error=" + result.getFirstError());
    }

    private boolean isAlreadyExists(ErrorResult<String> result) {
        String message = result == null ? null : result.getFirstError();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("already exists")
                || normalized.contains("已存在")
                || normalized.contains("duplicate");
    }

    private boolean isTableMissing(ErrorResult<String> result) {
        String message = result == null ? null : result.getFirstError();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("doesn't exist")
                || normalized.contains("does not exist")
                || normalized.contains("not exist")
                || normalized.contains("unknown table")
                || normalized.contains("不存在")
                || normalized.contains("not found");
    }

    private void validateExistingTable(DataSource dataSource, TableMetadata metadata) {
        Set<String> existingColumns = readExistingColumns(dataSource, metadata.getName());
        List<String> missingColumns = metadata.getColumnMetadata().stream()
                .map(column -> normalizeColumnName(column.getColumnName()))
                .filter(columnName -> !existingColumns.contains(columnName))
                .toList();
        if (!missingColumns.isEmpty()) {
            throw new IllegalStateException("Job 表初始化失败: table=" + metadata.getName()
                    + ", 缺少字段=" + missingColumns);
        }
        upgradeLegacyColumnTypes(dataSource, metadata.getName());
    }

    private void upgradeLegacySchema(DataSource dataSource, Class<?> entityType, String tableName) {
        Map<String, String> columnTypes = readExistingColumnTypes(dataSource, tableName);
        List<String> upgradeSql = new ArrayList<>();
        if (SysJob.class == entityType) {
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_no",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '任务编号' AFTER `job_id`");
            collectBigintUpgradeSql(upgradeSql, tableName, columnTypes, "job_trigger_last_time");
            collectBigintUpgradeSql(upgradeSql, tableName, columnTypes, "job_trigger_next_time");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_dispatch_mode",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_dispatch_mode` VARCHAR(20) NULL DEFAULT 'LOCAL' COMMENT '分发模式' AFTER `job_glue_type`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_remote_executor_address",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_remote_executor_address` VARCHAR(500) NULL DEFAULT NULL COMMENT '远程执行器地址' AFTER `job_dispatch_mode`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_storage_mode",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_storage_mode` VARCHAR(20) NULL DEFAULT 'DATABASE' COMMENT '存储模式' AFTER `job_remote_executor_address`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_retry_interval",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_retry_interval` INT NULL DEFAULT 0 COMMENT '失败重试间隔(秒)' AFTER `job_fail_retry`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_exception_callback_bean",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_exception_callback_bean` VARCHAR(255) NULL DEFAULT NULL COMMENT '异常回调处理器' AFTER `job_execute_param`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_retry_callback_bean",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_retry_callback_bean` VARCHAR(255) NULL DEFAULT NULL COMMENT '重试前回调处理器' AFTER `job_exception_callback_bean`");
        }
        if (SysJobLog.class == entityType) {
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_no",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '日志编号' AFTER `job_log_id`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_id",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_id` INT NULL DEFAULT NULL COMMENT '任务ID' AFTER `job_log_no`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_no",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '任务编号' AFTER `job_id`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_profile",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_profile` VARCHAR(50) NULL DEFAULT NULL COMMENT '环境' AFTER `job_log_trigger_type`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_trigger_address",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_trigger_address` VARCHAR(255) NULL DEFAULT NULL COMMENT '触发地址' AFTER `job_log_trigger_param`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_file_path",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_file_path` VARCHAR(500) NULL DEFAULT NULL COMMENT '日志文件路径' AFTER `job_log_trigger_address`");
        }
        if (SysJobLogDetail.class == entityType) {
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_no",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '任务日志编号' AFTER `job_log_id`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_no",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '任务编号' AFTER `job_id`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_detail_profile",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_detail_profile` VARCHAR(100) NULL DEFAULT NULL COMMENT '执行环境/Profile' AFTER `job_log_detail_handler`");
            collectMissingColumnSql(upgradeSql, tableName, columnTypes,
                    "job_log_detail_address",
                    "ALTER TABLE `" + tableName + "` ADD COLUMN `job_log_detail_address` VARCHAR(255) NULL DEFAULT NULL COMMENT '执行地址' AFTER `job_log_detail_profile`");
        }
        if (upgradeSql.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : upgradeSql) {
                statement.execute(sql);
            }
            backfillLegacyNumbers(statement, entityType, tableName, columnTypes);
            ensureLegacyIndexes(connection, statement, entityType, tableName);
        } catch (SQLException e) {
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 升级历史表结构失败", e);
        }

        log.info("[Job] 任务表历史结构已升级: table={}, sqlCount={}", tableName, upgradeSql.size());
    }

    private Set<String> readExistingColumns(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> columns = new LinkedHashSet<>(readColumns(metaData, connection.getCatalog(), tableName));
            if (!columns.isEmpty()) {
                return columns;
            }
            columns.addAll(readColumns(metaData, null, tableName));
            if (!columns.isEmpty()) {
                return columns;
            }
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 读取现有表结构为空");
        } catch (SQLException e) {
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 无法读取现有表结构", e);
        }
    }

    /**
     * 兼容历史上用 int 存储毫秒时间戳的表结构。
     * <p>
     * 旧表的 job_trigger_last_time / job_trigger_next_time 一旦仍是 int，
     * 平台在计算下一次触发时间时就会溢出。这里在启动阶段探测并自动升为 bigint，
     * 让命名空间老表可以平滑升级到当前实现。
     * </p>
     */
    private void upgradeLegacyColumnTypes(DataSource dataSource, String tableName) {
        Map<String, String> columnTypes = readExistingColumnTypes(dataSource, tableName);
        List<String> upgradeSql = new ArrayList<>();
        collectBigintUpgradeSql(upgradeSql, tableName, columnTypes, "job_trigger_last_time");
        collectBigintUpgradeSql(upgradeSql, tableName, columnTypes, "job_trigger_next_time");
        if (upgradeSql.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : upgradeSql) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 升级字段类型失败", e);
        }

        log.info("[Job] 任务表字段类型已升级: table={}, columns={}", tableName, List.of("job_trigger_last_time", "job_trigger_next_time"));
    }

    private void collectBigintUpgradeSql(List<String> upgradeSql,
                                         String tableName,
                                         Map<String, String> columnTypes,
                                         String columnName) {
        String type = columnTypes.get(normalizeColumnName(columnName));
        if (type == null || type.isBlank() || type.contains("bigint")) {
            return;
        }
        upgradeSql.add("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + columnName + "` BIGINT NULL DEFAULT 0");
    }

    private void collectMissingColumnSql(List<String> upgradeSql,
                                         String tableName,
                                         Map<String, String> columnTypes,
                                         String columnName,
                                         String sql) {
        if (columnTypes.containsKey(normalizeColumnName(columnName))) {
            return;
        }
        upgradeSql.add(sql);
    }

    private void backfillLegacyNumbers(Statement statement,
                                       Class<?> entityType,
                                       String tableName,
                                       Map<String, String> columnTypes) throws SQLException {
        if (SysJob.class == entityType) {
            statement.executeUpdate("UPDATE `" + tableName + "` SET `job_no` = CONCAT('JOB', LPAD(CAST(`job_id` AS CHAR), 12, '0')) WHERE `job_no` IS NULL OR `job_no` = ''");
        }
        if (SysJobLog.class == entityType) {
            statement.executeUpdate("UPDATE `" + tableName + "` SET `job_log_no` = CONCAT('JOBLOG', LPAD(CAST(`job_log_id` AS CHAR), 12, '0')) WHERE `job_log_no` IS NULL OR `job_log_no` = ''");
            statement.executeUpdate("UPDATE `" + tableName + "` SET `job_no` = CONCAT('JOB', LPAD(CAST(`job_id` AS CHAR), 12, '0')) WHERE (`job_no` IS NULL OR `job_no` = '') AND `job_id` IS NOT NULL");
        }
        if (SysJobLogDetail.class == entityType) {
            statement.executeUpdate("UPDATE `" + tableName + "` SET `job_no` = CONCAT('JOB', LPAD(CAST(`job_id` AS CHAR), 12, '0')) WHERE (`job_no` IS NULL OR `job_no` = '') AND `job_id` IS NOT NULL");
        }
    }

    private void ensureLegacyIndexes(Connection connection,
                                     Statement statement,
                                     Class<?> entityType,
                                     String tableName) throws SQLException {
        Set<String> indexes = readExistingIndexes(connection, tableName);
        if (SysJob.class == entityType) {
            createIndexIfMissing(statement, indexes, tableName, "uk_job_no", "CREATE UNIQUE INDEX `uk_job_no` ON `" + tableName + "` (`job_no`)");
            createIndexIfMissing(statement, indexes, tableName, "idx_job_dispatch_mode", "CREATE INDEX `idx_job_dispatch_mode` ON `" + tableName + "` (`job_dispatch_mode`)");
        }
        if (SysJobLog.class == entityType) {
            createIndexIfMissing(statement, indexes, tableName, "uk_job_log_no", "CREATE UNIQUE INDEX `uk_job_log_no` ON `" + tableName + "` (`job_log_no`)");
            createIndexIfMissing(statement, indexes, tableName, "idx_job_no", "CREATE INDEX `idx_job_no` ON `" + tableName + "` (`job_no`)");
        }
        if (SysJobLogDetail.class == entityType) {
            createIndexIfMissing(statement, indexes, tableName, "idx_job_no", "CREATE INDEX `idx_job_no` ON `" + tableName + "` (`job_no`)");
        }
    }

    private void createIndexIfMissing(Statement statement,
                                      Set<String> indexes,
                                      String tableName,
                                      String indexName,
                                      String sql) throws SQLException {
        if (indexes.contains(normalizeColumnName(indexName))) {
            return;
        }
        statement.execute(sql);
        log.info("[Job] 已补历史索引: table={}, index={}", tableName, indexName);
    }

    private Map<String, String> readExistingColumnTypes(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, String> columnTypes = new LinkedHashMap<>(readColumnTypes(metaData, connection.getCatalog(), tableName));
            if (!columnTypes.isEmpty()) {
                return columnTypes;
            }
            columnTypes.putAll(readColumnTypes(metaData, null, tableName));
            if (!columnTypes.isEmpty()) {
                return columnTypes;
            }
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 读取现有字段类型为空");
        } catch (SQLException e) {
            throw new IllegalStateException("Job 表初始化失败: table=" + tableName + ", 无法读取现有字段类型", e);
        }
    }

    private Set<String> readColumns(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(normalizeColumnName(resultSet.getString("COLUMN_NAME")));
            }
        }
        return columns;
    }

    private Map<String, String> readColumnTypes(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, null)) {
            while (resultSet.next()) {
                columnTypes.put(normalizeColumnName(resultSet.getString("COLUMN_NAME")),
                        normalizeColumnType(resultSet.getString("TYPE_NAME")));
            }
        }
        return columnTypes;
    }

    private Set<String> readExistingIndexes(Connection connection, String tableName) throws SQLException {
        Set<String> indexes = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        indexes.addAll(readIndexes(metaData, connection.getCatalog(), tableName));
        if (!indexes.isEmpty()) {
            return indexes;
        }
        indexes.addAll(readIndexes(metaData, null, tableName));
        return indexes;
    }

    private Set<String> readIndexes(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        Set<String> indexes = new LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getIndexInfo(catalog, null, tableName, false, false)) {
            while (resultSet.next()) {
                indexes.add(normalizeColumnName(resultSet.getString("INDEX_NAME")));
            }
        }
        return indexes;
    }

    private String normalizeColumnName(String columnName) {
        return columnName == null ? null : columnName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeColumnType(String columnType) {
        return columnType == null ? null : columnType.trim().toLowerCase(Locale.ROOT);
    }

    private String detectDatabaseProductName(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("Job 表初始化失败: 无法识别数据库类型", e);
        }
    }

    private boolean isH2Database(String databaseProductName) {
        if (databaseProductName == null) {
            return false;
        }
        return databaseProductName.trim().toLowerCase(Locale.ROOT).contains("h2");
    }
}

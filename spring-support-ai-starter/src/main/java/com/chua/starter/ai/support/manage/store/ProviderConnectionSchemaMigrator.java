package com.chua.starter.ai.support.manage.store;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 渠道连接表结构轻量迁移器。
 *
 * <p>为 {@code sys_ai_provider_connection} 增加<b>归属用户</b>列：
 * {@code sys_ai_provider_connection_user_id}（账号隔离：每个用户只看到/操作自己绑定的账号），
 * 并补联合索引 {@code idx_ai_provider_conn_user}（providerId + userId），
 * 供 {@code collectAllKeys} 按当前用户过滤连接时走索引。</p>
 *
 * <p>应用启动后通过 JDBC 幂等执行；当数据库暂不可达时后台每 30 秒重试，
 * 成功后自动停止，避免因瞬时连接问题导致列缺失。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderConnectionSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "sys_ai_provider_connection";

    /**
     * 迁移目标列
     */
    private static final String COLUMN = "sys_ai_provider_connection_user_id";

    /**
     * 归属用户列定义（AFTER 定位到 label 之后）
     */
    private static final String COLUMN_DEF = "VARCHAR(64) NULL COMMENT '归属用户（账号隔离）' AFTER `sys_ai_provider_connection_label`";

    /**
     * 待补的索引名
     */
    private static final String INDEX = "idx_ai_provider_conn_user";

    /**
     * 数据源
     */
    private final DataSource dataSource;

    /**
     * 重试调度器（懒初始化）
     */
    private ScheduledExecutorService retryExecutor;

    @Override
    public void run(ApplicationArguments args) {
        tryMigrate();
    }

    /**
     * 尝试执行一次迁移，失败则安排重试。
     *
     * <p>安全豁免：DDL 的表名、列名和索引名均来自本类常量；动态列名先经
     * {@link SqlName#checkSimple(String, String)} 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            final String column = SqlName.checkSimple(COLUMN, "渠道连接列名");
            final String index = SqlName.checkSimple(INDEX, "渠道连接索引名");
            final String table = SqlName.checkSimple(TABLE, "渠道连接表名");
            if (!columnExists(connection, catalog, column)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + COLUMN_DEF);
                }
                log.info("[ProviderConnSchema] 已添加列 {}.{}", catalog, column);
            } else {
                log.debug("[ProviderConnSchema] 列已存在 {}.{}", catalog, column);
            }
            if (!indexExists(connection, catalog, table, index)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD INDEX `" + index
                            + "` (`" + column + "`)");
                }
                log.info("[ProviderConnSchema] 已添加索引 {}.{}", catalog, index);
            } else {
                log.debug("[ProviderConnSchema] 索引已存在 {}.{}", catalog, index);
            }
            stopRetry();
        } catch (Exception e) {
            log.warn("[ProviderConnSchema] 迁移暂不可用，将在 30 秒后重试: {}", e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 判断指定列是否已存在。
     * @param catalog 字符串
     * @param column 字符串
     * @param connection 参数
     * @return 返回值
     * @throws Exception 异常
    */
    private static boolean columnExists(Connection connection, String catalog, String column) throws Exception {
        final String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, TABLE);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 判断指定索引是否已存在。
     * @param catalog 字符串
     * @param connection 参数
     * @param index 字符串
     * @param table 字符串
     * @return 返回值
     * @throws Exception 异常
    */
    private static boolean indexExists(Connection connection, String catalog, String table, String index) throws Exception {
        final String sql = "SELECT 1 FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, table);
            ps.setString(3, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 安排一次重试（30 秒后），复用单例调度器。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "provider-conn-schema-retry");
                thread.setDaemon(true);
                return thread;
            });
        }
        retryExecutor.schedule(this::tryMigrate, 30, TimeUnit.SECONDS);
    }

    /**
     * 停止重试并关闭调度器。
     */
    private synchronized void stopRetry() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
            retryExecutor = null;
        }
    }
}
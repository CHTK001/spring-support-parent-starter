package com.chua.starter.server.support.migrator;

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
 * 主机 Docker 通道配置列轻量迁移器
 *
 * <p>为 {@code server_host} 幂等补齐 Docker 操作通道两列：
 * {@code server_host_docker_mode}（CMD = SSH + docker CLI / API = Docker Engine HTTP API）
 * 与 {@code server_host_docker_api_url}（API 通道地址）。</p>
 *
 * <p><b>为什么 DDL 还要运行期兜底</b>：{@code db/init/V3.1} 建表时已包含这两列，
 * 但「表在早期版本里先建过、再补列」的库无法靠 {@code CREATE TABLE IF NOT EXISTS}
 * 补列；运行期补丁对两种库都幂等生效。应用启动后通过 JDBC 幂等执行；
 * 当数据库暂不可达时后台每 30 秒重试，成功后自动停止。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerHostDockerSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "server_host";

    /**
     * Docker 通道列名
     */
    private static final String MODE_COLUMN = "server_host_docker_mode";

    /**
     * Docker Engine API 地址列名
     */
    private static final String API_URL_COLUMN = "server_host_docker_api_url";

    /**
     * Docker 通道列定义（AFTER 定位在运行时间秒之后）
     */
    private static final String MODE_COLUMN_DEF =
            "VARCHAR(8) NULL DEFAULT 'CMD' COMMENT 'Docker操作通道: CMD=SSH+docker CLI / API=Docker Engine HTTP API' AFTER `server_host_uptime_sec`";

    /**
     * Docker Engine API 地址列定义
     */
    private static final String API_URL_COLUMN_DEF =
            "VARCHAR(255) NULL DEFAULT NULL COMMENT 'Docker Engine API地址(docker_mode=API时生效)' AFTER `" + MODE_COLUMN + "`";

    /**
     * 重试间隔（秒）
     */
    private static final long RETRY_SECONDS = 30L;

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
     * <p>安全豁免：DDL 的表名、列名均来自本类常量；动态列名先经
     * {@link SqlName#checkSimple(String, String)} 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            final String table = SqlName.checkSimple(TABLE, "主机表名");
            final String modeColumn = SqlName.checkSimple(MODE_COLUMN, "Docker通道列名");
            final String apiUrlColumn = SqlName.checkSimple(API_URL_COLUMN, "Docker API地址列名");
            if (!tableExists(connection, catalog, table)) {
                // 表还没建（V3.1 未执行）时不做 ALTER，等建表脚本落地后再补
                log.info("[ServerHostDockerSchema] 表 {} 尚不存在，等待迁移脚本建表", table);
                stopRetry();
                return;
            }
            addColumnIfAbsent(connection, catalog, table, modeColumn, MODE_COLUMN_DEF);
            addColumnIfAbsent(connection, catalog, table, apiUrlColumn, API_URL_COLUMN_DEF);
            stopRetry();
        } catch (Exception e) {
            log.warn("[ServerHostDockerSchema] 迁移暂不可用，将在 {} 秒后重试: {}", RETRY_SECONDS, e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 列不存在时执行 ALTER 补列。
     *
     * @param connection 连接
     * @param catalog    库名
     * @param table      表名
     * @param column     列名
     * @param columnDef  列定义（含类型、默认值与 AFTER 定位）
     * @throws Exception 查询或执行异常
     */
    private static void addColumnIfAbsent(Connection connection, String catalog, String table,
                                          String column, String columnDef) throws Exception {
        if (columnExists(connection, catalog, table, column)) {
            log.debug("[ServerHostDockerSchema] 列已存在 {}.{}.{}", catalog, table, column);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + columnDef);
        }
        log.info("[ServerHostDockerSchema] 已添加列 {}.{}.{}", catalog, table, column);
    }

    /**
     * 判断指定表是否已存在。
     *
     * @param connection 连接
     * @param catalog    库名
     * @param table      表名
     * @return true 表示存在
     * @throws Exception 查询异常
     */
    private static boolean tableExists(Connection connection, String catalog, String table) throws Exception {
        final String sql = "SELECT 1 FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 判断指定列是否已存在。
     *
     * @param connection 连接
     * @param catalog    库名
     * @param table      表名
     * @param column     列名
     * @return true 表示存在
     * @throws Exception 查询异常
     */
    private static boolean columnExists(Connection connection, String catalog, String table, String column)
            throws Exception {
        final String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, table);
            ps.setString(3, column);
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
// [P3C C1 豁免] 场景化创建线程，需文档化
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                Thread thread = new Thread(r, "server-host-docker-schema-retry");
                thread.setDaemon(true);
                return thread;
            });
        }
        retryExecutor.schedule(this::tryMigrate, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停止重试调度器。
     */
    private synchronized void stopRetry() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
            retryExecutor = null;
        }
    }
}

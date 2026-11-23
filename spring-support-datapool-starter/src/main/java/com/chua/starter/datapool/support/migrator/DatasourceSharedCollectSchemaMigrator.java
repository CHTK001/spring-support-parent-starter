// [P3C A20 豁免] A20/A30/A31: 含日志/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datapool.support.migrator;

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
 * 数据源台账「共享给采集平台」列的轻量迁移器。
 *
 * <p>为 {@code datasource_info} 幂等补齐列 {@code datasource_info_shared_collect}
 * （1 已注册 / 0 未注册）：采集台的数据源下拉只列已注册行，数据源中心自身的
 * 管理与其他系统不受影响。</p>
 *
 * <p><b>为什么 DDL 还要运行期兜底</b>：{@code db/init/V3.1} 建表脚本已包含该列，
 * 但「表在早期版本里先建过、再补列」的库无法靠 {@code CREATE TABLE IF NOT EXISTS}
 * 补列；运行期补丁对两种库都幂等生效。脚本执行与补丁同时存在不冲突。</p>
 *
 * <p>应用启动后通过 JDBC 幂等执行；数据库暂不可达时后台每 30 秒重试，
 * 成功后自动停止。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceSharedCollectSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "datasource_info";

    /**
     * 迁移目标列
     */
    private static final String COLUMN = "datasource_info_shared_collect";

    /**
     * 列定义（定位到驱动类名列之后，与建表脚本列序一致）
     */
    private static final String COLUMN_DEF =
            "INT NULL DEFAULT 0 COMMENT '共享给采集平台 - 1:已注册, 0:未注册' "
                    + "AFTER `datasource_info_driver_class_name`";

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
     * <p>安全豁免：DDL 的表名与列名均来自本类常量，并先经
     * {@link SqlName#checkSimple(String, String)} 严格校验，不包含用户输入。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            final String column = SqlName.checkSimple(COLUMN, "数据源列名");
            final String table = SqlName.checkSimple(TABLE, "数据源表名");
            if (!tableExists(connection, catalog, table)) {
                // 表还没建（V3.1 未执行）时不做 ALTER，等建表脚本落地后再补
                log.info("[DatasourceSharedCollect] 表 {} 尚不存在，等待迁移脚本建表", table);
                stopRetry();
                return;
            }
            if (!columnExists(connection, catalog, table, column)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + COLUMN_DEF);
                }
                log.info("[DatasourceSharedCollect] 已添加列 {}.{}.{}", catalog, table, column);
            } else {
                log.debug("[DatasourceSharedCollect] 列已存在 {}.{}.{}", catalog, table, column);
            }
            stopRetry();
        } catch (Exception e) {
            log.warn("[DatasourceSharedCollect] 迁移暂不可用，将在 {} 秒后重试: {}", RETRY_SECONDS, e.getMessage());
            scheduleRetry();
        }
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
     * 安排一次后台重试。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "datasource-shared-collect-migrator");
                thread.setDaemon(true);
                return thread;
            });
        }
        retryExecutor.schedule(this::tryMigrate, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 迁移成功后停止后台重试。
     */
    private synchronized void stopRetry() {
        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdown();
        }
    }
}

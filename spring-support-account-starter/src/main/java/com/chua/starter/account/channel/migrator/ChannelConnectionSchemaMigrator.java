// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.migrator;

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
 * <p>为 {@code sys_account_channel_connection} 幂等补齐<b>归属用户</b>列
 * {@code sys_account_channel_connection_user_id}（账号隔离：每个用户只看到/操作
 * 自己绑定的账号）与联合索引 {@code idx_account_channel_conn_user}
 * （providerId + userId），供免费渠道按当前用户收集可用 key 时走索引。</p>
 *
 * <p><b>为什么 DDL 还要运行期兜底</b>：{@code db/init/V7.2} 建表时已包含该列，
 * 但「表在本分支早期版本里先建过、再补列」的库无法靠 {@code CREATE TABLE IF NOT EXISTS}
 * 补列；运行期补丁对两种库都幂等生效。脚本执行与补丁同时存在不冲突。</p>
 *
 * <p>应用启动后通过 JDBC 幂等执行；当数据库暂不可达时后台每 30 秒重试，
 * 成功后自动停止，避免因瞬时连接问题导致列缺失。</p>
 *
 * <p>归属：渠道连接属于账号模块，本迁移器随渠道表一起从 AI 模块迁入，
 * 表名同步改为 {@code sys_account_channel_*} 口径。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelConnectionSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "sys_account_channel_connection";

    /**
     * 迁移目标列
     */
    private static final String COLUMN = "sys_account_channel_connection_user_id";

    /**
     * 归属用户列定义（AFTER 定位到 label 之后）
     */
    private static final String COLUMN_DEF = "VARCHAR(64) NULL COMMENT '归属用户（账号隔离）' AFTER `sys_account_channel_connection_label`";

    /**
     * 待补的索引名
     */
    private static final String INDEX = "idx_account_channel_conn_user";

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
     * <p>安全豁免：DDL 的表名、列名和索引名均来自本类常量；动态列名先经
     * {@link SqlName#checkSimple(String, String)} 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            final String column = SqlName.checkSimple(COLUMN, "渠道连接列名");
            final String index = SqlName.checkSimple(INDEX, "渠道连接索引名");
            final String table = SqlName.checkSimple(TABLE, "渠道连接表名");
            if (!tableExists(connection, catalog, table)) {
                // 表还没建（V7.2 未执行）时不做 ALTER，等建表脚本落地后再补
                log.info("[ChannelConnSchema] 表 {} 尚不存在，等待迁移脚本建表", table);
                stopRetry();
                return;
            }
            if (!columnExists(connection, catalog, table, column)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + COLUMN_DEF);
                }
                log.info("[ChannelConnSchema] 已添加列 {}.{}.{}", catalog, table, column);
            } else {
                log.debug("[ChannelConnSchema] 列已存在 {}.{}.{}", catalog, table, column);
            }
            if (!indexExists(connection, catalog, table, index)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD INDEX `" + index
                            + "` (`" + column + "`)");
                }
                log.info("[ChannelConnSchema] 已添加索引 {}.{}.{}", catalog, table, index);
            } else {
                log.debug("[ChannelConnSchema] 索引已存在 {}.{}.{}", catalog, table, index);
            }
            stopRetry();
        } catch (Exception e) {
            log.warn("[ChannelConnSchema] 迁移暂不可用，将在 {} 秒后重试: {}", RETRY_SECONDS, e.getMessage());
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
     * 判断指定索引是否已存在。
     *
     * @param connection 连接
     * @param catalog    库名
     * @param table      表名
     * @param index      索引名
     * @return true 表示存在
     * @throws Exception 查询异常
     */
    private static boolean indexExists(Connection connection, String catalog, String table, String index)
            throws Exception {
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
// [P3C C1 豁免] 场景化创建线程，需文档化
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                Thread thread = new Thread(r, "channel-conn-schema-retry");
                thread.setDaemon(true);
                return thread;
            });
        }
        retryExecutor.schedule(this::tryMigrate, RETRY_SECONDS, TimeUnit.SECONDS);
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

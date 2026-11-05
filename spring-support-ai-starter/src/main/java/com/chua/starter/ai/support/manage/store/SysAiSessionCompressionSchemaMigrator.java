// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * AI 会话压缩表结构轻量迁移器。
 *
 * <p>为 {@code sys_ai_session_compression} 增加<b>压缩验证基线</b>列：
 * {@code sys_ai_compression_baseline}（首次压缩结果永久保留，后续每次压缩前与基线融合
 * 生成新摘要，避免关键信息在多次压缩中丢失），保证强光模式下压缩验证能力可靠落地。</p>
 *
 * <p>应用启动后通过 JDBC 幂等执行；当数据库暂不可达时后台每 30 秒重试，
 * 成功后自动停止，避免因瞬时连接问题导致列缺失。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAiSessionCompressionSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "sys_ai_session_compression";

    /**
     * 迁移目标列
     */
    private static final String COLUMN = "sys_ai_compression_baseline";

    /**
     * 归属用户列定义（AFTER 定位到压缩上下文列之后）
     */
    private static final String COLUMN_DEF = "LONGTEXT NULL COMMENT '首次压缩结果（压缩验证基线）' AFTER `sys_ai_compression_context`";

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
     * <p>安全豁免：DDL 的表名、列名来自本类常量；动态列名先经 {@link SqlName#checkSimple(String, String)}
     * 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            final String column = SqlName.checkSimple(COLUMN, "压缩基矢列名");
            final String table = SqlName.checkSimple(TABLE, "压缩基矢表名");
            if (!columnExists(connection, catalog, column)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + COLUMN_DEF);
                }
                log.info("[CompressionSchema] 已添加列 {}.{}", catalog, column);
            } else {
                log.debug("[CompressionSchema] 列已存在 {}.{}", catalog, column);
            }
            stopRetry();
        } catch (Exception e) {
            log.warn("[CompressionSchema] 迁移暂不可用，将在 30 秒后重试: {}", e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 判断指定列是否已存在。
     *
     * @param catalog 目录（数据库名）
     * @param column 列名
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
     * 安排一次重试（30 秒后），复用单例调度器。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null) {
// [P3C C1 豁免] 场景化创建线程，需文档化
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                Thread thread = new Thread(r, "compression-schema-retry");
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
        }    }}
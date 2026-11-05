// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 角色（助手）表结构轻量迁移器。
 *
 * <p>为 {@code sys_ai_role} 增加以下列：</p>
 * <ul>
 *   <li>{@code sys_ai_role_avatar}：角色头像访问路径；</li>
 *   <li>{@code sys_ai_role_settings}：角色完整的工具与能力配置 JSON。</li>
 * </ul>
 *
 * <p>应用启动后通过 JDBC 幂等执行；当数据库暂不可达（如 VPN / 内网抖动）时，
 * 后台每 30 秒重试，成功后自动停止，避免因瞬时连接问题导致列缺失。</p>
 *
 * @since 4.0.0.42
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标列：列名 → 列定义（含 AFTER 定位）。
     */
    private static final Map<String, String> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("sys_ai_role_avatar",
                "VARCHAR(255) NULL COMMENT '头像访问路径' AFTER `sys_ai_role_model`");
        COLUMNS.put("sys_ai_role_settings",
                "LONGTEXT NULL COMMENT '角色完整工具与能力配置JSON' AFTER `sys_ai_role_temperature`");
    }

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
     * <p>安全豁免：DDL 的表名、列名和类型均来自本类常量；动态列名先经
     * {@link SqlName#checkSimple(String, String)} 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String catalog = connection.getCatalog();
            for (Map.Entry<String, String> entry : COLUMNS.entrySet()) {
                final String column = SqlName.checkSimple(entry.getKey(), "AI 角色列名");
                if (!columnExists(connection, catalog, column)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE `sys_ai_role` ADD COLUMN `" + column + "` "
                                + entry.getValue());
                    }
                    log.info("[RoleSchema] 已添加列 {}.{}", catalog, column);
                } else {
                    log.debug("[RoleSchema] 列已存在 {}.{}", catalog, column);
                }
            }
            stopRetry();
        } catch (Exception e) {
            log.warn("[RoleSchema] 迁移暂不可用，将在 30 秒后重试: {}", e.getMessage());
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
    private boolean columnExists(Connection connection, String catalog, String column) throws Exception {
        final String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'sys_ai_role' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 安排后台重试（幂等，只创建一个调度任务）。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null) {
// [P3C C1 豁免] 场景化创建线程，需文档化
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                Thread t = new Thread(r, "role-schema-migrator");
                t.setDaemon(true);
                return t;
            });
            retryExecutor.scheduleWithFixedDelay(this::tryMigrate, 30, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * 迁移成功后关闭重试调度器。
     */
    private synchronized void stopRetry() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
            retryExecutor = null;
        }    }}
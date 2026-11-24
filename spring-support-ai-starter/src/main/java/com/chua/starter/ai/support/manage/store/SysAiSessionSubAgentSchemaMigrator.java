// [P3C A20 豁免] A20/A30/A31: 含日志/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.store;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 会话子代理运行记录表结构迁移器。
 *
 * <p>为「主会话 ↔ 子代理」绑定关系建持久化表 {@code sys_ai_session_sub_agent}
 * （与 {@code db/init/V7.5__init_all_ai.sql} 同代口径：{@code CREATE TABLE IF NOT EXISTS}
 * 幂等建表）。应用启动后通过 JDBC 执行；数据库暂不可达时后台每 30 秒重试，
 * 成功后自动停止，避免因瞬时连接问题导致表缺失。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAiSessionSubAgentSchemaMigrator implements ApplicationRunner {

    /**
     * 迁移目标表名
     */
    private static final String TABLE = "sys_ai_session_sub_agent";

    /**
     * 建表 DDL（幂等；列语义见 {@code SysAiSessionSubAgent} 实体）
     */
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` ("
            + "`sys_ai_sub_session_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '子会话记录主键',"
            + "`sys_ai_session_id` BIGINT NOT NULL COMMENT '主会话标识(sys_ai_session)',"
            + "`agent_id` VARCHAR(128) NOT NULL COMMENT '子Agent标识(sys_ai_agent)',"
            + "`agent_name` VARCHAR(255) NULL COMMENT '子Agent名称快照',"
            + "`master_agent_id` VARCHAR(128) NULL COMMENT '所属主Agent标识快照',"
            + "`status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态:RUNNING/COMPLETED/FAILED/CLOSED',"
            + "`input` LONGTEXT NULL COMMENT '调用输入',"
            + "`output` LONGTEXT NULL COMMENT '调用输出',"
            + "`error` TEXT NULL COMMENT '失败原因',"
            + "`sys_user_id` VARCHAR(64) NULL COMMENT '归属用户',"
            + "`create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
            + "`update_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
            + "PRIMARY KEY (`sys_ai_sub_session_id`),"
            + "KEY `idx_sasa_session` (`sys_ai_session_id`),"
            + "KEY `idx_sasa_user` (`sys_user_id`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话子代理运行记录(主/子会话绑定)'";

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
     * 尝试执行一次建表，失败则安排重试。
     *
     * <p>安全豁免：DDL 的表名来自本类常量并经 {@link SqlName#checkSimple(String, String)}
     * 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            final String table = SqlName.checkSimple(TABLE, "子代理运行记录表名");
            if (!CREATE_TABLE_SQL.contains("`" + table + "`")) {
                // 防御：常量与校验名不一致时直接拒绝执行（正常不会发生）
                throw new IllegalStateException("DDL 表名与校验名不一致: " + table);
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE_SQL);
            }
            log.info("[SubAgentSchema] 表 {} 已就绪", table);
            stopRetry();
        } catch (Exception e) {
            log.warn("[SubAgentSchema] 建表暂不可用，将在 30 秒后重试: {}", e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 安排 30 秒后重试（数据库暂不可达场景）。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "sub-agent-schema-migrator");
                thread.setDaemon(true);
                return thread;
            });
        }
        retryExecutor.schedule(this::tryMigrate, 30, TimeUnit.SECONDS);
    }

    /**
     * 停止重试调度器。
     */
    private synchronized void stopRetry() {
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
        }
    }
}

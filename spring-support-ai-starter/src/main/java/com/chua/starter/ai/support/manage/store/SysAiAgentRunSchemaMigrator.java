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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI Agent 运行记录表结构迁移器。
 *
 * <p>为 Agent 运行观测体系建持久化表 {@code sys_ai_agent_run} / {@code sys_ai_agent_step} /
 * {@code sys_ai_agent_approval}（与 {@code db/init} 同代口径：{@code CREATE TABLE IF NOT EXISTS}
 * 幂等建表，DDL 与《AI 完整 Agent 体系设计方案》§5 一致）。应用启动后通过 JDBC 执行；
 * 数据库暂不可达时后台每 30 秒重试，成功后自动停止，避免因瞬时连接问题导致表缺失。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAiAgentRunSchemaMigrator implements ApplicationRunner {

    /**
     * 运行主表名
     */
    private static final String TABLE_RUN = "sys_ai_agent_run";

    /**
     * 步骤明细表名
     */
    private static final String TABLE_STEP = "sys_ai_agent_step";

    /**
     * 审批单据表名
     */
    private static final String TABLE_APPROVAL = "sys_ai_agent_approval";

    /**
     * 运行主表 DDL（幂等）
     */
    private static final String CREATE_RUN_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_RUN + "` ("
            + "`sys_ai_run_id`        VARCHAR(64)  NOT NULL COMMENT '运行ID(UUIDv7)',"
            + "`sys_ai_session_id`    BIGINT       NULL COMMENT '关联会话(可空=后台运行)',"
            + "`agent_id`             VARCHAR(64)  NOT NULL COMMENT 'Agent标识(sys_ai_agent)',"
            + "`trigger_type`         VARCHAR(32)  NOT NULL COMMENT '触发: chat/manual/orchestration/schedule/eval',"
            + "`mode`                 VARCHAR(32)  NULL COMMENT '执行模式: SINGLE/ROUTER/PIPELINE/FAN_OUT/...',"
            + "`status`               VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELED',"
            + "`input_summary`        TEXT         NULL COMMENT '输入摘要',"
            + "`output_summary`       TEXT         NULL COMMENT '输出摘要',"
            + "`error`                TEXT         NULL COMMENT '失败原因',"
            + "`tokens_used`          BIGINT       NOT NULL DEFAULT 0,"
            + "`rounds`               INT          NOT NULL DEFAULT 0,"
            + "`tool_calls`           INT          NOT NULL DEFAULT 0,"
            + "`sub_session_id`       BIGINT       NULL COMMENT '关联子代理运行记录(sys_ai_session_sub_agent)',"
            + "`sys_user_id`          VARCHAR(64)  NULL COMMENT '归属用户',"
            + "`started_at`           BIGINT       NOT NULL,"
            + "`ended_at`             BIGINT       NULL,"
            + "PRIMARY KEY (`sys_ai_run_id`),"
            + "KEY `idx_saar_session` (`sys_ai_session_id`),"
            + "KEY `idx_saar_agent` (`agent_id`, `started_at`),"
            + "KEY `idx_saar_user` (`sys_user_id`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 运行记录'";

    /**
     * 步骤明细表 DDL（幂等）
     */
    private static final String CREATE_STEP_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_STEP + "` ("
            + "`sys_ai_step_id`   VARCHAR(64) NOT NULL,"
            + "`sys_ai_run_id`    VARCHAR(64) NOT NULL,"
            + "`seq`              INT         NOT NULL COMMENT '步骤序号',"
            + "`type`             VARCHAR(32) NOT NULL COMMENT 'REASONING/TOOL_CALL/PLAN/SUB_AGENT/ERROR',"
            + "`name`             VARCHAR(255) NULL COMMENT '工具名/子代理名/节点名',"
            + "`arguments_json`   TEXT        NULL COMMENT '参数摘要(JSON, 截断)',"
            + "`result_summary`   TEXT        NULL COMMENT '结果摘要(截断)',"
            + "`status`           VARCHAR(32) NOT NULL DEFAULT 'OK' COMMENT 'OK/FAILED/SKIPPED',"
            + "`tokens`           BIGINT      NOT NULL DEFAULT 0,"
            + "`started_at`       BIGINT      NOT NULL,"
            + "`ended_at`         BIGINT      NULL,"
            + "PRIMARY KEY (`sys_ai_step_id`),"
            + "KEY `idx_saas_run` (`sys_ai_run_id`, `seq`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 运行步骤'";

    /**
     * 审批单据表 DDL（幂等）。
     *
     * <p>切片四审批闭环启用：{@code sys_ai_run_id} 可空（主聊天链路的确认不经
     * Run 体系，无运行记录）；补充会话 / 归属人 / replyId / toolCallId 关联列与
     * 决议说明、终态时间，支持「列表按本人过滤 + 按 replyId 回写 + 多标签页同步」。</p>
     */
    private static final String CREATE_APPROVAL_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_APPROVAL + "` ("
            + "`sys_ai_approval_id` VARCHAR(64) NOT NULL,"
            + "`sys_ai_run_id`      VARCHAR(64) NULL COMMENT '关联运行(主聊天链路确认无 Run 时空)',"
            + "`sys_ai_step_id`     VARCHAR(64) NULL,"
            + "`sys_ai_session_id`  BIGINT      NULL COMMENT '关联会话主键(resume回写/列表过滤)',"
            + "`session_uid`        VARCHAR(64) NULL COMMENT '前端会话标识(SSE订阅与内转resume用)',"
            + "`sys_user_id`        VARCHAR(64) NULL COMMENT '归属用户(列表本人过滤)',"
            + "`reply_id`           VARCHAR(64) NULL COMMENT '确认请求回复ID(前端确认卡关联)',"
            + "`tool_call_id`       VARCHAR(64) NULL COMMENT '工具调用ID(决策匹配)',"
            + "`action`             VARCHAR(255) NOT NULL COMMENT '工具动作',"
            + "`resources`          TEXT        NULL COMMENT '资源清单',"
            + "`risk_level`         VARCHAR(32) NOT NULL COMMENT 'ToolRiskPolicy 分级',"
            + "`status`             VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/EXPIRED',"
            + "`expire_at`          BIGINT      NOT NULL,"
            + "`decided_by`         VARCHAR(64) NULL,"
            + "`decided_at`         BIGINT      NULL,"
            + "`decision_reason`    VARCHAR(512) NULL COMMENT '决议说明(拒绝原因等)',"
            + "`resolved_at`        BIGINT      NULL COMMENT '终态时间(过期场景无 decided_at，以此为准)',"
            + "`created_at`         BIGINT      NOT NULL,"
            + "PRIMARY KEY (`sys_ai_approval_id`),"
            + "KEY `idx_saaa_run` (`sys_ai_run_id`, `status`),"
            + "KEY `idx_saaa_session` (`sys_ai_session_id`, `status`),"
            + "KEY `idx_saaa_user` (`sys_user_id`, `created_at`),"
            + "KEY `idx_saaa_reply` (`reply_id`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 工具审批'";

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
     * 尝试执行一次建表（三表顺序执行），失败则安排重试。
     *
     * <p>安全豁免：DDL 的表名来自本类常量并经 {@link SqlName#checkSimple(String, String)}
     * 严格校验，不包含用户输入或用户值。</p>
     */
    private synchronized void tryMigrate() {
        try (Connection connection = dataSource.getConnection()) {
            executeDdl(connection, TABLE_RUN, CREATE_RUN_SQL);
            executeDdl(connection, TABLE_STEP, CREATE_STEP_SQL);
            executeDdl(connection, TABLE_APPROVAL, CREATE_APPROVAL_SQL);
            log.info("[AgentRunSchema] 表 {}/{}/{} 已就绪", TABLE_RUN, TABLE_STEP, TABLE_APPROVAL);
            stopRetry();
        } catch (Exception e) {
            log.warn("[AgentRunSchema] 建表暂不可用，将在 30 秒后重试: {}", e.getMessage());
            scheduleRetry();
        }
    }

    /**
     * 执行单条建表 DDL（先校验表名与 DDL 一致）。
     *
     * @param connection 数据库连接
     * @param table      表名常量
     * @param ddl        建表语句
     * @throws SQLException 执行失败
     */
    private void executeDdl(Connection connection, String table, String ddl) throws SQLException {
        final String checked = SqlName.checkSimple(table, "Agent 运行记录表名");
        if (!ddl.contains("`" + checked + "`")) {
            // 防御：常量与校验名不一致时直接拒绝执行（正常不会发生）
            throw new IllegalStateException("DDL 表名与校验名不一致: " + checked);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    /**
     * 安排 30 秒后重试（数据库暂不可达场景）。
     */
    private synchronized void scheduleRetry() {
        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "agent-run-schema-migrator");
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

package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.common.support.ai.agent.run.AgentRunStatus;
import com.chua.starter.ai.support.manage.store.JdbcAgentRunStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真实 JDBC 的终态竞争回归：迟到完成和计数回写不得复活已取消的运行。
 * @author CH
 */
class JdbcAgentRunStoreTest {
    /** 终态只允许关联字段回填，原结束时间和决议必须保留。 */
    @Test
    void terminalStatusSurvivesStaleUpdates() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        // 同一连接保持内存数据库存活，独立 JDBC 操作与生产一致。
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_ai_agent_run(sys_ai_run_id VARCHAR(64) PRIMARY KEY, sys_ai_session_id BIGINT, "
                + "agent_id VARCHAR(64), trigger_type VARCHAR(32), mode VARCHAR(32), status VARCHAR(32), "
                + "input_summary VARCHAR(500), output_summary VARCHAR(500), error VARCHAR(500), tokens_used BIGINT, "
                + "rounds INT, tool_calls INT, sub_session_id BIGINT, sys_user_id VARCHAR(64), started_at BIGINT, ended_at BIGINT)");
        var store = new JdbcAgentRunStore(source);
        store.insertRun(run(AgentRunStatus.RUNNING, null, null));
        assertEquals(1, store.updateRun(run(AgentRunStatus.CANCELED, 20L, null)));
        assertEquals(0, store.updateRun(run(AgentRunStatus.COMPLETED, 21L, null)));
        assertEquals(0, store.updateRun(run(AgentRunStatus.RUNNING, null, null)));
        assertEquals(1, store.updateRun(run(AgentRunStatus.CANCELED, 20L, 100L)));
        assertEquals(AgentRunStatus.CANCELED, store.findRun("run").status());
        assertEquals(100L, store.findRun("run").sessionId());
    }

    /**
     * 创建不同阶段的同一运行快照。
     * @param status 状态
     * @param endedAt 终态时间
     * @param sessionId 回填的会话
     * @return 运行记录
     */
    private AgentRunRecord run(AgentRunStatus status, Long endedAt, Long sessionId) {
        return new AgentRunRecord("run", sessionId, "agent", "manual", "SINGLE", status,
                "input", null, null, 0, 0, 0, null, "owner", 1, endedAt);
    }
}

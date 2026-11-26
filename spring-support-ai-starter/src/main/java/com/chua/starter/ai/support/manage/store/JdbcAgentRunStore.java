// [P3C A20 豁免] A20/A30/A31: 含 SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.store;

import com.chua.common.support.ai.agent.run.AgentRunQuery;
import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.common.support.ai.agent.run.AgentRunStatus;
import com.chua.common.support.ai.agent.run.AgentRunStore;
import com.chua.common.support.ai.agent.run.AgentStepRecord;
import com.chua.common.support.ai.agent.run.AgentStepStatus;
import com.chua.common.support.ai.agent.run.AgentStepType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AgentRunStore} 的 JDBC 实现（MySQL）。
 *
 * <p>与 {@code SysAiAgentRunSchemaMigrator} 的三表 DDL 同构；运行/步骤字段
 * 按 {@code AgentRunRecord} / {@code AgentStepRecord} 契约透明存取，
 * 枚举以 name() 字符串落库。查询条件均为白名单列 + 绑定参数，无拼接注入面。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Component
public class JdbcAgentRunStore implements AgentRunStore {

    /**
     * 运行表全列（与 DDL 同序）
     */
    private static final String RUN_COLUMNS = "sys_ai_run_id, sys_ai_session_id, agent_id, trigger_type, mode,"
            + " status, input_summary, output_summary, error, tokens_used, rounds, tool_calls,"
            + " sub_session_id, sys_user_id, started_at, ended_at";

    /**
     * 步骤表全列（与 DDL 同序）
     */
    private static final String STEP_COLUMNS = "sys_ai_step_id, sys_ai_run_id, seq, type, name,"
            + " arguments_json, result_summary, status, tokens, started_at, ended_at";

    /**
     * JDBC 模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入数据源。
     *
     * @param dataSource 数据源
     */
    public JdbcAgentRunStore(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void insertRun(AgentRunRecord run) {
        String sql = "INSERT INTO sys_ai_agent_run (" + RUN_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql, run.runId(), run.sessionId(), run.agentId(), run.triggerType(), run.mode(),
                run.status().name(), run.inputSummary(), run.outputSummary(), run.error(), run.tokensUsed(),
                run.rounds(), run.toolCalls(), run.subSessionId(), run.userId(), run.startedAt(), run.endedAt());
    }

    @Override
    public int updateRun(AgentRunRecord run) {
        String sql = "UPDATE sys_ai_agent_run SET sys_ai_session_id=?, agent_id=?, trigger_type=?, mode=?,"
                + " status=?, input_summary=?, output_summary=?, error=?, tokens_used=?, rounds=?, tool_calls=?,"
                + " sub_session_id=?, sys_user_id=?, started_at=?, ended_at=? WHERE sys_ai_run_id=?"
                + " AND (status NOT IN ('COMPLETED','FAILED','CANCELED') OR (status=? AND ended_at=?))";
        return jdbcTemplate.update(sql, run.sessionId(), run.agentId(), run.triggerType(), run.mode(),
                run.status().name(), run.inputSummary(), run.outputSummary(), run.error(), run.tokensUsed(),
                run.rounds(), run.toolCalls(), run.subSessionId(), run.userId(), run.startedAt(), run.endedAt(),
                run.runId(), run.status().name(), run.endedAt());
    }

    @Override
    public AgentRunRecord findRun(String runId) {
        String sql = "SELECT " + RUN_COLUMNS + " FROM sys_ai_agent_run WHERE sys_ai_run_id=?";
        List<AgentRunRecord> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapRun(rs), runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public long countRuns(AgentRunQuery query) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>(4);
        appendRunFilters(where, params, query);
        String sql = "SELECT COUNT(*) FROM sys_ai_agent_run" + where;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<AgentRunRecord> listRuns(AgentRunQuery query) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>(6);
        appendRunFilters(where, params, query);
        String sql = "SELECT " + RUN_COLUMNS + " FROM sys_ai_agent_run" + where
                + " ORDER BY started_at DESC, sys_ai_run_id DESC LIMIT ? OFFSET ?";
        params.add(query.limit());
        params.add(query.offset());
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRun(rs), params.toArray());
    }

    @Override
    public void insertStep(AgentStepRecord step) {
        String sql = "INSERT INTO sys_ai_agent_step (" + STEP_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql, step.stepId(), step.runId(), step.seq(), step.type().name(), step.name(),
                step.argumentsJson(), step.resultSummary(), step.status().name(), step.tokens(),
                step.startedAt(), step.endedAt());
    }

    @Override
    public long countSteps(String runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_ai_agent_step WHERE sys_ai_run_id=?", Long.class, runId);
        return count == null ? 0L : count;
    }

    @Override
    public List<AgentStepRecord> listSteps(String runId) {
        String sql = "SELECT " + STEP_COLUMNS + " FROM sys_ai_agent_step WHERE sys_ai_run_id=?"
                + " ORDER BY seq ASC, started_at ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapStep(rs), runId);
    }

    /**
     * 拼接运行列表过滤条件（全部绑定参数，无拼接值）。
     *
     * @param where  条件构造器（调用方负责追加 WHERE 前缀）
     * @param params 绑定参数
     * @param query  查询条件
     */
    private static void appendRunFilters(StringBuilder where, List<Object> params, AgentRunQuery query) {
        List<String> conditions = new ArrayList<>(4);
        if (query.sessionId() != null) {
            conditions.add("sys_ai_session_id=?");
            params.add(query.sessionId());
        }
        if (query.agentId() != null && !query.agentId().isBlank()) {
            conditions.add("agent_id=?");
            params.add(query.agentId());
        }
        if (query.status() != null && !query.status().isBlank()) {
            conditions.add("status=?");
            params.add(query.status().trim().toUpperCase());
        }
        if (query.userId() != null && !query.userId().isBlank()) {
            conditions.add("sys_user_id=?");
            params.add(query.userId());
        }
        if (!conditions.isEmpty()) {
            where.append(" WHERE ").append(String.join(" AND ", conditions));
        }
    }

    /**
     * 结果集 → 运行记录。
     *
     * @param rs 结果集（游标当前行）
     * @return 运行记录
     * @throws SQLException 读取失败
     */
    private AgentRunRecord mapRun(ResultSet rs) throws SQLException {
        return new AgentRunRecord(rs.getString("sys_ai_run_id"),
                rs.getObject("sys_ai_session_id") == null ? null : rs.getLong("sys_ai_session_id"),
                rs.getString("agent_id"), rs.getString("trigger_type"), rs.getString("mode"),
                AgentRunStatus.of(rs.getString("status")), rs.getString("input_summary"),
                rs.getString("output_summary"), rs.getString("error"), rs.getLong("tokens_used"),
                rs.getInt("rounds"), rs.getInt("tool_calls"),
                rs.getObject("sub_session_id") == null ? null : rs.getLong("sub_session_id"),
                rs.getString("sys_user_id"), rs.getLong("started_at"),
                rs.getObject("ended_at") == null ? null : rs.getLong("ended_at"));
    }

    /**
     * 结果集 → 步骤记录。
     *
     * @param rs 结果集（游标当前行）
     * @return 步骤记录
     * @throws SQLException 读取失败
     */
    private AgentStepRecord mapStep(ResultSet rs) throws SQLException {
        return new AgentStepRecord(rs.getString("sys_ai_step_id"), rs.getString("sys_ai_run_id"), rs.getInt("seq"),
                AgentStepType.of(rs.getString("type")), rs.getString("name"), rs.getString("arguments_json"),
                rs.getString("result_summary"), AgentStepStatus.of(rs.getString("status")), rs.getLong("tokens"),
                rs.getLong("started_at"), rs.getObject("ended_at") == null ? null : rs.getLong("ended_at"));
    }
}

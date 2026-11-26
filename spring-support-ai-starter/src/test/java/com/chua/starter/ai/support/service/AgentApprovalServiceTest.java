package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.ConfirmationDecision;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 JDBC 验证审批归属、过期、决策冲突与并发 CAS，不替换数据库访问。
 *
 * @author CH
 */
class AgentApprovalServiceTest {

    /** 测试数据库访问。 */
    private JdbcTemplate jdbc;
    /** 被测审批服务。 */
    private AgentApprovalService service;

    /** 每例独立的 H2 数据库，使用与生产同构的审批列。 */
    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_ai_agent_approval (sys_ai_approval_id VARCHAR(64) PRIMARY KEY, "
                + "sys_ai_run_id VARCHAR(64), sys_ai_step_id VARCHAR(64), sys_ai_session_id BIGINT, "
                + "session_uid VARCHAR(64), sys_user_id VARCHAR(64), reply_id VARCHAR(64), tool_call_id VARCHAR(64), "
                + "action VARCHAR(64), resources VARCHAR(500), risk_level VARCHAR(32), status VARCHAR(32), "
                + "expire_at BIGINT, decided_by VARCHAR(64), decided_at BIGINT, decision_reason VARCHAR(500), "
                + "resolved_at BIGINT, created_at BIGINT)");
        service = new AgentApprovalService(jdbc, new AiSessionEventBroadcaster(new ObjectMapper()));
    }

    /** 他人不能读取或修改待审及终态单据。 */
    @Test
    void deniesOtherOwnerBeforeReturningAnyState() {
        insert("pending", "PENDING", System.currentTimeMillis() + 60_000);
        insert("done", "APPROVED", System.currentTimeMillis() + 60_000);
        for (String id : List.of("pending", "done", "missing")) {
            ApiException failure = assertThrows(ApiException.class,
                    () -> service.resolve(id, true, "other-user", null));
            assertEquals(ApiErrorCode.NOT_FOUND, failure.getErrorCode());
        }
        assertEquals("PENDING", service.find("pending").status());
    }

    /** 清扫尚未运行时，已超时单据仍不得批准。 */
    @Test
    void expiresAtDecisionTime() {
        insert("expired", "PENDING", System.currentTimeMillis() - 1);
        var result = service.resolve("expired", true, "owner", "approve");
        assertFalse(result.transitioned());
        assertEquals("EXPIRED", result.record().status());
        assertEquals(null, result.record().decidedBy());
    }

    /** 两个相反的并发决议只能有一个成功，重试保持终态。 */
    @Test
    void onlyOneConcurrentDecisionWins() throws Exception {
        insert("race", "PENDING", System.currentTimeMillis() + 60_000);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<AgentApprovalService.ApprovalResolveResult>> tasks = List.of(
                    () -> service.resolve("race", true, "owner", null),
                    () -> service.resolve("race", false, "owner", null));
            int transitions = 0;
            for (var future : executor.invokeAll(tasks)) {
                if (future.get().transitioned()) {
                    transitions++;
                }
            }
            assertEquals(1, transitions);
        }
        String terminal = service.find("race").status();
        assertFalse(service.resolve("race", true, "owner", null).transitioned());
        assertEquals(terminal, service.find("race").status());
    }

    /** 聊天恢复入口同样不能绕过归属与时效。 */
    @Test
    void resumeRejectsExpiredAndOtherOwner() {
        insert("reply", "PENDING", System.currentTimeMillis() - 1);
        assertThrows(ApiException.class,
                () -> service.resolveByReplyId(1L, "reply", List.of(decision(true)), "other-user"));
        assertEquals("PENDING", service.find("reply").status());
        ApiException failure = assertThrows(ApiException.class,
                () -> service.resolveByReplyId(1L, "reply", List.of(decision(true)), "owner"));
        assertEquals(ApiErrorCode.CONFLICT, failure.getErrorCode());
        assertEquals("EXPIRED", service.find("reply").status());
    }

    /** 已拒绝的单据不能被批准恢复；相同决策可幂等读取。 */
    @Test
    void resumeCannotReverseTerminalDecision() {
        insert("reply", "REJECTED", System.currentTimeMillis() + 60_000);
        assertThrows(ApiException.class,
                () -> service.resolveByReplyId(1L, "reply", List.of(decision(true)), "owner"));
        assertEquals(0, service.resolveByReplyId(1L, "reply", List.of(decision(false)), "owner"));
        assertTrue(service.find("reply").resolvedAt() == null);
    }

    /** 极大页码不会整数溢出变为负 OFFSET。 */
    @Test
    void largePageDoesNotOverflow() {
        assertEquals(List.of(), service.listForUser("owner", null, Integer.MAX_VALUE, 200).get("items"));
    }

    /**
     * 插入一条真实审批记录。
     * @param id 审批及回复标识
     * @param status 当前状态
     * @param expiry 过期时间
     */
    private void insert(String id, String status, long expiry) {
        jdbc.update("INSERT INTO sys_ai_agent_approval (sys_ai_approval_id, sys_ai_session_id, session_uid, "
                        + "sys_user_id, reply_id, tool_call_id, status, expire_at, created_at) VALUES (?,1,'session',"
                        + "'owner',?,'tool',?,?,?)", id, id, status, expiry, System.currentTimeMillis());
    }

    /**
     * 构造真实的确认决策值。
     * @param approved 是否批准
     * @return 决策
     */
    private ConfirmationDecision decision(boolean approved) {
        ConfirmationDecision decision = new ConfirmationDecision();
        decision.setToolCallId("tool");
        decision.setConfirmed(approved);
        return decision;
    }
}

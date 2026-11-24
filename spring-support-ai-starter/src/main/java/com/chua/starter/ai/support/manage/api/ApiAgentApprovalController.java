// [P3C A20 豁免] A20/A30/A31: 含日志/界面字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.AgentApprovalRecord;
import com.chua.starter.ai.support.service.AgentApprovalService;
import com.chua.starter.ai.support.stream.ChatFrameSink;
import com.chua.starter.ai.support.stream.ChatStreamOrchestrator;
import com.chua.starter.ai.support.stream.PreparedChat;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具审批单管理 API（切片四：审批闭环的列表 + 程序化决议入口）。
 *
 * <p>与主聊天链路的分工：聊天页的确认卡走 {@code /v2/chat/resume} 决议
 * （编排层在 prepareResume 里顺带回写审批单）；本控制器面向「审批列表页」
 * 与程序化调用——按单号决议，并在「本次批准落定」时把决策内转给
 * {@code resume} 链路，让被挂起的 Agent 轮次继续执行（多标签页/列表页
 * 决议与聊天页决议双轨并行，CAS 保证同一单据只被落定一次）。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@RestController
@RequestMapping("/api/agents/approvals")
@RequiredArgsConstructor
public class ApiAgentApprovalController {

    /**
     * 审批单服务
     */
    private final AgentApprovalService agentApprovalService;

    /**
     * 聊天流式编排（内转 resume 用）
     */
    private final ChatStreamOrchestrator chatStreamOrchestrator;

    /**
     * 归属当前用户的审批单分页列表。
     *
     * @param status   状态过滤（PENDING/APPROVED/REJECTED/EXPIRED），空为全部
     * @param page     页码（1 起）
     * @param pageSize 页大小（1~200，缺省 20）
     * @return { total, page, pageSize, items }
     */
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResult.ok(agentApprovalService.listForUser(
                AiUserContext.requireUserId(), status, page, pageSize));
    }

    /**
     * 按单号决议一次审批（批准 / 拒绝）。
     *
     * <p>幂等：单据已决议时返回现状且不重复内转 resume。仅当「本次调用完成
     * PENDING → APPROVED 的落定」且单据携带 replyId + 前端会话标识时，才把
     * 决策内转给 resume 链路（拒绝/过期不恢复执行）。</p>
     *
     * @param id   审批单号
     * @param body { approved: boolean, reason?: string }
     * @return { approval: 单据现状, resumed: 是否已内转 resume }
     */
    @PostMapping("/{id}/resolve")
    public ApiResult<Map<String, Object>> resolve(@PathVariable("id") String id,
                                                  @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.parseBoolean(String.valueOf(body.get("approved")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        String decidedBy = AiUserContext.requireUserId();
        AgentApprovalService.ApprovalResolveResult result =
                agentApprovalService.resolve(id, approved, decidedBy, reason);
        boolean resumed = false;
        if (result.transitioned() && approved) {
            resumed = forwardResume(result.record());
        }
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("approval", result.record());
        data.put("resumed", resumed);
        return ApiResult.ok(data);
    }

    /**
     * 把批准决策内转给 resume 链路：构造与前端确认卡等价的请求体
     * （sessionId/replyId/decisions），走 prepareResume + executeResume。
     *
     * <p>内转是尽力而为：会话映射缺失（如进程重启后）或恢复失败只记日志，
     * 不影响决议本身的生效。</p>
     *
     * @param rec 已批准的审批单
     * @return 是否成功触发恢复
     */
    private boolean forwardResume(AgentApprovalRecord rec) {
        if (rec.replyId() == null || rec.replyId().isBlank()
                || rec.sessionUid() == null || rec.sessionUid().isBlank()
                || rec.toolCallId() == null || rec.toolCallId().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> decision = new HashMap<>(4);
            decision.put("toolCallId", rec.toolCallId());
            decision.put("confirmed", true);
            decision.put("mode", "once");
            Map<String, Object> body = new HashMap<>(4);
            body.put("sessionId", rec.sessionUid());
            body.put("replyId", rec.replyId());
            body.put("decisions", List.of(decision));
            PreparedChat prepared = chatStreamOrchestrator.prepareResume(body);
            chatStreamOrchestrator.executeResume(prepared, new DiscardSink());
            log.info("[AgentApproval] 决议已内转 resume approvalId={} sessionId={} replyId={}",
                    rec.approvalId(), rec.sessionUid(), rec.replyId());
            return true;
        } catch (Exception e) {
            log.warn("[AgentApproval] 决议内转 resume 失败（决议仍生效）approvalId={}: {}",
                    rec.approvalId(), e.getMessage());
            return false;
        }
    }

    /**
     * 丢弃型帧槽：内转 resume 不经任何传输，帧产出直接丢弃（恢复过程的消息
     * 落库、确认终态回写等副作用照常发生）。
     *
     * @author CH
     * @since 4.0.0.52
     */
    private static final class DiscardSink implements ChatFrameSink {

        /**
         * 丢弃事件帧。
         *
         * @param event 事件结构
         */
        @Override
        public void sendEvent(Map<String, Object> event) {
            // 内转链路无传输对端，帧丢弃
        }

        /**
         * 丢弃轨迹帧。
         *
         * @param trace 执行轨迹
         */
        @Override
        public void sendTrace(com.chua.common.support.ai.agent.AgentTrace trace) {
            // 内转链路无传输对端，帧丢弃
        }

        /**
         * 丢弃结束帧。
         */
        @Override
        public void sendDone() {
            // 内转链路无传输对端，帧丢弃
        }

        /**
         * 内转链路无取消来源。
         *
         * @return 恒 false
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * 无取消语义，空实现。
         */
        @Override
        public void markCancelled() {
            // 内转链路不支持取消
        }

        /**
         * 无传输资源需要释放，空实现。
         */
        @Override
        public void close() {
            // 内转链路无传输资源
        }
    }
}

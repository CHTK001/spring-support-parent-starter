// [P3C A20 豁免] A20/A30/A31: 含日志/界面字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.AiSessionSubAgentService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话子代理运行记录端点（统一挂在 {@code /api/sessions} 下）。
 *
 * <p>补齐前端 {@code sessionAgentApi} 的子会话契约（G4）：会话页刷新 / 跨标签页后
 * 恢复子代理 Tab，侧边栏展开区展示「子会话 / 智能体」。生命周期变更经
 * {@code AiSessionEventBroadcaster} 按归属人广播 {@code sub_agent_create} /
 * {@code sub_agent_closed} / {@code sub_agent_status}，多标签页实时同步。</p>
 *
 * <ul>
 *   <li>{@code GET  /{id}/agents} —— 主会话关联的智能体列表（主 + 子，去重）；</li>
 *   <li>{@code GET  /{id}/sub-sessions} —— 主会话的子代理运行记录列表（时间正序）；</li>
 *   <li>{@code POST /{id}/sub-sessions/spawn} —— 为主会话 spawn 一个子代理会话
 *       （建绑定 + 广播创建，不执行模型调用）；</li>
 *   <li>{@code POST /{id}/sub-sessions/{subId}/close} —— 关闭子会话（广播关闭事件）；</li>
 *   <li>{@code POST /sub-sessions/{subId}/status} —— 更新子会话状态（广播状态事件）。</li>
 * </ul>
 *
 * <p>归属校验：所有端点都要求「当前登录用户 == 主会话/记录归属人」，
 * 与 {@code AiSessionCapabilityController} 同口径。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ApiSessionSubAgentController {

    /**
     * 子代理运行记录服务
     */
    private final AiSessionSubAgentService subAgentService;

    /**
     * 主会话关联的智能体列表（主 + 子，去重，主 Agent 在前）。
     *
     * @param id 主会话标识
     * @return [{agent_id, name, role: master|sub, status}]
     */
    @GetMapping("/{id}/agents")
    public ReturnResult<List<Map<String, Object>>> listAgents(@PathVariable("id") String id) {
        return ReturnResult.ok(subAgentService.listAgents(id, AiUserContext.requireUserId()));
    }

    /**
     * 主会话下的子代理运行记录列表。
     *
     * @param id 主会话标识
     * @return {items:[{id,sessionId,agentId,agentName,masterAgentId,status,title,input,output,error,isStreaming,createdAt,updatedAt}], total}
     */
    @GetMapping("/{id}/sub-sessions")
    public ReturnResult<Map<String, Object>> listSubSessions(@PathVariable("id") String id) {
        return ReturnResult.ok(subAgentService.listBySession(id, AiUserContext.requireUserId()));
    }

    /**
     * 为主会话 spawn 一个子代理会话（建绑定 + 广播 sub_agent_create，不执行模型调用）。
     *
     * @param id   主会话标识
     * @param body {agentId: 必填, name?: 展示名覆盖, input?: 初始输入}
     * @return 新建的运行记录
     */
    @PostMapping("/{id}/sub-sessions/spawn")
    public ReturnResult<Map<String, Object>> spawn(@PathVariable("id") String id,
                                                   @RequestBody Map<String, Object> body) {
        return ReturnResult.ok(subAgentService.spawn(id, body, AiUserContext.requireUserId()));
    }

    /**
     * 关闭子会话（广播 sub_agent_closed）。
     *
     * @param id     主会话标识
     * @param subId  子会话记录标识
     * @param body   {reason?: completed/failed/canceled/...}
     * @return 关闭后的运行记录
     */
    @PostMapping("/{id}/sub-sessions/{subId}/close")
    public ReturnResult<Map<String, Object>> close(@PathVariable("id") String id,
                                                   @PathVariable("subId") String subId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? "completed" : String.valueOf(body.get("reason"));
        return ReturnResult.ok(subAgentService.close(id, subId, reason, AiUserContext.requireUserId()));
    }

    /**
     * 更新子会话状态（广播 sub_agent_status）。
     *
     * @param subId 子会话记录标识
     * @param body  {status: RUNNING/COMPLETED/FAILED/CLOSED}
     * @return 更新后的运行记录
     */
    @PostMapping("/sub-sessions/{subId}/status")
    public ReturnResult<Map<String, Object>> updateStatus(@PathVariable("subId") String subId,
                                                          @RequestBody Map<String, Object> body) {
        String status = body.get("status") == null ? "" : String.valueOf(body.get("status"));
        return ReturnResult.ok(subAgentService.updateStatus(subId, status, AiUserContext.requireUserId()));
    }
}

// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.agent.ToolRiskPolicy;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.oshi.support.Cpu;
import com.chua.oshi.support.Mem;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Sys;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionSubAgent;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.AgentRunService;
import com.chua.starter.ai.support.service.AiSessionSubAgentService;
import com.chua.starter.ai.support.service.SysAiAgentService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话侧能力端点（统一挂在 {@code /v2/sessions/{id}} 下，与工作区文件端点同口径）。
 *
 * <ul>
 *   <li>{@code GET  /{id}/server-info} —— 服务器信息（OSHI：系统/CPU/内存 + JVM 版本），
 *       供会话页「服务器信息」面板展示；只读，无副作用。</li>
 *   <li>{@code POST /{id}/permission-test} —— 「5.1 测试权限」：对给定的工具动作与资源
 *       只读地做风险分级（风险等级 / 是否需确认 / 是否强确认 / 是否被 shell 禁令拦截），
 *       <b>不执行任何工具</b>，供前端在调用前自测权限口径。</li>
 *   <li>{@code GET  /{id}/sub-agents} —— 角色子代理列表（库内启用的子 Agent，含主 Agent 名），
 *       供会话页子代理调用面板选择。</li>
 *   <li>{@code POST /{id}/sub-agents/invoke} —— 手动调用单个子代理（复用
 *       {@link SysAiAgentService#invokeSubAgent}，用子代理自己的模型与系统提示词执行一次）。</li>
 * </ul>
 *
 * <p>归属校验：所有端点都要求「当前登录用户 == 会话归属人」（与
 * {@code AiCommandController#requireWritableSession} 同一口径），防止跨账号读取
 * 服务器信息或消耗他人额度调用子代理。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions/{id}")
@RequiredArgsConstructor
public class AiSessionCapabilityController {

    /**
     * CPU 采样间隔毫秒（{@code Oshi.newCpu} 内部会 sleep 该时长再算差值）
     */
    private static final long CPU_SAMPLE_MILLIS = 200L;

    /**
     * 字节 → MB 换算基数
     */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final SysAiSessionMapper sessionMapper;

    private final SysAiAgentService agentService;

    /**
     * 会话子代理运行记录服务（invoke 生命周期持久化 + 事件广播，G4）
     */
    private final AiSessionSubAgentService sessionSubAgentService;

    /**
     * Agent 运行记录服务（Run/Step 落库与会话关联回填）
     */
    private final AgentRunService agentRunService;

    /**
     * 服务器信息（只读）。
     *
     * @param id 会话标识
     * @return {osName, osArch, computerIp, cpu:{cores,usedPercent,freePercent},
     *         memory:{totalMb,usedMb,freeMb,usagePercent}, javaVersion, serverTime}
     */
    @GetMapping("/server-info")
    public ReturnResult<Map<String, Object>> serverInfo(@PathVariable("id") String id) {
        requireOwnedSession(id);
        Map<String, Object> data = new LinkedHashMap<>(8);
        try {
            Sys sys = Oshi.newSys();
            data.put("osName", sys.getOsName());
            data.put("osArch", sys.getOsArch());
            data.put("computerIp", sys.getComputerIp());
        } catch (Exception e) {
            log.warn("[session-capability] 读取系统信息失败: {}", e.getMessage());
        }
        try {
            Cpu cpu = Oshi.newCpu(CPU_SAMPLE_MILLIS);
            Map<String, Object> cpuMap = new LinkedHashMap<>(4);
            cpuMap.put("cores", cpu.getCpuNum());
            cpuMap.put("usedPercent", round1(cpu.getUsed()));
            cpuMap.put("freePercent", round1(cpu.getFree()));
            data.put("cpu", cpuMap);
        } catch (Exception e) {
            log.warn("[session-capability] 读取 CPU 信息失败: {}", e.getMessage());
        }
        try {
            Mem mem = Oshi.newMem();
            Map<String, Object> memMap = new LinkedHashMap<>(4);
            memMap.put("totalMb", mem.getTotal() / BYTES_PER_MB);
            memMap.put("usedMb", mem.getUsed() / BYTES_PER_MB);
            memMap.put("freeMb", mem.getFree() / BYTES_PER_MB);
            memMap.put("usagePercent", round1(mem.getUsage() * 100.0));
            data.put("memory", memMap);
        } catch (Exception e) {
            log.warn("[session-capability] 读取内存信息失败: {}", e.getMessage());
        }
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("serverTime", System.currentTimeMillis());
        return ReturnResult.ok(data);
    }

    /**
     * 「5.1 测试权限」：只读地回答「这个工具调用会是什么风险、要不要确认、能否放行」。
     *
     * <p>与网关（{@code OpencodeServerAgent#onPermissionAsked}）同一套分级口径
     * （{@link ToolRiskPolicy}），但<b>不执行任何工具</b>；shell 禁令也与网关一致：
     * 非管理员账号的 shell 动作会被直接拒绝。</p>
     *
     * @param id   会话标识
     * @param body {action: 工具/权限动作名, resources: 资源（字符串或数组，如命令行/路径）}
     * @return {action, resources, riskLevel, confirmationRequired, strongConfirm,
     *         allowed, shellBlockedForNonAdmin, admin, reason}
     */
    @PostMapping("/permission-test")
    public ReturnResult<Map<String, Object>> permissionTest(@PathVariable("id") String id,
                                                            @RequestBody Map<String, Object> body) {
        requireOwnedSession(id);
        String action = body.get("action") == null ? "" : String.valueOf(body.get("action")).trim();
        if (action.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "action 不能为空");
        }
        String resources = joinResources(body.get("resources"));
        boolean admin = AiUserContext.isAdmin();
        boolean shellBlocked = isShellAction(action) && !admin;
        String riskLevel = ToolRiskPolicy.classify(action, resources);
        Map<String, Object> result = new LinkedHashMap<>(10);
        result.put("action", action);
        result.put("resources", resources);
        result.put("riskLevel", riskLevel);
        result.put("confirmationRequired", !shellBlocked && ToolRiskPolicy.requiresConfirmation(riskLevel));
        result.put("strongConfirm", !shellBlocked && ToolRiskPolicy.isDangerous(riskLevel));
        result.put("shellBlockedForNonAdmin", shellBlocked);
        result.put("allowed", !shellBlocked);
        result.put("admin", admin);
        result.put("reason", shellBlocked ? "非管理员账号禁止执行 shell 命令" : riskReason(riskLevel));
        return ReturnResult.ok(result);
    }

    /**
     * 角色子代理列表（库内启用的子 Agent，含所属主 Agent 名）。
     *
     * @param id 会话标识
     * @return {items:[{agentId,name,description,role,model,enabled,masterId,masterName}], total}
     */
    @GetMapping("/sub-agents")
    public ReturnResult<Map<String, Object>> subAgents(@PathVariable("id") String id) {
        requireOwnedSession(id);
        List<Map<String, Object>> items = new ArrayList<>(4);
        for (Map<String, Object> master : agentService.listAgents()) {
            Object subs = master.get("sub_agents");
            if (!(subs instanceof List<?> list)) {
                continue;
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> sub)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("agentId", sub.get("agent_id"));
                item.put("name", sub.get("name"));
                item.put("description", sub.get("description"));
                item.put("role", sub.get("role"));
                item.put("model", sub.get("model"));
                item.put("enabled", sub.get("enabled"));
                item.put("masterId", master.get("agent_id"));
                item.put("masterName", master.get("name"));
                items.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 手动调用单个子代理（角色子代理调用路径）。
     *
     * <p>invoke 生命周期（G4）：先落一条 RUNNING 运行记录并广播 {@code sub_agent_create}，
     * 调用结束回填输出 / 失败原因并广播 {@code sub_agent_closed}——前端 Tab 实时增删，
     * 调用历史经 {@code GET /api/sessions/{id}/sub-sessions} 持久可查。</p>
     *
     * @param id   会话标识
     * @param body {agentId: 子 Agent 标识, input: 用户输入}
     * @return {agentId, name, masterId, input, output, subSessionId, error?}
     */
    @PostMapping("/sub-agents/invoke")
    public ReturnResult<Map<String, Object>> invokeSubAgent(@PathVariable("id") String id,
                                                            @RequestBody Map<String, Object> body) {
        requireOwnedSession(id);
        String agentId = body.get("agentId") == null ? "" : String.valueOf(body.get("agentId")).trim();
        String input = body.get("input") == null ? "" : String.valueOf(body.get("input"));
        if (agentId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "agentId 不能为空");
        }
        if (input.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "input 不能为空");
        }
        String userId = AiUserContext.requireUserId();
        SysAiSessionSubAgent binding = sessionSubAgentService.beginInvoke(id, agentId, input, userId);
        String subSessionId = String.valueOf(binding.getId());
        try {
            Map<String, Object> result = agentService.invokeSubAgent(agentId, input);
            Object error = result.get("error");
            sessionSubAgentService.finishInvoke(subSessionId, userId,
                    result.get("output") == null ? "" : String.valueOf(result.get("output")),
                    error == null ? null : String.valueOf(error));
            result.put("subSessionId", subSessionId);
            // 运行记录在 Agent 服务内创建时无会话上下文，这里回填 sessionId / subSessionId
            linkAgentRun(result, id, subSessionId);
            return ReturnResult.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 前置校验失败（子 Agent 不存在 / 停用 / 无可用模型凭证）：记录失败现场后再抛
            try {
                sessionSubAgentService.finishInvoke(subSessionId, userId, "", e.getMessage());
            } catch (Exception ignore) {
                // 收尾失败不影响原错误抛出
            }
            if (e instanceof IllegalArgumentException) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, e.getMessage());
            }
            throw new ApiException(ApiErrorCode.CONFLICT, e.getMessage());
        }
    }

    /**
     * 回填 Agent 运行记录与会话 / 子代理记录的关联。
     *
     * <p>运行记录在 {@code SysAiAgentServiceImpl#invokeSubAgent} 内创建，当时拿不到
     * 会话上下文（sessionId 为空）；本方法在拿到调用结果后补齐关联，使运行可按会话
     * 过滤并进入会话 SSE 通道。回填失败仅告警，不影响调用结果返回。</p>
     *
     * @param result       invokeSubAgent 返回结果（含 runId）
     * @param sessionId    会话标识（路径变量）
     * @param subSessionId 子代理运行记录标识（sys_ai_session_sub_agent）
     */
    private void linkAgentRun(Map<String, Object> result, String sessionId, String subSessionId) {
        Object runId = result.get("runId");
        if (runId == null || String.valueOf(runId).isBlank()) {
            return;
        }
        try {
            Long subId = subSessionId == null || subSessionId.isBlank() ? null : Long.parseLong(subSessionId);
            agentRunService.linkSession(String.valueOf(runId), Long.parseLong(sessionId), subId);
        } catch (Exception e) {
            log.warn("[AgentRun] 运行会话关联回填失败 runId={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 归属校验：会话必须存在且属于当前登录用户。
     *
     * @param id 会话标识（数字主键）
     * @return 校验通过的会话
     */
    private SysAiSession requireOwnedSession(String id) {
        Long sessionId;
        try {
            sessionId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return session;
    }

    /**
     * 资源参数转摘要文本（兼容字符串与数组两种传法）。
     *
     * @param raw 资源参数
     * @return 摘要文本
     */
    private static String joinResources(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(item == null ? "" : String.valueOf(item));
            }
            return sb.toString();
        }
        return String.valueOf(raw);
    }

    /**
     * 判断动作是否为 shell 类（与网关 {@code isShellAction} 同口径）。
     *
     * @param action 动作名
     * @return 是否 shell
     */
    private static boolean isShellAction(String action) {
        String value = action == null ? "" : action.toLowerCase(Locale.ROOT);
        return value.contains("bash") || value.contains("shell") || value.contains("command");
    }

    /**
     * 风险等级的人读解释。
     *
     * @param riskLevel 风险等级
     * @return 解释文本
     */
    private static String riskReason(String riskLevel) {
        if (ToolRiskPolicy.RISK_LOW.equalsIgnoreCase(riskLevel)) {
            return "只读操作，通常无需人工确认";
        }
        if (ToolRiskPolicy.RISK_HIGH.equalsIgnoreCase(riskLevel)) {
            return "影响面较大的操作，需要确认后执行";
        }
        if (ToolRiskPolicy.RISK_DANGEROUS.equalsIgnoreCase(riskLevel)) {
            return "破坏性操作，必须强确认（二次确认）后执行";
        }
        return "普通操作，需要确认后执行";
    }

    /**
     * 保留一位小数。
     *
     * @param value 数值
     * @return 四舍五入后的值
     */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

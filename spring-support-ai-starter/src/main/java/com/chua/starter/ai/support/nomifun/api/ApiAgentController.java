package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.store.ConversationStore;
import com.chua.starter.ai.support.nomifun.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体（Agent）控制器，替代 nomicore 的同名契约。
 *
 * <p>负责三类资源：
 * <ul>
 *     <li>/api/agents —— 内置/自定义 Agent 元数据目录；</li>
 *     <li>/api/agent-executions —— 持久化智能体执行（含步骤、尝试、决策）；</li>
 *     <li>/api/agent-execution-templates —— 执行模板（可一键创建执行）。</li>
 * </ul>
 * 全部为进程内内存实现，主键使用 {@link ConversationStore#newId()} 生成的 UUID；
 * 关键状态变更通过 {@link ApiWebSocketPushService} 推送 {@code agentExecution.changed} 等事件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiAgentController {

    /**
     * 主键生成与基础存储工具
     */
    private final ConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 内置 Agent 元数据目录（进程启动时初始化）
     */
    private final List<Map<String, Object>> builtinAgents = initBuiltinAgents();

    /**
     * 自定义 Agent 元数据目录
     */
    private final Map<String, Map<String, Object>> customAgents = new ConcurrentHashMap<>();

    /**
     * 执行聚合：execution_id → 执行主体
     */
    private final Map<String, Map<String, Object>> executions = new ConcurrentHashMap<>();

    /**
     * 执行步骤：execution_id → 步骤列表
     */
    private final Map<String, List<Map<String, Object>>> executionSteps = new ConcurrentHashMap<>();

    /**
     * 执行模板：execution_template_id → 模板主体
     */
    private final Map<String, Map<String, Object>> templates = new ConcurrentHashMap<>();

    /**
     * 模板参与者：execution_template_id → 参与者列表
     */
    private final Map<String, List<Map<String, Object>>> templateParticipants = new ConcurrentHashMap<>();

    // =====================================================================
    // Agents
    // =====================================================================

    @GetMapping("/agents")
    public ApiResult<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> all = new ArrayList<>(builtinAgents);
        all.addAll(customAgents.values());
        return ApiResult.ok(all);
    }

    @PostMapping("/agents/refresh")
    public ApiResult<Void> refreshCustomAgents() {
        // 内存实现：重新扫描自定义目录为幂等操作，无需实际 IO
        pushService.push("agent.listChanged", Map.of("action", "refreshed"));
        return ApiResult.ok();
    }

    @PostMapping("/agents/provider-health-check")
    public ApiResult<Map<String, Object>> providerHealthCheck(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider_id", body == null ? null : body.get("provider_id"));
        result.put("healthy", false);
        result.put("status", "unknown");
        result.put("detail", "内存后端未接入真实 provider，仅返回占位健康检查结果");
        return ApiResult.ok(result);
    }

    // =====================================================================
    // Agent Executions
    // =====================================================================

    @GetMapping("/agent-executions")
    public ApiResult<List<Map<String, Object>>> listExecutions() {
        return ApiResult.ok(new ArrayList<>(executions.values()));
    }

    @PostMapping("/agent-executions")
    public ApiResult<Map<String, Object>> createExecution(@RequestBody(required = false) Map<String, Object> body) {
        String executionId = conversationStore.newId();
        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("execution_id", executionId);
        exec.put("lead_conversation_id", body == null ? null : body.get("lead_conversation_id"));
        exec.put("status", "planned");
        exec.put("version", 1);
        exec.put("created_at", System.currentTimeMillis());
        exec.put("updated_at", System.currentTimeMillis());
        exec.put("participants", new ArrayList<Map<String, Object>>());
        executions.put(executionId, exec);
        executionSteps.put(executionId, new ArrayList<>());
        pushChange(executionId);
        return ApiResult.ok(exec);
    }

    @GetMapping("/agent-executions/{executionId}")
    public ApiResult<Map<String, Object>> getExecution(@PathVariable String executionId) {
        Map<String, Object> exec = requireExecution(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @DeleteMapping("/agent-executions/{executionId}")
    public ApiResult<Void> deleteExecution(@PathVariable String executionId,
                                           @RequestParam(required = false, defaultValue = "1") long expected_version) {
        Map<String, Object> exec = requireExecution(executionId);
        checkVersion(exec, expected_version);
        executions.remove(executionId);
        executionSteps.remove(executionId);
        pushChange(executionId);
        return ApiResult.ok();
    }

    @PatchMapping("/agent-executions/{executionId}/rename")
    public ApiResult<Map<String, Object>> renameExecution(@PathVariable String executionId,
                                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        if (body.containsKey("name")) {
            exec.put("name", body.get("name"));
        }
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/replan")
    public ApiResult<Map<String, Object>> replanExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/adjust")
    public ApiResult<Map<String, Object>> adjustExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/approve")
    public ApiResult<Map<String, Object>> approveExecution(@PathVariable String executionId,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        return commandExecution(executionId, "approved");
    }

    @PostMapping("/agent-executions/{executionId}/pause")
    public ApiResult<Map<String, Object>> pauseExecution(@PathVariable String executionId,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        return commandExecution(executionId, "paused");
    }

    @PostMapping("/agent-executions/{executionId}/resume")
    public ApiResult<Map<String, Object>> resumeExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        return commandExecution(executionId, "running");
    }

    @PostMapping("/agent-executions/{executionId}/cancel")
    public ApiResult<Map<String, Object>> cancelExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        return commandExecution(executionId, "cancelled");
    }

    @PostMapping("/agent-executions/{executionId}/steps")
    public ApiResult<Map<String, Object>> addSteps(@PathVariable String executionId,
                                                   @RequestBody Map<String, Object> body) {
        requireExecution(executionId);
        List<Map<String, Object>> steps = executionSteps.get(executionId);
        Object raw = body.get("steps");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                Map<String, Object> step = new LinkedHashMap<>();
                if (item instanceof Map) {
                    step.putAll((Map<String, Object>) item);
                }
                step.putIfAbsent("step_id", conversationStore.newId());
                step.putIfAbsent("execution_id", executionId);
                step.putIfAbsent("status", "pending");
                steps.add(step);
            }
        }
        Map<String, Object> exec = executions.get(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PatchMapping("/agent-executions/{executionId}/steps/{stepId}")
    public ApiResult<Map<String, Object>> updateStep(@PathVariable String executionId,
                                                      @PathVariable String stepId,
                                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> step = requireStep(executionId, stepId);
        body.forEach((k, v) -> {
            if (!"step_id".equals(k) && !"execution_id".equals(k)) {
                step.put(k, v);
            }
        });
        Map<String, Object> exec = executions.get(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(step);
    }

    @PutMapping("/agent-executions/{executionId}/steps/{stepId}/reassign")
    public ApiResult<Map<String, Object>> reassignStep(@PathVariable String executionId,
                                                       @PathVariable String stepId,
                                                       @RequestBody Map<String, Object> body) {
        Map<String, Object> step = requireStep(executionId, stepId);
        if (body.containsKey("assigned_participant_id")) {
            step.put("assigned_participant_id", body.get("assigned_participant_id"));
        }
        return ApiResult.ok(step);
    }

    @PostMapping("/agent-executions/{executionId}/steps/{stepId}/steer")
    public ApiResult<Void> steerStep(@PathVariable String executionId,
                                      @PathVariable String stepId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        requireStep(executionId, stepId);
        return ApiResult.ok();
    }

    @PostMapping("/agent-executions/{executionId}/steps/{stepId}/retry")
    public ApiResult<Map<String, Object>> retryStep(@PathVariable String executionId,
                                                     @PathVariable String stepId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/steps/{stepId}/adopt")
    public ApiResult<Map<String, Object>> adoptStep(@PathVariable String executionId,
                                                     @PathVariable String stepId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PatchMapping("/agent-executions/{executionId}/steps/{stepId}/configure")
    public ApiResult<Map<String, Object>> configureStep(@PathVariable String executionId,
                                                        @PathVariable String stepId,
                                                        @RequestBody Map<String, Object> body) {
        Map<String, Object> step = requireStep(executionId, stepId);
        body.forEach((k, v) -> {
            if (!"step_id".equals(k) && !"execution_id".equals(k)) {
                step.put(k, v);
            }
        });
        return ApiResult.ok(step);
    }

    @PostMapping("/agent-executions/{executionId}/steps/{stepId}/attempts/{attemptId}/answer")
    public ApiResult<Map<String, Object>> answerAttempt(@PathVariable String executionId,
                                                        @PathVariable String stepId,
                                                        @PathVariable String attemptId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @GetMapping("/agent-executions/{executionId}/events")
    public ApiResult<Map<String, Object>> listEvents(@PathVariable String executionId,
                                                     @RequestParam(required = false) Long after_sequence,
                                                     @RequestParam(required = false, defaultValue = "50") int limit) {
        requireExecution(executionId);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", new ArrayList<Map<String, Object>>());
        page.put("total", 0);
        page.put("has_more", false);
        return ApiResult.ok(page);
    }

    @GetMapping("/agent-executions/{executionId}/workspace")
    public ApiResult<List<Map<String, Object>>> executionWorkspace(@PathVariable String executionId,
                                                                  @RequestParam(required = false) String path,
                                                                  @RequestParam(required = false) String search) {
        requireExecution(executionId);
        return ApiResult.ok(new ArrayList<>());
    }

    // =====================================================================
    // Agent Execution Templates
    // =====================================================================

    @GetMapping("/agent-execution-templates")
    public ApiResult<List<Map<String, Object>>> listTemplates() {
        return ApiResult.ok(new ArrayList<>(templates.values()));
    }

    @GetMapping("/agent-execution-templates/{templateId}")
    public ApiResult<Map<String, Object>> getTemplate(@PathVariable String templateId) {
        return ApiResult.ok(toTemplateDetail(requireTemplate(templateId)));
    }

    @PostMapping("/agent-execution-templates")
    public ApiResult<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        String templateId = conversationStore.newId();
        Map<String, Object> tpl = new LinkedHashMap<>();
        tpl.put("execution_template_id", templateId);
        tpl.put("name", body.getOrDefault("name", "未命名模板"));
        tpl.put("description", body.get("description"));
        tpl.put("created_at", System.currentTimeMillis());
        tpl.put("updated_at", System.currentTimeMillis());
        templates.put(templateId, tpl);
        templateParticipants.put(templateId, new ArrayList<>());
        return ApiResult.ok(toTemplateDetail(tpl));
    }

    @PutMapping("/agent-execution-templates/{templateId}")
    public ApiResult<Map<String, Object>> updateTemplate(@PathVariable String templateId,
                                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> tpl = requireTemplate(templateId);
        if (body.containsKey("name")) {
            tpl.put("name", body.get("name"));
        }
        if (body.containsKey("description")) {
            tpl.put("description", body.get("description"));
        }
        tpl.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(toTemplateDetail(tpl));
    }

    @DeleteMapping("/agent-execution-templates/{templateId}")
    public ApiResult<Void> deleteTemplate(@PathVariable String templateId,
                                          @RequestParam(required = false, defaultValue = "1") long expected_version) {
        requireTemplate(templateId);
        templates.remove(templateId);
        templateParticipants.remove(templateId);
        return ApiResult.ok();
    }

    @PostMapping("/agent-execution-templates/{templateId}/create-execution")
    public ApiResult<Map<String, Object>> createExecutionFromTemplate(@PathVariable String templateId,
                                                                      @RequestBody(required = false) Map<String, Object> body) {
        requireTemplate(templateId);
        String executionId = conversationStore.newId();
        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("execution_id", executionId);
        exec.put("lead_conversation_id", body == null ? null : body.get("lead_conversation_id"));
        exec.put("execution_template_id", templateId);
        exec.put("status", "planned");
        exec.put("version", 1);
        exec.put("created_at", System.currentTimeMillis());
        exec.put("updated_at", System.currentTimeMillis());
        exec.put("participants", new ArrayList<Map<String, Object>>());
        executions.put(executionId, exec);
        executionSteps.put(executionId, new ArrayList<>());
        pushChange(executionId);
        return ApiResult.ok(exec);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private ApiResult<Map<String, Object>> commandExecution(String executionId, String status) {
        Map<String, Object> exec = requireExecution(executionId);
        exec.put("status", status);
        bumpVersion(exec);
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    private void pushChange(String executionId) {
        Map<String, Object> exec = executions.get(executionId);
        if (exec != null) {
            pushService.push("agentExecution.changed", toExecutionDetail(exec));
        }
    }

    private Map<String, Object> toExecutionDetail(Map<String, Object> exec) {
        Map<String, Object> detail = new LinkedHashMap<>(exec);
        List<Map<String, Object>> steps = executionSteps.get(exec.get("execution_id"));
        detail.put("steps", steps == null ? new ArrayList<>() : steps);
        return detail;
    }

    private Map<String, Object> requireExecution(String executionId) {
        Map<String, Object> exec = executions.get(executionId);
        if (exec == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "执行不存在: " + executionId);
        }
        return exec;
    }

    private Map<String, Object> requireStep(String executionId, String stepId) {
        List<Map<String, Object>> steps = executionSteps.get(executionId);
        if (steps == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "执行不存在: " + executionId);
        }
        return steps.stream()
                .filter(s -> stepId.equals(s.get("step_id")))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "步骤不存在: " + stepId));
    }

    private Map<String, Object> requireTemplate(String templateId) {
        Map<String, Object> tpl = templates.get(templateId);
        if (tpl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "执行模板不存在: " + templateId);
        }
        return tpl;
    }

    private Map<String, Object> toTemplateDetail(Map<String, Object> tpl) {
        Map<String, Object> detail = new LinkedHashMap<>(tpl);
        List<Map<String, Object>> participants = templateParticipants.get(tpl.get("execution_template_id"));
        detail.put("participants", participants == null ? new ArrayList<>() : participants);
        return detail;
    }

    private void bumpVersion(Map<String, Object> exec) {
        Object v = exec.get("version");
        long version = v instanceof Number ? ((Number) v).longValue() : 1;
        exec.put("version", version + 1);
        exec.put("updated_at", System.currentTimeMillis());
    }

    private void checkVersion(Map<String, Object> exec, long expectedVersion) {
        Object v = exec.get("version");
        long current = v instanceof Number ? ((Number) v).longValue() : 1;
        if (current != expectedVersion) {
            throw new ApiException(ApiErrorCode.CONFLICT, "版本冲突：期望 " + expectedVersion + " 实际 " + current);
        }
    }

    private List<Map<String, Object>> initBuiltinAgents() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildAgent("nomi", "Nomi", "nomi", "builtin", true, true));
        list.add(buildAgent("researcher", "研究员", "nomi", "internal", true, false));
        return list;
    }

    private Map<String, Object> buildAgent(String id, String name, String type, String source, boolean enabled, boolean available) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("agent_id", id);
        agent.put("name", name);
        agent.put("agent_type", type);
        agent.put("agent_source", source);
        agent.put("enabled", enabled);
        agent.put("available", available);
        return agent;
    }
}

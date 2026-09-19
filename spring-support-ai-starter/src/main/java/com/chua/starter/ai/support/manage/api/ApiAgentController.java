package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent（Agent）控制器，替代 nomicore 的同名契约。
 *
 * <p>负责三类资源：
 * <ul>
 *     <li>/api/agents —— 内置/自定义 Agent 元数据目录；</li>
 *     <li>/api/agent-executions —— 持久化Agent执行（含步骤、尝试、决策）；</li>
 *     <li>/api/agent-execution-templates —— 执行模板（可一键创建执行）。</li>
 * </ul>
 * 全部为进程内内存实现，主键使用 {@link SysAiConversationStore#newId()} 生成的 UUID；
 * 关键状态变更通过 {@link ApiWebSocketPushService} 推送 {@code agentExecution.changed} 等事件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiAgentController {

    /**
     * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 供应商 映射器（健康检测读取凭证与地址）
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商模型 映射器（健康检测读取能力并回写结果）
     */
    private final SysAiProviderModelMapper modelMapper;

    /**
     * 健康探测超时时间
     */
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 共享 HTTP 客户端（连接池复用）
     */
    private final HttpClient healthHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 内置 Agent 元数据目录（进程启动时初始化）
     */
    private final List<Map<String, Object>> builtinAgents = initBuiltinAgents();

    /**
     * 自定义 Agent 元数据目录
     */
    private final Map<String, Map<String, Object>> customAgents = new ConcurrentHashMap<>();

    /**
     * 执行聚合：执行_标识 → 执行主体
     */
    private final Map<String, Map<String, Object>> executions = new ConcurrentHashMap<>();

    /**
     * 执行步骤：执行_标识 → 步骤列表
     */
    private final Map<String, List<Map<String, Object>>> executionSteps = new ConcurrentHashMap<>();

    /**
     * 执行模板：执行_template_标识 → 模板主体
     */
    private final Map<String, Map<String, Object>> templates = new ConcurrentHashMap<>();

    /**
     * 模板参与者：执行_template_标识 → 参与者列表
     */
    private final Map<String, List<Map<String, Object>>> templateParticipants = new ConcurrentHashMap<>();

    // =====================================================================
 // Agent
    // =====================================================================

    @GetMapping("/agents")
    /**
     * 列表Agent。
     * @return 列表Agent的结果
     */
    public ApiResult<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> all = new ArrayList<>(builtinAgents);
        all.addAll(customAgents.values());
        return ApiResult.ok(all);
    }

    @PostMapping("/agents/refresh")
    /**
     * refresh习俗Agent。
     * @return refresh习俗Agent的结果
     */
    public ApiResult<Void> refreshCustomAgents() {
        // 内存实现：重新扫描自定义目录为幂等操作，无需实际 IO
        pushService.push("agent.listChanged", Map.of("action", "refreshed"));
        return ApiResult.ok();
    }

    // =====================================================================
 // 模型 Failover 队列（故障转移队列，Agent.模型_failover 偏好）
    // =====================================================================

    /**
     * 故障转移配置存储（单例 JSON blob，进程内持久）
     */
    private final Map<String, Object> modelFailoverConfig = new ConcurrentHashMap<>(Map.of(
            "enabled", false,
            "queue", new ArrayList<Map<String, Object>>(),
            "max_switches", 4));

    @GetMapping("/agent/model-failover")
    /**
     * 获取模型failover。
     * @return 获取模型failover的结果
     */
    public ApiResult<Map<String, Object>> getModelFailover() {
        return ApiResult.ok(new LinkedHashMap<>(modelFailoverConfig));
    }

    @PutMapping("/agent/model-failover")
    /**
     * 更新模型failover。
     * @param false false
     * @return 更新模型failover的结果
     * @param body 请求体，不允许为 null
     */
    public ApiResult<Map<String, Object>> updateModelFailover(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请求体不能为空");
        }
        Object enabled = body.get("enabled");
        if (!(enabled instanceof Boolean)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "enabled 必须为布尔值");
        }
        Object maxSwitches = body.getOrDefault("max_switches", 4);
        if (!(maxSwitches instanceof Number number) || number.intValue() < 1 || number.intValue() > 20) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "max_switches 必须在 1..20 之间");
        }
        if (!(body.get("queue") instanceof List<?> queue)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "queue 必须为数组");
        }
        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Object item : queue) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "queue 元素必须为对象");
            }
            Object providerId = ((Map<?, ?>) raw).get("provider_id");
            Object model = ((Map<?, ?>) raw).get("model");
            if (providerId == null || String.valueOf(providerId).isBlank()
                    || model == null || String.valueOf(model).isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "queue 元素缺少 provider_id / model");
            }
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("provider_id", String.valueOf(providerId));
            candidate.put("model", String.valueOf(model));
            cleaned.add(candidate);
        }

        modelFailoverConfig.put("enabled", enabled);
        modelFailoverConfig.put("max_switches", ((Number) maxSwitches).intValue());
        modelFailoverConfig.put("queue", cleaned);
        return ApiResult.ok(new LinkedHashMap<>(modelFailoverConfig));
    }

    @PostMapping("/agents/provider-health-check")
    /**
     * 提供者健康检查。
     * @param false false
     * @return 提供者健康检查的结果
     * @param body 请求体，不允许为 null
     */
    public ApiResult<Map<String, Object>> providerHealthCheck(@RequestBody(required = false) Map<String, Object> body) {
        String providerId = str(body == null ? null : body.get("provider_id"));
        String modelName = str(body == null ? null : body.get("model"));
        String task = str(body == null ? null : body.get("task"));
        if (providerId == null || modelName == null || task == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "provider_id / model / task 均为必填");
        }

        // 1. 加载供应商与模型行
        SysAiProvider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        SysAiProviderModel modelRow = modelMapper.selectOne(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)
                .eq(SysAiProviderModel::getModel, modelName));
        if (modelRow == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "模型不存在: " + modelName);
        }

        // 2. 定位任务能力（无该任务能力直接参数错误）
        Map<String, Object> capability = null;
        if (modelRow.getCapabilities() != null) {
            for (Map<String, Object> cap : modelRow.getCapabilities()) {
                if (task.equals(str(cap.get("task")))) {
                    capability = cap;
                    break;
                }
            }
        }
        if (capability == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "模型 " + modelName + " 未声明任务能力: " + task);
        }

        // 3. 真实探测上游
        long start = System.currentTimeMillis();
        ProbeOutcome outcome = probe(provider, capability, modelName);

 // 4. 持久化探测结果到能力 JSON（健康 + 健康_检查_at）
        long now = System.currentTimeMillis();
        long latency = outcome.latencyMs >= 0 ? outcome.latencyMs : System.currentTimeMillis() - start;
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", outcome.status);
        health.put("latency", latency);
        if (outcome.error != null && !outcome.error.isBlank()) {
            health.put("error", outcome.error);
        }
        capability.put("health", health);
        capability.put("health_checked_at", System.currentTimeMillis());
        modelRow.setUpdatedAt(now);
 // 复合主键 (提供者_id, 模型)：显式按双键更新，避免同名模型跨供应商误伤
        modelMapper.update(modelRow, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)
                .eq(SysAiProviderModel::getModel, modelName));

        // 5. 组装契约响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider_id", providerId);
        result.put("platform", provider.getPlatform());
        result.put("model", modelName);
        result.put("task", task);
        result.put("status", outcome.status);
        result.put("elapsed_ms", latency);
        if (outcome.error != null && !outcome.error.isBlank()) {
            result.put("message", outcome.error);
        }
        if (outcome.errorKind != null) {
            result.put("error_kind", outcome.errorKind);
        }
        if (outcome.httpStatus > 0) {
            result.put("http_status", outcome.httpStatus);
        }
        return ApiResult.ok(result);
    }

    /**
     * 按能力协议对上游发起最小真实请求。
     *
     * <p>chat → 最小补全（max_tokens=1）；embedding → 最小向量请求；
     * 其余（图像/视频等生成类）→ 获取 /模型 校验连通性与鉴权，避免产生实际生成费用。</p>
     * @param provider 提供者
     * @param capability capability
     * @param modelName 模型名称
     * @return 探针的结果
     */
    private ProbeOutcome probe(SysAiProvider provider, Map<String, Object> capability, String modelName) {
 // ONNX 本地离线模型：基础_url 即模型文件路径，只校验文件存在与可读
        if ("onnx".equalsIgnoreCase(str(provider.getPlatform()))) {
            String modelPath = str(capability.get("base_url_override"));
            if (modelPath == null || modelPath.isBlank()) {
                modelPath = provider.getBaseUrl();
            }
            if (modelPath == null || modelPath.isBlank()) {
                return new ProbeOutcome("unhealthy", -1, "invalid_request", 0, "未配置模型文件路径");
            }
            java.io.File f = new java.io.File(modelPath);
            if (!f.exists()) {
                return new ProbeOutcome("unhealthy", -1, "not_found", 0, "模型文件不存在: " + modelPath);
            }
            if (!f.canRead()) {
                return new ProbeOutcome("unhealthy", -1, "forbidden", 0, "模型文件不可读: " + modelPath);
            }
            /**
             * 探针outcome。
             * @param "healthy" "healthy"
             * @param -1 -1
             * @param null 空
             * @param 0 0
             * @param null 空
             * @return 探针outcome的结果
             */
            return new ProbeOutcome("healthy", -1, null, 0, null);
        }
        String protocol = str(capability.get("protocol"));
        String overrideUrl = str(capability.get("base_url_override"));
        String baseUrl = overrideUrl != null && !overrideUrl.isBlank() ? overrideUrl : provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return new ProbeOutcome("unhealthy", -1, "invalid_request", 0, "base_url 未配置");
        }
        String apiKey = extractApiKey(provider.getCredentials());
        boolean chatProtocol = protocol != null && protocol.contains("chat");
        boolean embeddingProtocol = protocol != null && protocol.contains("embedding");

        try {
            HttpRequest request;
            if (chatProtocol) {
 // 打开AI 兼容最小补全
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", modelName);
                payload.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                payload.put("max_tokens", 1);
                payload.put("stream", false);
                request = buildProbeRequest(baseUrl + "/chat/completions", apiKey, writeJson(payload));
            } else if (embeddingProtocol) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", modelName);
                payload.put("input", "ping");
                request = buildProbeRequest(baseUrl + "/embeddings", apiKey, writeJson(payload));
            } else {
                // 生成类任务只校验地址可达与鉴权有效
                StringBuilder url = new StringBuilder(baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models");
                request = buildProbeRequest(url.toString(), apiKey, null);
            }

            HttpResponse<String> response = healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new ProbeOutcome("healthy", -1, null, status, null);
            }
            /**
             * 探针outcome。
             * @param "unhealthy" "unhealthy"
             * @param -1 -1
             * @param mapErrorKind(status 映射错误种类(状态
             * @return 探针outcome的结果
             */
            return new ProbeOutcome("unhealthy", -1, mapErrorKind(status), status, httpErrorMessage(status, response.body()));
        } catch (java.net.http.HttpTimeoutException e) {
            return new ProbeOutcome("unhealthy", -1, "timeout", 0, "上游请求超时（15s）");
        } catch (java.net.ConnectException e) {
            return new ProbeOutcome("unhealthy", -1, "connection_error", 0, "无法连接上游: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[agent] provider health probe failed: {}", e.getMessage());
            /**
             * 探针outcome。
             * @param "unhealthy" "unhealthy"
             * @param -1 -1
             * @param "connection_error" "connection_错误"
             * @param 0 0
             * @param e.getMessage( e.获取消息(
             * @return 探针outcome的结果
             */
            return new ProbeOutcome("unhealthy", -1, "connection_error", 0, "探测失败: " + e.getMessage());
        }
    }

    /**
     * 构建探测 HTTP 请求；获取 时 主体 为 空。
     * @param url url
     * @param apiKey apiKey
     * @param jsonBody json主体
     * @return 构建探针请求的结果
     */
    private HttpRequest buildProbeRequest(String url, String apiKey, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HEALTH_TIMEOUT);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    /**
     * 序列化 JSON；失败返回空对象文本。
     * @param payload payload
     * @return 写入json的结果
     */
    private String writeJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * HTTP 状态码 → 前端契约错误类别。
     * @param status 状态
     * @return 映射错误种类的结果
     */
    private String mapErrorKind(int status) {
        switch (status) {
            case 400: return "invalid_request";
            case 401: return "unauthorized";
            case 403: return "forbidden";
            case 404: return "not_found";
            case 429: return "rate_limited";
            default: return status >= 500 ? "api_error" : "unknown";
        }
    }

    /**
     * 依据状态码生成简明错误信息。
     * @param status 状态
     * @param body 主体
     * @return http错误消息的结果
     */
    private String httpErrorMessage(int status, String body) {
        String hint;
        switch (status) {
            case 400:
                hint = "请求被拒绝（协议或参数不兼容）";
                break;
            case 401:
                hint = "API Key 无效或已过期";
                break;
            case 403:
                hint = "无权访问该模型";
                break;
            case 404:
                hint = "模型或接口路径不存在";
                break;
            case 429:
                hint = "请求过于频繁或配额受限";
                break;
            default:
                hint = "上游返回 " + status;
                break;
        }
        // 截断响应片段辅助排障
        String snippet = body == null ? "" : body.replaceAll("\\s+", " ");
        if (snippet.length() > 200) {
            snippet = snippet.substring(0, 200) + "...";
        }
        return snippet.isEmpty() ? hint : hint + ": " + snippet;
    }

    /**
     * 从凭证中提取首个可用 API Key（兼容 api_Key 与 api_Key 形态）。
     * @param credentials 凭证
     * @return extractapiKey的结果
     */
    private String extractApiKey(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        Object single = credentials.get("api_key");
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        Object multi = credentials.get("api_keys");
        if (multi instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * str。
     * @param value 值
     * @return str的结果
     */
    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 探测结果内部载体。
     * @param status 状态
     * @param latencyMs 延迟ms
     * @param errorKind 错误种类
     * @param httpStatus http状态
     * @param error 错误
     * @return 探针outcome的结果
     */
    private record ProbeOutcome(String status, long latencyMs, String errorKind, int httpStatus, String error) {
    }

    // =====================================================================
 // Agent Executions
    // =====================================================================

    @GetMapping("/agent-executions")
    /**
     * 列表executions。
     * @return 列表executions的结果
     */
    public ApiResult<List<Map<String, Object>>> listExecutions() {
        return ApiResult.ok(new ArrayList<>(executions.values()));
    }

    @PostMapping("/agent-executions")
    /**
     * 创建执行。
     * @param false false
     * @return 创建执行的结果
     * @param body 请求体，不允许为 null
     */
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
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(exec);
    }

    @GetMapping("/agent-executions/{executionId}")
    /**
     * 获取执行。
     * @param executionId 执行标识
     * @return 获取执行的结果
     */
    public ApiResult<Map<String, Object>> getExecution(@PathVariable String executionId) {
        Map<String, Object> exec = requireExecution(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @DeleteMapping("/agent-executions/{executionId}")
    public ApiResult<Void> deleteExecution(@PathVariable String executionId,
                                           @RequestParam(required = false, defaultValue = "1") long expected_version) {
        Map<String, Object> exec = requireExecution(executionId);
        /**
         * 检查版本。
         * @param exec 执行
         * @param expected_version 期望版本
         */
        checkVersion(exec, expected_version);
        executions.remove(executionId);
        executionSteps.remove(executionId);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/replan")
    public ApiResult<Map<String, Object>> replanExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/adjust")
    public ApiResult<Map<String, Object>> adjustExecution(@PathVariable String executionId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    @PostMapping("/agent-executions/{executionId}/steps/{stepId}/adopt")
    public ApiResult<Map<String, Object>> adoptStep(@PathVariable String executionId,
                                                     @PathVariable String stepId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> exec = requireExecution(executionId);
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
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
 // Agent 执行 Templates
    // =====================================================================

    @GetMapping("/agent-execution-templates")
    /**
     * 列表templates。
     * @return 列表templates的结果
     */
    public ApiResult<List<Map<String, Object>>> listTemplates() {
        return ApiResult.ok(new ArrayList<>(templates.values()));
    }

    @GetMapping("/agent-execution-templates/{templateId}")
    /**
     * 获取template。
     * @param templateId templateid
     * @return 获取template的结果
     */
    public ApiResult<Map<String, Object>> getTemplate(@PathVariable String templateId) {
        return ApiResult.ok(toTemplateDetail(requireTemplate(templateId)));
    }

    @PostMapping("/agent-execution-templates")
    /**
     * 创建template。
     * @param body 主体
     * @return 创建template的结果
     */
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
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(exec);
    }

    // =====================================================================
 // 助手
    // =====================================================================

    /**
     * 命令执行。
     * @param executionId 执行标识
     * @param status 状态
     * @return 命令执行的结果
     */
    private ApiResult<Map<String, Object>> commandExecution(String executionId, String status) {
        Map<String, Object> exec = requireExecution(executionId);
        exec.put("status", status);
        /**
         * bump版本。
         * @param exec 执行
         */
        bumpVersion(exec);
        /**
         * push改变。
         * @param executionId 执行标识
         */
        pushChange(executionId);
        return ApiResult.ok(toExecutionDetail(exec));
    }

    /**
     * push改变。
     * @param executionId 执行标识
     */
    private void pushChange(String executionId) {
        Map<String, Object> exec = executions.get(executionId);
        if (exec != null) {
            pushService.push("agentExecution.changed", toExecutionDetail(exec));
        }
    }

    /**
     * 转为执行detail。
     * @param exec 执行
     * @return 转为执行detail的结果
     */
    private Map<String, Object> toExecutionDetail(Map<String, Object> exec) {
        Map<String, Object> detail = new LinkedHashMap<>(exec);
        List<Map<String, Object>> steps = executionSteps.get(exec.get("execution_id"));
        detail.put("steps", steps == null ? new ArrayList<>() : steps);
        return detail;
    }

    /**
     * require执行。
     * @param executionId 执行标识
     * @return require执行的结果
     */
    private Map<String, Object> requireExecution(String executionId) {
        Map<String, Object> exec = executions.get(executionId);
        if (exec == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "执行不存在: " + executionId);
        }
        return exec;
    }

    /**
     * requirestep。
     * @param executionId 执行标识
     * @param stepId stepid
     * @return requireStep的结果
     */
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

    /**
     * requiretemplate。
     * @param templateId templateid
     * @return requireTemplate的结果
     */
    private Map<String, Object> requireTemplate(String templateId) {
        Map<String, Object> tpl = templates.get(templateId);
        if (tpl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "执行模板不存在: " + templateId);
        }
        return tpl;
    }

    /**
     * 转为templatedetail。
     * @param tpl tpl
     * @return 转为templatedetail的结果
     */
    private Map<String, Object> toTemplateDetail(Map<String, Object> tpl) {
        Map<String, Object> detail = new LinkedHashMap<>(tpl);
        List<Map<String, Object>> participants = templateParticipants.get(tpl.get("execution_template_id"));
        detail.put("participants", participants == null ? new ArrayList<>() : participants);
        return detail;
    }

    /**
     * bump版本。
     * @param exec 执行
     */
    private void bumpVersion(Map<String, Object> exec) {
        Object v = exec.get("version");
        long version = v instanceof Number ? ((Number) v).longValue() : 1;
        exec.put("version", version + 1);
        exec.put("updated_at", System.currentTimeMillis());
    }

    /**
     * 检查版本。
     * @param exec 执行
     * @param expectedVersion 期望版本
     */
    private void checkVersion(Map<String, Object> exec, long expectedVersion) {
        Object v = exec.get("version");
        long current = v instanceof Number ? ((Number) v).longValue() : 1;
        if (current != expectedVersion) {
            throw new ApiException(ApiErrorCode.CONFLICT, "版本冲突：期望 " + expectedVersion + " 实际 " + current);
        }
    }

    /**
     * 初始化builtinAgent。
     * @return 初始化builtinAgent的结果
     */
    private List<Map<String, Object>> initBuiltinAgents() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildAgent("nomi", "Nomi", "nomi", "builtin", true, true));
        list.add(buildAgent("researcher", "研究员", "nomi", "internal", true, false));
        return list;
    }

    /**
     * 构建Agent。
     * @param id 标识
     * @param name 名称
     * @param type 类型
     * @param source 源
     * @param enabled 已启用
     * @param available 可用
     * @return 构建Agent的结果
     */
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


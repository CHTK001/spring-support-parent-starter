package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.AiModelCatalogService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 打开AI 标准协议控制器。
 *
 * <p>提供 OpenAI 兼容的 Chat Completions、Completions、Models 接口，
 * 以及 CC Switch 模型切换控制接口。
 * 继承 {@link BaseAiController} 统一使用 Bearer 令牌 鉴权与分组解析。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /v2/v1/chat/completions} — OpenAI Chat Completions</li>
 *   <li>{@code POST /v2/v1/completions} — OpenAI Completions（旧版）</li>
 *   <li>{@code GET /v2/v1/models} — 模型列表</li>
 *   <li>{@code GET /v2/v1/cc/switch/models} — CC Switch 可用模型</li>
 *   <li>{@code POST /v2/v1/cc/switch} — 切换模型（持久化到全局设置）</li>
 *   <li>{@code GET /v2/v1/cc/switch/status} — 当前模型状态</li>
 *   <li>{@code POST /v2/v1/cc/switch/batch} — 批量配置</li>
 *   <li>{@code GET /v2/v1/cc/switch/health} — 健康检查</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "AI OpenAI Protocol Controller", description = "OpenAI 标准协议接口与 CC Switch 控制接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
@RequestMapping("/v2")
public class AiOpenaiController extends BaseAiController {

    /**
     * AI 对话/补全服务（绑定所有聊天模型）
     */
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * AI 模型指标富化服务
    */
    private final com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService modelMetricsEnrichmentService;

    /**
     * AI 模型能力目录（按能力类目返回模型 标识 列表）
    */
    private final AiModelCatalogService aiModelCatalogService;

    /**
     * SSE 超时时间（毫秒）
     */
    private static final long SSE_TIMEOUT_MILLIS = 30_000L;

    /**
     * 打开AI 对话 Completions 接口。
     *
     * <p>接收 OpenAI 标准格式的聊天补全请求，支持同步与流式两种模式。
     * 鉴权通过后，将请求转换为 {@link AiChatRequest} 并委托给 {@link AiChatFacadeService} 处理。</p>
     *
     * <p>流式模式下返回 SSE 事件流，非流式模式返回标准 JSON 响应。</p>
     *
     * @param request        聊天补全请求体
     * @param servletRequest HTTP 请求
     * @return 同步模式下返回 {@link ResponseEntity}，流式模式下返回 {@link SseEmitter}
     */
    @Operation(summary = "OpenAI Chat Completions", description = "OpenAI 标准协议聊天补全接口")
    @PostMapping("/v1/chat/completions")
    public Object chatCompletions(@RequestBody OpenAiChatRequest request,
                                  HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            if (Boolean.TRUE.equals(request.getStream())) {
                return disabledEmitter("OpenAI RESTful 端口未启用");
            }
            return ResponseEntity.ok(buildOpenAiChatResponse(null, request.getModel()));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            if (Boolean.TRUE.equals(request.getStream())) {
                return authErrorEmitter();
            }
            return authErrorResponse();
        }
 // 解析分组 标识 与 appKey
        Integer groupId = resolveGroupId(servletRequest);
        String appKey = resolveAppKey(servletRequest);
        // 流式模式
        if (Boolean.TRUE.equals(request.getStream())) {
            return aiChatFacadeService.stream(convertToChatRequest(request, groupId, appKey));
        }
        // 同步模式
        try {
            AiChatRequest chatRequest = convertToChatRequest(request, groupId, appKey);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(buildOpenAiChatResponse(result, request.getModel()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(buildOpenAiChatError(request.getModel(), ex.getMessage()));
        }
    }

    /**
     * 打开AI Completions 接口（旧版补全）。
     *
     * <p>接收 OpenAI 标准格式的文本补全请求，仅支持同步非流式响应。</p>
     *
     * @param request        文本补全请求体
     * @param servletRequest HTTP 请求
     * @return 补全响应
     */
    @Operation(summary = "OpenAI Completions", description = "OpenAI 标准协议补全接口")
    @PostMapping("/v1/completions")
    public ResponseEntity<Map<String, Object>> completions(@RequestBody OpenAiCompletionRequest request,
                                                            HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(buildOpenAiCompletionResponse(null, request.getModel(), request.getPrompt()));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 执行对话
        try {
            AiChatRequest chatRequest = convertCompletionToChatRequest(request);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            return ResponseEntity.ok(buildOpenAiCompletionResponse(result, request.getModel(), request.getPrompt()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(buildOpenAiCompletionError(request.getModel(), request.getPrompt(), ex.getMessage()));
        }
    }

    /**
     * 打开AI 模型 接口。
     *
     * <p>返回当前可用的模型列表。若 {@link AiChatFacadeService#modelDefinitions(Integer)}
     * 返回非空列表，则优先使用详细模型定义（含模型名称、描述、能力列表），
     * 否则回退到 {@link AiChatFacadeService#models(Integer)} 返回的模型 标识 列表。</p>
     *
     * @param servletRequest HTTP 请求
     * @return 模型列表响应
     */
    @Operation(summary = "OpenAI Models", description = "获取支持的模型列表")
    @GetMapping("/v1/models")
    public ResponseEntity<Map<String, Object>> models(HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 解析分组并查询模型
        Integer groupId = resolveGroupId(servletRequest);
        List<ModelDefinition> modelDefinitions = aiChatFacadeService.modelDefinitions(groupId);
        List<Map<String, Object>> data = new ArrayList<>();
        if (!modelDefinitions.isEmpty()) {
            // 优先使用详细模型定义
            for (ModelDefinition def : modelDefinitions) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", def.getId());
                row.put("object", "model");
                row.put("created", (int) (System.currentTimeMillis() / 1000));
                row.put("owned_by", "system");
                row.put("name", def.getName() != null ? def.getName() : def.getId());
                row.put("description", def.getDescription() != null ? def.getDescription() : "");
                row.put("capabilities", def.getCapabilities() != null ? def.getCapabilities() : List.of());
                modelMetricsEnrichmentService.enrich(def.getId(), row);
                data.add(row);
            }
        } else {
 // 回退到模型 标识 列表
            List<String> modelIds = aiChatFacadeService.models(groupId);
            for (String id : modelIds) {
                data.add(Map.of(
                        "id", id,
                        "object", "model",
                        "created", (int) (System.currentTimeMillis() / 1000),
                        "owned_by", "system",
                        "name", id,
                        "description", "",
                        "capabilities", List.of()
                ));
            }
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    // ==================== CC Switch（模型切换） ====================

    /**
     * CC Switch 全局设置键前缀。
     *
     * <p>完整键格式为 {@code cc.model[.groupId]}，无分组时为 {@code cc.model}。</p>
     */
    private static final String CC_MODEL_KEY_PREFIX = "cc.model";

    /**
     * CC Switch 全局设置分组。
     */
    private static final String CC_SETTING_GROUP = "runtime";

    /**
     * CC Switch - 获取可用模型列表。
     *
     * <p>返回当前 AppKey 所在分组可用的模型列表。</p>
     *
     * @param servletRequest HTTP 请求
     * @return 可用模型列表
     */
    @Operation(summary = "CC Switch - 获取可用模型列表", description = "获取支持切换的模型列表")
    @GetMapping("/v1/cc/switch/models")
    public ResponseEntity<Map<String, Object>> ccSwitchModels(HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("object", "list", "data", List.of()));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 查询分组可用模型
        Integer groupId = resolveGroupId(servletRequest);
        List<String> modelIds = aiChatFacadeService.models(groupId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String id : modelIds) {
            data.add(Map.of("id", id, "object", "model"));
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    /**
     * CC Switch - 切换模型。
     *
     * <p>将当前 AppKey 所在分组的选中模型持久化到全局设置表。
     * 存储键为 {@code runtime.cc.model[.groupId]}，支持按分组隔离。</p>
     *
     * @param request        切换请求体，包含目标模型名称
     * @param servletRequest HTTP 请求
     * @return 切换结果
     */
    @Operation(summary = "CC Switch - 切换模型", description = "切换当前使用的模型")
    @PostMapping("/v1/cc/switch")
    public ResponseEntity<Map<String, Object>> ccSwitch(@RequestBody CcSwitchRequest request,
                                                         HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "CC Switch 服务未启用"));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 校验模型名称
        String model = request.getModel();
        if (StringUtils.isBlank(model)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "模型名称不能为空"));
        }
        // 持久化模型选择
        try {
            Integer groupId = resolveGroupId(servletRequest);
            String key = buildCcModelKey(groupId);
            /**
             * persistcc模型。
             * @param key Key
             * @param model 模型
             */
            persistCcModel(key, model);
            log.info("CC Switch 模型切换成功: groupId={}, model={}", groupId, model);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "model", model,
                    "message", "模型切换成功"
            ));
        } catch (Exception ex) {
            log.error("CC Switch 模型切换失败", ex);
            return ResponseEntity.ok(Map.of("success", false, "message", "模型切换失败: " + ex.getMessage()));
        }
    }

    /**
     * CC Switch - 获取当前模型状态。
     *
     * <p>返回当前 AppKey 所在分组的选中模型、可用模型列表及时间戳。</p>
     *
     * @param servletRequest HTTP 请求
     * @return 模型状态
     */
    @Operation(summary = "CC Switch - 获取当前模型状态", description = "获取当前模型配置状态")
    @GetMapping("/v1/cc/switch/status")
    public ResponseEntity<Map<String, Object>> ccSwitchStatus(HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("status", "disabled", "message", "CC Switch 服务未启用"));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 查询当前模型与可用列表
        Integer groupId = resolveGroupId(servletRequest);
        String key = buildCcModelKey(groupId);
        String currentModel = sysAiGlobalSettingService.getValue(CC_SETTING_GROUP, key)
                .orElse(null);
        List<String> available = aiChatFacadeService.models(groupId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (String id : available) {
            data.add(Map.of("id", id, "object", "model"));
        }
        return ResponseEntity.ok(Map.of(
                "currentModel", currentModel != null ? currentModel : "未设置",
                "availableModels", data,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * CC Switch - 批量模型配置。
     *
     * <p>接收多个模型的优先级和权重配置（当前仅做占位返回，实际配置逻辑待扩展）。</p>
     *
     * @param request        批量配置请求体
     * @param servletRequest HTTP 请求
     * @return 批量配置结果
     */
    @Operation(summary = "CC Switch - 批量模型配置", description = "批量配置多个模型的优先级和权重")
    @PostMapping("/v1/cc/switch/batch")
    public ResponseEntity<Map<String, Object>> ccSwitchBatch(@RequestBody CcBatchSwitchRequest request,
                                                              HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "OpenAI RESTful 端口未启用"));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        // 批量配置处理
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configs", request.getConfigs(),
                    "message", "批量配置成功"
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", "批量配置失败: " + ex.getMessage()));
        }
    }

    /**
     * CC Switch - 健康检查。
     *
     * <p>返回 CC Switch 服务的健康状态与版本信息。</p>
     *
     * @param servletRequest HTTP 请求
     * @return 健康检查结果
     */
    @Operation(summary = "CC Switch - 健康检查", description = "检查 CC Switch 服务健康状态")
    @GetMapping("/v1/cc/switch/health")
    public ResponseEntity<Map<String, Object>> ccSwitchHealth(HttpServletRequest servletRequest) {
        // 检查 REST 开关
        if (!restEnabled()) {
            return ResponseEntity.ok(Map.of("status", "disabled"));
        }
        // 鉴权校验
        if (!authCheck(servletRequest)) {
            return authErrorResponse();
        }
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "version", "1.0.0"
        ));
    }

    /**
     * 按能力类目返回已注册模型 标识 列表。
     * <p>供前端设置页（OCR/人脸/图片/布局）的能力筛选下拉框使用——能力由
     * {@link AiModelCatalogService} 聚合（本地 ModelRegistry + 云端 SPI 客户端），
     * 返回每个能力类目下已加载的模型 标识 列表。</p>
     * @return 模型capabilities的结果
     */
    @Operation(summary = "按能力类目返回模型", description = "返回每个能力类目下已注册的模型 ID 列表")
    @GetMapping("/v2/ai/models/capabilities")
    public Map<String, List<String>> modelCapabilities() {
        return aiModelCatalogService.listModelsByCapability();
    }


    /**
     * 构建 CC 模型 存储键。
     *
     * <p>有分组时返回 {@code cc.model.groupId}，无分组时返回 {@code cc.model}。</p>
     *
     * @param groupId 分组 标识，可能为 {@code null}
     * @return 存储键
     */
    private static String buildCcModelKey(Integer groupId) {
        if (groupId != null) {
            return CC_MODEL_KEY_PREFIX + "." + groupId;
        }
        return CC_MODEL_KEY_PREFIX;
    }

    /**
     * 持久化 CC 模型 配置到全局设置表。
     *
     * <p>使用 {@link SysAiGlobalSettingService#upsertValue(String, String, String, String)}
     * 增量写入指定键，避免 替换 先删后插清空 runtime 分组下 提供者/Key 等其它配置。</p>
     *
     * @param key   存储键（如 {@code cc.model} 或 {@code cc.model.2}）
     * @param model 模型名称
     */
    @SuppressWarnings("unchecked")
    private void persistCcModel(String key, String model) {
        sysAiGlobalSettingService.upsertValue(CC_SETTING_GROUP, key, model, "CC Switch 选中模型");
    }

    // ==================== 响应构建器 ====================

    /**
     * 构建 打开AI 对话 Completions 成功响应。
     *
     * @param result 对话结果，可能为 {@code null}
     * @param model  模型名称
     * @return OpenAI 标准格式的聊天补全响应
     */
    private Map<String, Object> buildOpenAiChatResponse(Map<String, Object> result, String model) {
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String text = result != null ? (String) result.get("text") : "";
        String resolvedModel = model != null ? model : "unknown";

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", text);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

 // 模型调用失败（call() 返回 {错误:{...}}）时，把错误信息作为 内容 返回，避免前端收到 200 空文本
        if (result != null && result.containsKey("error")) {
            Object error = result.get("error");
            String errorMsg;
            if (error instanceof Map) {
                Object msg = ((Map<?, ?>) error).get("message");
                errorMsg = msg != null ? String.valueOf(msg) : "模型调用失败";
            } else {
                errorMsg = String.valueOf(error);
            }
            message.put("content", errorMsg);
            choice.put("message", message);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "chat.completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", resolvedModel);
        body.put("choices", List.of(choice));

        // 填充用量统计
        if (result != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", result.getOrDefault("promptTokens", 0));
            usage.put("completion_tokens", result.getOrDefault("completionTokens", 0));
            usage.put("total_tokens", result.getOrDefault("totalTokens", 0));
            body.put("usage", usage);
        }
        return body;
    }

    /**
     * 构建 打开AI 对话 Completions 错误响应。
     *
     * @param model        模型名称
     * @param errorMessage 错误描述
     * @return OpenAI 标准格式的错误响应
     */
    private Map<String, Object> buildOpenAiChatError(String model, String errorMessage) {
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", errorMessage);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "chat.completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", model != null ? model : "unknown");
        body.put("choices", List.of(choice));
        return body;
    }

    /**
     * 构建 打开AI Completions（旧版）成功响应。
     *
     * @param result 对话结果
     * @param model  模型名称
     * @param prompt 用户提示词
     * @return OpenAI 标准格式的补全响应
     */
    private Map<String, Object> buildOpenAiCompletionResponse(Map<String, Object> result, String model, String prompt) {
        String id = "cmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String text = result != null ? (String) result.get("text") : "";
 // 模型调用失败（call() 返回 {错误:{...}}）时，把错误信息作为 文本 返回
        if (result != null && result.containsKey("error")) {
            Object error = result.get("error");
            if (error instanceof Map) {
                Object msg = ((Map<?, ?>) error).get("message");
                text = msg != null ? String.valueOf(msg) : "模型调用失败";
            } else {
                text = String.valueOf(error);
            }
        }
        String resolvedModel = model != null ? model : "unknown";
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("text", text);
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "text_completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", resolvedModel);
        body.put("choices", List.of(choice));
        if (result != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", result.getOrDefault("promptTokens", 0));
            usage.put("completion_tokens", result.getOrDefault("completionTokens", 0));
            usage.put("total_tokens", result.getOrDefault("totalTokens", 0));
            body.put("usage", usage);
        }
        return body;
    }

    /**
     * 构建 打开AI Completions（旧版）错误响应。
     *
     * @param model        模型名称
     * @param prompt       用户提示词
     * @param errorMessage 错误描述
     * @return OpenAI 标准格式的错误响应
     */
    private Map<String, Object> buildOpenAiCompletionError(String model, String prompt, String errorMessage) {
        String id = "cmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("text", errorMessage);
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "text_completion");
        body.put("created", (int) (System.currentTimeMillis() / 1000));
        body.put("model", model != null ? model : "unknown");
        body.put("choices", List.of(choice));
        return body;
    }

    // ==================== 请求转换 ====================

    /**
     * 将 打开AI 对话 Completions 请求转换为内部 {@link AiChatRequest}。
     *
     * <p>提取 model、messages（system/user 角色）、分组 ID、AppKey、think/search 等字段。</p>
     *
     * @param request 打开AI 格式的聊天请求
     * @param groupId 分组 标识，用于模型隔离
     * @param appKey  appKey，用于 令牌 配额累计
     * @return 内部对话请求对象
     */
    private AiChatRequest convertToChatRequest(OpenAiChatRequest request, Integer groupId, String appKey) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setModel(request.getModel());
 // 注入分组 标识 到 参数，供 AI对话facade服务 进行模型隔离
        if (groupId != null || StringUtils.isNotBlank(appKey)) {
            chatRequest.setParameters(new LinkedHashMap<>());
            if (groupId != null) {
                chatRequest.getParameters().put("_groupId", groupId);
            }
            if (StringUtils.isNotBlank(appKey)) {
                chatRequest.getParameters().put("_appKey", appKey);
            }
        }
        // 深度思考与智能搜索开关透传
        chatRequest.setThink(request.getThink());
        chatRequest.setSearch(request.getSearch());
        // 提取消息内容
        if (request.getMessages() != null) {
            StringBuilder promptBuilder = new StringBuilder();
            for (OpenAiMessage msg : request.getMessages()) {
                if ("system".equals(msg.getRole())) {
                    chatRequest.setPrompt(msg.getContent());
                } else if ("user".equals(msg.getRole())) {
                    promptBuilder.append(msg.getContent()).append("\n");
                }
            }
            if (promptBuilder.length() > 0) {
                chatRequest.setPrompt(promptBuilder.toString().trim());
            }
        }
        return chatRequest;
    }

    /**
     * 将 打开AI Completions（旧版）请求转换为内部 {@link AiChatRequest}。
     *
     * @param request 打开AI 格式的补全请求
     * @return 内部对话请求对象
     */
    private AiChatRequest convertCompletionToChatRequest(OpenAiCompletionRequest request) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setModel(request.getModel());
        chatRequest.setPrompt(request.getPrompt());
        return chatRequest;
    }

    // ==================== 内部 DTO ====================

    /**
     * 打开AI 对话 Completions 请求体。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class OpenAiChatRequest {

        /**
         * 模型名称
         */
        private String model;

        /**
         * 消息列表
         */
        private List<OpenAiMessage> messages;

        /**
         * 采样温度
         */
        private Double temperature;

        /**
         * 最大输出 令牌 数
         */
        private Integer maxTokens;

        /**
         * Top-P 采样参数
         */
        private Double topP;

        /**
         * 生成候选项数
         */
        private Integer n;

        /**
         * 是否启用流式响应
         */
        private Boolean stream;

        /**
         * 停止序列
         */
        private String stop;

        /**
         * 频率惩罚系数
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚系数
         */
        private Double presencePenalty;

        /**
         * 工具调用定义
         */
        private Map<String, Object> tools;

        /**
         * 工具选择策略
         */
        private String toolChoice;

        /**
         * 深度思考开关（非 打开AI 标准字段，由前端 对话客户端 透传）
         */
        private Boolean think;

        /**
         * 智能搜索开关（非 打开AI 标准字段，由前端 对话客户端 透传）
         */
        private Boolean search;
    }

    /**
     * 打开AI 消息体。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class OpenAiMessage {

        /**
         * 角色（系统 / 用户 / assistant）
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 消息作者名称
         */
        private String name;
    }

    /**
     * 打开AI Completions（旧版）请求体。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class OpenAiCompletionRequest {

        /**
         * 模型名称
         */
        private String model;

        /**
         * 提示词
         */
        private String prompt;

        /**
         * 后缀
         */
        private String suffix;

        /**
         * 最大输出 令牌 数
         */
        private Integer maxTokens;

        /**
         * 采样温度
         */
        private Double temperature;

        /**
         * Top-P 采样参数
         */
        private Double topP;

        /**
         * 生成候选项数
         */
        private Integer n;

        /**
         * 是否启用流式响应
         */
        private Boolean stream;

        /**
         * Logprobs 返回数量
         */
        private Integer logprobs;

        /**
         * 是否回显提示词
         */
        private String echo;

        /**
         * 停止序列
         */
        private String stop;

        /**
         * 频率惩罚系数
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚系数
         */
        private Double presencePenalty;
    }

    /**
     * CC Switch 切换请求体。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class CcSwitchRequest {

        /**
         * 目标模型名称
         */
        private String model;

        /**
         * 提供商标识
         */
        private String provider;

        /**
         * 额外配置
         */
        private Map<String, Object> config;
    }

    /**
     * CC Switch 批量配置请求体。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class CcBatchSwitchRequest {

        /**
         * 模型配置列表
         */
        private List<ModelConfig> configs;
    }

    /**
     * 模型配置项。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class ModelConfig {

        /**
         * 模型名称
         */
        private String model;

        /**
         * 提供商标识
         */
        private String provider;

        /**
         * 优先级
         */
        private Integer priority;

        /**
         * 权重
         */
        private Double weight;

        /**
         * 是否启用
         */
        private Boolean enabled;
    }
}

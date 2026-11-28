// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.ai.chat.ChatTool;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceInstanceMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.handler.ServerHandler;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;

/**
 * AI 服务实例管理器。
 *
 * <p>职责：在独立端口上启动和停止 AI HTTP 服务，按实例开关暴露 OpenAI、Claude 和 Gemini 协议端点，
 * 完成请求鉴权、协议转换、流式响应、访问日志回填及用量落库。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ServiceInstanceManager {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SysAiServiceInstanceMapper instanceMapper;
    private final SysAiServiceAccessLogMapper accessLogMapper;
    private final ChatClientRegistry registry;
    private final AiChatFacadeService facade;
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * AppKey 服务：服务实例的鉴权、模型范围、额度与计量一律以 appKey 为准。
     */
    private final SysAiAppKeyService appKeyService;

    /**
     * 模型范围解析（分组清单 ∩ Key 限定模型），与网关共用同一份判定
     */
    private final AppKeyModelScope modelScope;

    /**
     * 生效对话模型解析器（连接器级 → Bot 默认 → 配置中心默认）
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    private final Map<String, Server> servers = new ConcurrentHashMap<>();

    /**
     * 创建服务实例管理器。
     *
     * @param instanceMapper 服务实例数据访问器
     * @param accessLogMapper 访问日志数据访问器
     * @param registry 聊天客户端注册表
     * @param facade 聊天调用门面
     * @param globalSettingService 全局设置服务
     * @param appKeyService appKey 服务
     * @param modelScope 模型范围解析器
     * @param effectiveChatModelResolver 生效对话模型解析器
     */
    public ServiceInstanceManager(SysAiServiceInstanceMapper instanceMapper,
                                  SysAiServiceAccessLogMapper accessLogMapper,
                                  ChatClientRegistry registry,
                                  AiChatFacadeService facade,
                                  SysAiGlobalSettingService globalSettingService,
                                  SysAiAppKeyService appKeyService,
                                  AppKeyModelScope modelScope,
                                  EffectiveChatModelResolver effectiveChatModelResolver) {
        this.instanceMapper = instanceMapper;
        this.accessLogMapper = accessLogMapper;
        this.registry = registry;
        this.facade = facade;
        this.globalSettingService = globalSettingService;
        this.appKeyService = appKeyService;
        this.modelScope = modelScope;
        this.effectiveChatModelResolver = effectiveChatModelResolver;
    }

    // ==================== 实例启停 ====================

    /**
     * 启动指定服务实例。
     *
     * @param inst 服务实例配置，不能为 null
     * @return 启动成功或实例已运行时返回 true
     */
    public synchronized boolean startInstance(SysAiServiceInstance inst) {
        String id = inst.getInstanceId();
        if (servers.containsKey(id)) {
            return true;
        }
        try {
            Server server = registerContexts(ServerBuilder.create()
                    .type("nio")
                    .port(inst.getPort()), inst)
                    .build();
            server.start();
            servers.put(id, server);
            log.info("[ServiceInstance] 实例 {} 已启动，端口 {}", inst.getInstanceName(), inst.getPort());
            return true;
        } catch (Exception e) {
            // 框架绑定失败经 PortBindRetry 重试后抛 RuntimeException，统一按启动失败处理
            log.error("[ServiceInstance] 端口 {} 绑定失败: {}", inst.getPort(), e.getMessage());
            return false;
        }
    }

    /**
     * 停止指定服务实例并释放监听端口。
     *
     * @param instanceId 服务实例标识
     */
    public synchronized void stopInstance(String instanceId) {
        Server server = servers.remove(instanceId);
        if (server != null) {
            server.stop();
        }
        log.info("[ServiceInstance] 实例 {} 已停止", instanceId);
    }

    /**
     * 判断指定服务实例是否正在运行。
     *
     * @param instanceId 服务实例标识
     * @return 正在运行返回 true
     */
    public boolean isRunning(String instanceId) {
        return servers.containsKey(instanceId);
    }

    /**
     * 应用就绪后恢复所有已启用的服务实例。
     */
    public void recoverInstances() {
        List<SysAiServiceInstance> instances = instanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiServiceInstance>()
                        .eq(SysAiServiceInstance::getEnabled, 1));
        log.info("[ServiceInstance] 启动恢复，共 {} 个启用实例", instances.size());
        for (SysAiServiceInstance inst : instances) {
            try {
                if (!startInstance(inst)) {
                    inst.setEnabled(0);
                    instanceMapper.updateById(inst);
                }
            } catch (Exception e) {
                log.error("[ServiceInstance] 恢复实例 {} 失败: {}", inst.getInstanceId(), e.getMessage());
            }
        }
    }

    // ==================== 协议上下文注册（按开关） ====================

    /**
     * 按实例开关注册各协议端点路由。
     *
     * @param builder 框架 Server 构建器，不能为 null
     * @param inst    服务实例配置，不能为 null，其协议开关决定注册哪些路径
     * @return 原构建器，便于链式调用
     */
    private ServerBuilder registerContexts(ServerBuilder builder, SysAiServiceInstance inst) {
        if (inst.getOpenaiEnabled() != null && inst.getOpenaiEnabled() == 1) {
            builder.mapping("/v1/chat/completions", handler(inst, "openai_chat"));
            builder.mapping("/v1/models", handler(inst, "openai_models"));
        }
        if (inst.getCcEnabled() != null && inst.getCcEnabled() == 1) {
            // 框架路由按注册键精确匹配（支持 Ant 模式）：count_tokens 与 messages 精确键互不遮蔽
            builder.mapping("/v1/messages/count_tokens", handler(inst, "cc_count_tokens"));
            builder.mapping("/v1/messages", handler(inst, "cc_messages"));
        }
        if (inst.getGeminiEnabled() != null && inst.getGeminiEnabled() == 1) {
            // Ant 模式 ** 不匹配裸路径本身：/v1beta/models 列表接口需单独注册兜底
            builder.mapping("/v1beta/models/**", handler(inst, "gemini"));
            builder.mapping("/v1beta/models", handler(inst, "gemini"));
        }
        return builder;
    }

    /**
     * 构造协议端点处理器：把框架请求/响应包装成网关交换对象后走统一入口。
     *
     * @param inst 服务实例配置，不能为 null
     * @param kind 端点类型标识，不能为 null 或空字符串
     * @return 框架处理器
     */
    private ServerHandler handler(SysAiServiceInstance inst, String kind) {
        return (req, resp) -> safeHandle(inst, new GatewayExchange(req, resp), kind);
    }

    /**
     * 统一包装请求处理，捕获异常并回写 500 错误，避免单次请求异常导致连接悬挂。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param kind     端点类型标识，不能为 null 或空字符串
     */
    private void safeHandle(SysAiServiceInstance inst, GatewayExchange exchange, String kind) {
        try {
            route(inst, exchange, kind);
        } catch (Exception e) {
            log.error("[ServiceInstance] 请求处理异常 kind={}: {}", kind, e.getMessage(), e);
            tryWriteError(exchange, 500, "internal_error", e.getMessage());
        }
    }

    // ==================== 路由 ====================

    /**
     * 依据端点类型标识分发到具体协议处理器，未识别的类型回写 404。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param kind     端点类型标识，不能为 null 或空字符串
     * @throws IOException 回写响应体失败时抛出
     */
    private void route(SysAiServiceInstance inst, GatewayExchange exchange, String kind) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        switch (kind) {
            case "openai_models": {
                Caller caller = authenticate(exchange);
                if (caller == null) {
                    return;
                }
                handleModelsList(exchange, "openai", caller);
                return;
            }
            case "openai_chat": {
                Caller caller = authenticate(exchange);
                if (caller == null) {
                    return;
                }
                handleOpenAiChat(inst, exchange, caller);
                return;
            }
            case "cc_messages": {
                Caller caller = authenticate(exchange);
                if (caller == null) {
                    return;
                }
                handleCcMessages(inst, exchange, caller);
                return;
            }
            case "cc_count_tokens": {
                Caller caller = authenticate(exchange);
                if (caller == null) {
                    return;
                }
                handleCcCountTokens(exchange);
                return;
            }
            case "gemini": {
                Caller caller = authenticate(exchange);
                if (caller == null) {
                    return;
                }
                handleGemini(inst, exchange, method, path, caller);
                return;
            }
            default:
                tryWriteError(exchange, 404, "not_found", "未知端点");
        }
    }

    // ==================== OpenAI /v1/chat/completions ====================

    /**
     * 处理 OpenAI {@code /v1/chat/completions} 请求，按 stream 标志选择流式或非流式回写。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param caller   已通过鉴权的调用方（appKey 优先），不能为 null
     * @throws IOException 读取请求体或回写响应失败时抛出
     */
    private void handleOpenAiChat(SysAiServiceInstance inst, GatewayExchange exchange, Caller caller) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        boolean wantStream = Boolean.TRUE.equals(body.get("stream"));
        List<Map<String, Object>> messages = parseOpenAiMessages(body.get("messages"));
        String model = str(body.get("model"));

        AiChatRequest request = buildRequest(inst, body, messages, model);
        bindAppKey(request, caller);
        String id = "chatcmpl-" + uuid8();
        long created = System.currentTimeMillis() / 1000;
        String effModel = effectiveModel(model);
        // 调用方的模型范围（appKey 分组 / 限定模型）先拦越权，再决定是否开流（开流后响应头已发，无法改判）
        if (!assertModelAllowed(caller, effModel, exchange)) {
            return;
        }

        if (wantStream) {
            openSse(exchange, "text/event-stream");
            StreamState st = new StreamState(exchange);
            // 首帧：角色
            Map<String, Object> first = openAiChunkEnvelope(id, created, effModel);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("role", "assistant");
            delta.put("content", "");
            first.put("choices", List.of(choiceDelta(0, delta, null)));
            st.writeSseData(first);
            runFacade(inst, request, exchange, st, "openai", id, effModel, body, caller);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "openai_nonstream", id, effModel, body, caller);
        }
    }

    /**
     * 把一段模型输出写为 OpenAI 流式分片：先 reasoning_content、再 content，
     * 收尾时发 finish 帧（工具调用时为 tool_calls），
     * 并按请求的 stream_options.include_usage 决定是否附 usage 帧。
     *
     * @param st        流式状态，不能为 null
     * @param id        分片所属响应标识
     * @param created   创建时间戳（秒）
     * @param model     实际生效的模型名
     * @param text      正文增量文本，可为 null 或空
     * @param reasoning 思考增量文本，可为 null 或空
     * @param finish    是否为收尾帧
     * @param usage     本次调用用量，收尾且需要时报
     * @param reqBody   原始请求体（读 stream_options）
     * @throws IOException 回写 SSE 分片失败时抛出
     */
    private void writeOpenAiStream(StreamState st, String id, long created, String model,
                                   String text, String reasoning, boolean finish, AiUsage usage,
                                   Map<String, Object> reqBody) throws IOException {
        if (StringUtils.isNotEmpty(reasoning)) {
            Map<String, Object> env = openAiChunkEnvelope(id, created, model);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("reasoning_content", reasoning);
            env.put("choices", List.of(choiceDelta(0, delta, null)));
            st.writeSseData(env);
        }
        if (StringUtils.isNotEmpty(text)) {
            Map<String, Object> env = openAiChunkEnvelope(id, created, model);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("content", text);
            env.put("choices", List.of(choiceDelta(0, delta, null)));
            st.writeSseData(env);
        }
        if (finish) {
            Map<String, Object> env = openAiChunkEnvelope(id, created, model);
            String finishReason = st.toolCallsEmitted ? "tool_calls" : "stop";
            env.put("choices", List.of(choiceDelta(0, new LinkedHashMap<>(), finishReason)));
            st.writeSseData(env);
            boolean includeUsage = reqBody.get("stream_options") instanceof Map<?, ?> so
                    && Boolean.TRUE.equals(so.get("include_usage"));
            if (includeUsage) {
                Map<String, Object> usageEnv = openAiChunkEnvelope(id, created, model);
                usageEnv.put("choices", List.of());
                usageEnv.put("usage", openAiUsage(usage));
                st.writeSseData(usageEnv);
            }
            st.writeDone();
        }
    }

    /**
     * 把底层流式 tool_calls 增量翻译为 OpenAI delta.tool_calls：首帧带 id/type/function.name，
     * 后续帧 function.arguments 片段；并标记本轮 finish_reason=tool_calls。
     *
     * @param st        流式状态，不能为 null
     * @param id        分片所属响应标识
     * @param created   创建时间戳（秒）
     * @param model     实际生效的模型名
     * @param toolCalls 待翻译的工具调用列表
     * @throws IOException 回写 SSE 分片失败时抛出
     */
    private void writeOpenAiToolCalls(StreamState st, String id, long created, String model,
                                      List<ChatToolCallData> toolCalls) throws IOException {
        st.toolCallsEmitted = true;
        for (ChatToolCallData tc : toolCalls) {
            Map<String, Object> env = openAiChunkEnvelope(id, created, model);
            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("index", (int) tc.getIndex());
            toolCall.put("type", "function");
            if (tc.getId() != null) {
                toolCall.put("id", tc.getId());
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            if (tc.getName() != null) {
                fn.put("name", tc.getName());
            }
            if (tc.getArguments() != null) {
                fn.put("arguments", tc.getArguments());
            }
            if (!fn.isEmpty()) {
                toolCall.put("function", fn);
            }
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("tool_calls", List.of(toolCall));
            env.put("choices", List.of(choiceDelta(0, delta, null)));
            st.writeSseData(env);
        }
    }

    /**
     * 构造 OpenAI 流式分片的外层信封骨架。
     *
     * @param id      分片所属响应标识，不能为 null
     * @param created 创建时间戳，单位为秒
     * @param model   实际生效的模型名，不能为 null
     * @return 含 id/object/created/model 四个字段的有序 Map
     */
    private Map<String, Object> openAiChunkEnvelope(String id, long created, String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("object", "chat.completion.chunk");
        m.put("created", created);
        m.put("model", model);
        return m;
    }

    /**
     * 构造 OpenAI 流式分片中的 choices 元素。
     *
     * @param index        该 choice 在数组中的下标
     * @param delta        本片增量内容，不能为 null
     * @param finishReason 结束原因，未结束时为 null
     * @return 含 index/delta/finish_reason 的有序 Map
     */
    private Map<String, Object> choiceDelta(int index, Map<String, Object> delta, String finishReason) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("index", index);
        c.put("delta", delta);
        c.put("finish_reason", finishReason);
        return c;
    }

    /**
     * 把内部用量对象转换为 OpenAI 的 usage 结构，空值按 0 处理。
     *
     * @param usage 内部用量快照，可为 null
     * @return 含 prompt_tokens/completion_tokens/total_tokens 的有序 Map
     */
    private Map<String, Object> openAiUsage(AiUsage usage) {
        Map<String, Object> u = new LinkedHashMap<>();
        int p = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
        int c = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        u.put("prompt_tokens", p);
        u.put("completion_tokens", c);
        u.put("total_tokens", p + c);
        return u;
    }

    /**
     * 归一化 OpenAI 请求中的 messages 数组，补齐缺省角色并展开多段文本内容。
     *
     * @param raw 原始 messages 值，可为 null 或非 List 类型，此时返回空列表
     * @return 归一化后的消息列表，元素为 role/content/tool_calls 等键的有序 Map
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseOpenAiMessages(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String role = str(m.get("role"));
            if (role.isBlank()) {
                role = "user";
            }
            String content = extractOpenAiContent(m.get("content"));
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            if ("tool".equals(role) && m.get("tool_call_id") != null) {
                msg.put("tool_call_id", str(m.get("tool_call_id")));
            }
            if ("assistant".equals(role)
                    && m.get("tool_calls") instanceof List<?> tcl && !tcl.isEmpty()) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Object tco : tcl) {
                    if (!(tco instanceof Map<?, ?> tcm)) {
                        continue;
                    }
                    Map<String, Object> tc = new LinkedHashMap<>();
                    if (tcm.get("id") != null) {
                        tc.put("id", str(tcm.get("id")));
                    }
                    tc.put("type", "function");
                    Map<String, Object> fn = new LinkedHashMap<>();
                    if (tcm.get("function") instanceof Map<?, ?> fm) {
                        if (fm.get("name") != null) {
                            fn.put("name", str(fm.get("name")));
                        }
                        if (fm.get("arguments") != null) {
                            fn.put("arguments", str(fm.get("arguments")));
                        }
                    }
                    tc.put("function", fn);
                    normalized.add(tc);
                }
                if (!normalized.isEmpty()) {
                    msg.put("tool_calls", normalized);
                }
            }
            out.add(msg);
        }
        return out;
    }

    /**
     * 抽取 OpenAI 消息内容为纯文本：字符串直接返回，分段数组只拼接 text 分段。
     *
     * @param content 原始 content 值，可为 null
     * @return 抽取出的文本，无内容时返回空字符串
     */
    private String extractOpenAiContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> pm) {
                    Object type = pm.get("type");
                    if ("text".equals(String.valueOf(type))) {
                        sb.append(str(pm.get("text")));
                    }
                }
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    // ==================== Claude /v1/messages ====================

    /**
     * 处理 Claude {@code /v1/messages} 请求，按 stream 标志选择流式或非流式回写。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param caller   已通过鉴权的调用方（appKey 优先），不能为 null
     * @throws IOException 读取请求体或回写响应失败时抛出
     */
    private void handleCcMessages(SysAiServiceInstance inst, GatewayExchange exchange, Caller caller) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        boolean wantStream = Boolean.TRUE.equals(body.get("stream"));
        List<Map<String, Object>> messages = parseCcMessages(body);
        String model = str(body.get("model"));

        AiChatRequest request = buildRequest(inst, body, messages, model);
        applyCcTools(request, body);
        bindAppKey(request, caller);
        String id = "msg_" + uuid8();
        String effModel = effectiveModel(model);
        if (!assertModelAllowed(caller, effModel, exchange)) {
            return;
        }

        if (wantStream) {
            openSse(exchange, "text/event-stream");
            StreamState st = new StreamState(exchange);
            // message_start
            int inTok = 0;
            Map<String, Object> startMsg = new LinkedHashMap<>();
            startMsg.put("id", id);
            startMsg.put("type", "message");
            startMsg.put("role", "assistant");
            startMsg.put("model", effModel);
            startMsg.put("content", List.of());
            startMsg.put("stop_reason", null);
            startMsg.put("stop_sequence", null);
            startMsg.put("usage", Map.of("input_tokens", inTok, "output_tokens", 0));
            Map<String, Object> ms = new LinkedHashMap<>();
            ms.put("type", "message_start");
            ms.put("message", startMsg);
            st.writeSseEvent("message_start", ms);
            st.writeSseEvent("ping", Map.of("type", "ping"));
            runFacade(inst, request, exchange, st, "cc", id, effModel, body, caller);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "cc_nonstream", id, effModel, body, caller);
        }
    }

    /**
     * 把 Claude 请求体转换为 OpenAI 风格消息序列，system 作为首条 system 消息插入。
     *
     * @param body Claude 请求体，不能为 null；其中 system/messages 字段可为 null
     * @return 转换后的消息列表，不存在任何输入时返回空列表
     */
    private List<Map<String, Object>> parseCcMessages(Map<String, Object> body) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sysText = extractCcText(body.get("system"));
        if (!sysText.isBlank()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", sysText);
            out.add(sys);
        }
        if (body.get("messages") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                String role = str(m.get("role"));
                if (role.isBlank()) {
                    role = "user";
                }
                if ("assistant".equals(role)) {
                    appendCcAssistant(out, m.get("content"));
                } else {
                    appendCcUser(out, m.get("content"));
                }
            }
        }
        return out;
    }

    /**
     * CC assistant 内容 → OpenAI assistant：text 块并入 content，tool_use 块转为 tool_calls。
     *
     * @param out     目标消息列表，转换结果追加到末尾，不能为 null
     * @param content CC 侧的 content 值，可为 null、字符串或块数组
     */
    private void appendCcAssistant(List<Map<String, Object>> out, Object content) {
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        if (content instanceof String s) {
            text.append(s);
        } else if (content instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (!(b instanceof Map<?, ?> bm)) {
                    continue;
                }
                String type = str(bm.get("type"));
                if ("text".equals(type)) {
                    text.append(str(bm.get("text")));
                } else if ("tool_use".equals(type)) {
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", str(bm.get("id")));
                    tc.put("type", "function");
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", str(bm.get("name")));
                    fn.put("arguments", ccInputJson(bm.get("input")));
                    tc.put("function", fn);
                    toolCalls.add(tc);
                }
            }
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", text.toString());
        if (!toolCalls.isEmpty()) {
            msg.put("tool_calls", toolCalls);
        }
        out.add(msg);
    }

    /**
     * CC user 内容 → OpenAI：tool_result 块转为 role=tool（须紧跟 assistant 的 tool_calls），
     * 其余 text 块合并为普通 user 消息。
     *
     * @param out     目标消息列表，转换结果追加到末尾，不能为 null
     * @param content CC 侧的 content 值，可为 null、字符串或块数组
     */
    private void appendCcUser(List<Map<String, Object>> out, Object content) {
        if (!(content instanceof List<?> blocks)) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", content == null ? "" : str(content));
            out.add(msg);
            return;
        }
        StringBuilder text = new StringBuilder();
        boolean hasToolResult = false;
        for (Object b : blocks) {
            if (!(b instanceof Map<?, ?> bm)) {
                continue;
            }
            String type = str(bm.get("type"));
            if ("tool_result".equals(type)) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("role", "tool");
                tool.put("tool_call_id", str(bm.get("tool_use_id")));
                tool.put("content", extractCcText(bm.get("content")));
                out.add(tool);
                hasToolResult = true;
            } else if ("text".equals(type)) {
                text.append(str(bm.get("text")));
            }
        }
        if (text.length() > 0 || !hasToolResult) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", text.toString());
            out.add(msg);
        }
    }

    /**
     * 把 CC 工具入参序列化为 JSON 字符串，序列化失败或空值时退化为 {@code "{}"}。
     *
     * @param input 工具入参对象，可为 null
     * @return 合法 JSON 字符串，不会为 null
     */
    private String ccInputJson(Object input) {
        if (input == null) {
            return "{}";
        }
        try {
            String json = JSON.writeValueAsString(input);
            return json == null || json.isBlank() ? "{}" : json;
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 把 CC 的 tools / tool_choice 翻译为内部结构：
     * 自定义工具（name/description/input_schema）→ {@link ChatTool}；
     * tool_choice auto/any/none/tool{name} → auto/required/none/工具名。
     * computer-use 等内置类型工具（无 name/input_schema）跳过。
     *
     * @param request 待填充的内部聊天请求，不能为 null
     * @param body    CC 请求体，不能为 null；缺 tools/tool_choice 时不修改 request
     */
    @SuppressWarnings("unchecked")
    private void applyCcTools(AiChatRequest request, Map<String, Object> body) {
        if (body.get("tools") instanceof List<?> tools && !tools.isEmpty()) {
            List<ChatTool> chatTools = new ArrayList<>();
            for (Object t : tools) {
                if (!(t instanceof Map<?, ?> tm)) {
                    continue;
                }
                String name = str(tm.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, Object> schema = tm.get("input_schema") instanceof Map<?, ?> is
                        ? (Map<String, Object>) is : new LinkedHashMap<>();
                chatTools.add(ChatTool.builder()
                        .name(name)
                        .description(str(tm.get("description")))
                        .parameters(schema)
                        .build());
            }
            if (!chatTools.isEmpty()) {
                request.setTools(chatTools);
            }
        }
        if (body.get("tool_choice") instanceof Map<?, ?> tcm) {
            String type = str(tcm.get("type"));
            switch (type) {
                case "auto":
                    request.setToolChoice("auto");
                    break;
                case "any":
                    request.setToolChoice("required");
                    break;
                case "none":
                    request.setToolChoice("none");
                    break;
                case "tool":
                    if (tcm.get("name") != null) {
                        request.setToolChoice(str(tcm.get("name")));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 抽取 CC 内容为纯文本：字符串直接返回，块数组只拼接 text 块。
     *
     * @param raw 原始内容值，可为 null
     * @return 抽取出的文本，无内容时返回空字符串
     */
    private String extractCcText(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof List<?> blocks) {
            StringBuilder sb = new StringBuilder();
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> bm && "text".equals(String.valueOf(bm.get("type")))) {
                    sb.append(str(bm.get("text")));
                }
            }
            return sb.toString();
        }
        return String.valueOf(raw);
    }

    // ==================== Claude /v1/messages/count_tokens ====================

    /**
     * 处理 Claude {@code /v1/messages/count_tokens} 请求，返回估算的输入 token 数。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     */
    private void handleCcCountTokens(GatewayExchange exchange) {
        try {
            Map<String, Object> body = readJsonBody(exchange);
            int inputTokens = estimateCcInputTokens(body);
            writeJsonResponse(exchange, 200, Map.of("input_tokens", inputTokens));
        } catch (Exception e) {
            log.warn("[ServiceInstance] count_tokens 失败: {}", e.getMessage());
            tryWriteError(exchange, 500, "internal_error", e.getMessage());
        }
    }

    /**
     * 估算一次 CC 请求的输入 token 数。服务端无 Anthropic 分词器，按「可见字符 / 4」近似，
     * 覆盖 system、消息正文/思考/工具调用与工具结果、以及工具定义，供 Claude Code 做上下文预算。
     *
     * @param body CC 请求体，不能为 null
     * @return 估算的输入 token 数，至少为 0
     */
    private int estimateCcInputTokens(Map<String, Object> body) {
        long chars = ccTextChars(body.get("system"));
        if (body.get("messages") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    chars += ccContentChars(m.get("content"));
                }
            }
        }
        chars += ccToolsChars(body.get("tools"));
        if (chars <= 0) {
            return 0;
        }
        return (int) Math.max(1, (chars + 3) / 4);
    }

    /**
     * 统计 system 字段的可见字符数，块数组只累计 text 块。
     *
     * @param raw 原始内容值，可为 null
     * @return 字符数，null 时为 0
     */
    private long ccTextChars(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof String s) {
            return s.length();
        }
        if (raw instanceof List<?> blocks) {
            long n = 0;
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> bm && "text".equals(String.valueOf(bm.get("type")))) {
                    n += strLen(bm.get("text"));
                }
            }
            return n;
        }
        return String.valueOf(raw).length();
    }

    /**
     * 统计单条消息内容的可见字符数，按块类型分别累计文本、思考、工具调用与工具结果。
     *
     * @param raw 消息 content 值，可为 null、字符串或块数组
     * @return 字符数，null 时为 0
     */
    private long ccContentChars(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof String s) {
            return s.length();
        }
        if (raw instanceof List<?> blocks) {
            long n = 0;
            for (Object b : blocks) {
                if (!(b instanceof Map<?, ?> bm)) {
                    continue;
                }
                String type = str(bm.get("type"));
                switch (type) {
                    case "text":
                        n += strLen(bm.get("text"));
                        break;
                    case "thinking":
                        n += strLen(bm.get("thinking"));
                        break;
                    case "tool_use":
                        n += strLen(bm.get("name")) + jsonLen(bm.get("input"));
                        break;
                    case "tool_result":
                        n += ccTextChars(bm.get("content"));
                        break;
                    default:
                        break;
                }
            }
            return n;
        }
        return String.valueOf(raw).length();
    }

    /**
     * 统计工具定义的字符数，含名称、描述与入参 schema 的 JSON 长度。
     *
     * @param raw 原始 tools 值，非 List 类型时按 0 处理
     * @return 字符数，无工具定义时为 0
     */
    private long ccToolsChars(Object raw) {
        if (!(raw instanceof List<?> tools)) {
            return 0;
        }
        long n = 0;
        for (Object t : tools) {
            if (!(t instanceof Map<?, ?> tm)) {
                continue;
            }
            n += strLen(tm.get("name")) + strLen(tm.get("description")) + jsonLen(tm.get("input_schema"));
        }
        return n;
    }

    /**
     * 取对象的字符串形式长度，null 记为 0。
     *
     * @param o 任意对象，可为 null
     * @return 字符串长度，null 时为 0
     */
    private static long strLen(Object o) {
        return o == null ? 0 : String.valueOf(o).length();
    }

    /**
     * 计算对象的 JSON 序列化长度，序列化失败时退化为字符串长度。
     *
     * @param o 任意对象，可为 null
     * @return JSON 文本长度，null 时为 0
     */
    private long jsonLen(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return JSON.writeValueAsString(o).length();
        } catch (Exception e) {
            return String.valueOf(o).length();
        }
    }

    /**
     * 把一段模型输出写为 Anthropic 流式分片：动态块 thinking → text → tool_use，
     * 块下标按开启顺序分配，收尾时调用 {@link #finishCcStream(StreamState, AiUsage)}。
     *
     * @param st        流式状态，不能为 null
     * @param id        本次响应标识
     * @param model     实际生效的模型名
     * @param text      正文增量文本，可为 null 或空
     * @param reasoning 思考增量文本，可为 null 或空
     * @param finish    是否为收尾帧
     * @param usage     本次调用用量，收尾时使用
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private void writeCcStream(StreamState st, String id, String model, String text, String reasoning,
                               boolean finish, AiUsage usage) throws IOException {
        // 动态块：thinking → text → tool_use，块下标按开启顺序分配
        if (StringUtils.isNotEmpty(reasoning)) {
            int thinkingIndex = openCcThinkingBlock(st);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "thinking_delta");
            delta.put("thinking", reasoning);
            st.writeSseEvent("content_block_delta",
                    Map.of("type", "content_block_delta", "index", thinkingIndex, "delta", delta));
        }
        if (StringUtils.isNotEmpty(text)) {
            int textIndex = openCcTextBlock(st);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "text_delta");
            delta.put("text", text);
            st.writeSseEvent("content_block_delta",
                    Map.of("type", "content_block_delta", "index", textIndex, "delta", delta));
        }
        if (finish) {
            finishCcStream(st, usage);
        }
    }

    /**
     * 开启（或复用）Anthropic 流中的 thinking 内容块，返回块下标。
     *
     * @param st 流式状态，不能为 null
     * @return 该 thinking 块在本次响应中的下标
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private int openCcThinkingBlock(StreamState st) throws IOException {
        if (st.ccThinkingOpened) {
            return st.ccThinkingIndex;
        }
        int index = st.ccBlockCount++;
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "thinking");
        block.put("thinking", "");
        st.writeSseEvent("content_block_start",
                Map.of("type", "content_block_start", "index", index, "content_block", block));
        st.ccThinkingOpened = true;
        st.ccThinkingIndex = index;
        st.ccOpenedBlocks.add(index);
        return index;
    }

    /**
     * 开启（或复用）Anthropic 流中的 text 内容块，返回块下标。
     *
     * @param st 流式状态，不能为 null
     * @return 该 text 块在本次响应中的下标
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private int openCcTextBlock(StreamState st) throws IOException {
        if (st.ccTextOpened) {
            return st.ccTextIndex;
        }
        int index = st.ccBlockCount++;
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", "");
        st.writeSseEvent("content_block_start",
                Map.of("type", "content_block_start", "index", index, "content_block", block));
        st.ccTextOpened = true;
        st.ccTextIndex = index;
        st.ccOpenedBlocks.add(index);
        return index;
    }

    /**
     * 开启（或复用）Anthropic 流中的 tool_use 内容块，返回块下标。
     *
     * @param st            流式状态，不能为 null
     * @param toolCallIndex 底层 tool_calls 数组中的下标
     * @param id            工具调用标识，可为 null，落库时空串兜底
     * @param name          工具名，可为 null，落库时空串兜底
     * @return 该 tool_use 块在本次响应中的下标
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private int openCcToolBlock(StreamState st, int toolCallIndex, String id, String name) throws IOException {
        Integer existing = st.ccToolBlockByIndex.get(toolCallIndex);
        if (existing != null) {
            return existing;
        }
        int index = st.ccBlockCount++;
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_use");
        block.put("id", id == null ? "" : id);
        block.put("name", name == null ? "" : name);
        block.put("input", new LinkedHashMap<>());
        st.writeSseEvent("content_block_start",
                Map.of("type", "content_block_start", "index", index, "content_block", block));
        st.ccToolBlockByIndex.put(toolCallIndex, index);
        st.ccOpenedBlocks.add(index);
        return index;
    }

    /**
     * 把底层流式 tool_calls 增量翻译为 Anthropic tool_use 事件：
     * 首帧 content_block_start（tool_use, id, name, input{}），随后 input_json_delta 入参片段。
     *
     * @param st        流式状态，不能为 null
     * @param toolCalls 底层下发的工具调用增量列表，不能为 null
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private void writeCcToolCalls(StreamState st, List<ChatToolCallData> toolCalls) throws IOException {
        for (ChatToolCallData tc : toolCalls) {
            int toolCallIndex = (int) tc.getIndex();
            int blockIndex = openCcToolBlock(st, toolCallIndex, tc.getId(), tc.getName());
            String partial = tc.getArguments();
            if (StringUtils.isNotEmpty(partial)) {
                Map<String, Object> delta = new LinkedHashMap<>();
                delta.put("type", "input_json_delta");
                delta.put("partial_json", partial);
                st.writeSseEvent("content_block_delta",
                        Map.of("type", "content_block_delta", "index", blockIndex, "delta", delta));
            }
        }
    }

    /**
     * 收尾 CC 流：按下标顺序 content_block_stop 所有块，再 message_delta（有工具调用则
     * stop_reason=tool_use，否则 end_turn）与 message_stop。
     *
     * @param st    流式状态，不能为 null
     * @param usage 本次调用的用量快照，可为 null
     * @throws IOException 回写 SSE 事件失败时抛出
     */
    private void finishCcStream(StreamState st, AiUsage usage) throws IOException {
        if (st.ccOpenedBlocks.isEmpty()) {
            openCcTextBlock(st); // 空回复兜底，保证事件序列完整
        }
        for (Integer index : st.ccOpenedBlocks) {
            st.writeSseEvent("content_block_stop",
                    Map.of("type", "content_block_stop", "index", index));
        }
        int outTok = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        String stopReason = st.ccToolBlockByIndex.isEmpty() ? "end_turn" : "tool_use";
        Map<String, Object> stopDelta = new LinkedHashMap<>();
        stopDelta.put("stop_reason", stopReason);
        stopDelta.put("stop_sequence", null);
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("type", "message_delta");
        md.put("delta", stopDelta);
        md.put("usage", Map.of("output_tokens", outTok));
        st.writeSseEvent("message_delta", md);
        st.writeSseEvent("message_stop", Map.of("type", "message_stop"));
    }

    // ==================== Gemini ====================

    /**
     * 处理 Gemini {@code /v1beta/models/**} 请求：GET 走模型列表，POST 走生成或流式生成。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param method   HTTP 方法名，不能为 null
     * @param path     请求路径，用于识别模型名与生成动作
     * @param caller   已通过鉴权的调用方（appKey 优先），不能为 null
     * @throws IOException 读取请求体或回写响应失败时抛出
     */
    private void handleGemini(SysAiServiceInstance inst, GatewayExchange exchange, String method, String path,
                              Caller caller)
            throws IOException {
        boolean stream = path.endsWith(":streamGenerateContent");
        boolean generate = stream || path.endsWith(":generateContent");
        if ("GET".equalsIgnoreCase(method) && !generate) {
            handleModelsList(exchange, "gemini", caller);
            return;
        }
        Map<String, Object> body = readJsonBody(exchange);
        String pathModel = extractGeminiModel(path);
        List<Map<String, Object>> messages = parseGeminiContents(body);
        AiChatRequest request = buildRequest(inst, body, messages, pathModel);
        bindAppKey(request, caller);
        String effModel = effectiveModel(pathModel);
        if (!assertModelAllowed(caller, effModel, exchange)) {
            return;
        }

        if (stream) {
            openSse(exchange, "text/event-stream");
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "gemini", null, effModel, body, caller);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "gemini_nonstream", null, effModel, body, caller);
        }
    }

    /**
     * 从 Gemini 请求路径中解析模型名。
     *
     * @param path 请求路径，不能为 null
     * @return 模型名；路径不含 {@code /v1beta/models/} 时返回空字符串
     */
    private String extractGeminiModel(String path) {
        int marker = path.indexOf("/v1beta/models/");
        if (marker < 0) {
            return "";
        }
        String rest = path.substring(marker + "/v1beta/models/".length());
        int colon = rest.indexOf(':');
        return colon >= 0 ? rest.substring(0, colon) : rest;
    }

    /**
     * 把 Gemini 请求体转换为 OpenAI 风格消息序列，systemInstruction 作为首条 system 消息。
     *
     * @param body Gemini 请求体，不能为 null；contents/systemInstruction 可为 null
     * @return 转换后的消息列表，无内容时返回空列表
     */
    private List<Map<String, Object>> parseGeminiContents(Map<String, Object> body) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object sysInstr = body.get("systemInstruction");
        if (sysInstr == null) {
            sysInstr = body.get("system_instruction");
        }
        String sysText = geminiPartsText(sysInstr);
        if (!sysText.isBlank()) {
            out.add(Map.of("role", "system", "content", sysText));
        }
        if (body.get("contents") instanceof List<?> contents) {
            for (Object c : contents) {
                if (!(c instanceof Map<?, ?> cm)) {
                    continue;
                }
                String role = "model".equals(str(cm.get("role"))) ? "assistant" : str(cm.get("role"));
                // geminiPartsText 的入参是「内容对象」（内含 parts），不是 parts 列表本身；
                // 传列表会被判成非 Map 直接返回空串，导致 prompt 为空、上游报「输入为空」。
                String text = geminiPartsText(cm);
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", role.isBlank() ? "user" : role);
                msg.put("content", text);
                out.add(msg);
            }
        }
        return out;
    }

    /**
     * 拼接 Gemini 内容块中所有 parts 的文本。
     *
     * @param raw 内容对象，非 Map 或缺少 parts 时返回空字符串
     * @return 拼接后的文本，无文本时返回空字符串
     */
    private String geminiPartsText(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return "";
        }
        Object parts = m.get("parts");
        if (!(parts instanceof List<?> list)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object p : list) {
            if (p instanceof Map<?, ?> pm && pm.get("text") != null) {
                sb.append(str(pm.get("text")));
            }
        }
        return sb.toString();
    }

    /**
     * 写出一个 Gemini 流式分片，结束时附带 usageMetadata 用量信息。
     *
     * @param st     流式状态，不能为 null
     * @param model  实际生效的模型名，可为 null
     * @param text   本片文本增量，可为 null
     * @param finish 是否为最后一片
     * @param usage  本次调用用量，可为 null，仅在结束时输出
     * @throws IOException 回写响应失败时抛出
     */
    private void writeGeminiStream(StreamState st, String model, String text, boolean finish, AiUsage usage)
            throws IOException {
        Map<String, Object> candidate = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(Map.of("text", text != null ? text : "")));
        content.put("role", "model");
        candidate.put("content", content);
        candidate.put("finishReason", finish ? "STOP" : null);
        candidate.put("index", 0);
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("candidates", List.of(candidate));
        if (finish && usage != null) {
            int p = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
            int c = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
            env.put("usageMetadata", Map.of(
                    "promptTokenCount", p,
                    "candidatesTokenCount", c,
                    "totalTokenCount", p + c));
        }
        st.writeSseData(env);
    }

    // ==================== 模型列表 ====================

    /**
     * 输出模型列表，按 flavor 分别渲染 Gemini {@code models[]} 或 OpenAI {@code data[]} 结构。
     *
     * <p>AppKey 限定了模型清单时，列表只回这些模型 —— 客户端（Cherry Studio 之类）常用
     * {@code /v1/models} 做下拉，若照旧全量返回，用户选到一个没权限的模型再被 403，
     * 体验与「按模型授权」的语义都不对。</p>
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param flavor   协议风味，取值为 {@code gemini} 或其他（按 OpenAI 结构输出）
     * @param caller   已鉴权的调用方，可为 null（视为不限制模型）
     */
    private void handleModelsList(GatewayExchange exchange, String flavor, Caller caller) {
        try {
            // /models 必须与 /chat/completions 同一口径：实例放行哪个模型，清单就报哪个。
            // 真源是「允许范围」，注册表只是本机已装配的 ChatClient 目录 —— 实例按模型透传给
            // 上游时注册表通常是空的，若照旧与注册表取交集，清单会恒为空；而 Claude Code
            // 这类客户端会先取 /v1/models 校验 --model，清单为空就判「模型不可用」，
            // 请求根本发不出去（与 /chat/completions 的放行结果自相矛盾）。
            Set<String> allowed = allowedModels(caller);
            List<String> models;
            if (allowed != null) {
                // 空集合＝一个都不许（分组不存在 / 已停用），清单同样为空
                models = new ArrayList<>(allowed);
            } else {
                // 不限制模型时按注册表报；注册表也空（未装配任何 ChatClient）时，
                // 至少报出实际生效的默认模型，别让清单空着
                models = new ArrayList<>(registry.getModels());
                if (models.isEmpty()) {
                    String fallback = defaultModel();
                    if (fallback != null && !fallback.isBlank()) {
                        models.add(fallback.trim());
                    }
                }
            }
            Object payload;
            if ("gemini".equals(flavor)) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (String m : models) {
                    Map<String, Object> mi = new LinkedHashMap<>();
                    mi.put("name", "models/" + m);
                    mi.put("supportedGenerationMethods", List.of("generateContent", "streamGenerateContent"));
                    items.add(mi);
                }
                payload = Map.of("models", items);
            } else {
                List<Map<String, Object>> items = new ArrayList<>();
                long created = System.currentTimeMillis() / 1000;
                for (String m : models) {
                    Map<String, Object> mi = new LinkedHashMap<>();
                    mi.put("id", m);
                    mi.put("object", "model");
                    mi.put("created", created);
                    mi.put("owned_by", "system");
                    items.add(mi);
                }
                payload = Map.of("object", "list", "data", items);
            }
            writeJsonResponse(exchange, 200, payload);
        } catch (Exception e) {
            tryWriteError(exchange, 500, "internal_error", e.getMessage());
        }
    }

    // ==================== 统一 Facade 调用与帧编排 ====================

    /**
     * 按 OpenAI / Gemini 请求体构造内部聊天请求：透传 temperature/max_tokens/top_p 与
     * Gemini generationConfig 兼容字段，prompt 取最后一条用户消息兜底，并翻译 tools/tool_choice。
     *
     * @param inst     服务实例（取用户身份）
     * @param body     OpenAI / Gemini 请求体，不能为 null
     * @param messages 归一化后的消息列表
     * @param model    请求的模型名
     * @return 构造完成的内部聊天请求（始终 stream=true）
     */
    private AiChatRequest buildRequest(SysAiServiceInstance inst, Map<String, Object> body,
                                       List<Map<String, Object>> messages, String model) {
        AiChatRequest request = new AiChatRequest();
        request.setMessages(messages);
        request.setModel(str(model));
        request.setStream(true);
        request.setUsageSource("service");
        if (body.get("temperature") != null) {
            request.setTemperature(Double.valueOf(str(body.get("temperature"))));
        }
        if (body.get("max_tokens") != null) {
            request.setMaxTokens(Integer.valueOf(str(body.get("max_tokens"))));
        }
        if (body.get("top_p") != null) {
            request.setTopP(Double.valueOf(str(body.get("top_p"))));
        }
        // Gemini 字段兼容
        if (body.get("generationConfig") instanceof Map<?, ?> gc) {
            if (gc.get("temperature") != null) {
                request.setTemperature(Double.valueOf(str(gc.get("temperature"))));
            }
            if (gc.get("maxOutputTokens") != null) {
                request.setMaxTokens(Integer.valueOf(str(gc.get("maxOutputTokens"))));
            }
            if (gc.get("topP") != null) {
                request.setTopP(Double.valueOf(str(gc.get("topP"))));
            }
        }
        // 完整上下文已在 messages，但下游门面（Agent 实现）只读 prompt：
        // prompt 置空会让上游把整次请求判成「输入为空」（实例端口 502），
        // 所以这里用最后一条用户输入兜底，历史仍由 messages 承载。
        request.setPrompt(lastUserPrompt(messages));
        applyOpenAiTools(request, body);
        return request;
    }

    /**
     * 取 messages 里最后一条用户消息的文本，作为门面 / Agent 的输入。
     *
     * <p>实例端口按 OpenAI / Claude 的形状把完整历史放进 {@code messages}，
     * 而下游门面只消费 {@code prompt}。两者口径不一致时，上游看到的是空输入，
     * 客户端拿到的却是 502 —— 排除空输入是这里唯一要做的事，上下文不改写。</p>
     *
     * @param messages 归一化后的消息列表，可为 null 或空
     * @return 最后一条用户消息文本；没有用户消息时返回空字符串
     */
    private String lastUserPrompt(List<Map<String, Object>> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if (msg == null || !"user".equals(str(msg.get("role")))) {
                continue;
            }
            String text = str(msg.get("content"));
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    /**
     * 把 OpenAI 入站 tools / tool_choice 翻译为内部结构：
     * {@code {type:"function", function:{name, description, parameters}}} → {@link ChatTool}；
     * tool_choice 字符串 auto/none/required，或对象 {@code {type:"function", function:{name}}} → 指定工具名。
     *
     * @param request 待填充的内部聊天请求，不能为 null
     * @param body    OpenAI 请求体，不能为 null；缺 tools/tool_choice 时不修改 request
     */
    @SuppressWarnings("unchecked")
    private void applyOpenAiTools(AiChatRequest request, Map<String, Object> body) {
        if (body.get("tools") instanceof List<?> tools && !tools.isEmpty()) {
            List<ChatTool> chatTools = new ArrayList<>();
            for (Object t : tools) {
                if (!(t instanceof Map<?, ?> tm)) {
                    continue;
                }
                if (!(tm.get("function") instanceof Map<?, ?> fm)) {
                    continue;
                }
                String name = str(fm.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, Object> schema = fm.get("parameters") instanceof Map<?, ?> ps
                        ? (Map<String, Object>) ps : new LinkedHashMap<>();
                chatTools.add(ChatTool.builder()
                        .name(name)
                        .description(str(fm.get("description")))
                        .parameters(schema)
                        .build());
            }
            if (!chatTools.isEmpty()) {
                request.setTools(chatTools);
            }
        }
        Object tc = body.get("tool_choice");
        if (tc instanceof String s) {
            switch (s.trim().toLowerCase()) {
                case "auto" -> request.setToolChoice("auto");
                case "none" -> request.setToolChoice("none");
                case "required" -> request.setToolChoice("required");
                default -> { }
            }
        } else if (tc instanceof Map<?, ?> tcm
                && tcm.get("function") instanceof Map<?, ?> fm && fm.get("name") != null) {
            request.setToolChoice(str(fm.get("name")));
        }
    }

    /**
     * 以实例所属用户身份运行门面流式调用：按 mode 把增量分片写入对应协议的 SSE 帧
     * （openai / cc / gemini / openai_nonstream），结束帧与工具调用帧同样按模式分发。
     *
     * @param inst     服务实例（提供用户身份与端口上下文）
     * @param request  内部聊天请求，不能为 null
     * @param exchange 当前 HTTP 交换对象
     * @param st       流式状态，不能为 null
     * @param mode     调用模式（openai / cc / gemini / openai_nonstream）
     * @param id       响应标识
     * @param effModel 实际生效的模型名
     * @param reqBody  原始请求体（供 OpenAI 流透传）
     * @param caller   调用方身份（鉴权通过后不为 null）
     */
    private void runFacade(SysAiServiceInstance inst, AiChatRequest request, GatewayExchange exchange,
                           StreamState st, String mode, String id, String effModel,
                           Map<String, Object> reqBody, Caller caller) {
        long start = System.currentTimeMillis();
        final long chunkCreated = System.currentTimeMillis() / 1000;
        // 缓冲响应必须等回调落完响应体再返回，否则 NIO 会先交付默认的空 200。
        boolean buffered = mode.endsWith("_nonstream");
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean terminal = new AtomicBoolean();
        UserResume resume = new UserResume();
        resume.setUserId(inst.getUserId());
        resume.setRoles(java.util.Collections.emptySet());

        AiUserContext.runWith(resume, () ->
                facade.streamWithUsage(request,
                        chunk -> {
                            if (chunk == null || chunk.isEmpty() || st.closed) {
                                return;
                            }
                            try {
                                switch (mode) {
                                    case "openai":
                                        writeOpenAiStream(st, id, chunkCreated,
                                                effModel, chunk, null, false, null, reqBody);
                                        break;
                                    case "cc":
                                        writeCcStream(st, id, effModel, chunk, null, false, null);
                                        break;
                                    case "gemini":
                                        writeGeminiStream(st, effModel, chunk, false, null);
                                        break;
                                    default:
                                        st.buffer.append(chunk);
                                }
                            } catch (IOException e) {
                                log.warn("[ServiceInstance] 写帧失败: {}", e.getMessage());
                            }
                        },
                        (reply, usage) -> {
                            if (buffered && !terminal.compareAndSet(false, true)) return;
                            try {
                                int p = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
                                int c = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
                                switch (mode) {
                                    case "openai": {
                                        writeOpenAiStream(st, id, chunkCreated, effModel, null, null, true, usage, reqBody);
                                        st.close();
                                        break;
                                    }
                                    case "cc":
                                        writeCcStream(st, id, effModel, null, null, true, usage);
                                        st.close();
                                        break;
                                    case "gemini":
                                        writeGeminiStream(st, effModel, null, true, usage);
                                        st.close();
                                        break;
                                    case "openai_nonstream": {
                                        String text = reply != null ? reply : st.buffer.toString();
                                        Map<String, Object> resp = new LinkedHashMap<>();
                                        resp.put("id", id);
                                        resp.put("object", "chat.completion");
                                        resp.put("created", System.currentTimeMillis() / 1000);
                                        resp.put("model", effModel);
                                        Map<String, Object> message = new LinkedHashMap<>();
                                        message.put("role", "assistant");
                                        message.put("content", text);
                                        resp.put("choices", List.of(Map.of(
                                                "index", 0, "message", message, "finish_reason", "stop")));
                                        resp.put("usage", openAiUsage(usage));
                                        writeJsonResponse(exchange, 200, resp);
                                        break;
                                    }
                                    case "cc_nonstream": {
                                        String text = reply != null ? reply : st.buffer.toString();
                                        Map<String, Object> resp = new LinkedHashMap<>();
                                        resp.put("id", id);
                                        resp.put("type", "message");
                                        resp.put("role", "assistant");
                                        resp.put("model", effModel);
                                        resp.put("content", List.of(Map.of("type", "text", "text", text)));
                                        resp.put("stop_reason", "end_turn");
                                        resp.put("stop_sequence", null);
                                        int inTok = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
                                        int outTok = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
                                        resp.put("usage", Map.of("input_tokens", inTok, "output_tokens", outTok));
                                        writeJsonResponse(exchange, 200, resp);
                                        break;
                                    }
                                    case "gemini_nonstream": {
                                        String text = reply != null ? reply : st.buffer.toString();
                                        Map<String, Object> content = new LinkedHashMap<>();
                                        content.put("parts", List.of(Map.of("text", text)));
                                        content.put("role", "model");
                                        Map<String, Object> resp = new LinkedHashMap<>();
                                        resp.put("candidates", List.of(Map.of(
                                                "content", content, "finishReason", "STOP", "index", 0)));
                                        int inTok = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
                                        int outTok = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
                                        resp.put("usageMetadata", Map.of(
                                                "promptTokenCount", inTok,
                                                "candidatesTokenCount", outTok,
                                                "totalTokenCount", inTok + outTok));
                                        writeJsonResponse(exchange, 200, resp);
                                        break;
                                    }
                                    default:
                                        break;
                                }
                            } catch (Exception e) {
                                log.error("[ServiceInstance] 完成处理失败: {}", e.getMessage(), e);
                            }
                            try {
                                writeAccessLog(inst, exchange, effModel, modeToProtocol(mode),
                                        200, usage, System.currentTimeMillis() - start, caller);
                                consumeQuota(caller, true);
                            } catch (Exception e) {
                                log.warn("[ServiceInstance] 完成日志/额度回填失败: {}", e.getMessage());
                            } finally {
                                completed.countDown();
                            }
                        },
                        error -> {
                            if (buffered && !terminal.compareAndSet(false, true)) return;
                            log.error("[ServiceInstance] 上游调用失败: {}", error.getMessage(), error);
                            try {
                                if (!st.closed) {
                                    if (st.committed) {
                                        st.close();
                                    } else if (sseHeadersSent(exchange)) {
                                        // SSE 响应头已发出但尚未写过任何帧（gemini 流式在首帧前出错）：
                                        // 此时不能再改写 502，补一条错误帧并关闭响应流，避免连接无限期挂起
                                        abortOpenSse(exchange, st, mode, error);
                                    } else {
                                        writeJsonResponse(exchange, 502, Map.of(
                                                "error", Map.of("message", error.getMessage(), "type", "upstream_error")));
                                    }
                                }
                                writeAccessLog(inst, exchange, effModel, modeToProtocol(mode),
                                        502, null, System.currentTimeMillis() - start, caller);
                                consumeQuota(caller, false);
                            } catch (Exception e) {
                                log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                            } finally {
                                completed.countDown();
                            }
                        },
                        reasoning -> {
                            if (StringUtils.isEmpty(reasoning) || st.closed) {
                                return;
                            }
                            try {
                                switch (mode) {
                                    case "openai": {
                                        writeOpenAiStream(st, id, chunkCreated, effModel, null, reasoning, false, null, reqBody);
                                        break;
                                    }
                                    case "cc":
                                        writeCcStream(st, id, effModel, null, reasoning, false, null);
                                        break;
                                    default:
                                        break;
                                }
                            } catch (IOException e) {
                                log.warn("[ServiceInstance] 思考帧写入失败: {}", e.getMessage());
                            }
                        },
                        trace -> {
                            // 协议代理通道无通用 trace 帧，轨迹（工具结果 / 边缘事件等）仅落服务端日志；
                            // 正文 / 思考 / 工具调用已由各自回调翻译为对应协议帧
                            if (trace != null && log.isDebugEnabled()) {
                                log.debug("[ServiceInstance] 执行轨迹 kind={} tool={} text={}",
                                        trace.getKind(), trace.getToolName(), trace.getText());
                            }
                        },
                        toolCalls -> {
                            if (CollectionUtils.isEmpty(toolCalls) || st.closed) {
                                return;
                            }
                            try {
                                switch (mode) {
                                    case "cc":
                                        writeCcToolCalls(st, toolCalls);
                                        break;
                                    case "openai":
                                        writeOpenAiToolCalls(st, id, chunkCreated, effModel, toolCalls);
                                        break;
                                    default:
                                        break;
                                }
                            } catch (IOException e) {
                                log.warn("[ServiceInstance] 工具调用帧写入失败: {}", e.getMessage());
                            }
                        }
                ));
        if (buffered) {
            try {
                if (!completed.await(180, TimeUnit.SECONDS) && terminal.compareAndSet(false, true)) {
                    st.closed = true;
                    tryWriteError(exchange, 504, "upstream_timeout", "上游响应超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (terminal.compareAndSet(false, true)) {
                    st.closed = true;
                    tryWriteError(exchange, 503, "request_interrupted", "请求已中断");
                }
            }
        }
    }

    /**
     * 把外部调用模式串映射为协议标识。
     *
     * @param mode 调用模式，可为 null
     * @return 对应协议标识
     */
    private String modeToProtocol(String mode) {
        if (mode.startsWith("openai")) {
            return "openai";
        }
        if (mode.startsWith("cc")) {
            return "cc";
        }
        if (mode.startsWith("gemini")) {
            return "gemini";
        }
        return "unknown";
    }

    /**
     * 落一条访问日志（端点 / 方法 / 模型 / 协议 / 状态 / Token 用量 / 延迟 / 来源 IP），
     * 并记下凭证归属。失败仅告警，不回写响应。
     *
     * @param inst     服务实例（提供实例 ID 与用户 ID）
     * @param exchange 当前 HTTP 交换对象（取路径 / 方法 / 来源 IP）
     * @param model    实际生效的模型名
     * @param protocol 协议标识
     * @param status   响应状态码
     * @param usage    本次调用用量，可为 null
     * @param latency  调用耗时（毫秒）
     * @param caller   调用方身份，可为 null
     */
    private void writeAccessLog(SysAiServiceInstance inst, GatewayExchange exchange, String model,
                                String protocol, int status, AiUsage usage, long latency, Caller caller) {
        try {
            SysAiServiceAccessLog logRow = new SysAiServiceAccessLog();
            logRow.setInstanceId(inst.getInstanceId());
            logRow.setEndpoint(exchange.getRequestURI().getPath());
            logRow.setMethod(exchange.getRequestMethod());
            logRow.setModel(model);
            logRow.setProtocol(protocol);
            logRow.setStatus(status);
            int p = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
            int c = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
            logRow.setPromptTokens(p);
            logRow.setCompletionTokens(c);
            logRow.setTotalTokens(p + c);
            logRow.setLatencyMs(latency);
            logRow.setClientIp(clientIp(exchange));
            logRow.setUserId(inst.getUserId());
            // 凭证归属：谁花的这笔账要能查得出来
            if (caller != null && caller.appKey != null) {
                logRow.setAppKey(caller.appKey);
            }
            accessLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("[ServiceInstance] 访问日志写入失败: {}", e.getMessage());
        }
    }

    // ==================== 鉴权 ====================

    /**
     * 调用方身份：<b>appKey 是唯一凭证</b>。
     *
     * <p>为什么统一到 appKey：服务实例对外暴露的 {@code /v1/models}、{@code /v1/chat/completions}、
     * {@code /v1/messages}、Gemini 生成，必须与网关 {@code /v2/v1/*} 同一套口径——同一个凭证、
     * 同一份模型范围（分组 + 限定模型）、同一本额度账（次数 / Token / 余额）、同一套用量归集。
     * 否则「控制台签发的 Key 能用什么模型」和「用量看板里这笔调用算谁的」会各说各话。</p>
     */
    private static final class Caller {
        /** appKey 值（唯一凭证） */
        private final String appKey;
        /** appKey 行（含分组 / 限定模型 / 额度列） */
        private final SysAiAppKey appKeyRow;

        /**
         * 构造调用方身份。
         *
         * @param appKey    appKey 值（唯一凭证）
         * @param appKeyRow appKey 行（含分组 / 限定模型 / 额度列）
         */
        private Caller(String appKey, SysAiAppKey appKeyRow) {
            this.appKey = appKey;
            this.appKeyRow = appKeyRow;
        }

        /**
         * 由 appKey 行构造调用方身份。
         *
         * @param row appKey 行，不能为 null
         * @return 调用方身份
         */
        static Caller ofAppKey(SysAiAppKey row) {
            return new Caller(row.getAppKey(), row);
        }
    }

    /**
     * 校验请求携带的凭证：只按 appKey（sys_ai_app_key）判定。
     *
     * <p>错误码与网关保持一套（{@code token_expired} / {@code request_quota_exhausted} /
     * {@code token_quota_exhausted}），客户端一套处理逻辑即可；余额不足单独回 402
     * {@code insufficient_balance}，与网关的计费守卫同语义。</p>
     *
     * @param exchange 当前 HTTP 交换对象，用于提取凭证与回写错误
     * @return 校验通过的调用方；校验失败时已回写错误并返回 null
     */
    private Caller authenticate(GatewayExchange exchange) {
        String presented = extractPresentedToken(exchange);
        if (presented == null || presented.isBlank()) {
            tryWriteError(exchange, 401, "authentication_error", "无效或缺失的 AppKey");
            return null;
        }
        // 实时查（不走缓存），否则停用/超额要等缓存过期才生效
        SysAiAppKey row = appKeyService.getByAppKeyLive(presented);
        if (row != null) {
            return authenticateAppKey(row, exchange);
        }
        tryWriteError(exchange, 401, "authentication_error", "无效或缺失的 AppKey");
        return null;
    }

    /**
     * 校验 appKey：停用、过期、次数、Token 总量、IP 白名单、余额逐条判定。
     *
     * @param row      appKey 行，不能为 null
     * @param exchange 当前 HTTP 交换对象
     * @return 校验通过的调用方；失败时已回写错误并返回 null
     */
    private Caller authenticateAppKey(SysAiAppKey row, GatewayExchange exchange) {
        // 判定口径与网关同一份（AppKeyQuotaGate），这里只负责把它翻译成实例端口的错误体，
        // 不再各自维护一套「先判什么后判什么」——两套口径曾导致网关永不过期、实例已过期的鬼故事。
        AppKeyQuotaGate.Deny deny = AppKeyQuotaGate.check(row, clientIp(exchange));
        if (deny == AppKeyQuotaGate.Deny.NONE) {
            // 余额是计费口径，排在身份与额度之后：先确认「这把 Key 还能用」，再说「还付得起」
            deny = AppKeyQuotaGate.checkBillable(row);
        }
        switch (deny) {
            case NONE:
                return Caller.ofAppKey(row);
            case DISABLED:
                tryWriteError(exchange, 401, "appkey_disabled", "该 AppKey 已停用");
                return null;
            case EXPIRED:
                tryWriteError(exchange, 401, "token_expired", "该 AppKey 已过期");
                return null;
            case USE_EXHAUSTED:
                tryWriteError(exchange, 401, "request_quota_exhausted",
                        "该 AppKey 请求次数已用尽（上限 " + row.getMaxUse() + "，已用 " + nz(row.getUseCount()) + "）");
                return null;
            case TOKEN_EXHAUSTED:
                tryWriteError(exchange, 401, "token_quota_exhausted",
                        "该 AppKey Token 余额已用尽（总量 " + row.getMaxTokens() + "，已消耗 " + nz(row.getTotalTokens()) + "）");
                return null;
            case IP_NOT_ALLOWED:
                tryWriteError(exchange, 403, "ip_not_allowed", "客户端 IP 不在该 AppKey 的白名单内");
                return null;
            case INSUFFICIENT_BALANCE:
                BigDecimal balance = row.getBalance();
                tryWriteError(exchange, 402, "insufficient_balance",
                        "该 AppKey 余额不足（" + (balance != null ? balance.toPlainString() : "0") + "），请充值后使用");
                return null;
            default:
                return null;
        }
    }

    /**
     * 把 appKey 注入到请求参数 {@code _appKey}：对话门面据此把本次调用的 Token 用量
     * 记到这把 Key 的账上（{@code total_tokens} 自增）。
     *
     * <p>网关侧三个控制器做的是同一件事。这条链路是「按 Token 总量」配额唯一的增长来源 ——
     * 少了这一步，配额永远不会涨，用户超额也拦不住。</p>
     *
     * @param request 对话请求，不能为 null
     * @param caller  调用方，可为 null
     */
    private void bindAppKey(AiChatRequest request, Caller caller) {
        if (caller == null || request == null) {
            return;
        }
        Map<String, Object> params = request.getParameters();
        if (params == null) {
            params = new LinkedHashMap<>();
            request.setParameters(params);
        }
        params.put("_appKey", caller.appKey);
    }

    /**
     * 计算调用方允许调用的模型集合；返回 {@code null} 表示不限制（全量）。
     *
     * <p>appKey 的范围 = 所属分组的模型清单 ∩ Key 上的限定模型。
     * 推导逻辑与网关共用 {@link AppKeyModelScope}，避免两个入口判定不一致。</p>
     *
     * @param caller 调用方，可为 null
     * @return 允许的模型集合；{@code null} 表示不限制；空集合表示一个都不许
     */
    private Set<String> allowedModels(Caller caller) {
        if (caller == null) {
            return null;
        }
        return modelScope.resolve(caller.appKeyRow);
    }

    /**
     * 校验请求的模型是否在调用方的模型范围内。
     *
     * <p>越权回 403 {@code model_not_allowed}：401 会让客户端以为凭证失效，而这里凭证是好的，
     * 只是不允许用这个模型。范围为空（一个模型都没分配）时同样拒绝，并明确说出来。</p>
     *
     * @param caller   调用方，可为 null（不限制）
     * @param model    本次请求实际生效的模型名，可为 null 或空白
     * @param exchange 当前 HTTP 交换对象，用于回写错误
     * @return 允许调用时返回 true；越权时已回写 403 并返回 false
     */
    private boolean assertModelAllowed(Caller caller, String model, GatewayExchange exchange) {
        Set<String> scope = allowedModels(caller);
        if (AppKeyModelScope.allows(scope, model)) {
            return true;
        }
        String wanted = model == null ? "" : model.trim();
        if (scope.isEmpty()) {
            tryWriteError(exchange, 403, "model_not_allowed",
                    "该 AppKey 未分配任何模型，请联系管理员在分组或 Key 上配置可用模型");
            return false;
        }
        tryWriteError(exchange, 403, "model_not_allowed",
                "该 AppKey 不允许调用模型 " + (wanted.isEmpty() ? "（未指定模型）" : wanted)
                        + "，可用模型：" + String.join(",", scope));
        return false;
    }

    /**
     * 从请求头提取凭证，依次尝试 Authorization Bearer、x-goog-api-key、x-api-key。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @return 去除首尾空白的凭证；三个请求头都缺失时返回 null
     */
    private String extractPresentedToken(GatewayExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
        if (key == null || key.isBlank()) {
            key = exchange.getRequestHeaders().getFirst("x-api-key");
        }
        return key == null ? null : key.trim();
    }

    /**
     * 额度回填：每次请求终结（成功/失败）计一次调用。
     *
     * <p>Token 消耗<b>不在这里累计</b>：真实用量只有在对话门面拿到 {@code usage} 时才存在，
     * 门面（{@code AiChatFacadeServiceImpl#accumulateAppKeyTokens}）是 Token 记账的唯一权威，
     * 网关与实例端口都经过它。此处若再累加一次，同一笔调用会被记成两倍 —— 用户会看到额度
     * 莫名双倍消耗，且永远先于真实用量触碰上限。</p>
     *
     * <p>请求次数则相反：只有本类知道「请求已经发出去了」（包括上游失败的那一次），
     * 所以计次留在这里，与网关「鉴权即计次」的口径一致。</p>
     *
     * @param caller  已鉴权的调用方，可为 null（此时直接返回）
     * @param success 本次请求是否成功
     */
    private void consumeQuota(Caller caller, boolean success) {
        if (caller == null) {
            return;
        }
        try {
            // 失败也计一次（与按次数配额的口径一致：请求已经发出去了）
            appKeyService.updateUseInfoByAppKey(caller.appKey);
        } catch (Exception e) {
            log.warn("[ServiceInstance] 额度回填失败: {}", e.getMessage());
        }
    }

    /**
     * 空安全的 Long 取值，null 记为 0。
     *
     * @param v 原始值，可为 null
     * @return 非空数值
     */
    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * 解析客户端 IP，优先取 {@code X-Forwarded-For} 的第一段。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @return 客户端 IP；无法解析时返回 {@code unknown}
     */
    private String clientIp(GatewayExchange exchange) {
        String fwd = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return comma >= 0 ? fwd.substring(0, comma).trim() : fwd.trim();
        }
        InetSocketAddress addr = exchange.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    // ==================== 模型兜底 ====================

    /**
     * 取实际生效的模型名，请求未指定时回落到系统默认模型。
     *
     * @param requested 请求中的模型名，可为 null 或空白
     * @return 生效模型名，未配置时可能返回空字符串
     */
    private String effectiveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return defaultModel();
    }

    /**
     * 按 runtime/models 三级配置键解析默认模型名，均未配置时交给统一模型解析器。
     *
     * @return 默认模型名，无法解析时返回空字符串
     */
    private String defaultModel() {
        String[][] keys = {
                {"runtime", "default_model"},
                {"models", "defaultChatModelId"},
                {"runtime", "default_chat_model"}
        };
        for (String[] k : keys) {
            try {
                // 直读数据库（不走 Redis 缓存），理由同 ChatStreamOrchestrator.getDefaultModel
                String configured = globalSettingService.getValueDirect(k[0], k[1])
                        .map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
                if (configured != null) {
                    int idx = configured.indexOf(':');
                    return idx >= 0 ? configured.substring(idx + 1) : configured;
                }
            } catch (Exception e) {
                log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
            }
        }
        // 不写死兜底模型名：上面三级都取不到时交给统一解析器（它会连带 Bot 默认一起看），
        // 仍解析不到就返回 空，由调用方报明确错误，绝不悄悄用一个可能不存在的模型名
        return effectiveChatModelResolver.resolve(null);
    }

    // ==================== IO 工具 ====================

    /**
     * 读取并解析 JSON 请求体，解析失败时以 {@code _raw} 键包裹原始文本返回。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @return 解析后的 Map，请求体为空时返回空 Map，不会为 null
     * @throws IOException 读取请求体失败时抛出
     */
    private Map<String, Object> readJsonBody(GatewayExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JSON.readValue(bytes, Map.class);
            return m != null ? m : new LinkedHashMap<>();
        } catch (Exception e) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("_raw", new String(bytes, StandardCharsets.UTF_8));
            return wrap;
        }
    }

    /**
     * 以 200 状态打开 SSE 响应头，禁用缓存并保持长连接。
     *
     * @param exchange    当前 HTTP 交换对象，不能为 null
     * @param contentType 响应的基础 Content-Type，不能为 null，方法内追加 UTF-8 字符集
     * @throws IOException 发送响应头失败时抛出
     */
    private void openSse(GatewayExchange exchange, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        // 切换框架响应为 SSE 模式：响应头按 chunked 立即写出，后续逐帧下发
        exchange.openSseStream();
    }

    /**
     * 输出 JSON 响应，未设置 Content-Type 时补默认值。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param status   HTTP 状态码
     * @param payload  响应体对象，不能为 null
     * @throws IOException 序列化或写出响应失败时抛出
     */
    private void writeJsonResponse(GatewayExchange exchange, int status, Object payload) throws IOException {
        if (exchange.getResponseHeaders().get("Content-Type") == null) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        }
        byte[] data = JSON.writeValueAsBytes(payload);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * 尽力回写错误响应；响应头已发送时静默跳过，避免二次写头触发异常。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param status   HTTP 状态码
     * @param type     错误类型标识，不能为 null
     * @param message 错误描述，可为 null
     */
    private void tryWriteError(GatewayExchange exchange, int status, String type, String message) {
        try {
            if (exchange.getResponseCode() >= 0) {
                // 响应头已发送，不能再写入新的错误响应
                return;
            }
        } catch (Exception ignored) {
            return;
        }
        try {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", Map.of("type", type, "message", message));
            writeJsonResponse(exchange, status, err);
        } catch (Exception e) {
            log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
        }
    }

    /**
     * 判断响应头是否已发出（已调用过 sendResponseHeaders）。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @return 已发出返回 true；状态不可知时按未发出处理
     */
    private boolean sseHeadersSent(GatewayExchange exchange) {
        try {
            return exchange.getResponseCode() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * SSE 响应头已发出但尚未写过任何帧时的错误收尾：补写一条协议内错误帧并关闭响应流。
     *
     * <p>gemini 流式在 {@code runFacade} 前只调用了 {@code openSse}（响应头已发出），
     * 若门面在首个分片到达前报错，原逻辑会尝试改写 502 而触发「响应头已发送」异常并被吞掉，
     * 导致交换既不写也不关、客户端连接无限期挂起。这里改为按协议补一条错误帧后立即收流。</p>
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param st       流式状态，不能为 null
     * @param mode     流式协议模式，不能为 null
     * @param error    上游错误，可为 null
     */
    private void abortOpenSse(GatewayExchange exchange, StreamState st, String mode, Throwable error) {
        String message = error != null && error.getMessage() != null ? error.getMessage() : "upstream error";
        try {
            Map<String, Object> err = Map.of("message", message, "type", "upstream_error");
            if ("gemini".equals(mode)) {
                st.writeSseData(Map.of("error", err));
            } else {
                st.writeSseEvent("error", Map.of("error", err));
            }
        } catch (Exception e) {
            log.debug("[ai] SSE 错误帧写出失败，已忽略: {}", e.getMessage());
        } finally {
            st.close();
            if (!st.committed) {
                // 错误帧未能写出（如客户端已断开）时输出流尚未打开，显式关闭交换确保连接收尾
                try {
                    exchange.close();
                } catch (Exception e) {
                    log.debug("[ai] 交换关闭失败，已忽略: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 空安全的字符串取值，null 转为空字符串。
     *
     * @param o 任意对象，可为 null
     * @return 字符串形式，null 时返回空字符串
     */
    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * 生成 12 位无连字符的随机标识片段。
     *
     * @return 12 位十六进制随机字符串
     */
    private static String uuid8() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ==================== 框架交换适配 ====================

    /**
     * 框架 Server 交换适配器。
     *
     * <p>把框架 {@link ServerRequest}/{@link ServerResponse} 包装成与原 JDK
     * {@code HttpExchange} 兼容的最小 API 面，使各协议处理逻辑无需感知底层
     * HTTP 实现差异。请求头经框架按小写归一存取；响应体由框架在
     * {@code complete()} 阶段统一落盘；SSE 通过框架的 {@code sse()} 系列方法逐帧下发。</p>
     */
    private static final class GatewayExchange {
        final ServerRequest request;
        final ServerResponse response;
        private final GatewayHeaders requestHeaders;
        private final GatewayHeaders responseHeaders;
        private int responseCode = -1;
        private boolean sseOpened;

        /**
         * 创建交换适配器。
         *
         * @param request  框架请求对象，不能为 null
         * @param response 框架响应对象，不能为 null
         */
        GatewayExchange(ServerRequest request, ServerResponse response) {
            this.request = request;
            this.response = response;
            this.requestHeaders = new GatewayHeaders(request, null);
            this.responseHeaders = new GatewayHeaders(null, response);
        }

        /**
         * 获取请求 URI（含路径与查询参数）。
         *
         * @return 请求 URI，不会为 null
         */
        URI getRequestURI() {
            return URI.create(request.getUri());
        }

        /**
         * 获取请求方法名。
         *
         * @return 如 {@code GET}、{@code POST}
         */
        String getRequestMethod() {
            return request.getMethod().name();
        }

        /**
         * 获取请求头集合视图。
         *
         * @return 请求头集合，不会为 null
         */
        GatewayHeaders getRequestHeaders() {
            return requestHeaders;
        }

        /**
         * 获取响应头集合视图。
         *
         * @return 响应头集合，不会为 null
         */
        GatewayHeaders getResponseHeaders() {
            return responseHeaders;
        }

        /**
         * 获取请求体输入流。
         *
         * @return 请求体输入流，不会为 null
         */
        InputStream getRequestBody() {
            return request.getInputStream();
        }

        /**
         * 获取响应体输出流（框架缓冲流，complete 阶段统一落盘）。
         *
         * @return 响应体输出流，不会为 null
         */
        OutputStream getResponseBody() {
            return response.getOutputStream();
        }

        /**
         * 记录响应状态码并写入框架响应。
         *
         * @param code          HTTP 状态码
         * @param contentLength 响应体长度；框架按缓冲体统一交付，该值仅保留兼容性
         */
        void sendResponseHeaders(int code, long contentLength) {
            responseCode = code;
            response.setStatus(code);
        }

        /**
         * 获取已记录的响应状态码。
         *
         * @return 已调用 {@link #sendResponseHeaders(int, long)} 时返回该状态码，否则返回 -1
         */
        int getResponseCode() {
            return responseCode;
        }

        /**
         * 获取客户端远程地址。
         *
         * @return 客户端地址；无法解析时返回 null
         */
        InetSocketAddress getRemoteAddress() {
            String ip = request.getRemoteAddress();
            if (ip == null || ip.isBlank()) {
                return null;
            }
            try {
                InetSocketAddress addr = new InetSocketAddress(ip, request.getRemotePort());
                return addr.getAddress() != null ? addr : null;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 将框架响应切换为 SSE 流模式：响应头立即按 chunked 写出，后续逐帧下发。
         */
        void openSseStream() {
            sseOpened = true;
            response.sse();
        }

        /**
         * 写出一条 SSE 事件帧。
         *
         * @param event 事件名，可为 null
         * @param json  帧负载 JSON 文本，可为 null
         */
        void sseEvent(String event, String json) {
            response.sseEvent(event, json);
        }

        /**
         * 写出 SSE 关闭帧并断开连接。
         */
        void sseClose() {
            response.sseClose();
        }

        /**
         * 关闭交换：SSE 模式写关闭帧收流，普通模式终止响应并交付缓冲体。
         */
        void close() {
            if (sseOpened) {
                response.sseClose();
            } else {
                response.end();
            }
        }
    }

    /**
     * 请求/响应头集合的兼容视图。
     *
     * <p>对齐 JDK {@code Headers} 的常用语义：{@code getFirst} 取首个值、
     * {@code get} 返回单值列表或 null、{@code set} 覆盖写。请求头经框架
     * 小写归一存取；响应头直接读写框架响应对象。</p>
     */
    private static final class GatewayHeaders {
        private final ServerRequest request;
        private final ServerResponse response;

        /**
         * 创建头集合视图（请求与响应二选一）。
         *
         * @param request  框架请求对象，请求头视图时不能为 null
         * @param response 框架响应对象，响应头视图时不能为 null
         */
        GatewayHeaders(ServerRequest request, ServerResponse response) {
            this.request = request;
            this.response = response;
        }

        /**
         * 获取指定头的第一个值。
         *
         * @param name 头名称，框架侧按小写归一匹配
         * @return 头值，不存在时返回 null
         */
        String getFirst(String name) {
            if (request != null) {
                return request.getHeader(name);
            }
            return response.getHeader(name);
        }

        /**
         * 获取指定头的值列表。
         *
         * @param name 头名称
         * @return 单值列表；不存在时返回 null
         */
        List<String> get(String name) {
            String value = getFirst(name);
            return value == null ? null : List.of(value);
        }

        /**
         * 覆盖写指定响应头。
         *
         * @param name  头名称
         * @param value 头值
         */
        void set(String name, String value) {
            response.setHeader(name, value);
        }
    }

    // ==================== 流式状态持有 ====================

    private static final class StreamState {
        final GatewayExchange exchange;
        boolean committed;
        boolean closed;
        final StringBuilder buffer = new StringBuilder();
        boolean ccThinkingOpened;
        boolean ccTextOpened;
        int ccThinkingIndex = -1;
        int ccTextIndex = -1;
        int ccBlockCount; // 已开启的 content 块数量（同时是下一个块的下标）
        final List<Integer> ccOpenedBlocks = new ArrayList<>(); // 已开启块下标（按开启顺序=下标顺序）
        final Map<Integer, Integer> ccToolBlockByIndex = new LinkedHashMap<>(); // 工具调用序号 -> content 块下标
        boolean toolCallsEmitted; // OpenAI 协议：本轮是否产生过 tool_calls（决定 finish_reason）

        /**
         * Stream State
         * @param exchange HTTP 交换对象
         * @return 返回值
        */
        StreamState(GatewayExchange exchange) {
            this.exchange = exchange;
        }

        /**
     * 写出单行 SSE data 帧。
     *
     * @param payload 帧负载，不能为 null，方法内部序列化为 JSON
     * @throws IOException 写出失败时抛出
     */
    void writeSseData(Object payload) throws IOException {
            writeFrame(null, JSON.writeValueAsString(payload));
        }

        /**
     * 写出 SSE 结束帧 {@code data: [DONE]}。
     *
     * @throws IOException 写出失败时抛出
     */
    void writeDone() throws IOException {
            writeFrame(null, "[DONE]");
        }

        /**
     * 写出带 event 名的 SSE 帧。
     *
     * @param event   事件名，不能为 null
     * @param payload 帧负载，不能为 null，方法内部序列化为 JSON
     * @throws IOException 写出失败时抛出
     */
    void writeSseEvent(String event, Object payload) throws IOException {
            writeFrame(event, JSON.writeValueAsString(payload));
        }

        /**
     * 同步写出一帧 SSE 事件并标记已提交。
     *
     * <p>框架 {@code sseEvent} 把整帧作为单次写入入队，写失败（如连接已断）时静默丢弃；
     * 这里仍以 {@code committed} 记录「已产出过帧」，供错误收尾分支判定。</p>
     *
     * @param event 事件名，可为 null
     * @param json  帧负载 JSON 文本，不能为 null
     */
    private synchronized void writeFrame(String event, String json) {
            exchange.sseEvent(event, json);
            committed = true;
        }

        /**
     * 幂等关闭 SSE 流：写 zero chunk 并断开连接。
     */
    synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            exchange.sseClose();
        }
    }
}

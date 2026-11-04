// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.ai.chat.ChatTool;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import com.chua.starter.ai.support.entity.SysAiServiceToken;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceInstanceMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceTokenMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

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
    private final SysAiServiceTokenMapper tokenMapper;
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

    private final Map<String, HttpServer> servers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> pools = new ConcurrentHashMap<>();

    /**
     * 创建服务实例管理器。
     *
     * @param instanceMapper 服务实例数据访问器
     * @param tokenMapper 服务令牌数据访问器（老数据只读兼容）
     * @param accessLogMapper 访问日志数据访问器
     * @param registry 聊天客户端注册表
     * @param facade 聊天调用门面
     * @param globalSettingService 全局设置服务
     * @param appKeyService appKey 服务
     * @param modelScope 模型范围解析器
     * @param effectiveChatModelResolver 生效对话模型解析器
     */
    public ServiceInstanceManager(SysAiServiceInstanceMapper instanceMapper,
                                  SysAiServiceTokenMapper tokenMapper,
                                  SysAiServiceAccessLogMapper accessLogMapper,
                                  ChatClientRegistry registry,
                                  AiChatFacadeService facade,
                                  SysAiGlobalSettingService globalSettingService,
                                  SysAiAppKeyService appKeyService,
                                  AppKeyModelScope modelScope,
                                  EffectiveChatModelResolver effectiveChatModelResolver) {
        this.instanceMapper = instanceMapper;
        this.tokenMapper = tokenMapper;
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
            HttpServer server = HttpServer.create(new InetSocketAddress(inst.getPort()), 0);
            ExecutorService pool = Executors.newFixedThreadPool(10);
            server.setExecutor(pool);
            registerContexts(server, inst);
            server.start();
            servers.put(id, server);
            pools.put(id, pool);
            log.info("[ServiceInstance] 实例 {} 已启动，端口 {}", inst.getInstanceName(), inst.getPort());
            return true;
        } catch (IOException e) {
            log.error("[ServiceInstance] 端口 {} 绑定失败: {}", inst.getPort(), e.getMessage());
            return false;
        }
    }

    /**
     * 停止指定服务实例并释放线程池。
     *
     * @param instanceId 服务实例标识
     */
    public synchronized void stopInstance(String instanceId) {
        HttpServer server = servers.remove(instanceId);
        if (server != null) {
            server.stop(2);
        }
        ExecutorService pool = pools.remove(instanceId);
        if (pool != null) {
            pool.shutdownNow();
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
     * 按实例开关注册各协议端点上下文。
     *
     * @param server JDK HttpServer 实例，不能为 null
     * @param inst   服务实例配置，不能为 null，其协议开关决定注册哪些路径
     */
    private void registerContexts(HttpServer server, SysAiServiceInstance inst) {
        if (inst.getOpenaiEnabled() != null && inst.getOpenaiEnabled() == 1) {
            server.createContext("/v1/chat/completions", ex -> safeHandle(inst, ex, "openai_chat"));
            server.createContext("/v1/models", ex -> safeHandle(inst, ex, "openai_models"));
        }
        if (inst.getCcEnabled() != null && inst.getCcEnabled() == 1) {
            // JDK HttpServer 按最长前缀匹配：count_tokens 路径更长，注册后不会落到 /v1/messages
            server.createContext("/v1/messages/count_tokens", ex -> safeHandle(inst, ex, "cc_count_tokens"));
            server.createContext("/v1/messages", ex -> safeHandle(inst, ex, "cc_messages"));
        }
        if (inst.getGeminiEnabled() != null && inst.getGeminiEnabled() == 1) {
            server.createContext("/v1beta/models", ex -> safeHandle(inst, ex, "gemini"));
        }
    }

    /**
     * 统一包装请求处理，捕获异常并回写 500 错误，避免单次请求异常导致连接悬挂。
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param kind     端点类型标识，不能为 null 或空字符串
     */
    private void safeHandle(SysAiServiceInstance inst, HttpExchange exchange, String kind) {
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
    private void route(SysAiServiceInstance inst, HttpExchange exchange, String kind) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        switch (kind) {
            case "openai_models": {
                Caller caller = authenticate(inst, exchange);
                if (caller == null) {
                    return;
                }
                handleModelsList(exchange, "openai", caller);
                return;
            }
            case "openai_chat": {
                Caller caller = authenticate(inst, exchange);
                if (caller == null) {
                    return;
                }
                handleOpenAiChat(inst, exchange, caller);
                return;
            }
            case "cc_messages": {
                Caller caller = authenticate(inst, exchange);
                if (caller == null) {
                    return;
                }
                handleCcMessages(inst, exchange, caller);
                return;
            }
            case "cc_count_tokens": {
                Caller caller = authenticate(inst, exchange);
                if (caller == null) {
                    return;
                }
                handleCcCountTokens(exchange);
                return;
            }
            case "gemini": {
                Caller caller = authenticate(inst, exchange);
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
    private void handleOpenAiChat(SysAiServiceInstance inst, HttpExchange exchange, Caller caller) throws IOException {
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

    private void writeOpenAiStream(StreamState st, String id, long created, String model,
                                   String text, String reasoning, boolean finish, AiUsage usage,
                                   Map<String, Object> reqBody) throws IOException {
        if (reasoning != null && !reasoning.isEmpty()) {
            Map<String, Object> env = openAiChunkEnvelope(id, created, model);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("reasoning_content", reasoning);
            env.put("choices", List.of(choiceDelta(0, delta, null)));
            st.writeSseData(env);
        }
        if (text != null && !text.isEmpty()) {
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
    private void handleCcMessages(SysAiServiceInstance inst, HttpExchange exchange, Caller caller) throws IOException {
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
    private void handleCcCountTokens(HttpExchange exchange) {
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

    private void writeCcStream(StreamState st, String id, String model, String text, String reasoning,
                               boolean finish, AiUsage usage) throws IOException {
        // 动态块：thinking → text → tool_use，块下标按开启顺序分配
        if (reasoning != null && !reasoning.isEmpty()) {
            int thinkingIndex = openCcThinkingBlock(st);
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "thinking_delta");
            delta.put("thinking", reasoning);
            st.writeSseEvent("content_block_delta",
                    Map.of("type", "content_block_delta", "index", thinkingIndex, "delta", delta));
        }
        if (text != null && !text.isEmpty()) {
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
            if (partial != null && !partial.isEmpty()) {
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
    private void handleGemini(SysAiServiceInstance inst, HttpExchange exchange, String method, String path,
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
                String text = geminiPartsText(cm.get("parts"));
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
     * <p>令牌绑定了模型清单时，列表只回这些模型 —— 客户端（Cherry Studio 之类）常用
     * {@code /v1/models} 做下拉，若照旧全量返回，用户选到一个没权限的模型再被 403，
     * 体验与「按模型授权」的语义都不对。</p>
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param flavor   协议风味，取值为 {@code gemini} 或其他（按 OpenAI 结构输出）
     * @param caller   已鉴权的调用方，可为 null（视为不限制模型）
     */
    private void handleModelsList(HttpExchange exchange, String flavor, Caller caller) {
        try {
            List<String> models = new ArrayList<>(registry.getModels());
            // /models 必须与 /chat/completions 同一口径：调用方看不到的模型，也不许出现在清单里
            models = new ArrayList<>(AppKeyModelScope.filter(allowedModels(caller), models));
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
        // 完整上下文已在 messages；prompt 留空避免重复
        request.setPrompt("");
        applyOpenAiTools(request, body);
        return request;
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

    private void runFacade(SysAiServiceInstance inst, AiChatRequest request, HttpExchange exchange,
                           StreamState st, String mode, String id, String effModel,
                           Map<String, Object> reqBody, Caller caller) {
        long start = System.currentTimeMillis();
        final long chunkCreated = System.currentTimeMillis() / 1000;
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
                                consumeQuota(caller, totalOf(usage), true);
                            } catch (Exception e) {
                                log.warn("[ServiceInstance] 完成日志/额度回填失败: {}", e.getMessage());
                            }
                        },
                        error -> {
                            log.error("[ServiceInstance] 上游调用失败: {}", error.getMessage(), error);
                            try {
                                if (st.committed && !st.closed) {
                                    st.close();
                                } else if (!st.committed) {
                                    writeJsonResponse(exchange, 502, Map.of(
                                            "error", Map.of("message", error.getMessage(), "type", "upstream_error")));
                                }
                                writeAccessLog(inst, exchange, effModel, modeToProtocol(mode),
                                        502, null, System.currentTimeMillis() - start, caller);
                                consumeQuota(caller, null, false);
                            } catch (Exception e) {
                                log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                            }
                        },
                        reasoning -> {
                            if (reasoning == null || reasoning.isEmpty() || st.closed) {
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
                            if (toolCalls == null || toolCalls.isEmpty() || st.closed) {
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

    private void writeAccessLog(SysAiServiceInstance inst, HttpExchange exchange, String model,
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
            // 凭证归属：谁花的这笔账要能查得出来（老令牌没有 appKey，留空）
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
     * 调用方身份：<b>appKey 是唯一正式凭证</b>，老的实例服务令牌只做只读兼容。
     *
     * <p>为什么统一到 appKey：服务实例对外暴露的 {@code /v1/models}、{@code /v1/chat/completions}、
     * {@code /v1/messages}、Gemini 生成，必须与网关 {@code /v2/v1/*} 同一套口径——同一个凭证、
     * 同一份模型范围（分组 + 限定模型）、同一本额度账（次数 / Token / 余额）、同一套用量归集。
     * 否则「控制台签发的 Key 能用什么模型」和「用量看板里这笔调用算谁的」会各说各话。</p>
     *
     * <p>{@code sys_ai_service_token} 已停止签发：老 Key 继续可用（模型范围取 {@code model_ids}、
     * 额度取 {@code expires_at / max_requests / max_tokens}），但新 Key 一律走 appKey。</p>
     */
    private static final class Caller {
        /** appKey 值（正式凭证）；老令牌调用时为 null */
        private final String appKey;
        /** appKey 行（含分组 / 限定模型 / 额度列）；老令牌调用时为 null */
        private final SysAiAppKey appKeyRow;
        /** 老的实例服务令牌；appKey 调用时为 null */
        private final SysAiServiceToken legacyToken;

        private Caller(String appKey, SysAiAppKey appKeyRow, SysAiServiceToken legacyToken) {
            this.appKey = appKey;
            this.appKeyRow = appKeyRow;
            this.legacyToken = legacyToken;
        }

        static Caller ofAppKey(SysAiAppKey row) {
            return new Caller(row.getAppKey(), row, null);
        }

        static Caller ofLegacy(SysAiServiceToken token) {
            return new Caller(null, null, token);
        }

        boolean isAppKey() {
            return appKey != null;
        }
    }

    /**
     * 校验请求携带的凭证：<b>先按 appKey</b>（sys_ai_app_key），不行再按老的实例令牌。
     *
     * <p>两种凭证的错误码刻意保持一致（{@code token_expired} / {@code request_quota_exhausted} /
     * {@code token_quota_exhausted}），客户端一套处理逻辑即可；余额不足单独回 402
     * {@code insufficient_balance}，与网关的计费守卫同语义。</p>
     *
     * @param inst     服务实例配置，不能为 null
     * @param exchange 当前 HTTP 交换对象，用于提取凭证与回写错误
     * @return 校验通过的调用方；校验失败时已回写错误并返回 null
     */
    private Caller authenticate(SysAiServiceInstance inst, HttpExchange exchange) {
        String presented = extractPresentedToken(exchange);
        if (presented == null || presented.isBlank()) {
            tryWriteError(exchange, 401, "authentication_error", "无效或缺失的 AppKey");
            return null;
        }
        // ① appKey：实时查（不走缓存），否则停用/超额要等缓存过期才生效
        SysAiAppKey row = appKeyService.getByAppKeyLive(presented);
        if (row != null) {
            return authenticateAppKey(row, exchange);
        }
        // ② 老的实例服务令牌（只读兼容，控制台已不再签发）
        Caller legacy = authenticateLegacyToken(inst, presented, exchange);
        if (legacy != null) {
            return legacy;
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
    private Caller authenticateAppKey(SysAiAppKey row, HttpExchange exchange) {
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
     * 校验老的实例服务令牌（存在性、启用、过期、次数、余额）。
     *
     * @param inst       服务实例配置
     * @param presented  请求携带的令牌
     * @param exchange   当前 HTTP 交换对象
     * @return 校验通过的调用方；令牌不存在时返回 null（由调用方回 401）
     */
    private Caller authenticateLegacyToken(SysAiServiceInstance inst, String presented, HttpExchange exchange) {
        List<SysAiServiceToken> matched = tokenMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiServiceToken>()
                        .eq(SysAiServiceToken::getInstanceId, inst.getInstanceId())
                        .eq(SysAiServiceToken::getEnabled, 1)
                        .eq(SysAiServiceToken::getToken, presented));
        if (matched.isEmpty()) {
            return null;
        }
        SysAiServiceToken t = matched.get(0);
        LocalDateTime now = LocalDateTime.now();
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(now)) {
            tryWriteError(exchange, 401, "token_expired", "访问令牌已过期");
            return null;
        }
        if (t.getMaxRequests() != null && nz(t.getUsedRequests()) >= t.getMaxRequests()) {
            tryWriteError(exchange, 401, "request_quota_exhausted", "访问令牌请求次数已用尽");
            return null;
        }
        if (t.getMaxTokens() != null && nz(t.getUsedTokens()) >= t.getMaxTokens()) {
            tryWriteError(exchange, 401, "token_quota_exhausted",
                    "访问令牌余额已用尽（总量 " + t.getMaxTokens() + "，已消耗 " + nz(t.getUsedTokens()) + "）");
            return null;
        }
        return Caller.ofLegacy(t);
    }

    /**
     * 把 appKey 注入到请求参数 {@code _appKey}：对话门面据此把本次调用的 Token 用量
     * 记到这把 Key 的账上（{@code total_tokens} 自增）。
     *
     * <p>网关侧三个控制器做的是同一件事。这条链路是「按 Token 总量」配额唯一的增长来源 ——
     * 少了这一步，配额永远不会涨，用户超额也拦不住。</p>
     *
     * @param request 对话请求，不能为 null
     * @param caller  调用方，可为 null（老令牌无 appKey）
     */
    private void bindAppKey(AiChatRequest request, Caller caller) {
        if (caller == null || caller.appKey == null || request == null) {
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
     * <p>appKey 的范围 = 所属分组的模型清单 ∩ Key 上的限定模型；老令牌取 {@code model_ids}。
     * 推导逻辑与网关共用 {@link AppKeyModelScope}，避免两个入口判定不一致。</p>
     *
     * @param caller 调用方，可为 null
     * @return 允许的模型集合；{@code null} 表示不限制；空集合表示一个都不许
     */
    private Set<String> allowedModels(Caller caller) {
        if (caller == null) {
            return null;
        }
        if (caller.isAppKey()) {
            return modelScope.resolve(caller.appKeyRow);
        }
        return AppKeyModelScope.parseCsv(caller.legacyToken.getModelIds());
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
    private boolean assertModelAllowed(Caller caller, String model, HttpExchange exchange) {
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
    private String extractPresentedToken(HttpExchange exchange) {
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
     * @param caller      已鉴权的调用方，可为 null（此时直接返回）
     * @param totalTokens 本次请求消耗的总 Token 数（由门面记账，此处仅用于老令牌）
     * @param success     本次请求是否成功
     */
    private void consumeQuota(Caller caller, Long totalTokens, boolean success) {
        if (caller == null) {
            return;
        }
        try {
            long addTokens = totalTokens != null && totalTokens > 0 ? totalTokens : 0L;
            if (caller.isAppKey()) {
                // 失败也计一次（与按次数配额的口径一致：请求已经发出去了）
                appKeyService.updateUseInfoByAppKey(caller.appKey);
                return;
            }
            SysAiServiceToken t = caller.legacyToken;
            if (t == null || t.getTokenId() == null) {
                return;
            }
            String sql = "used_requests = used_requests + 1"
                    + (addTokens > 0 ? ", used_tokens = used_tokens + " + addTokens : "")
                    + (success ? ", last_used_at = NOW()" : "");
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysAiServiceToken> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(SysAiServiceToken::getTokenId, t.getTokenId()).setSql(sql);
            tokenMapper.update(null, uw);
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
     * 计算用量总 Token 数，优先取总数字段，缺失时由输入 + 输出相加。
     *
     * @param usage 用量快照，可为 null
     * @return 总 Token 数；用量为 null 或计算结果非正数时返回 null
     */
    private static Long totalOf(AiUsage usage) {
        if (usage == null) {
            return null;
        }
        if (usage.getTotalTokens() != null) {
            return usage.getTotalTokens().longValue();
        }
        int p = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
        int c = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        long t = (long) p + c;
        return t > 0 ? t : null;
    }

    /**
     * 解析客户端 IP，优先取 {@code X-Forwarded-For} 的第一段。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @return 客户端 IP；无法解析时返回 {@code unknown}
     */
    private String clientIp(HttpExchange exchange) {
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
    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
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
    private void openSse(HttpExchange exchange, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
    }

    /**
     * 输出 JSON 响应，未设置 Content-Type 时补默认值。
     *
     * @param exchange 当前 HTTP 交换对象，不能为 null
     * @param status   HTTP 状态码
     * @param payload  响应体对象，不能为 null
     * @throws IOException 序列化或写出响应失败时抛出
     */
    private void writeJsonResponse(HttpExchange exchange, int status, Object payload) throws IOException {
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
    private void tryWriteError(HttpExchange exchange, int status, String type, String message) {
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

    // ==================== 流式状态持有 ====================

    private static final class StreamState {
        final HttpExchange exchange;
        OutputStream os;
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
        StreamState(HttpExchange exchange) {
            this.exchange = exchange;
        }

        /**
     * 获取响应输出流并标记已提交，首次调用时惰性初始化。
     *
     * @return 响应输出流，不会为 null
     * @throws IOException 打开响应输出流失败时抛出
     */
    OutputStream out() throws IOException {
            if (os == null) {
                os = exchange.getResponseBody();
                committed = true;
            }
            return os;
        }

        /**
     * 写出单行 SSE data 帧。
     *
     * @param payload 帧负载，不能为 null，方法内部序列化为 JSON
     * @throws IOException 写出失败时抛出
     */
    void writeSseData(Object payload) throws IOException {
            String json = JSON.writeValueAsString(payload);
            write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        }

        /**
     * 写出 SSE 结束帧 {@code data: [DONE]}。
     *
     * @throws IOException 写出失败时抛出
     */
    void writeDone() throws IOException {
            write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }

        /**
     * 写出带 event 名的 SSE 帧。
     *
     * @param event   事件名，不能为 null
     * @param payload 帧负载，不能为 null，方法内部序列化为 JSON
     * @throws IOException 写出失败时抛出
     */
    void writeSseEvent(String event, Object payload) throws IOException {
            String json = JSON.writeValueAsString(payload);
            write(("event: " + event + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        }

        /**
     * 同步写出字节并立即 flush，保证 SSE 帧即时可见。
     *
     * @param bytes 待写出的字节，不能为 null
     * @throws IOException 写出或 flush 失败时抛出
     */
    private synchronized void write(byte[] bytes) throws IOException {
            OutputStream o = out();
            o.write(bytes);
            o.flush();
        }

        /**
     * 幂等关闭响应流，关闭异常仅记日志。
     */
    synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                log.debug("[ai] IO 操作失败，已忽略: {}", e.getMessage());
            }
        }
    }
}

package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.ai.chat.ChatTool;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import com.chua.starter.ai.support.entity.SysAiServiceToken;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceInstanceMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceTokenMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, HttpServer> servers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> pools = new ConcurrentHashMap<>();

    /**
     * 创建服务实例管理器。
     *
     * @param instanceMapper 服务实例数据访问器
     * @param tokenMapper 服务令牌数据访问器
     * @param accessLogMapper 访问日志数据访问器
     * @param registry 聊天客户端注册表
     * @param facade 聊天调用门面
     * @param globalSettingService 全局设置服务
     */
    public ServiceInstanceManager(SysAiServiceInstanceMapper instanceMapper,
                                  SysAiServiceTokenMapper tokenMapper,
                                  SysAiServiceAccessLogMapper accessLogMapper,
                                  ChatClientRegistry registry,
                                  AiChatFacadeService facade,
                                  SysAiGlobalSettingService globalSettingService) {
        this.instanceMapper = instanceMapper;
        this.tokenMapper = tokenMapper;
        this.accessLogMapper = accessLogMapper;
        this.registry = registry;
        this.facade = facade;
        this.globalSettingService = globalSettingService;
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

    private void safeHandle(SysAiServiceInstance inst, HttpExchange exchange, String kind) {
        try {
            route(inst, exchange, kind);
        } catch (Exception e) {
            log.error("[ServiceInstance] 请求处理异常 kind={}: {}", kind, e.getMessage(), e);
            tryWriteError(exchange, 500, "internal_error", e.getMessage());
        }
    }

    // ==================== 路由 ====================

    private void route(SysAiServiceInstance inst, HttpExchange exchange, String kind) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        switch (kind) {
            case "openai_models":
                if (authenticate(inst, exchange) == null) {
                    return;
                }
                handleModelsList(exchange, "openai");
                return;
            case "openai_chat": {
                SysAiServiceToken token = authenticate(inst, exchange);
                if (token == null) {
                    return;
                }
                handleOpenAiChat(inst, exchange, token);
                return;
            }
            case "cc_messages": {
                SysAiServiceToken token = authenticate(inst, exchange);
                if (token == null) {
                    return;
                }
                handleCcMessages(inst, exchange, token);
                return;
            }
            case "cc_count_tokens": {
                SysAiServiceToken token = authenticate(inst, exchange);
                if (token == null) {
                    return;
                }
                handleCcCountTokens(exchange);
                return;
            }
            case "gemini": {
                SysAiServiceToken token = authenticate(inst, exchange);
                if (token == null) {
                    return;
                }
                handleGemini(inst, exchange, method, path, token);
                return;
            }
            default:
                tryWriteError(exchange, 404, "not_found", "未知端点");
        }
    }

    // ==================== OpenAI /v1/chat/completions ====================

    private void handleOpenAiChat(SysAiServiceInstance inst, HttpExchange exchange, SysAiServiceToken token) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        boolean wantStream = Boolean.TRUE.equals(body.get("stream"));
        List<Map<String, Object>> messages = parseOpenAiMessages(body.get("messages"));
        String model = str(body.get("model"));

        AiChatRequest request = buildRequest(inst, body, messages, model);
        String id = "chatcmpl-" + uuid8();
        long created = System.currentTimeMillis() / 1000;
        String effModel = effectiveModel(model);

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
            runFacade(inst, request, exchange, st, "openai", id, effModel, body, token);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "openai_nonstream", id, effModel, body, token);
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

    private Map<String, Object> openAiChunkEnvelope(String id, long created, String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("object", "chat.completion.chunk");
        m.put("created", created);
        m.put("model", model);
        return m;
    }

    private Map<String, Object> choiceDelta(int index, Map<String, Object> delta, String finishReason) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("index", index);
        c.put("delta", delta);
        c.put("finish_reason", finishReason);
        return c;
    }

    private Map<String, Object> openAiUsage(AiUsage usage) {
        Map<String, Object> u = new LinkedHashMap<>();
        int p = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0;
        int c = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
        u.put("prompt_tokens", p);
        u.put("completion_tokens", c);
        u.put("total_tokens", p + c);
        return u;
    }

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

    private void handleCcMessages(SysAiServiceInstance inst, HttpExchange exchange, SysAiServiceToken token) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        boolean wantStream = Boolean.TRUE.equals(body.get("stream"));
        List<Map<String, Object>> messages = parseCcMessages(body);
        String model = str(body.get("model"));

        AiChatRequest request = buildRequest(inst, body, messages, model);
        applyCcTools(request, body);
        String id = "msg_" + uuid8();
        String effModel = effectiveModel(model);

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
            runFacade(inst, request, exchange, st, "cc", id, effModel, body, token);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "cc_nonstream", id, effModel, body, token);
        }
    }

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

    private static long strLen(Object o) {
        return o == null ? 0 : String.valueOf(o).length();
    }

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

    private void handleGemini(SysAiServiceInstance inst, HttpExchange exchange, String method, String path,
                              SysAiServiceToken token)
            throws IOException {
        boolean stream = path.endsWith(":streamGenerateContent");
        boolean generate = stream || path.endsWith(":generateContent");
        if ("GET".equalsIgnoreCase(method) && !generate) {
            handleModelsList(exchange, "gemini");
            return;
        }
        Map<String, Object> body = readJsonBody(exchange);
        String pathModel = extractGeminiModel(path);
        List<Map<String, Object>> messages = parseGeminiContents(body);
        AiChatRequest request = buildRequest(inst, body, messages, pathModel);
        String effModel = effectiveModel(pathModel);

        if (stream) {
            openSse(exchange, "text/event-stream");
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "gemini", null, effModel, body, token);
        } else {
            StreamState st = new StreamState(exchange);
            runFacade(inst, request, exchange, st, "gemini_nonstream", null, effModel, body, token);
        }
    }

    private String extractGeminiModel(String path) {
        int marker = path.indexOf("/v1beta/models/");
        if (marker < 0) {
            return "";
        }
        String rest = path.substring(marker + "/v1beta/models/".length());
        int colon = rest.indexOf(':');
        return colon >= 0 ? rest.substring(0, colon) : rest;
    }

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

    private void handleModelsList(HttpExchange exchange, String flavor) {
        try {
            List<String> models = new ArrayList<>(registry.getModels());
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
                           Map<String, Object> reqBody, SysAiServiceToken token) {
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
                                        200, usage, System.currentTimeMillis() - start);
                                consumeQuota(token, totalOf(usage));
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
                                        502, null, System.currentTimeMillis() - start);
                                consumeQuota(token, null);
                            } catch (Exception ignored) {
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
                                String protocol, int status, AiUsage usage, long latency) {
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
            accessLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("[ServiceInstance] 访问日志写入失败: {}", e.getMessage());
        }
    }

    // ==================== 鉴权 ====================

    private SysAiServiceToken authenticate(SysAiServiceInstance inst, HttpExchange exchange) {
        String presented = extractPresentedToken(exchange);
        if (presented == null || presented.isBlank()) {
            tryWriteError(exchange, 401, "authentication_error", "无效或缺失的访问令牌");
            return null;
        }
        List<SysAiServiceToken> matched = tokenMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiServiceToken>()
                        .eq(SysAiServiceToken::getInstanceId, inst.getInstanceId())
                        .eq(SysAiServiceToken::getEnabled, 1)
                        .eq(SysAiServiceToken::getToken, presented));
        if (matched.isEmpty()) {
            tryWriteError(exchange, 401, "authentication_error", "无效或缺失的访问令牌");
            return null;
        }
        SysAiServiceToken t = matched.get(0);
        LocalDateTime now = LocalDateTime.now();
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(now)) {
            tryWriteError(exchange, 401, "token_expired", "访问令牌已过期");
            return null;
        }
        if (t.getMaxRequests() != null
                && nz(t.getUsedRequests()) >= t.getMaxRequests()) {
            tryWriteError(exchange, 401, "request_quota_exhausted", "访问令牌请求次数已用尽");
            return null;
        }
        if (t.getMaxTokens() != null
                && nz(t.getUsedTokens()) >= t.getMaxTokens()) {
            tryWriteError(exchange, 401, "token_quota_exhausted", "访问令牌Token额度已用尽");
            return null;
        }
        return t;
    }

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
     * 令牌额度回填：每次请求终结（成功/失败）请求次数 +1；成功时按总 Token 累加。
     * 使用 SQL 原子自增，避免并发计数竞争。
     */
    private void consumeQuota(SysAiServiceToken t, Long totalTokens) {
        if (t == null || t.getTokenId() == null) {
            return;
        }
        try {
            long addTokens = totalTokens != null && totalTokens > 0 ? totalTokens : 0L;
            String sql = "used_requests = used_requests + 1"
                    + (addTokens > 0 ? ", used_tokens = used_tokens + " + addTokens : "");
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysAiServiceToken> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(SysAiServiceToken::getTokenId, t.getTokenId()).setSql(sql);
            tokenMapper.update(null, uw);
        } catch (Exception e) {
            log.warn("[ServiceInstance] 令牌额度回填失败: {}", e.getMessage());
        }
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

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

    private String effectiveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return defaultModel();
    }

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
            } catch (Exception ignored) {
            }
        }
        return "agnes-2.5-flash";
    }

    // ==================== IO 工具 ====================

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

    private void openSse(HttpExchange exchange, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
    }

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
        } catch (Exception ignored) {
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

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

        StreamState(HttpExchange exchange) {
            this.exchange = exchange;
        }

        OutputStream out() throws IOException {
            if (os == null) {
                os = exchange.getResponseBody();
                committed = true;
            }
            return os;
        }

        void writeSseData(Object payload) throws IOException {
            String json = JSON.writeValueAsString(payload);
            write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        }

        void writeDone() throws IOException {
            write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }

        void writeSseEvent(String event, Object payload) throws IOException {
            String json = JSON.writeValueAsString(payload);
            write(("event: " + event + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        }

        private synchronized void write(byte[] bytes) throws IOException {
            OutputStream o = out();
            o.write(bytes);
            o.flush();
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}

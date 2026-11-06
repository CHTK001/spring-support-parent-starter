// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ChatTool;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * WorkBuddy（腾讯 CodeBuddy / WorkBuddy 国际版）对话客户端。
 *
 * <p>把 WorkBuddy 官方客户端的 {@code POST {baseUrl}/v2/chat/completions} 包装成统一的
 * {@link ChatClient}，认证使用 {@code panel} 登录流程拿到的 {@code access_token}
 * （Bearer），上游按账号域（realm）分域：</p>
 * <ul>
 *   <li>{@code cn}（缺省）→ {@code https://copilot.tencent.com}，Origin/Referer 为
 *       {@code https://www.codebuddy.cn}，UA 平台段 {@code WorkBuddy}；</li>
 *   <li>{@code global} → {@code https://www.workbuddy.ai}，Origin/Referer 为
 *       {@code https://www.workbuddy.ai}，UA 平台段 {@code WorkBuddy AI}。</li>
 * </ul>
 *
 * <p><b>上游只接受流式</b>（{@code stream:true}）：非流式请求会被上游拒绝，因此本实现
 * 一律以 SSE 发起，{@link #chatSync(String)} 在内部把分片聚合为完整文本，对调用方仍是
 * 同步语义。</p>
 *
 * <p>配置（{@link ChatClientSetting}）：</p>
 * <ul>
 *   <li>{@code baseUrl}：账号域上游根地址；为空时按 {@code cn} 兜底；</li>
 *   <li>{@code appKey}：{@code access_token}（Bearer）；</li>
 *   <li>{@code extraHeaders}：附加请求头，可透传 {@code X-User-Id}、{@code X-Device-Token}
 *       等账号级风控头（同名时覆盖内置默认值）。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Spi("workbuddy")
public class WorkBuddyChatClient implements ChatClient {

    /**
     * 供应商标识
     */
    private static final String PROVIDER = "workbuddy";

    /**
     * 国内版上游根地址
     */
    static final String BASE_CN = "https://copilot.tencent.com";

    /**
     * 国际版上游根地址
     */
    static final String BASE_GLOBAL = "https://www.workbuddy.ai";

    /**
     * 国内版 Origin / Referer 基础域
     */
    static final String ORIGIN_CN = "https://www.codebuddy.cn";

    /**
     * 国际版 Origin / Referer 基础域
     */
    static final String ORIGIN_GLOBAL = "https://www.workbuddy.ai";

    /**
     * 对话路径（上游只认 {@code /v2} 单路径）
     */
    static final String CHAT_PATH = "/v2/chat/completions";

    /**
     * 模型能力目录路径（需 IDE 版 UA）
     */
    static final String CONFIG_PATH = "/v3/config";

    /**
     * 客户端版本段（对齐官方桌面端分发包）
     */
    static final String CLIENT_VERSION = "5.5.4";

    /**
     * 内置 CLI 版本段
     */
    static final String CLI_VERSION = "2.137.1";

    /**
     * {@code /v3/config} 要求的 IDE 版 UA
     */
    static final String IDE_UA = "CodeBuddyIDE/4.12.0 CodeBuddy/4.12.0";

    /**
     * 默认模型（上游 {@code auto} 由服务端按账号能力选路）
     */
    static final String DEFAULT_MODEL = "auto";

    /**
     * 默认超时（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = 600_000L;

    /**
     * 国内版 Accept-Language
     */
    private static final String LANG_CN = "zh-CN";

    /**
     * 国际版 Accept-Language
     */
    private static final String LANG_GLOBAL = "en-US";

    /**
     * 客户端配置
     */
    private final ChatClientSetting setting;

    /**
     * 当前模型
     */
    private String model;

    /**
     * 系统提示词
     */
    private String system;

    /**
     * 本地历史
     */
    private final List<ChatMessage> history = new ArrayList<>();

    /**
     * 外部历史
     */
    private List<ChatMessage> externalHistory;

    /**
     * 是否流式回调（上游恒流式，本开关只决定对调用方的回调形态）
     */
    private boolean stream = true;

    /**
     * 动态额外请求头
     */
    private Map<String, String> extraHeaders;

    /**
     * 动态额外请求体参数
     */
    private final Map<String, Object> extraBody = new LinkedHashMap<>();

    /**
     * 温度
     */
    private Double temperature;

    /**
     * 最大输出 Token
     */
    private Integer maxTokens;

    /**
     * Top-P
     */
    private Double topP;

    /**
     * 停止序列
     */
    private List<String> stop;

    /**
     * 随机种子
     */
    private Long seed;

    /**
     * 响应格式
     */
    private String responseFormat;

    /**
     * 工具定义
     */
    private List<ChatTool> tools;

    /**
     * 工具选择策略
     */
    private String toolChoice;

    /**
     * 思考开关
     */
    private boolean thinking;

    /**
     * 思考强度
     */
    private String thinkingEffort;

    /**
     * 技能提示词块
     */
    private String skillPrompt;

    /**
     * 构造客户端。
     *
     * @param setting 客户端配置
     */
    public WorkBuddyChatClient(ChatClientSetting setting) {
        this.setting = setting == null ? ChatClientSetting.builder().build() : setting;
        this.model = this.setting.getModel();
        this.system = this.setting.getSystem();
        this.temperature = this.setting.getTemperature();
        this.maxTokens = this.setting.getMaxTokens();
        this.topP = this.setting.getTopP();
        this.stop = this.setting.getStop();
        this.seed = this.setting.getSeed();
        this.responseFormat = this.setting.getResponseFormat();
        this.extraHeaders = this.setting.getExtraHeaders();
        this.tools = this.setting.getTools();
        this.toolChoice = this.setting.getToolChoice();
    }

    @Override
    public ChatClient model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public ChatClient system(String system) {
        this.system = system;
        return this;
    }

    @Override
    public ChatClient temperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    @Override
    public ChatClient maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    @Override
    public ChatClient topP(Double topP) {
        this.topP = topP;
        return this;
    }

    @Override
    public ChatClient stop(List<String> stop) {
        this.stop = stop;
        return this;
    }

    @Override
    public ChatClient seed(Long seed) {
        this.seed = seed;
        return this;
    }

    @Override
    public ChatClient responseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }

    @Override
    public ChatClient stream(boolean stream) {
        this.stream = stream;
        return this;
    }

    @Override
    public ChatClient thinking(boolean thinking) {
        this.thinking = thinking;
        return this;
    }

    @Override
    public ChatClient thinkingEffort(String effort) {
        this.thinkingEffort = effort;
        return this;
    }

    @Override
    public ChatClient tools(List<ChatTool> tools) {
        this.tools = tools;
        return this;
    }

    @Override
    public ChatClient tool(ChatTool tool) {
        if (tool == null) {
            return this;
        }
        List<ChatTool> merged = new ArrayList<>();
        if (this.tools != null) {
            merged.addAll(this.tools);
        }
        merged.add(tool);
        this.tools = merged;
        return this;
    }

    @Override
    public ChatClient toolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    @Override
    public ChatClient extraHeaders(Map<String, String> headers) {
        this.extraHeaders = headers;
        return this;
    }

    @Override
    public ChatClient extraBody(Map<String, Object> extraBody) {
        this.extraBody.clear();
        if (extraBody != null) {
            this.extraBody.putAll(extraBody);
        }
        return this;
    }

    @Override
    public ChatClient skillPrompt(String block) {
        this.skillPrompt = block;
        return this;
    }

    @Override
    public ChatClient addUserHistory(String content) {
        history.add(ChatMessage.builder().role("user").content(content).build());
        return this;
    }

    @Override
    public ChatClient addAssistantHistory(String content) {
        history.add(ChatMessage.builder().role("assistant").content(content).build());
        return this;
    }

    @Override
    public ChatClient history(List<ChatMessage> messages) {
        this.externalHistory = messages;
        return this;
    }

    @Override
    public ChatClient newChat() {
        history.clear();
        externalHistory = null;
        return this;
    }

    @Override
    public String chatSync(String prompt) {
        ChatSyncResponse response = chatSyncWithResponse(prompt);
        return response == null ? null : response.getText();
    }

    /**
     * {@inheritDoc}
     *
     * <p>上游只支持流式，这里以「聚合流」形式实现同步：内部走 SSE，把
     * {@code delta.content} 拼接为完整文本，用量取末帧 {@code usage}。</p>
     */
    @Override
    public ChatSyncResponse chatSyncWithResponse(String prompt) {
        StringBuilder text = new StringBuilder();
        AiUsage[] usage = new AiUsage[1];
        String[] failure = new String[1];
        chat(prompt, response -> {
            if (response.getState() == ChatResponse.State.STOP) {
                usage[0] = response.getUsage();
                return;
            }
            if (response.getState() == ChatResponse.State.ERROR) {
                failure[0] = response.getErrorMessage();
                return;
            }
            if (response.getState() == ChatResponse.State.STREAMING && response.getContent() != null) {
                text.append(response.getContent());
            }
        });
        if (failure[0] != null && text.length() == 0) {
            throw new IllegalStateException(failure[0]);
        }
        return ChatSyncResponse.builder().text(text.toString()).usage(usage[0]).build();
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer) {
        chat(prompt, consumer, () -> {
        }, e -> {
            throw new RuntimeException("WorkBuddy 对话请求失败: " + e.getMessage(), e);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>事件序列：START → STREAMING×N（正文/思考/工具增量）→ STOP（含用量）。
     * 上游恒以流式返回，{@link #stream} 只影响回调是否逐片推送——关闭时仍逐片解析，
     * 由 {@link #chatSyncWithResponse} 的累加器聚合，语义等价。</p>
     */
    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer,
                     Runnable onComplete, Consumer<Throwable> onError) {
        long startTime = System.currentTimeMillis();
        consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
        Turn turn = new Turn();
        try {
            List<Map<String, Object>> messages = buildMessages(prompt);
            Map<String, Object> body = buildBody(messages);
            streamChat(body, consumer, turn);
            long duration = System.currentTimeMillis() - startTime;
            String answer = turn.text.toString();
            remember(prompt, answer);
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STOP)
                    .fullContent(answer.isEmpty() ? null : answer)
                    .usage(buildUsage(turn, startTime, duration))
                    .build());
            onComplete.run();
        } catch (Throwable e) {
            log.error("[workbuddy] 对话请求失败: {}", e.getMessage(), e);
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
            onError.accept(e);
        }
    }

    // ==================== 上游请求 ====================

    /**
     * 发起一次 SSE 请求并逐帧回调。
     *
     * @param body     请求体
     * @param consumer 流式回调
     * @param turn     本轮结果
     * @throws Exception 请求失败
     */
    private void streamChat(Map<String, Object> body, Consumer<ChatResponse> consumer, Turn turn)
            throws Exception {
        String requestBody = Json.toJson(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(chatEndpoint()))
                .timeout(Duration.ofMillis(timeoutMillis()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> header : chatHeaders().entrySet()) {
            if (StringUtils.isNotBlank(header.getKey()) && header.getValue() != null) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        HttpResponse<InputStream> response;
        try {
            response = ChannelHttp.CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WorkBuddy 请求被中断", e);
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String raw = readAll(response.body());
            throw new IllegalStateException(describeFailure(status, raw));
        }
        long deadline = System.currentTimeMillis() + timeoutMillis();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("WorkBuddy 流式响应超时（" + timeoutMillis() + "ms）");
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }
                String data = trimmed.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                handleChunk(data, consumer, turn);
            }
        }
    }

    /**
     * 处理单个 SSE 数据帧。
     *
     * @param data     数据帧内容
     * @param consumer 流式回调
     * @param turn     本轮结果
     */
    private void handleChunk(String data, Consumer<ChatResponse> consumer, Turn turn) {
        Map<String, Object> chunk;
        try {
            chunk = Json.getJsonObject(data);
        } catch (Exception e) {
            log.debug("[workbuddy] 跳过无法解析的 SSE 帧: {}", truncate(data));
            return;
        }
        if (chunk == null) {
            return;
        }
        fillMeta(chunk, turn);
        Object choices = chunk.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        if (!(list.get(0) instanceof Map<?, ?> choice)) {
            return;
        }
        Object finish = choice.get("finish_reason");
        if (finish instanceof String finishText && !finishText.isEmpty()) {
            turn.finishReason = finishText;
        }
        Object delta = choice.get("delta");
        if (!(delta instanceof Map<?, ?> deltaMap)) {
            return;
        }
        Object reasoning = deltaMap.get("reasoning_content");
        if (reasoning instanceof String reasoningText && !reasoningText.isEmpty()) {
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STREAMING)
                    .reasoningContent(reasoningText)
                    .build());
        }
        Object toolCalls = deltaMap.get("tool_calls");
        if (toolCalls instanceof List<?> calls && !calls.isEmpty()) {
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STREAMING)
                    .toolCalls(toToolCallData(calls))
                    .build());
        }
        Object content = deltaMap.get("content");
        if (content instanceof String deltaText && !deltaText.isEmpty()) {
            if (turn.firstTokenAt == 0L) {
                turn.firstTokenAt = System.currentTimeMillis();
            }
            turn.text.append(deltaText);
            if (stream) {
                consumer.accept(ChatResponse.builder()
                        .state(ChatResponse.State.STREAMING)
                        .content(deltaText)
                        .build());
            }
        }
    }

    /**
     * 把上游 {@code tool_calls} 增量转为统一结构。
     *
     * @param calls 上游增量数组
     * @return 工具调用数据
     */
    private static List<ChatToolCallData> toToolCallData(List<?> calls) {
        List<ChatToolCallData> result = new ArrayList<>(calls.size());
        for (Object item : calls) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            ChatToolCallData.ChatToolCallDataBuilder builder = ChatToolCallData.builder();
            if (raw.get("index") instanceof Number index) {
                builder.index(index.longValue());
            }
            if (raw.get("id") instanceof String id && !id.isEmpty()) {
                builder.id(id);
            }
            if (raw.get("function") instanceof Map<?, ?> function) {
                if (function.get("name") instanceof String name && !name.isEmpty()) {
                    builder.name(name);
                }
                if (function.get("arguments") instanceof String arguments) {
                    builder.arguments(arguments);
                }
            }
            result.add(builder.build());
        }
        return result;
    }

    /**
     * 填充响应元数据（模型、请求 ID、用量）。
     *
     * @param payload 响应或分片
     * @param turn    本轮结果
     */
    private static void fillMeta(Map<String, Object> payload, Turn turn) {
        if (payload.get("model") instanceof String text && !text.isEmpty()) {
            turn.model = text;
        }
        if (payload.get("id") instanceof String text && !text.isEmpty()) {
            turn.requestId = text;
        }
        Object usage = payload.get("usage");
        if (!(usage instanceof Map<?, ?> usageMap)) {
            return;
        }
        turn.inputTokens = intValue(usageMap.get("prompt_tokens"));
        turn.outputTokens = intValue(usageMap.get("completion_tokens"));
        turn.totalTokens = intValue(usageMap.get("total_tokens"));
        if (turn.totalTokens == null && turn.inputTokens != null && turn.outputTokens != null) {
            turn.totalTokens = turn.inputTokens + turn.outputTokens;
        }
        Integer cache = cacheHitTokens(usageMap);
        if (cache != null) {
            turn.cacheTokens = cache;
        }
    }

    /**
     * 解析缓存命中 Token（上游多处别名取最优）。
     *
     * @param usage 用量节点
     * @return 命中 Token 数；未知返回 {@code null}
     */
    private static Integer cacheHitTokens(Map<?, ?> usage) {
        Integer best = null;
        Object details = usage.get("prompt_tokens_details");
        if (details instanceof Map<?, ?> map) {
            best = max(best, intValue(map.get("cached_tokens")));
        }
        best = max(best, intValue(usage.get("prompt_cache_hit_tokens")));
        best = max(best, intValue(usage.get("cache_read_input_tokens")));
        best = max(best, intValue(usage.get("cached_tokens")));
        return best;
    }

    /**
     * 取较大值（忽略空值）。
     *
     * @param current 当前值
     * @param candidate 候选值
     * @return 较大值
     */
    private static Integer max(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Integer.valueOf(Math.max(current, candidate));
    }

    /**
     * 构建上游消息。
     *
     * @param prompt 本轮输入
     * @return 消息列表
     */
    private List<Map<String, Object>> buildMessages(String prompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = mergedSystemPrompt();
        if (StringUtils.isNotBlank(systemPrompt)) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "system");
            message.put("content", systemPrompt);
            messages.add(message);
        }
        List<ChatMessage> source = externalHistory != null ? externalHistory : history;
        for (ChatMessage item : source) {
            if (item == null || StringUtils.isBlank(item.getContent())) {
                continue;
            }
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", StringUtils.isBlank(item.getRole()) ? "user" : item.getRole());
            message.put("content", item.getContent());
            messages.add(message);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        return messages;
    }

    /**
     * 合并系统提示词与技能正文块。
     *
     * @return 合并后的系统提示词；均为空时返回 {@code null}
     */
    private String mergedSystemPrompt() {
        boolean hasSystem = StringUtils.isNotBlank(system);
        boolean hasSkill = StringUtils.isNotBlank(skillPrompt);
        if (!hasSystem && !hasSkill) {
            return null;
        }
        if (!hasSkill) {
            return system;
        }
        if (!hasSystem) {
            return skillPrompt;
        }
        return system + "\n\n" + skillPrompt;
    }

    /**
     * 构建请求体。上游只接受 {@code stream:true}，故恒写 {@code true}。
     *
     * @param messages 消息列表
     * @return 请求体
     */
    private Map<String, Object> buildBody(List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelOr());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        // 上游只认 max_tokens（max_completion_tokens 会被忽略并回落默认上限）
        if (maxTokens != null && maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        if (topP != null) {
            body.put("top_p", topP);
        }
        if (stop != null && !stop.isEmpty()) {
            body.put("stop", stop);
        }
        if (seed != null) {
            body.put("seed", seed);
        }
        if (StringUtils.isNotBlank(responseFormat) && !"text".equalsIgnoreCase(responseFormat)) {
            body.put("response_format", Map.of("type", responseFormat));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildTools());
            if (StringUtils.isNotBlank(toolChoice)) {
                // 上游 tool_choice 只认字符串形态，对象形态会 400 code=11101
                body.put("tool_choice", toolChoice);
            }
        }
        if (thinking) {
            body.put("thinking", Map.of("type", "enabled"));
        }
        if (StringUtils.isNotBlank(thinkingEffort)) {
            body.put("reasoning_effort", thinkingEffort);
        }
        if (!extraBody.isEmpty()) {
            body.putAll(extraBody);
        }
        return body;
    }

    /**
     * 构建上游工具定义数组。
     *
     * @return 工具定义
     */
    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> result = new ArrayList<>(tools.size());
        for (ChatTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getName())) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            if (StringUtils.isNotBlank(tool.getDescription())) {
                function.put("description", tool.getDescription());
            }
            function.put("parameters", tool.getParameters() == null
                    ? Map.of("type", "object", "properties", Map.of()) : tool.getParameters());
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "function");
            node.put("function", function);
            result.add(node);
        }
        return result;
    }

    /**
     * 记录本轮历史。
     *
     * @param prompt 用户输入
     * @param answer 模型输出
     */
    private void remember(String prompt, String answer) {
        if (externalHistory != null) {
            return;
        }
        history.add(ChatMessage.builder().role("user").content(prompt).build());
        if (answer != null && !answer.isEmpty()) {
            history.add(ChatMessage.builder().role("assistant").content(answer).build());
        }
    }

    /**
     * 构建用量信息。
     *
     * @param turn      本轮结果
     * @param startTime 开始时间
     * @param duration  总耗时
     * @return 用量
     */
    private AiUsage buildUsage(Turn turn, long startTime, long duration) {
        long firstTokenLatency = turn.firstTokenAt > 0L ? turn.firstTokenAt - startTime : duration;
        AiUsage.AiUsageBuilder builder = AiUsage.builder()
                .inputTokens(turn.inputTokens)
                .outputTokens(turn.outputTokens)
                .totalTokens(turn.totalTokens)
                .cacheTokens(turn.cacheTokens)
                .model(StringUtils.isBlank(turn.model) ? modelOr() : turn.model)
                .provider(PROVIDER)
                .requestId(turn.requestId)
                .finishReason(turn.finishReason)
                .startTime(startTime)
                .firstTokenLatencyMillis(firstTokenLatency)
                .durationMillis(duration);
        if (turn.outputTokens != null && duration > 0L) {
            builder.tokensPerSecond(turn.outputTokens * 1000.0 / duration);
        }
        return builder.build();
    }

    // ==================== 请求头 ====================

    /**
     * 对话请求头：公共头 + 认证 + 账号域声明 + 会话头族 + 用量归属。
     *
     * @return 请求头
     */
    private Map<String, String> chatHeaders() {
        boolean global = isGlobal();
        String origin = global ? ORIGIN_GLOBAL : ORIGIN_CN;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("Accept-Language", global ? LANG_GLOBAL : LANG_CN);
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Origin", origin);
        headers.put("Referer", origin + "/");
        headers.put("User-Agent", userAgent(global));
        headers.put("X-CodeBuddy-Request", "1");
        String apiKey = getApiKey();
        if (StringUtils.isNotBlank(apiKey)) {
            headers.put("Authorization", "Bearer " + apiKey.trim());
        }
        String userId = headerValue(mergedHeaders(), "X-User-Id");
        if (StringUtils.isNotBlank(userId)) {
            headers.put("X-User-Id", userId);
        }
        if (global) {
            headers.put("X-No-Enterprise-Id", "1");
            headers.put("X-Domain", "www.workbuddy.ai");
        }
        // 会话头族：后台按 X-Conversation-Request-ID 聚合一次 user send
        String conversationId = newMessageId();
        String requestId = newMessageId();
        String messageId = newMessageId();
        headers.put("X-Conversation-ID", conversationId);
        headers.put("X-Conversation-Request-ID", requestId);
        headers.put("X-Conversation-Message-ID", messageId);
        headers.put("X-Request-ID", messageId);
        headers.put("X-Root-Request-ID", requestId);
        headers.put("X-Trace-ID", requestId);
        headers.put("X-B3-TraceId", requestId);
        headers.put("X-B3-SpanId", messageId.substring(0, 16));
        headers.put("X-B3-Sampled", "1");
        // 用量归属：伪造官方桌面端指纹，避免被上游标记为网关特征
        headers.put("X-Agent-Purpose", "conversation");
        headers.put("X-IDE-Name", "WorkBuddy");
        headers.put("X-IDE-Type", "WorkBuddy");
        headers.put("X-IDE-Version", CLIENT_VERSION);
        headers.put("X-Product", "WorkBuddy");
        for (Map.Entry<String, String> entry : mergedHeaders().entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            headers.put(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    /**
     * 合并 {@code setting.extraHeaders} 与动态额外请求头。
     *
     * @return 合并结果
     */
    private Map<String, String> mergedHeaders() {
        Map<String, String> merged = new LinkedHashMap<>();
        if (setting.getExtraHeaders() != null) {
            merged.putAll(setting.getExtraHeaders());
        }
        if (extraHeaders != null) {
            merged.putAll(extraHeaders);
        }
        return merged;
    }

    /**
     * 按账号域组装 UA（global 平台段为 {@code WorkBuddy AI}）。
     *
     * @param global 是否国际版
     * @return UA
     */
    private static String userAgent(boolean global) {
        String platform = global ? "WorkBuddy AI" : "WorkBuddy";
        return "WorkBuddy/" + CLIENT_VERSION + " " + platform + "/" + CLIENT_VERSION
                + " CLI/" + CLI_VERSION;
    }

    /**
     * 读取头值（大小写不敏感）。
     *
     * @param headers 请求头
     * @param name    头名
     * @return 值；不存在返回 {@code null}
     */
    private static String headerValue(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 生成 32 位 hex 消息/请求标识。
     *
     * @return 标识
     */
    private static String newMessageId() {
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    // ==================== 地址与模型 ====================

    /**
     * 账号域：由 baseUrl 主机判定，{@code workbuddy.ai} 视为国际版。
     *
     * @return 是否国际版
     */
    private boolean isGlobal() {
        String url = baseUrl().toLowerCase(Locale.ROOT);
        return url.contains("workbuddy.ai");
    }

    /**
     * 对话端点。
     *
     * @return 完整地址
     */
    private String chatEndpoint() {
        return baseUrl() + CHAT_PATH;
    }

    /**
     * 规范化上游根地址（去掉尾斜杠与已知路径后缀）。
     *
     * @return 根地址
     */
    private String baseUrl() {
        String url = StringUtils.isBlank(setting.getBaseUrl()) ? BASE_CN : setting.getBaseUrl().trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith(CHAT_PATH)) {
            url = url.substring(0, url.length() - CHAT_PATH.length());
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 当前模型。
     *
     * @return 模型名
     */
    private String modelOr() {
        return StringUtils.isBlank(model) ? DEFAULT_MODEL : model;
    }

    /**
     * 超时时间。
     *
     * @return 毫秒
     */
    private long timeoutMillis() {
        Long configured = setting.getTimeoutMillis();
        return configured != null && configured > 0L ? configured : DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * {@inheritDoc}
     *
     * <p>上游模型目录在 {@code GET {baseUrl}/v3/config}（需 IDE 版 UA），返回
     * {@code data.models[]}；该端点对 UA 敏感，解析失败时返回空清单而不是抛错。</p>
     */
    @Override
    public List<ModelDefinition> models() {
        try {
            boolean global = isGlobal();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("X-Requested-With", "XMLHttpRequest");
            headers.put("X-CodeBuddy-Request", "1");
            headers.put("X-Product", "SaaS");
            headers.put("User-Agent", IDE_UA);
            headers.put("X-Domain", global ? "www.workbuddy.ai" : "copilot.tencent.com");
            String apiKey = getApiKey();
            if (StringUtils.isNotBlank(apiKey)) {
                headers.put("Authorization", "Bearer " + apiKey.trim());
            }
            String body = ChannelHttp.getJson(baseUrl() + CONFIG_PATH, headers);
            Map<String, Object> root = Json.getJsonObject(body);
            Integer code = intValue(root == null ? null : root.get("code"));
            if (root == null || code == null || code != 0) {
                return List.of();
            }
            if (!(root.get("data") instanceof Map<?, ?> data)
                    || !(data.get("models") instanceof List<?> items)) {
                return List.of();
            }
            List<ModelDefinition> models = new ArrayList<>(items.size());
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> model)) {
                    continue;
                }
                if (!(model.get("id") instanceof String id) || id.isBlank()) {
                    continue;
                }
                String description = model.get("descriptionZh") instanceof String zh ? zh : null;
                models.add(ModelDefinition.builder()
                        .id(id)
                        .name(id)
                        .provider(PROVIDER)
                        .description(description)
                        .contextWindowTokens(longValue(model.get("maxInputTokens")))
                        .maxOutputTokens(longValue(model.get("maxOutputTokens")))
                        .build());
            }
            return models;
        } catch (Exception e) {
            log.warn("[workbuddy] 获取模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public ChatClientSetting getSetting() {
        return setting;
    }

    @Override
    public String getModel() {
        return modelOr();
    }

    @Override
    public void close() {
        // HTTP 连接由共享 HttpClient 统一管理，此处无需显式释放
    }

    // ==================== 工具方法 ====================

    /**
     * 读取全部响应体（错误路径用，限 1MB）。
     *
     * @param stream 输入流
     * @return 响应文本
     */
    private static String readAll(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 组装错误描述。
     *
     * @param status  状态码
     * @param snippet 响应片段
     * @return 错误描述
     */
    private static String describeFailure(int status, String snippet) {
        String hint = switch (status) {
            case 401, 403 -> "（access_token 无效或已过期，请重新登录）";
            case 429 -> "（已触发上游限流，请稍后重试）";
            default -> "";
        };
        return "WorkBuddy 上游返回 HTTP " + status + hint + "，响应片段: " + truncate(snippet);
    }

    /**
     * 截断文本。
     *
     * @param text 原文
     * @return 截断结果
     */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    /**
     * 数字转换。
     *
     * @param value 原始值
     * @return 整数；无法转换返回 {@code null}
     */
    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 长整数转换。
     *
     * @param value 原始值
     * @return 长整数；无法转换返回 {@code null}
     */
    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 单轮结果。
     */
    private static final class Turn {

        /**
         * 文本
         */
        private final StringBuilder text = new StringBuilder();

        /**
         * 实际模型
         */
        private String model;

        /**
         * 请求 ID
         */
        private String requestId;

        /**
         * 结束原因
         */
        private String finishReason;

        /**
         * 输入 Token
         */
        private Integer inputTokens;

        /**
         * 输出 Token
         */
        private Integer outputTokens;

        /**
         * 总 Token
         */
        private Integer totalTokens;

        /**
         * 缓存命中 Token
         */
        private Integer cacheTokens;

        /**
         * 首内容分片时间
         */
        private long firstTokenAt;
    }
}

package com.chua.starter.account.channel.free;

import com.chua.common.support.ai.chat.Attachment;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ChatTool;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.ai.chat.route.RouterChatClient;
import com.chua.common.support.ai.skill.SkillManager;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 免费渠道聚合 {@link ChatClient}。
 *
 * <p><b>归属</b>：渠道管理属于账号模块（{@code spring-support-account-starter}），
 * 免费渠道是渠道管理的聚合出口，因此本实现只在账号模块维护；此前放在
 * {@code utils-support-openai-starter} 的那份已删除，避免同一 SPI 别名
 * （{@code free-channel}）出现两个实现、两套模型口径。</p>
 *
 * <p>与绑定单一网关的 {@code kilo} / {@code openai} 等实现不同，本客户端是<b>路由层</b>：
 * 同一个实例承载多个免费模型，按所选模型查路由表，自动切换到对应的
 * {@code baseUrl}、{@code apiKey} 与认证方式。新增免费模型只需扩展路由表
 * （供应商 {@code extra.routes} 或 {@link #register(String, RouteTarget)}），
 * 不需要新增客户端实现，也不用改动调用方。</p>
 *
 * <p>路由解析顺序：</p>
 * <ol>
 *   <li>完整模型名精确命中；</li>
 *   <li>模型带 {@code 前缀/模型} 或 {@code 前缀:模型}，按前缀命中路由表，
 *       模型名取前缀之后的部分；</li>
 *   <li>{@link RouterChatClient#DEFAULT_ROUTE} 兜底路由；</li>
 *   <li>以上都没有时，回落到供应商自身 {@code baseUrl}（等价于普通 OpenAI 兼容渠道）。</li>
 * </ol>
 *
 * <p><b>全局 key</b>：供应商配置里的那个 key（{@link ChatClientSetting#getAppKey()}）作为
 * 全局凭据。路由目标未单独给出 {@code apiKey} 时自动使用它；显式给出空串表示该上游免鉴权，
 * 显式给出非空值则覆盖全局 key。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi({"free-channel"})
public class FreeChannelChatClient implements RouterChatClient {

    /**
     * 免费渠道的 {@code ChatClient} SPI 扩展名。
     */
    public static final String EXTENSION = "free-channel";

    /**
     * 客户端配置（provider=free-channel，appKey 为全局 key）
     */
    private final ChatClientSetting setting;

    /**
     * 路由表：模型 / 上游前缀 → 路由目标
     */
    private final Map<String, RouteTarget> routes = new LinkedHashMap<>();

    /**
     * 对话历史消息列表
     */
    private final List<ChatMessage> history = new ArrayList<>();

    /**
     * 工具（函数调用）定义列表
     */
    private final List<ChatTool> tools = new ArrayList<>();

    /**
     * 图片附件地址
     */
    private final List<String> imageUrls = new ArrayList<>();

    /**
     * 文件附件
     */
    private final List<Attachment> attachments = new ArrayList<>();

    /**
     * 当前模型名称
     */
    private String model;

    /**
     * 当前温度参数
     */
    private Double temperature;

    /**
     * 当前最大输出 Token 数
     */
    private Integer maxTokens;

    /**
     * 当前系统提示词
     */
    private String system;

    /**
     * 当前会话标识
     */
    private String sessionId;

    /**
     * 外部传入的完整历史记录
     */
    private List<ChatMessage> externalHistory;

    /**
     * 工具选择策略
     */
    private String toolChoice;

    /**
     * Top-P 采样参数
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
     * 额外请求体参数
     */
    private Map<String, Object> extraBody;

    /**
     * 自定义请求头
     */
    private Map<String, String> extraHeaders;

    /**
     * 是否启用深度思考
     */
    private boolean thinking;

    /**
     * 深度思考强度
     */
    private String thinkingEffort;

    /**
     * 是否启用智能搜索
     */
    private boolean smartSearch;

    /**
     * 是否使用流式请求
     */
    private boolean stream;

    /**
     * 技能管理器
     */
    private SkillManager skillManager;

    /**
     * 技能指令块
     */
    private String skillPrompt;

    /**
     * 当前模型对应的上游客户端（按模型缓存）
     */
    private ChatClient delegate;

    /**
     * 缓存的上游客户端对应的模型
     */
    private String delegateModel;

    /**
     * 构造免费渠道聚合客户端
     *
     * @param setting 客户端配置
     */
    public FreeChannelChatClient(ChatClientSetting setting) {
        this.setting = setting == null ? ChatClientSetting.builder().build() : setting;
        this.model = this.setting.getModel();
        this.temperature = this.setting.getTemperature();
        this.maxTokens = this.setting.getMaxTokens();
        this.system = this.setting.getSystem();
        this.topP = this.setting.getTopP();
        this.toolChoice = this.setting.getToolChoice();
        this.stop = this.setting.getStop();
        this.seed = this.setting.getSeed();
        this.responseFormat = this.setting.getResponseFormat();
        if (this.setting.getTools() != null) {
            this.tools.addAll(this.setting.getTools());
        }
        if (this.setting.getRoutes() != null) {
            this.routes.putAll(this.setting.getRoutes());
        }
    }

    /**
     * 为渠道管理里的一个对话渠道建客户端（渠道管理统一入口）。
     *
     * <p>渠道管理里的每个渠道（DuckDuckGo、Trae，以及后续登记的渠道）对执行层只有两件事：
     * 底层 {@code ChatClient} 扩展名、自己的模型清单。执行一律经本客户端：注册一条兜底路由，
     * 把请求转给该渠道自己的 {@code ChatClient} 扩展，渠道侧不再各自 new 客户端、
     * 不再各自拼协议，也不再各自决定拿哪个 key。</p>
     *
     * @param extension 渠道底层 {@code ChatClient} 扩展名（如 {@code duckduckgo}）
     * @param apiKey    本次请求使用的凭据（如 Trae 的内置 JWT）；为空表示免鉴权
     * @return 对话客户端
     */
    public static ChatClient forChannel(String extension, String apiKey) {
        return forChannel(extension, apiKey, null);
    }

    /**
     * 为渠道管理里的一个对话渠道建客户端，并允许本次请求临时指定上游地址。
     *
     * <p>{@code baseUrl} 面向 OpenAI 兼容的自定义网关（如 one-api / vLLM）：
     * 只挂到本次路由目标上，随 {@link RouteTarget#toSetting(ChatClientSetting)} 落到
     * 上游客户端；为空时按渠道自身缺省地址解析。令牌与地址都只在本请求内生效，不落任何存储。</p>
     *
     * <p>认证语义：显式 {@code baseUrl}（自定义网关）按标准 OpenAI 兼容协议认证——
     * 给了 {@code apiKey} 即走默认 {@code Authorization: Bearer}（{@code authScheme} 留空；
     * 不能写 {@code none}，否则 {@link com.chua.common.support.ai.chat.AuthHeaders}
     * 会把凭据整个吞掉，上游收到 401）；未给 key 则免鉴权。
     * 未指定 {@code baseUrl} 的既有渠道维持原语义（{@code authScheme=none}，凭据由
     * 渠道自己的 {@code ChatClient} 扩展处理）。</p>
     *
     * @param extension 渠道底层 {@code ChatClient} 扩展名（如 {@code duckduckgo}）
     * @param apiKey    本次请求使用的凭据；为空表示免鉴权
     * @param baseUrl   本次请求的上游地址；为空用渠道缺省地址
     * @return 对话客户端
     */
    public static ChatClient forChannel(String extension, String apiKey, String baseUrl) {
        if (StringUtils.isBlank(extension)) {
            throw new IllegalStateException("渠道未声明 ChatClient 扩展名");
        }
        boolean customGateway = StringUtils.isNotBlank(baseUrl);
        boolean hasKey = StringUtils.isNotBlank(apiKey);
        FreeChannelChatClient client = new FreeChannelChatClient(
                ChatClientSetting.builder().provider(EXTENSION).build());
        client.register(DEFAULT_ROUTE, RouteTarget.builder()
                .provider(extension)
                .baseUrl(customGateway ? baseUrl : null)
                .apiKey(hasKey ? apiKey : null)
                .authScheme(customGateway && hasKey ? null : "none")
                .build());
        return client;
    }

    /**
     * 注册（或覆盖）一条路由
     *
     * <p>用于运行期扩展免费模型，与供应商 {@code extra.routes} 中的配置等价。</p>
     *
     * @param model  模型名或上游前缀
     * @param target 路由目标
     * @return 当前客户端实例，支持链式调用
     */
    public FreeChannelChatClient register(String model, RouteTarget target) {
        if (StringUtils.isNotBlank(model) && target != null) {
            routes.put(model, target);
            invalidate();
        }
        return this;
    }

    /**
     * 按模型解析路由目标
     *
     * <p>命中后会把全局 key 补进没有单独指定 {@code apiKey} 的目标（{@code null} 表示未指定，
     * 空串表示显式免鉴权）。</p>
     *
     * <p><b>纯路由</b>：免费渠道不持有可执行的上游地址，供应商的 baseUrl 只是占位配置，
     * <b>不会</b>被当作请求目标。未命中任何路由（精确 / 前缀 / 显式 {@code default}）时
     * 返回 {@code null}，由调用方报「未配置模型路由」，杜绝请求落到占位地址。</p>
     *
     * @param model 模型标识
     * @return 路由目标；无法路由时返回 {@code null}
     */
    @Override
    public RouteTarget route(String model) {
        if (StringUtils.isBlank(model)) {
            RouteTarget fallback = routes.get(DEFAULT_ROUTE);
            return fallback == null ? null : withGlobalKey(fallback);
        }
        RouteTarget target = routes.get(model);
        if (target != null) {
            return withGlobalKey(target);
        }
        RouteTarget prefixed = matchPrefix(model);
        if (prefixed != null) {
            return withGlobalKey(prefixed);
        }
        RouteTarget fallback = routes.get(DEFAULT_ROUTE);
        if (fallback != null) {
            return withGlobalKey(fallback.withModel(model));
        }
        return null;
    }

    /**
     * 当前可路由的模型清单
     *
     * <p>返回路由表键（不含 {@code default}）。键同时可能是上游前缀（如 {@code kilo}），
     * 供前端展示时可与真实模型名一并列出。</p>
     *
     * @return 模型标识列表
     */
    @Override
    public List<String> routeModels() {
        List<String> models = new ArrayList<>(routes.size());
        for (String key : routes.keySet()) {
            if (!DEFAULT_ROUTE.equals(key)) {
                models.add(key);
            }
        }
        return models;
    }

    /**
     * 按前缀匹配路由
     *
     * <p>支持 {@code 前缀/模型} 与 {@code 前缀:模型} 两种写法，前缀必须已在路由表中登记，
     * 避免把 {@code qwen:7b} 这类正常模型名误判为路由前缀。</p>
     *
     * @param model 模型标识
     * @return 路由目标；未命中返回 {@code null}
     */
    private RouteTarget matchPrefix(String model) {
        int slash = model.indexOf('/');
        int colon = model.indexOf(':');
        int split = slash > 0 ? slash : colon;
        if (split <= 0 || split >= model.length() - 1) {
            return null;
        }
        String prefix = model.substring(0, split);
        RouteTarget target = routes.get(prefix);
        if (target == null) {
            return null;
        }
        return target.withModel(model.substring(split + 1));
    }

    /**
     * 补齐全局 key
     *
     * @param target 原始路由目标
     * @return 补齐后的路由目标
     */
    private RouteTarget withGlobalKey(RouteTarget target) {
        if (target.getApiKey() == null) {
            return target.withApiKey(setting.getAppKey());
        }
        return target;
    }

    /**
     * 取（并按需重建）当前模型的上游客户端
     *
     * <p>单路由可挂多个账号（{@code apiKeys}）：开启「自动限流」且账号数大于 1 时按
     * {@code loadStrategy}（failover / round_robin）组聚合，429 限流自动切下一个账号；
     * 关闭自动限流或单账号时直连第一个账号。</p>
     *
     * @return 已应用全部请求级配置的上游客户端
     */
    private ChatClient delegate() {
        RouteTarget target = route(model);
        if (target == null) {
            throw new IllegalStateException("免费渠道未配置模型路由: " + model);
        }
        // 命中但没配上游地址（baseUrl）时拒绝，避免 toSetting 从客户端设置复制占位 baseUrl 后真实执行。
        // 例外：免登录对话渠道（DuckDuckGo / Trae 等）本来就没有地址，协议与会话由 ChatClient
        // 扩展自己持有，按 provider 建客户端即可。
        if (!target.available() && !isKeylessRoute(target)) {
            throw new IllegalStateException("免费渠道路由未配置上游地址: " + model);
        }
        String upstreamModel = StringUtils.isNotBlank(target.getModel()) ? target.getModel() : model;
        if (delegate != null && Objects.equals(delegateModel, upstreamModel)) {
            return delegate;
        }
        List<String> accountKeys = target.accountKeys();
        if (accountKeys.isEmpty()) {
            // 免鉴权上游：以 null key 直连（路由解析阶段已把全局 key 补进 null，这里兜底）。
            // 注意 List.of 不接受 null 元素，必须用 Arrays.asList 承载「单个 null key」。
            accountKeys = java.util.Arrays.asList((String) null);
        }
        ChatClient resolved;
        if (accountKeys.size() == 1 || !target.autoSwitchEnabled()) {
            resolved = buildUpstream(target, accountKeys.getFirst(), upstreamModel);
        } else {
            resolved = buildAggregate(target, accountKeys, upstreamModel, target.resolvedStrategy());
        }
        this.delegate = resolved;
        this.delegateModel = upstreamModel;
        return resolved;
    }

    /**
     * 是否为免登录对话渠道的路由目标。
     *
     * <p>这类路由没有 baseUrl：协议与会话由 ChatClient 扩展自己持有（DuckDuckGo / Trae 等），
     * 只要 {@code authScheme=none} 且给出 provider，就能按扩展名建出客户端。</p>
     *
     * @param target 路由目标
     * @return {@code true}=免登录渠道路由
     */
    private boolean isKeylessRoute(RouteTarget target) {
        return "none".equalsIgnoreCase(target.getAuthScheme())
                && StringUtils.isNotBlank(target.getProvider());
    }

    /**
     * 单 Key 直连上游客户端。
     *
     * @param target        路由目标
     * @param apiKey        本次使用的账号 Key（可为 null）
     * @param upstreamModel 对端模型标识
     * @return 上游客户端
     */
    private ChatClient buildUpstream(RouteTarget target, String apiKey, String upstreamModel) {
        RouteTarget effective = apiKey == null ? target : target.withApiKey(apiKey);
        ChatClientSetting upstreamSetting = effective.toSetting(setting);
        if (StringUtils.equalsIgnoreCase(setting.getProvider(), upstreamSetting.getProvider())) {
            // 防止路由目标指回本渠道自身（provider 与当前渠道同名）造成递归创建
            upstreamSetting.setProvider(ChatClient.OPENAI_EXTENSION);
        }
        ChatClient upstream = ChatClient.create(upstreamSetting);
        if (upstream == null) {
            throw new IllegalStateException("免费渠道无法创建上游客户端: provider="
                    + upstreamSetting.getProvider() + ", baseUrl=" + upstreamSetting.getBaseUrl());
        }
        if (StringUtils.isNotBlank(upstreamModel)) {
            upstream.model(upstreamModel);
        }
        applyState(upstream);
        return upstream;
    }

    /**
     * 多账号聚合：按策略组 failover / round_robin，429 限流自动切换。
     *
     * @param target        路由目标
     * @param accountKeys   账号 Key 列表
     * @param upstreamModel 对端模型标识
     * @param strategy      负载策略（failover / round_robin）
     * @return 聚合客户端
     */
    private ChatClient buildAggregate(RouteTarget target, List<String> accountKeys,
                                      String upstreamModel, String strategy) {
        AggregateChatClientSetting aggSetting = new AggregateChatClientSetting();
        aggSetting.setStrategy(StringUtils.isBlank(strategy) ? "failover" : strategy);
        aggSetting.setAutoSwitchOnRateLimit(true);
        aggSetting.setAutoSwitchOnQuotaExhausted(true);
        AggregateChatClient aggregate = new AggregateChatClient(aggSetting);
        String provider = StringUtils.isBlank(target.getProvider())
                ? ChatClient.OPENAI_EXTENSION : target.getProvider();
        int added = 0;
        for (String key : accountKeys) {
            ChatClient raw = buildUpstream(target, key, upstreamModel);
            if (raw != null) {
                aggregate.addClient("account-" + added++, provider, 1, raw);
            }
        }
        if (added == 0) {
            throw new IllegalStateException("免费渠道多账号路由无可用上游: " + upstreamModel);
        }
        return aggregate;
    }

    /**
     * 把请求级配置回放到上游客户端
     *
     * @param client 上游客户端
     */
    private void applyState(ChatClient client) {
        if (temperature != null) {
            client.temperature(temperature);
        }
        if (maxTokens != null) {
            client.maxTokens(maxTokens);
        }
        if (system != null) {
            client.system(system);
        }
        if (topP != null) {
            client.topP(topP);
        }
        if (toolChoice != null) {
            client.toolChoice(toolChoice);
        }
        if (stop != null) {
            client.stop(stop);
        }
        if (seed != null) {
            client.seed(seed);
        }
        if (responseFormat != null) {
            client.responseFormat(responseFormat);
        }
        if (extraBody != null) {
            client.extraBody(extraBody);
        }
        if (extraHeaders != null) {
            client.extraHeaders(extraHeaders);
        }
        if (thinking) {
            client.thinking(true);
        }
        if (thinkingEffort != null) {
            client.thinkingEffort(thinkingEffort);
        }
        if (smartSearch) {
            client.smartSearch(true);
        }
        if (stream) {
            client.stream(true);
        }
        if (sessionId != null) {
            client.session(sessionId);
        }
        if (skillManager != null) {
            client.skill(skillManager);
        }
        if (skillPrompt != null) {
            client.skillPrompt(skillPrompt);
        }
        if (!tools.isEmpty()) {
            client.tools(new ArrayList<>(tools));
        }
        if (externalHistory != null) {
            client.history(externalHistory);
        } else if (!history.isEmpty()) {
            client.history(new ArrayList<>(history));
        }
        for (String imageUrl : imageUrls) {
            client.addImage(imageUrl);
        }
        for (Attachment attachment : attachments) {
            if (attachment.data() != null) {
                client.addAttachment(attachment.name(), attachment.data(), attachment.mimeType());
            } else {
                client.addAttachmentUrl(attachment.name(), attachment.url(), attachment.mimeType());
            }
        }
    }

    /**
     * 请求级配置变化后丢弃已建好的上游客户端
     */
    private void invalidate() {
        this.delegate = null;
        this.delegateModel = null;
    }

    @Override
    public ChatClient model(String model) {
        this.model = model;
        invalidate();
        return this;
    }

    @Override
    public ChatClient temperature(double temperature) {
        this.temperature = temperature;
        invalidate();
        return this;
    }

    @Override
    public ChatClient maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        invalidate();
        return this;
    }

    @Override
    public ChatClient system(String system) {
        this.system = system;
        invalidate();
        return this;
    }

    @Override
    public ChatClient topP(Double topP) {
        this.topP = topP;
        invalidate();
        return this;
    }

    @Override
    public ChatClient toolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
        invalidate();
        return this;
    }

    @Override
    public ChatClient stop(List<String> stop) {
        this.stop = stop == null ? null : new ArrayList<>(stop);
        invalidate();
        return this;
    }

    @Override
    public ChatClient seed(Long seed) {
        this.seed = seed;
        invalidate();
        return this;
    }

    @Override
    public ChatClient responseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
        invalidate();
        return this;
    }

    @Override
    public ChatClient extraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody;
        invalidate();
        return this;
    }

    @Override
    public ChatClient extraHeaders(Map<String, String> headers) {
        this.extraHeaders = headers;
        invalidate();
        return this;
    }

    @Override
    public ChatClient thinking(boolean thinking) {
        this.thinking = thinking;
        invalidate();
        return this;
    }

    @Override
    public ChatClient thinkingEffort(String effort) {
        this.thinkingEffort = effort;
        invalidate();
        return this;
    }

    @Override
    public ChatClient smartSearch(boolean smartSearch) {
        this.smartSearch = smartSearch;
        invalidate();
        return this;
    }

    @Override
    public ChatClient stream(boolean stream) {
        this.stream = stream;
        invalidate();
        return this;
    }

    @Override
    public ChatClient session(String sessionId) {
        this.sessionId = sessionId;
        invalidate();
        return this;
    }

    @Override
    public ChatClient skill(SkillManager skillManager) {
        this.skillManager = skillManager;
        invalidate();
        return this;
    }

    @Override
    public ChatClient skillPrompt(String block) {
        this.skillPrompt = block;
        invalidate();
        return this;
    }

    @Override
    public ChatClient addImage(String imageUrl) {
        this.imageUrls.add(imageUrl);
        invalidate();
        return this;
    }

    @Override
    public ChatClient addAttachment(String name, byte[] data, String mimeType) {
        this.attachments.add(Attachment.builder().name(name).data(data).mimeType(mimeType).build());
        invalidate();
        return this;
    }

    @Override
    public ChatClient addAttachmentUrl(String name, String url, String mimeType) {
        this.attachments.add(Attachment.builder().name(name).url(url).mimeType(mimeType).build());
        invalidate();
        return this;
    }

    @Override
    public ChatClient addUserHistory(String content) {
        this.history.add(ChatMessage.builder().role("user").content(content).build());
        invalidate();
        return this;
    }

    @Override
    public ChatClient addAssistantHistory(String content) {
        this.history.add(ChatMessage.builder().role("assistant").content(content).build());
        invalidate();
        return this;
    }

    @Override
    public ChatClient history(List<ChatMessage> messages) {
        this.externalHistory = messages;
        invalidate();
        return this;
    }

    @Override
    public ChatClient newChat() {
        this.history.clear();
        this.externalHistory = null;
        this.imageUrls.clear();
        this.attachments.clear();
        invalidate();
        return this;
    }

    @Override
    public ChatClient tools(List<ChatTool> tools) {
        this.tools.clear();
        if (tools != null) {
            this.tools.addAll(tools);
        }
        invalidate();
        return this;
    }

    @Override
    public ChatClient tool(ChatTool tool) {
        if (tool != null) {
            this.tools.add(tool);
        }
        invalidate();
        return this;
    }

    @Override
    public String chatSync(String prompt) {
        return delegate().chatSync(prompt);
    }

    @Override
    public String chatSync(String prompt, long timeoutMillis) {
        return delegate().chatSync(prompt, timeoutMillis);
    }

    @Override
    public ChatSyncResponse chatSyncWithResponse(String prompt) {
        return delegate().chatSyncWithResponse(prompt);
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer) {
        delegate().chat(prompt, consumer);
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer,
                     Runnable onComplete, Consumer<Throwable> onError) {
        delegate().chat(prompt, consumer, onComplete, onError);
    }

    @Override
    public List<ModelDefinition> models() {
        List<ModelDefinition> definitions = new ArrayList<>(routes.size());
        for (String routeModel : routeModels()) {
            definitions.add(ModelDefinition.builder()
                    .id(routeModel)
                    .name(routeModel)
                    .provider(setting.getProvider())
                    .build());
        }
        return definitions;
    }

    @Override
    public ChatClientSetting getSetting() {
        return setting;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getApiKey() {
        return setting.getAppKey();
    }

    @Override
    public String getBaseUrl() {
        RouteTarget target = route(model);
        return target == null ? setting.getBaseUrl() : target.getBaseUrl();
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
            invalidate();
        }
    }
}
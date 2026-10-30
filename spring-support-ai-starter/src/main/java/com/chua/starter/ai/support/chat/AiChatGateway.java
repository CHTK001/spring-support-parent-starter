package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.runtime.AiRuntimeConfig;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 对话网关 —— 所有业务模块调用大模型的统一出口。
 *
 * <p>业务模块（eduai 等）只描述「说什么」，不再自己解析客户端、拼装供应商配置、
 * 判断限流、实现重试退避。这些都属于 AI 模块职责，散落在业务模块里会带来两个后果：
 * 每处实现的口径慢慢分叉（有的读 runtime、有的只读注册表），且 AI 配置形式一变就要
 * 改业务代码。本网关把这些收敛为一处：</p>
 *
 * <ul>
 *   <li>{@link #resolveClient()} —— 客户端解析链：AI 控制台 runtime 主链路 &gt; 静态
 *       YAML 指定厂商 &gt; 默认模型注册 &gt; 容器聚合客户端；</li>
 *   <li>{@link #complete(String, String, long)} —— 单次同步调用（system + user 合并）；</li>
 *   <li>{@link #completeWithRetry(String, String, AiChatOptions)} —— 限流/瞬时故障重试；</li>
 *   <li>{@link #capability()} / {@link #probe(long)} —— 能力探测与真实连通性测试。</li>
 * </ul>
 *
 * <p>本类不读任何业务配置：超时、重试次数等由调用方以 {@link AiChatOptions} 传入，
 * 因此同一套传输逻辑可被阅卷、组卷、问答等不同业务按各自预算复用。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Service
public class AiChatGateway {

    /**
     * 默认超时：90 秒（与历史阅卷单次调用预算一致）
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = 90_000L;

    /**
     * 默认重试次数（不含首次）
     */
    private static final int DEFAULT_MAX_RETRIES = 5;

    /**
     * AI 运行时配置（生效模型、客户端注册表、静态配置状态）
     */
    private final AiRuntimeConfig runtimeConfig;

    /**
     * 容器内聚合对话客户端（UI 完全未配置时的兜底）
     */
    private final ObjectProvider<ChatClient> chatClientProvider;

    /**
     * runtime 主链路配置签名（provider|baseUrl|model|apiKey），变化即重建客户端
     */
    private volatile String runtimeClientSignature;

    /**
     * 按 runtime 主链路配置构建的对话客户端缓存
     */
    private volatile ChatClient runtimeClientCache;

    /**
     * 构造网关。
     *
     * @param runtimeConfig     AI 运行时配置，不允许为 null
     * @param chatClientProvider 聚合对话客户端提供者，可为空
     */
    public AiChatGateway(AiRuntimeConfig runtimeConfig, ObjectProvider<ChatClient> chatClientProvider) {
        this.runtimeConfig = runtimeConfig;
        this.chatClientProvider = chatClientProvider;
    }

    /**
     * 是否已有可用的对话客户端。
     *
     * @return true 表示可以发起模型调用
     */
    public boolean available() {
        return resolveClient() != null;
    }

    /**
     * 解析对话客户端。
     *
     * <p>解析顺序（第一个可用者胜出）：</p>
     * <ol>
     *   <li>AI 控制台「系统设置」的 {@code runtime} 分组（provider / api_key / base_url / model），
     *       配置齐全时直接按它构建 —— UI 配置优先于 YAML；</li>
     *   <li>YAML 显式指定 {@code plugin.ai.chat.provider} 时对应的客户端；</li>
     *   <li>默认对话客户端（AI 控制台「默认模型」保存时热更新登记）；</li>
     *   <li>容器注入的聚合 {@link ChatClient}（UI 未配置时的兜底）。</li>
     * </ol>
     *
     * @return 可用的对话客户端；都没有时返回 null
     */
    public ChatClient resolveClient() {
        ChatClient runtimeClient = buildRuntimeChatClient();
        if (runtimeClient != null) {
            return runtimeClient;
        }
        if (runtimeConfig != null) {
            String provider = runtimeConfig.staticChatProvider();
            if (StringUtils.isNotBlank(provider)) {
                ChatClient byProvider = runtimeConfig.chatClient(provider);
                if (byProvider != null) {
                    return byProvider;
                }
            }
            ChatClient registryDefault = runtimeConfig.defaultChatClient();
            if (registryDefault != null) {
                return registryDefault;
            }
        }
        return chatClientProvider == null ? null : chatClientProvider.getIfAvailable();
    }

    /**
     * 探测 AI 对话能力，给出「可用与否 + 不可用的具体原因」。
     *
     * <p>判定只看 {@link #resolveClient()} 能否解析出客户端；回显的厂商/模型取「实际生效」值，
     * 保证界面显示与实际调用一致。</p>
     *
     * @return 能力探测结果
     */
    public AiChatCapability capability() {
        String effective = runtimeConfig == null ? null : runtimeConfig.effectiveChatModel(null);
        String runtimeProvider = runtimeSetting("provider");
        String runtimeModel = runtimeSetting("default_model");
        boolean runtimeUsable = StringUtils.hasText(runtimeProvider)
                && StringUtils.hasText(runtimeSetting("provider_api_key"));
        String provider = firstNonBlank(
                runtimeUsable ? runtimeProvider : null,
                headOf(effective),
                runtimeConfig == null ? null : runtimeConfig.staticChatProvider());
        String model = firstNonBlank(
                runtimeUsable ? runtimeModel : null,
                tailOf(effective),
                runtimeConfig == null ? null : runtimeConfig.staticChatModel());

        if (runtimeConfig != null && runtimeConfig.staticChatKeyMissing()) {
            return new AiChatCapability(false,
                    "YAML 已开启 AI 对话客户端（plugin.ai.chat.enabled=true）但未配置密钥，"
                            + "客户端未创建；请设置 plugin.ai.chat.app-key 或环境变量 AI_CHAT_APP_KEY",
                    provider, model);
        }
        if (resolveClient() == null) {
            return new AiChatCapability(false,
                    "后端未提供可用的 AI 对话客户端（没有可用的 ChatClient 实现，"
                            + "请在「AI 配置」里填写提供方、密钥与默认模型）",
                    provider, model);
        }
        return new AiChatCapability(true, "AI 对话可用", provider, model);
    }

    /**
     * 测试连通性：用当前生效的客户端真实发一条最小请求。
     *
     * <p>能力探测只判断「解析得出客户端」，密钥过期、网关不可达、模型名写错时仍会显示可用；
     * 这里把同一条解析链跑一遍并立刻调用，让配置问题在配置页暴露，而不是等到业务链路里
     * 才以「判分掉 0 分」的形式出现。</p>
     *
     * @param timeoutMillis 单次探测超时（毫秒），小于 5000 时按 5000 处理
     * @return 连通性测试结果
     */
    public AiChatProbe probe(long timeoutMillis) {
        ChatClient client = resolveClient();
        if (client == null) {
            return new AiChatProbe(false,
                    "没有可用的 AI 对话客户端，请先在「AI 配置」里填写提供方、密钥与默认模型",
                    null, null, 0L, null);
        }
        AiChatCapability effective = capability();
        long timeout = Math.max(5000L, timeoutMillis);
        long started = System.currentTimeMillis();
        try {
            String reply = client.chatSync("只回复两个字：可用", timeout);
            long cost = System.currentTimeMillis() - started;
            String sample = reply == null ? "" : reply.trim();
            if (sample.length() > 120) {
                sample = sample.substring(0, 120);
            }
            return new AiChatProbe(true, "连通正常", effective.provider(), effective.model(), cost, sample);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - started;
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[AiChat] 连通性测试失败: {}", reason);
            return new AiChatProbe(false, "调用失败：" + reason,
                    effective.provider(), effective.model(), cost, null);
        }
    }

    /**
     * 单次同步调用。
     *
     * @param systemPrompt 系统提示词，可为空白
     * @param userPrompt   用户提示词
     * @param timeoutMillis 超时（毫秒），小于等于 0 时按 {@value #DEFAULT_TIMEOUT_MILLIS} 处理
     * @return 模型输出；客户端不可用时返回空串
     */
    public String complete(String systemPrompt, String userPrompt, long timeoutMillis) {
        ChatClient client = resolveClient();
        if (client == null) {
            return "";
        }
        long timeout = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        // ChatClient 只有单段提示词入口，必须把 system 指令并进 user 提示词；
        // 直接丢弃 systemPrompt 会让「只返回 JSON」这类约束静默失效。
        String combined = StringUtils.isBlank(systemPrompt) ? userPrompt : systemPrompt + "\n\n" + userPrompt;
        String text = client.chatSync(combined, timeout);
        return text == null ? "" : text;
    }

    /**
     * 带重试的同步调用：限流与瞬时故障按退避曲线重试，其余异常立即抛出。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param options      调用选项，为 null 时用默认值
     * @return 模型输出
     * @throws RuntimeException 重试耗尽或不可重试的失败
     */
    public String completeWithRetry(String systemPrompt, String userPrompt, AiChatOptions options) {
        AiChatOptions opts = options == null ? AiChatOptions.defaults() : options;
        int maxRetry = Math.max(0, opts.maxRetries());
        double multiplier = opts.backoffMultiplier() > 1 ? opts.backoffMultiplier() : 2.0D;
        long cap = opts.maxBackoffMillis() > 0 ? opts.maxBackoffMillis() : 15_000L;
        long backoff = Math.max(1L, opts.backoffMillis());
        for (int attempt = 0; ; attempt++) {
            try {
                return complete(systemPrompt, userPrompt, opts.timeoutMillis());
            } catch (Exception e) {
                boolean retryable = isRateLimited(e)
                        || (opts.retryOnTransient() && isTransientFailure(e));
                if (!retryable || attempt >= maxRetry) {
                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(e.getMessage(), e);
                }
                log.warn("[AiChat] 模型调用失败，第 {} 次重试（退避 {}ms，原因: {}）",
                        attempt + 1, backoff, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
                }
                backoff = Math.min(cap, (long) (backoff * multiplier));
            }
        }
    }

    /**
     * 当前 AI 对话运行状态（供运维排查）。
     *
     * @return 状态键值
     */
    public Map<String, Object> status() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("available", available());
        info.put("provider", runtimeConfig == null ? null : runtimeConfig.staticChatProvider());
        info.put("model", runtimeConfig == null ? null : runtimeConfig.staticChatModel());
        info.put("registeredProviders", runtimeConfig == null
                ? java.util.Collections.emptySet() : runtimeConfig.chatProviders());
        return info;
    }

    /**
     * 判断异常链是否为「瞬时故障」：超时 / 502 / 503 / 连接重置等。
     *
     * @param error 异常
     * @return true 表示值得重试
     */
    public static boolean isTransientFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("timeout") || name.contains("connect")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("timeout") || lower.contains("timed out")
                    || lower.contains(" 502") || lower.contains(" 503") || lower.contains(" 504")
                    || lower.contains("connection reset") || lower.contains("temporarily")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常链里是否出现限流（429 / rate limit / 配额）。
     *
     * @param error 异常
     * @return true 表示是限流
     */
    public static boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("ratelimit")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("rate limit") || lower.contains("rate_limit")
                    || lower.contains("too many requests") || lower.contains(" 429 ")
                    || lower.contains("tpm limit") || lower.contains("rpm limit")
                    || lower.contains("rpm exhausted") || lower.contains("tpm exhausted")
                    || lower.contains("quota") || lower.contains("throttl")
                    || lower.contains("限流")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取 AI 控制台「系统设置」{@code runtime} 分组的字段值。
     *
     * @param field 字段名
     * @return 字段值；未配置或读取异常时返回 null
     */
    private String runtimeSetting(String field) {
        if (runtimeConfig == null) {
            return null;
        }
        return runtimeConfig.runtimeSetting("runtime", field);
    }

    /**
     * 按 AI 控制台 runtime 分组构建真实对话客户端。
     *
     * @return 可用的对话客户端；配置不完整或构建失败时返回 null
     */
    private ChatClient buildRuntimeChatClient() {
        if (runtimeConfig == null) {
            return null;
        }
        String provider = runtimeSetting("provider");
        String apiKey = runtimeSetting("provider_api_key");
        String baseUrl = runtimeSetting("provider_base_url");
        String model = runtimeSetting("default_model");
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(apiKey)) {
            return null;
        }
        String signature = provider + "|" + baseUrl + "|" + model + "|" + apiKey;
        if (signature.equals(runtimeClientSignature) && runtimeClientCache != null) {
            return runtimeClientCache;
        }
        try {
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.trim())
                    .appKey(apiKey.trim())
                    .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null)
                    .model(StringUtils.hasText(model) ? model.trim() : null)
                    .authScheme(runtimeSetting("provider_auth_scheme"))
                    .authHeader(runtimeSetting("provider_auth_header"))
                    .authPrefix(runtimeSetting("provider_auth_prefix"))
                    .build();
            ChatClient client = ChatClient.create(setting);
            if (client != null) {
                runtimeClientSignature = signature;
                runtimeClientCache = client;
                log.info("[AiChat] 已按 AI 控制台 runtime 配置创建对话客户端: provider={}, model={}", provider, model);
            }
            return client;
        } catch (Exception e) {
            log.warn("[AiChat] 按 runtime 配置创建对话客户端失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param values 候选值
     * @return 首个非空白值；都为空白时返回 null
     */
    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /**
     * 取「provider:model」复合取值的 provider 前缀。
     *
     * @param composite 复合取值
     * @return 前缀；无前缀时返回 null
     */
    private static String headOf(String composite) {
        if (composite == null) {
            return null;
        }
        int idx = composite.indexOf(':');
        return idx > 0 ? composite.substring(0, idx).trim() : null;
    }

    /**
     * 取「provider:model」复合取值的模型名；无前缀时原样返回。
     *
     * @param composite 复合取值
     * @return 模型名；空值时返回 null
     */
    private static String tailOf(String composite) {
        if (composite == null) {
            return null;
        }
        int idx = composite.indexOf(':');
        String v = idx > 0 ? composite.substring(idx + 1) : composite;
        return v.isBlank() ? null : v.trim();
    }

    /**
     * 能力探测结果。
     *
     * @param available 是否可用
     * @param reason    可读原因（不可用时给出可操作提示）
     * @param provider  实际生效的厂商
     * @param model     实际生效的模型
     */
    public record AiChatCapability(boolean available, String reason, String provider, String model) {
    }

    /**
     * 连通性测试结果。
     *
     * @param success    是否成功
     * @param message    结果说明
     * @param provider   实际生效的厂商
     * @param model      实际生效的模型
     * @param costMillis 耗时（毫秒）
     * @param sample     模型回复样例（截断到 120 字）
     */
    public record AiChatProbe(boolean success, String message, String provider, String model,
                              long costMillis, String sample) {
    }

    /**
     * 单次调用选项。
     *
     * @param timeoutMillis      单次调用超时（毫秒）
     * @param maxRetries         重试次数（不含首次）
     * @param backoffMillis      首次退避（毫秒）
     * @param backoffMultiplier  退避倍数
     * @param maxBackoffMillis   退避上限（毫秒）
     * @param retryOnTransient   瞬时故障是否重试
     */
    public record AiChatOptions(long timeoutMillis, int maxRetries, long backoffMillis,
                                double backoffMultiplier, long maxBackoffMillis, boolean retryOnTransient) {

        /**
         * 默认选项：90 秒超时、5 次重试、1 秒起、2 倍退避、上限 20 秒、瞬时故障重试。
         *
         * @return 默认选项
         */
        public static AiChatOptions defaults() {
            return new AiChatOptions(DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_RETRIES, 1000L,
                    2.0D, 20_000L, true);
        }
    }
}

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
 * {@link AiChatGateway} 的默认实现：按 AI 控制台 runtime 配置解析客户端并执行同步调用。
 *
 * <p>解析链、重试退避、能力探测的口径全部收敛在本类，业务模块只依赖
 * {@link AiChatGateway} 接口，不感知 provider / apiKey / baseUrl 的拼装方式。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Service
public class DefaultAiChatGateway implements AiChatGateway {

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
     * @param runtimeConfig      AI 运行时配置，不允许为 null
     * @param chatClientProvider 聚合对话客户端提供者，可为空
     */
    public DefaultAiChatGateway(AiRuntimeConfig runtimeConfig, ObjectProvider<ChatClient> chatClientProvider) {
        this.runtimeConfig = runtimeConfig;
        this.chatClientProvider = chatClientProvider;
    }

    /**
     * {@inheritDoc}
     *
     * @return true 表示可以发起模型调用
     */
    @Override
    public boolean available() {
        return resolveClient() != null;
    }

    /**
     * {@inheritDoc}
     *
     * @return 可用的对话客户端；都没有时返回 null
     */
    @Override
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
     * {@inheritDoc}
     *
     * @return 能力探测结果
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param timeoutMillis 单次探测超时（毫秒）
     * @return 连通性测试结果
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词
     * @param timeoutMillis 超时（毫秒）
     * @return 模型输出
     */
    @Override
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
     * {@inheritDoc}
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param options      调用选项
     * @return 模型输出
     */
    @Override
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
                boolean retryable = AiChatGateway.isRateLimited(e)
                        || (opts.retryOnTransient() && AiChatGateway.isTransientFailure(e));
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
     * {@inheritDoc}
     *
     * @return 状态键值
     */
    @Override
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
}

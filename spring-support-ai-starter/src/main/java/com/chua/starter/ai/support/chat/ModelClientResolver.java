package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型 → 对话客户端 解析器（辅助场景统一出口）。
 *
 * <p>输入优化、摘要压缩等「非主对话」辅助场景，原先只在 {@code ChatClientRegistry} 中按
 * 裸模型名查找；而系统默认模型以 {@code <providerId>:<model>} 形式存储（如
 * {@code 1789547286889:agnes-2.5-flash}），且真实可用客户端是按 DB 供应商配置动态构建的，
 * 注册中心里既没有该限定名、默认客户端也可能为空，导致辅助链路取不到客户端而静默回退。</p>
 *
 * <p>本解析器统一完成：
 * <ol>
 *   <li>归一化：剥离「{@code providerId:}」前缀，得到裸模型名与可选 providerId；</li>
 *   <li>供应商解析：显式 providerId 直查，否则按启用的 provider_model 行反查；</li>
 *   <li>客户端构建：收集供应商级 + 连接级全部密钥，单密钥直连、多密钥 failover 聚合；</li>
 *   <li>按 {@code provider::model} 缓存（TTL {@value #CACHE_TTL_MILLIS} ms）。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelClientResolver {

    /**
     * 客户端缓存 TTL：5 分钟
     */
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    /**
     * 供应商 Mapper
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商模型 Mapper
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 供应商连接 Mapper
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    /**
     * 客户端缓存：key = providerId::model
     */
    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();

    /**
     * 按（可能带 providerId 前缀的）模型名解析可用对话客户端。
     *
     * @param qualifiedModel 模型名，可为 {@code <providerId>:<model>} 或裸模型名
     * @return 已绑定模型的 ChatClient；模型/供应商/密钥不可用时返回 {@code null}
     */
    public ChatClient resolve(String qualifiedModel) {
        ParsedModel parsed = parse(qualifiedModel);
        if (parsed == null) {
            return null;
        }
        SysAiProvider provider = resolveProvider(parsed);
        if (provider == null) {
            log.debug("[ModelClientResolver] 未找到启用的供应商: {}", qualifiedModel);
            return null;
        }
        log.info("[ModelClientResolver] 路由 {} -> 供应商={}({}) baseUrl={} model={}",
                qualifiedModel, provider.getProviderId(), provider.getPlatform(),
                provider.getBaseUrl(), parsed.model());
        String cacheKey = provider.getProviderId() + "::" + parsed.model();
        long now = System.currentTimeMillis();
        CachedClient cached = cache.get(cacheKey);
        if (cached != null && now - cached.builtAt() < CACHE_TTL_MILLIS) {
            return cached.client();
        }
        ChatClient built = build(provider, parsed.model());
        if (built != null) {
            cache.put(cacheKey, new CachedClient(built, now));
        }
        return built;
    }

    /**
     * 归一化模型名，剥离「数字 providerId:」前缀。
     *
     * @param raw 原始模型名
     * @return 裸模型名；入参为空返回 {@code null}
     */
    public static String normalize(String raw) {
        ParsedModel parsed = parse(raw);
        return parsed == null ? null : parsed.model();
    }

    /**
     * 解析供应商：显式 providerId 直查（并校验启用），否则按启用的模型行反查。
     *
     * <p>反查按模型名查时可能命中多行（不同供应商允许同名模型）。此时不再静默取
     * {@code LIMIT 1} 的第一行 —— 那等于让数据库的返回顺序决定实际调谁，用户选了哪家
     * 就完全不起作用。改为打一条列出全部候选的 WARN，让路由问题在日志里直接可见。</p>
     *
     * @param parsed 归一化后的模型
     * @return 启用的供应商；未找到返回 {@code null}
     */
    private SysAiProvider resolveProvider(ParsedModel parsed) {
        try {
            if (parsed.providerId() != null) {
                SysAiProvider provider = providerMapper.selectById(parsed.providerId());
                if (provider != null && Boolean.TRUE.equals(provider.getEnabled())) {
                    return provider;
                }
                log.warn("[ModelClientResolver] 指定的供应商 {} 不可用（不存在或已停用），回退按模型名反查: {}",
                        parsed.providerId(), parsed.model());
            }
            // 反查：不带 providerId 时按模型名找启用行
            List<SysAiProviderModel> candidates = providerModelMapper.selectList(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, parsed.model())
                            .eq(SysAiProviderModel::getEnabled, true)
                            .orderByAsc(SysAiProviderModel::getProviderId));
            if (candidates.isEmpty()) {
                return null;
            }
            if (candidates.size() > 1) {
                log.warn("[ModelClientResolver] 模型 {} 在 {} 个供应商下同名（{}），未指定供应商时取第一家；"
                                + "若这不符合预期，请让调用方传 <providerId>:<model> 限定名",
                        parsed.model(), candidates.size(),
                        candidates.stream().map(SysAiProviderModel::getProviderId).toList());
            }
            for (SysAiProviderModel pm : candidates) {
                SysAiProvider provider = providerMapper.selectById(pm.getProviderId());
                if (provider != null && Boolean.TRUE.equals(provider.getEnabled())) {
                    return provider;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[ModelClientResolver] 解析供应商失败 {}: {}", parsed.model(), e.getMessage());
            return null;
        }
    }

    /**
     * 收集供应商级 + 连接级全部密钥，构建单密钥直连或多密钥 failover 聚合客户端。
     *
     * @param provider 供应商
     * @param model    裸模型名
     * @return 已绑定模型的客户端；无可用密钥或构建失败返回 {@code null}
     */
    private ChatClient build(SysAiProvider provider, String model) {
        try {
            List<String> keys = collectAllKeys(provider);
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            if (keys.isEmpty()) {
                log.debug("[ModelClientResolver] 供应商无可用密钥: {}", provider.getProviderId());
                return null;
            }
            if (keys.size() == 1) {
                ChatClient raw = ChatClient.create(buildSetting(provider, keys.getFirst(), extraHeaders));
                return raw == null ? null : raw.model(model);
            }
            AggregateChatClientSetting aggSetting = new AggregateChatClientSetting();
            aggSetting.setStrategy("failover");
            AggregateChatClient aggregate = new AggregateChatClient(aggSetting);
            for (int i = 0; i < keys.size(); i++) {
                ChatClient raw = ChatClient.create(buildSetting(provider, keys.get(i), extraHeaders));
                if (raw != null) {
                    aggregate.addClient("client-" + i, provider.getPlatform(), 1, raw.model(model));
                }
            }
            return aggregate.model(model);
        } catch (Exception e) {
            log.warn("[ModelClientResolver] 构建客户端失败 provider={} model={}: {}",
                    provider.getProviderId(), model, e.getMessage());
            return null;
        }
    }

    /**
     * 组装客户端配置，含认证方案。
     *
     * <p>认证方案必须一并下发：{@code auth_scheme} 决定是否发送认证头，
     * {@code extra.auth_header} / {@code extra.auth_prefix} 决定头名与前缀。
     * 缺了这三项，Dots Studio 这类只认 {@code api-key} 的服务商会因
     * 收到 {@code Authorization: Bearer} 而 401。</p>
     *
     * @param provider     供应商
     * @param apiKey       API Key
     * @param extraHeaders 自定义请求头
     * @return 客户端配置
     */
    private ChatClientSetting buildSetting(SysAiProvider provider, String apiKey, Map<String, String> extraHeaders) {
        return ChatClientSetting.builder()
                .provider(provider.getPlatform())
                .appKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(provider.getAuthScheme())
                .authHeader(extraString(provider.getExtra(), "auth_header"))
                .authPrefix(extraString(provider.getExtra(), "auth_prefix"))
                .proxy(provider.getProxy())
                .build();
    }

    /**
     * 读取 extra 中的字符串值。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 字符串值；缺失返回 {@code null}
     */
    private static String extraString(Map<String, Object> extra, String key) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 收集供应商级与其全部连接级凭证中的密钥（去重、保序）。
     *
     * @param provider 供应商
     * @return 密钥列表
     */
    private List<String> collectAllKeys(SysAiProvider provider) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(ProviderCredentials.apiKeys(provider.getCredentials()));
        try {
            List<SysAiProviderConnection> connections = connectionMapper.selectList(
                    Wrappers.<SysAiProviderConnection>lambdaQuery()
                            .eq(SysAiProviderConnection::getProviderId, provider.getProviderId()));
            for (SysAiProviderConnection conn : connections) {
                keys.addAll(ProviderCredentials.apiKeys(conn.getCredentials()));
            }
        } catch (Exception e) {
            log.debug("[ModelClientResolver] 查询连接凭证失败: {}", e.getMessage());
        }
        return new ArrayList<>(keys);
    }

    /**
     * 从供应商 extra.custom_headers 提取附加请求头。
     *
     * @param extra 扩展配置
     * @return 请求头；无则返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object headers = extra.get("custom_headers");
        if (headers instanceof Map<?, ?> map) {
            Map<String, String> result = new java.util.HashMap<>(8);
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 归一化结果：裸模型名 + 可选 providerId。
     *
     * @param model      裸模型名
     * @param providerId 供应商 id（无显式前缀时为 {@code null}）
     */
    private record ParsedModel(String model, String providerId) {
    }

    /**
     * 解析 {@code <providerId>:<model>} 形态：冒号前的非空片段一律当作供应商标识。
     *
     * <p>刻意不要求前缀是纯数字：供应商 id 除了雪花 id 还可能是 {@code onnx} 这类平台标识
     * （见 {@code sys_ai_provider.sys_ai_provider_provider_id}）。前缀不存在时
     * {@link #resolveProvider(ParsedModel)} 会打 WARN 并回退按模型名反查，不会静默错配。</p>
     *
     * @param raw 原始模型名
     * @return 归一化结果；空白输入返回 {@code null}
     */
    private static ParsedModel parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int colon = value.indexOf(':');
        if (colon > 0) {
            String prefix = value.substring(0, colon).trim();
            String model = value.substring(colon + 1).trim();
            if (!prefix.isEmpty() && !model.isEmpty()) {
                return new ParsedModel(model, prefix);
            }
        }
        return new ParsedModel(value, null);
    }

    /**
     * 缓存条目。
     *
     * @param client  客户端
     * @param builtAt 构建时间戳
     */
    private record CachedClient(ChatClient client, long builtAt) {
    }
}

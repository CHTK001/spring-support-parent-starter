// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.event.ChannelHealthProbeEvent;
import com.chua.starter.ai.support.service.ChannelHealthService;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.ai.support.util.AiUserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * <p>本解析器统一完成：</p>
 * <ol>
 *   <li>归一化：剥离「{@code providerId:}」前缀，得到裸模型名与可选 providerId；</li>
 *   <li>供应商解析：显式 providerId 直查，否则按启用的 provider_model 行反查；</li>
 *   <li>客户端构建：收集供应商级 + 连接级全部密钥，单密钥直连、多密钥 failover 聚合；</li>
 *   <li>渠道路由收集：遍历 OAuth 渠道供应商，把每个连接的 access_token 经 SPI 的
 *       {@code toRouteTarget} 转为路由目标，聚合同一渠道多账号到 {@code apiKeys} 列表；</li>
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
     * 渠道路由缓存：key = ownerScope
     */
    private final Map<String, CachedRoutes> routeCache = new ConcurrentHashMap<>();

    /**
     * 渠道健康探针服务：通道隔离（剔除下线的连接）
     */
    private final ChannelHealthService channelHealthService;

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
        // 缓存键必须带账号归属：不同用户绑定的账号不同，聚合出来的客户端不能跨用户复用
        String ownerScope = ownerScopeOf(provider);
        String cacheKey = ownerScope + "::" + provider.getProviderId() + "::" + parsed.model();
        long now = System.currentTimeMillis();
        CachedClient cached = cache.get(cacheKey);
        if (cached != null && now - cached.builtAt() < CACHE_TTL_MILLIS) {
            return cached.client();
        }
        ChatClient built = build(provider, parsed.model(), ownerScope);
        if (built != null) {
            cache.put(cacheKey, new CachedClient(built, now));
        }
        return built;
    }

    /**
     * 计算归属作用域，用于缓存键与连接过滤。
     *
     * <p>免费渠道固定走「管理员/系统账号池」（{@code _free}，与当前用户无关）；
     * 其余渠道按当前用户隔离（未登录/后台线程无身份时为 {@code _sys}，只取系统池）。</p>
     *
     * @param provider 供应商
     * @return 归属作用域
     */
    public static String ownerScopeOf(SysAiProvider provider) {
        if (ProviderRouteTargets.isFreeChannel(provider.getPlatform())) {
            return "_free";
        }
        String userId = AiUserContext.currentUserId();
        return userId == null ? "_sys" : userId;
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
     * 收集供应商级 + 连接级全部密钥，构建单密钥直连或多密钥 failover/round-robin 聚合客户端。
     *
     * @param provider   供应商
     * @param model      裸模型名
     * @param ownerScope 归属作用域（缓存键/连接过滤，见 {@link #ownerScopeOf(SysAiProvider)}）
     * @return 已绑定模型的客户端；无可用密钥或构建失败返回 {@code null}
     */
    private ChatClient build(SysAiProvider provider, String model, String ownerScope) {
        try {
            List<String> keys = collectAllKeys(provider, ownerScope);
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            // 免费渠道：收集动态渠道路由（多账号 429 failover）
            Map<String, RouteTarget> channelRoutes = ProviderRouteTargets.isFreeChannel(provider.getPlatform())
                    ? mergedRoutes(provider, ownerScope)
                    : null;
            if (keys.isEmpty()) {
                if (!ProviderRouteTargets.isFreeChannel(provider.getPlatform())) {
                    log.debug("[ModelClientResolver] 供应商无可用密钥: {}", provider.getProviderId());
                    return null;
                }
                // 免费渠道允许无 key：部分上游免鉴权，缺失时由客户端按路由决定是否发送认证头
                keys = new ArrayList<>();
                keys.add(null);
            }
            // UI 上的「自动限流」开关：关闭时固定用第一个 Key，不组聚合、不横向切换账号
            boolean autoSwitch = !"false".equalsIgnoreCase(
                    extraString(provider.getExtra(), "auto_switch_on_rate_limit"));
            if (keys.size() == 1 || !autoSwitch) {
                ChatClient raw = ChatClient.create(buildSetting(provider, keys.getFirst(), extraHeaders, channelRoutes));
                return raw == null ? null : raw.model(model);
            }
            // 多 Key：按 UI 上的 Key 策略（failover / round_robin）组聚合，429 限流自动切下一个
            String strategy = extraString(provider.getExtra(), "load_strategy");
            AggregateChatClientSetting aggSetting = new AggregateChatClientSetting();
            aggSetting.setStrategy(StringUtils.isNotBlank(strategy) ? strategy : "failover");
            aggSetting.setAutoSwitchOnRateLimit(true);
            aggSetting.setAutoSwitchOnQuotaExhausted(true);
            AggregateChatClient aggregate = new AggregateChatClient(aggSetting);
            for (int i = 0; i < keys.size(); i++) {
                ChatClient raw = ChatClient.create(buildSetting(provider, keys.get(i), extraHeaders, channelRoutes));
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
     * 组装客户端配置，含认证方案与渠道路由。
     *
     * <p>认证方案必须一并下发：{@code auth_scheme} 决定是否发送认证头，
     * {@code extra.auth_header} / {@code extra.auth_prefix} 决定头名与前缀。
     * 缺了这三项，Dots Studio 这类只认 {@code api-key} 的服务商会因
     * 收到 {@code Authorization: Bearer} 而 401。</p>
     *
     * @param provider      供应商
     * @param apiKey        API Key
     * @param extraHeaders  自定义请求头
     * @param channelRoutes 渠道路由（免费渠道专用，可为 null）
     * @return 客户端配置
     */
    private ChatClientSetting buildSetting(SysAiProvider provider, String apiKey,
                                            Map<String, String> extraHeaders,
                                            Map<String, RouteTarget> channelRoutes) {
        return ChatClientSetting.builder()
                .provider(provider.getPlatform())
                .appKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(provider.getAuthScheme())
                .authHeader(extraString(provider.getExtra(), "auth_header"))
                .authPrefix(extraString(provider.getExtra(), "auth_prefix"))
                .metricsUrl(extraString(provider.getExtra(), "metrics_url"))
                .proxy(provider.getProxy())
                .routes(channelRoutes != null ? channelRoutes
                        : ProviderRouteTargets.fromExtra(provider.getExtra()))
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
     * 本连接是否已下线（通道探针连续失败达到阈值后自动下线）
     *
     * @param conn 连接
     * @return true 表示已下线（路由层应剔除）
     */
    private boolean isDown(SysAiProviderConnection conn) {
        return channelHealthService.isDown(conn.getConnectionId());
    }

    /**
     * 收集供应商级与其全部连接级凭证中的密钥（去重、保序），并按归属作用域过滤连接。
     *
     * <p>归属规则：</p>
     * <ul>
     *   <li>免费渠道（{@code _free}）：只用管理员/系统账号池（历史 {@code user_id IS NULL} 行
     *       = 渠道管理改造前管理员绑定的账号）——「免费的走管理员的渠道管理配置」；</li>
     *   <li>当前用户（userId）：只用该用户自己的连接；管理员额外可见 NULL 系统池；</li>
     *   <li>无身份后台任务（{@code _sys}）：只用 NULL 系统池，避免匿名读到他人账号。</li>
     * </ul>
     *
     * @param provider   供应商
     * @param ownerScope 归属作用域
     * @return 密钥列表
     */
    private List<String> collectAllKeys(SysAiProvider provider, String ownerScope) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(ProviderCredentials.apiKeys(provider.getCredentials()));
        try {
            LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, provider.getProviderId());
            if ("_free".equals(ownerScope) || "_sys".equals(ownerScope)) {
                wrapper.isNull(SysAiProviderConnection::getUserId);
            } else {
                boolean admin = AiUserContext.isAdmin();
                String userId = ownerScope;
                wrapper.and(w -> {
                    w.eq(SysAiProviderConnection::getUserId, userId);
                    if (admin) {
                        w.or().isNull(SysAiProviderConnection::getUserId);
                    }
                });
            }
            List<SysAiProviderConnection> connections = connectionMapper.selectList(wrapper);
            for (SysAiProviderConnection conn : connections) {
                if (isDown(conn)) {
                    continue;
                }
                keys.addAll(ProviderCredentials.apiKeys(conn.getCredentials()));
            }
        } catch (Exception e) {
            log.debug("[ModelClientResolver] 查询连接凭证失败: {}", e.getMessage());
        }
        return new ArrayList<>(keys);
    }

    /**
     * 收集所有 OAuth 渠道的路由目标（多账号聚合）。
     *
     * <p>遍历所有 {@code auth_scheme=oauth} 的启用供应商，找到对应 SPI 实现，
     * 查询其连接（按 ownerScope 过滤），调用 {@link AccountChannelAuthManager#toRouteTarget}
     * 把每个连接的 access_token 转为 RouteTarget，聚合同一渠道的多个账号到同一个
     * {@code apiKeys} 列表。返回的路由按渠道名 + 模型名注册，供 FreeChannelChatClient
     * 按模型名路由并触发 429 failover。</p>
     *
     * <p>缓存 5 分钟，按 ownerScope 隔离。</p>
     *
     * @param ownerScope 归属作用域
     * @return 渠道路由表；无可用渠道时返回空 Map
     */
    public Map<String, RouteTarget> collectChannelRoutes(String ownerScope) {
        long now = System.currentTimeMillis();
        CachedRoutes cached = routeCache.get(ownerScope);
        if (cached != null && now - cached.builtAt() < CACHE_TTL_MILLIS) {
            return cached.routes();
        }
        Map<String, RouteTarget> routes = new LinkedHashMap<>();
        try {
            // 查询所有 OAuth 渠道供应商
            List<SysAiProvider> oauthProviders = providerMapper.selectList(
                    Wrappers.<SysAiProvider>lambdaQuery()
                            .eq(SysAiProvider::getAuthScheme, "oauth")
                            .eq(SysAiProvider::getEnabled, true));
            for (SysAiProvider provider : oauthProviders) {
                // 找到对应 SPI
                AccountChannelAuthManager spi = findSpi(provider.getPlatform());
                if (spi == null) {
                    continue;
                }
                // 查询连接（按 ownerScope 过滤）
                LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                        new LambdaQueryWrapper<SysAiProviderConnection>()
                                .eq(SysAiProviderConnection::getProviderId, provider.getProviderId());
                if ("_free".equals(ownerScope) || "_sys".equals(ownerScope)) {
                    wrapper.isNull(SysAiProviderConnection::getUserId);
                } else {
                    boolean admin = AiUserContext.isAdmin();
                    String userId = ownerScope;
                    wrapper.and(w -> {
                        w.eq(SysAiProviderConnection::getUserId, userId);
                        if (admin) {
                            w.or().isNull(SysAiProviderConnection::getUserId);
                        }
                    });
                }
                List<SysAiProviderConnection> connections = connectionMapper.selectList(wrapper);
                if (connections.isEmpty()) {
                    continue;
                }
                // 聚合同一渠道的所有账号（剔除下线的连接）
                List<String> apiKeys = new ArrayList<>();
                RouteTarget template = null;
                for (SysAiProviderConnection conn : connections) {
                    if (isDown(conn)) {
                        continue;
                    }
                    RouteTarget target = spi.toRouteTarget(conn.getCredentials());
                    if (target == null) {
                        continue;
                    }
                    // 免鉴权渠道（authScheme=none）的 apiKey 为空串，允许通过
                    if (StringUtils.isBlank(target.getApiKey())
                            && !"none".equals(target.getAuthScheme())) {
                        continue;
                    }
                    if (template == null) {
                        template = target;
                    }
                    apiKeys.add(target.getApiKey());
                }
                if (apiKeys.isEmpty() || template == null) {
                    continue;
                }
                // 构建聚合路由：同一渠道多个账号的 apiKeys
                RouteTarget merged = RouteTarget.builder()
                        .provider(template.getProvider())
                        .baseUrl(template.getBaseUrl())
                        .authScheme(template.getAuthScheme())
                        .authHeader(template.getAuthHeader())
                        .authPrefix(template.getAuthPrefix())
                        .apiKeys(apiKeys)
                        .headers(template.getHeaders())
                        .autoSwitchOnRateLimit(true)
                        .build();
                // 查询模型名并注册路由
                List<SysAiProviderModel> models = providerModelMapper.selectList(
                        Wrappers.<SysAiProviderModel>lambdaQuery()
                                .eq(SysAiProviderModel::getProviderId, provider.getProviderId())
                                .eq(SysAiProviderModel::getEnabled, true));
                // 按渠道名注册（prefix match）
                routes.put(provider.getPlatform(), merged);
                // 按模型名注册（exact match）
                for (SysAiProviderModel m : models) {
                    routes.put(m.getModel(), merged);
                }
                log.info("[ModelClientResolver] 渠道路由 {} 注册 {} 个模型 {} 个账号",
                        provider.getPlatform(), models.size(), apiKeys.size());
            }
        } catch (Exception e) {
            log.warn("[ModelClientResolver] 收集渠道路由失败: {}", e.getMessage());
        }
        routeCache.put(ownerScope, new CachedRoutes(routes, now));
        return routes;
    }

    /**
     * 根据平台名查找对应的 OAuth 渠道 SPI 实现。
     *
     * @param platform 平台名（如 "loomy", "qoder", "workbuddy"）
     * @return SPI 实例；未找到返回 null
     */
    private AccountChannelAuthManager findSpi(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        try {
            return ServiceProvider.of(AccountChannelAuthManager.class)
                    .getExtension(platform.trim());
        } catch (Exception e) {
            log.debug("[ModelClientResolver] 未找到 SPI: {}", platform);
            return null;
        }
    }

    /**
     * 合并静态路由（extra.routes）与动态渠道路由。
     *
     * <p>免费渠道供应商（{@code free-channel}）的路由由两部分组成：</p>
     * <ul>
     *   <li>静态路由：管理员在供应商 extra 中手写的 {@code routes} 配置</li>
     *   <li>动态路由：由 {@link #collectChannelRoutes} 从 OAuth 渠道连接自动收集</li>
     * </ul>
     *
     * <p>动态路由覆盖静态路由（自动同步优先于手动配置）。</p>
     *
     * @param provider   供应商
     * @param ownerScope 归属作用域
     * @return 合并后的路由表
     */
    public Map<String, RouteTarget> mergedRoutes(SysAiProvider provider, String ownerScope) {
        Map<String, RouteTarget> staticRoutes = ProviderRouteTargets.fromExtra(provider.getExtra());
        Map<String, RouteTarget> dynamicRoutes = collectChannelRoutes(ownerScope);
        Map<String, RouteTarget> merged = new LinkedHashMap<>();
        if (staticRoutes != null) {
            merged.putAll(staticRoutes);
        }
        merged.putAll(dynamicRoutes);
        return merged;
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
     * 渠道健康探针完成：失效路由与客户端缓存，令下线的连接立即从路由表剔除。
     *
     * <p>默认路由缓存 5 分钟（TTL），探针完成时主动失效可把下线/恢复窗口从小时级压到秒级。</p>
     *
     * @param event 探针完成事件
     */
    @EventListener
    public void onChannelHealthProbe(ChannelHealthProbeEvent event) {
        // 失效缓存
        cache.clear();
        routeCache.clear();
        log.info("[ModelClientResolver] 渠道探针完成 失效缓存 down={} probed={} scheduled={}",
                event.downConnections(), event.probed(), event.scheduled());
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

    /**
     * 渠道路由缓存条目。
     *
     * @param routes  路由表
     * @param builtAt 构建时间戳
     */
    private record CachedRoutes(Map<String, RouteTarget> routes, long builtAt) {
    }
}

// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.aggregate.MultiKeyChatClient;
import com.chua.common.support.ai.chat.aggregate.RouterGroupChatClient;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.task.cooldown.Backoff;
import com.chua.common.support.task.cooldown.CooldownPolicies;
import com.chua.common.support.task.cooldown.CooldownPolicy;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.channel.service.AccountChannelConnectionService;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.event.ChannelHealthProbeEvent;
import com.chua.starter.ai.support.service.ChannelHealthService;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * 渠道业务（账号模块）
     */
    private final AccountChannelService channelService;

    /**
     * 渠道模型清单业务（账号模块的查询接口，含缓存）
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 渠道连接业务（账号模块）：按归属作用域聚合密钥
     */
    private final AccountChannelConnectionService connectionService;

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
     * 渠道路由源（账号渠道 / 免登录渠道 / magpie 网关），按 {@link ChannelRouteSource#order()} 叠加
     */
    private final List<ChannelRouteSource> routeSourceBeans;

    /**
     * 排好序的路由源（启动后不变，双重检查初始化）
     */
    private volatile List<ChannelRouteSource> routeSources;

    /**
     * 路由组业务（成员表读写与配置解析）
     */
    private final RouterGroupService routerGroupService;

    /**
     * 路由组配置或成员变化后，让该组的客户端缓存立即失效。
     *
     * @param groupId 路由组供应商 ID
     */
    public void invalidateRouterGroup(String groupId) {
        if (groupId == null) {
            return;
        }
        // 缓存键形如 scope::providerId::model：组 ID 在中间，
        // 不能按 "::groupId::" 前后都带冒号匹配（那样会把结尾的 model 段漏掉）。
        String marker = "::" + groupId + "::";
        int removed = 0;
        for (String key : cache.keySet()) {
            if (key.startsWith(marker) || key.contains(marker)) {
                cache.remove(key);
                removed++;
            }
        }
        log.info("[ModelClientResolver] 路由组缓存已失效 provider={} 清理={}", groupId, removed);
    }

    /**
     * 读取某个模型所属供应商的限流参数。
     *
     * <p>限流按供应商维度共享：同一供应商下的所有 Key 共用并发与每分钟配额。
     * 模型解析不到供应商时返回 {@code null}（不限流）。</p>
     *
     * @param qualifiedModel 模型名，可为 {@code <providerId>:<model>} 或裸模型名
     * @return 限流参数；无供应商时 {@code null}
     */
    public ThrottleSpec throttleOf(String qualifiedModel) {
        ParsedModel parsed = parse(qualifiedModel);
        if (parsed == null) {
            return null;
        }
        SysAccountChannel provider = resolveProvider(parsed);
        if (provider == null) {
            return null;
        }
        Map<String, Object> extra = provider.getExtra() == null ? Map.of() : provider.getExtra();
        ThrottleLimits limits = resolveThrottleLimits(extra);
        return new ThrottleSpec(provider.getProviderId(), limits.concurrency(), limits.rpm());
    }

    /**
     * 供应商级限流参数。
     *
     * @param providerId  供应商 ID
     * @param concurrency 并发上限（≤0 不限制）
     * @param rpm         每分钟请求数（≤0 不限制）
     */
    public record ThrottleSpec(String providerId, int concurrency, int rpm) {
    }

    /**
     * 供应商级限流数值（未含供应商 ID，便于单测）。
     *
     * @param concurrency 并发上限（≤0 不限制）
     * @param rpm         每分钟请求数（≤0 不限制）
     */
    record ThrottleLimits(int concurrency, int rpm) {
    }

    /**
     * 解析供应商限流数值：同步落库的官方限制 → 手动限制 → 表单字段，逐维度回退。
     *
     * <p>取值路径：</p>
     * <ol>
     *   <li>{@code extra.provider_limits.declared}（「同步」落库、以及文档登记表里核对过的口径）；</li>
     *   <li>{@code extra.limits_manual}（管理员按后台账单手填）；</li>
     *   <li>{@code extra.max_concurrency} / {@code extra.requests_per_minute}（供应商表单原字段）。</li>
     * </ol>
     *
     * <p>逐维度回退：同步只拿到频率、没拿到并发时，并发继续往下找，不将就 0。
     * 0 / 负值一律视为「未配置」继续回退；{@code model_concurrency} 是按模型的，
     * 不参与供应商级闸门（这里只取账号级 {@code concurrency}）。</p>
     *
     * @param extra 供应商扩展配置（可空）
     * @return 限流数值
     */
    static ThrottleLimits resolveThrottleLimits(Map<String, Object> extra) {
        return new ThrottleLimits(
                firstPositiveLimit(extra, "concurrency", "max_concurrency"),
                firstPositiveLimit(extra, "requests_per_minute", "requests_per_minute"));
    }

    /**
     * 按优先级取一个限流维度。
     *
     * @param extra    供应商扩展配置
     * @param limitKey 限制口径里的键名（同步报告 / 手动配置共用）
     * @param formKey  供应商表单里的原键名
     * @return 第一个大于 0 的值；都没配上返回 0（不限制）
     */
    private static int firstPositiveLimit(Map<String, Object> extra, String limitKey, String formKey) {
        int synced = extraInt(extraMap(extraMap(extra, "provider_limits"), "declared"), limitKey);
        if (synced > 0) {
            return synced;
        }
        int manual = extraInt(extraMap(extra, "limits_manual"), limitKey);
        if (manual > 0) {
            return manual;
        }
        return extraInt(extra, formKey);
    }

    /**
     * 让某个路由组缓存立即失效（配置 / 成员 / 顺序变化后调用）。
     *
     * @param providerId 路由组供应商 ID
     */
    public void invalidateProvider(String providerId) {
        invalidateRouterGroup(providerId);
    }

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
        // 同名模型的跨供应商自动切换已移除：外层聚合与路由组策略会互相覆盖，
        // 且没有 per-key 冷却；需要自动切换时请建一个路由组供应商。
        SysAccountChannel provider = resolveProvider(parsed);
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
    public static String ownerScopeOf(SysAccountChannel provider) {
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
    private SysAccountChannel resolveProvider(ParsedModel parsed) {
        try {
            if (parsed.providerId() != null) {
                SysAccountChannel provider = channelService.find(parsed.providerId());
                if (provider != null && Boolean.TRUE.equals(provider.getEnabled())) {
                    return provider;
                }
                log.warn("[ModelClientResolver] 指定的供应商 {} 不可用（不存在或已停用），回退按模型名反查: {}",
                        parsed.providerId(), parsed.model());
            }
            // 反查：不带 providerId 时按模型名找启用行（「只认 enabled=1」的判定与缓存都在账号模块）
            List<SysAccountChannelModel> candidates = channelModelService.listEnabledByModel(parsed.model());
            if (candidates.isEmpty()) {
                return null;
            }
            if (candidates.size() > 1) {
                log.warn("[ModelClientResolver] 模型 {} 在 {} 个供应商下同名（{}），未指定供应商时取第一家；"
                            + "若这不符合预期，请让调用方传 <providerId>:<model> 限定名",
                        parsed.model(), candidates.size(),
                        candidates.stream().map(SysAccountChannelModel::getProviderId).toList());
            }
            for (SysAccountChannelModel pm : candidates) {
                SysAccountChannel provider = channelService.find(pm.getProviderId());
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
     * @param ownerScope 归属作用域（缓存键/连接过滤，见 {@link #ownerScopeOf(SysAccountChannel)}）
     * @return 已绑定模型的客户端；无可用密钥或构建失败返回 {@code null}
     */
    private ChatClient build(SysAccountChannel provider, String model, String ownerScope) {
        // 路由组供应商：模型来自模型库，选中成员 = 交给路由组容器按策略选真实上游
        if (ProviderRouteTargets.isRouterGroup(provider.getPlatform())) {
            return buildRouterGroup(provider, ownerScope);
        }
        try {
            return buildKeyed(provider, model, ownerScope);
        } catch (Exception e) {
            log.warn("[ModelClientResolver] 构建客户端失败 provider={} model={}: {}",
                    provider.getProviderId(), model, e.getMessage());
            return null;
        }
    }

    /**
     * 按 Key 构建客户端：单 Key 与多 Key 走同一条路。
     *
     * <p>只有一个 Key 时也包一层 {@link MultiKeyChatClient}：Key 的失败/成功必须
     * 记进 {@link com.chua.common.support.ai.chat.aggregate.ClientCooldownRegistry}，
     * 否则同一个 Key 会被路由组反复选中、被上层当成健康账号。多 Key 的轮换、
     * 冷却与「自动切换」开关同样由它内部处理，这里不再另建一套聚合。</p>
     *
     * @param provider   供应商
     * @param model      裸模型名
     * @param ownerScope 归属作用域
     * @return 客户端；没有任何可用 Key 时返回 {@code null}
     */
    private ChatClient buildKeyed(SysAccountChannel provider, String model, String ownerScope) {
        registerCooldownPolicy(provider);
        Map<String, ChatClient> keyed = keyedClients(provider, model, ownerScope);
        if (keyed.isEmpty()) {
            return null;
        }
        return MultiKeyChatClient.multiKey(provider.getProviderId(), model,
                MultiKeyChatClient.Strategy.of(extraString(provider.getExtra(), "load_strategy")),
                !"false".equalsIgnoreCase(extraString(provider.getExtra(), "auto_switch_on_rate_limit")),
                keyed);
    }

    /**
     * 按「Key → 已绑定模型的客户端」收集单个供应商下的客户端。
     *
     * <p>多 Key 的轮换、冷却、开关全部交给 {@link MultiKeyChatClient} 在内部维护，
     * 这里只负责把「哪些 Key、每个 Key 用哪个地址」翻译成客户端。</p>
     *
     * @param provider   供应商
     * @param model      裸模型名
     * @param ownerScope 归属作用域
     * @return Key（可空，代表免鉴权）→ 客户端；无可用 Key 时为空表
     */
    private Map<String, ChatClient> keyedClients(SysAccountChannel provider, String model, String ownerScope) {
        Map<String, Object> providerExtra = provider.getExtra() == null ? Map.of() : provider.getExtra();
        List<String> keys = collectAllKeys(provider, ownerScope);
        // 同时使用的账号数：只用前 N 个 Key 轮换（0/缺失 = 全部启用）
        int maxAccounts = extraInt(providerExtra, "max_accounts");
        if (maxAccounts > 0 && keys.size() > maxAccounts) {
            log.info("[ModelClientResolver] 供应商 {} 限制同时使用 Key：{} / {}",
                    provider.getProviderId(), maxAccounts, keys.size());
            keys = new ArrayList<>(keys.subList(0, maxAccounts));
        }
        Map<String, String> extraHeaders = extractExtraHeaders(providerExtra);
        mergePassthroughRequestHeaders(providerExtra, extraHeaders);
        Map<String, Object> extraBody = extraMap(providerExtra, "extra_body");
        Map<String, RouteTarget> channelRoutes = ProviderRouteTargets.isFreeChannel(provider.getPlatform())
                ? mergedRoutes(provider, ownerScope)
                : null;
        if (keys.isEmpty()) {
            if (!allowsKeyless(provider)) {
                log.debug("[ModelClientResolver] 供应商无可用密钥: {}", provider.getProviderId());
                return Map.of();
            }
            // 免费渠道 / 免登录渠道允许无 key：上游本身不鉴权，
            // 缺失时由客户端按路由与 auth_scheme 决定是否发送认证头
            keys = new ArrayList<>();
            keys.add(null);
        }
        Map<String, ChatClient> result = new LinkedHashMap<>();
        for (String key : keys) {
            ChatClient raw = ChatClient.create(buildSetting(provider, key, extraHeaders, channelRoutes, model));
            if (raw == null) {
                continue;
            }
            if (!extraBody.isEmpty()) {
                // 额外请求体：整段并入出站 JSON（prompt_cache_key、safety_settings 这类上游私有字段）
                raw = raw.extraBody(extraBody);
            }
            result.put(key == null ? "" : key, raw.model(model));
        }
        return result;
    }

    /**
     * 免登录对话渠道的 provider 行前缀。
     *
     * <p>渠道管理把「内置模型渠道」（DuckDuckGo / Trae 这类免登录对话渠道）的模型
     * 落库到 {@code chat-<渠道>} 大写的 provider 行上，而 ChatClient SPI 注册的是
     * 渠道自身的扩展名（{@code duckduckgo}）。解析客户端时按该前缀还原。</p>
     */
    private static final String CHAT_CHANNEL_PREFIX = "chat-";

    /**
     * 该供应商是否允许「无 Key」构建客户端。
     *
     * <p>两类供应商本来就没有 Key：</p>
     * <ul>
     *   <li>免费渠道（{@code free-channel}）：路由表里的上游可能免鉴权；</li>
     *   <li>免登录内置渠道：渠道管理物化出来的 {@code chat-*} 行，以及
     *       {@code auth_scheme=none} 的供应商（如 DuckDuckGo），凭据由客户端自己处理。</li>
     * </ul>
     *
     * <p>缺了这条判断，这些供应商的模型行虽然能在模型管理里看到，
     * 但会话选中时 {@link #keyedClients} 收集不到 Key 就会返回空表，
     * 表现为「模型已启用却永远解析不到客户端」。</p>
     *
     * @param provider 供应商
     * @return 允许无 Key 时返回 {@code true}
     */
    private static boolean allowsKeyless(SysAccountChannel provider) {
        if (ProviderRouteTargets.isFreeChannel(provider.getPlatform())) {
            return true;
        }
        String authScheme = provider.getAuthScheme();
        if (authScheme != null && "none".equalsIgnoreCase(authScheme.trim())) {
            return true;
        }
        String platform = provider.getPlatform();
        return platform != null && platform.trim().toLowerCase(java.util.Locale.ROOT)
                .startsWith(CHAT_CHANNEL_PREFIX);
    }

    /**
     * 把供应商平台标识翻译成 ChatClient SPI 扩展名。
     *
     * <p>免登录对话渠道的 {@code chat-<渠道>} 行去掉前缀（{@code CHAT-DUCKDUCKGO} →
     * {@code duckduckgo}），其它平台原样返回 —— SPI 查找本身大小写不敏感。</p>
     *
     * @param platform 供应商平台标识
     * @return SPI 扩展名；入参为空返回 {@code null}
     */
    static String clientProviderOf(String platform) {
        if (platform == null) {
            return null;
        }
        String value = platform.trim();
        if (value.length() > CHAT_CHANNEL_PREFIX.length()
                && value.toLowerCase(java.util.Locale.ROOT).startsWith(CHAT_CHANNEL_PREFIX)) {
            return value.substring(CHAT_CHANNEL_PREFIX.length());
        }
        return value;
    }

    /**
     * 构造路由组供应商的客户端：成员 = 该路由组配置里引用的模型库行。
     *
     * <p>配置（策略 / 被限流 / 延迟 / 全忙 / 上下文 / 每个成员的 effort 与顺序）全部由
     * {@link RouterGroupChatClient} 自己解析与执行，这里只做两件事：</p>
     * <ol>
     *   <li>把「成员模型 → 真实供应商客户端」的构造方式交给它（{@link #buildKeyed}）；</li>
     *   <li>把模型行上的价格 / 上下文 / 智能 / 延迟回填给它，供「智能 / 最便宜 / 速度」策略打分。</li>
     * </ol>
     *
     * @param provider   路由组供应商
     * @param ownerScope 归属作用域
     * @return 路由组客户端；没有任何可用成员时返回 {@code null}
     */
    private ChatClient buildRouterGroup(SysAccountChannel provider, String ownerScope) {
        try {
            RouterGroupSetting setting = routerGroupService.readSetting(provider);
            if (setting.getMembers().isEmpty()) {
                log.debug("[ModelClientResolver] 路由组无成员模型: {}", provider.getProviderId());
                return null;
            }
            RouterGroupChatClient group = RouterGroupChatClient.create(provider.getProviderId(), setting,
                    member -> buildRouterGroupMember(member));
            for (RouterGroupSetting.Member member : setting.getMembers()) {
                SysAccountChannelModel row = channelModelService.find(member.providerId(), member.model());
                if (row == null) {
                    continue;
                }
                group.describeMember(member.label(),
                        row.getContextWindow() == null ? 0 : row.getContextWindow(),
                        priceInputOf(row), intelligenceOf(row), ttftOf(row));
            }
            log.info("[ModelClientResolver] 路由组就绪 provider={} 名称={} 策略={} 成员={}",
                    provider.getProviderId(), provider.getName(), setting.getStrategy(),
                    setting.getMembers().stream().map(RouterGroupSetting.Member::label).toList());
            return group;
        } catch (Exception e) {
            log.warn("[ModelClientResolver] 构建路由组失败 provider={}: {}",
                    provider.getProviderId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构造单个路由组成员的客户端（成员是普通供应商下的一行模型）。
     *
     * @param member 成员
     * @return 客户端；成员供应商不可用或模型已删除时返回 {@code null}
     */
    private ChatClient buildRouterGroupMember(RouterGroupSetting.Member member) {
        SysAccountChannel memberProvider = channelService.find(member.providerId());
        if (memberProvider == null || !Boolean.TRUE.equals(memberProvider.getEnabled())) {
            log.warn("[ModelClientResolver] 路由组成员供应商不可用: {}", member.label());
            return null;
        }
        if (ProviderRouteTargets.isRouterGroup(memberProvider.getPlatform())) {
            // 路由组嵌套路由组会让策略互相覆盖，直接拒绝
            log.warn("[ModelClientResolver] 路由组不能引用另一个路由组: {}", member.label());
            return null;
        }
        // 成员内部同样按 Key 统一封装：单 Key 也记冷却，多 Key 在成员内部轮换
        return buildKeyed(memberProvider, member.model(), ownerScopeOf(memberProvider));
    }

    /**
     * 读取模型行的每百万输入 token 价格（没有价格时按 0 处理，即视为免费或未知）。
     *
     * @param row 模型行
     * @return 价格
     */
    private static double priceInputOf(SysAccountChannelModel row) {
        Object price = metricValue(row, "price_input_per_musd");
        return price == null ? 0D : parseDouble(price);
    }

    /**
     * 读取模型行的智能分。
     *
     * @param row 模型行
     * @return 智能分；未知为 0
     */
    private static double intelligenceOf(SysAccountChannelModel row) {
        Object value = metricValue(row, "intelligence");
        return value == null ? 0D : parseDouble(value);
    }

    /**
     * 读取模型行的实测首字延迟（毫秒）。
     *
     * @param row 模型行
     * @return 首字延迟；未知为 0
     */
    private static long ttftOf(SysAccountChannelModel row) {
        Object value = metricValue(row, "ttft_ms");
        if (value == null) {
            value = metricValue(row, "latency_ms");
        }
        return value == null ? 0L : (long) parseDouble(value);
    }

    /**
     * 从模型行 metrics 里取值。
     *
     * @param row 模型行
     * @param key 指标键
     * @return 值；缺失返回 null
     */
    private static Object metricValue(SysAccountChannelModel row, String key) {
        if (row == null || row.getMetrics() == null) {
            return null;
        }
        return row.getMetrics().get(key);
    }

    /**
     * 宽松解析数字（兼容字符串形式的数字）。
     *
     * @param value 原始值
     * @return 数值；无法解析返回 0
     */
    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (Exception e) {
            return 0D;
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
     * @param model         模型标识（用于解析 BaseUrl）
     * @return 客户端配置
     */
    private ChatClientSetting buildSetting(SysAccountChannel provider, String apiKey,
                                            Map<String, String> extraHeaders,
                                            Map<String, RouteTarget> channelRoutes, String model) {
        return ChatClientSetting.builder()
                // 免登录对话渠道物化出来的 chat-<渠道> 行要去掉前缀再查 ChatClient SPI，
                // 否则 create("CHAT-DUCKDUCKGO") 找不到 DuckChatClient，导致模型永远解析不到客户端
                .provider(clientProviderOf(provider.getPlatform()))
                .appKey(apiKey)
                .baseUrl(resolveBaseUrl(provider, model))
                .extraHeaders(extraHeaders)
                .authScheme(provider.getAuthScheme())
                .authHeader(extraString(provider.getExtra(), "auth_header"))
                .authPrefix(extraString(provider.getExtra(), "auth_prefix"))
                .metricsUrl(extraString(provider.getExtra(), "metrics_url"))
                .proxy(provider.getProxy())
                .passthroughResponseHeaders(extraStringList(provider.getExtra(), "passthrough_response_headers"))
                .routes(channelRoutes != null ? channelRoutes
                        : ProviderRouteTargets.fromExtra(provider.getExtra()))
                .build();
    }

    /**
     * 把渠道上配置的限流冷却时长挂到冷却引擎。
     *
     * <p>每个渠道一套恢复节奏：被 429 / 额度耗尽的账号先排除
     * {@code rate_limit_base_ms}，同账号连续失败按 2 的幂次翻倍，
     * 封顶 {@code rate_limit_max_ms}；一次成功立即恢复参与轮换。
     * 两项都不配（或配成 0）时撤销该渠道的策略，回落到全局默认 60s / 10m。</p>
     *
     * <p>同时注册 platform 与 providerId 两个前缀：单供应商聚合用 platform 拼冷却键，
     * 路由组成员用 providerId 拼冷却键，取最长匹配，两者都能命中。</p>
     *
     * @param provider 供应商
     */
    private void registerCooldownPolicy(SysAccountChannel provider) {
        long base = extraLong(provider.getExtra(), "rate_limit_base_ms");
        long max = extraLong(provider.getExtra(), "rate_limit_max_ms");
        String backoffName = extraString(provider.getExtra(), "rate_limit_backoff");
        Backoff backoff = StringUtils.isBlank(backoffName) ? null : Backoff.of(backoffName);
        CooldownPolicy policy = new CooldownPolicy(base, max, backoff);
        CooldownPolicies.register(provider.getPlatform() + "|", policy);
        CooldownPolicies.register(provider.getProviderId() + "|", policy);
    }

    /**
     * 解析本次请求实际使用的上游地址。
     *
     * <p>供应商 {@code extra.endpoints} 可按协议单独覆盖地址（截图里的
     * 「端点 OpenAI / Responses / Anthropic」三行）：键为协议名
     * （openai / openai-response / gemini / anthropic），值为该协议的基础地址。
     * 命中则用协议地址，未命中回落到供应商的 base_url。</p>
     *
     * @param provider 供应商
     * @param model    裸模型名
     * @return 实际基础地址
     */
    private String resolveBaseUrl(SysAccountChannel provider, String model) {
        Map<String, Object> extra = provider.getExtra();
        Map<String, Object> endpoints = extraMap(extra, "endpoints");
        if (endpoints.isEmpty()) {
            return provider.getBaseUrl();
        }
        String protocol = protocolOf(provider.getProviderId(), model);
        Object url = protocol == null ? null : endpoints.get(protocol);
        if (url == null || String.valueOf(url).isBlank()) {
            return provider.getBaseUrl();
        }
        log.debug("[ModelClientResolver] 端点覆盖 provider={} model={} protocol={} url={}",
                provider.getProviderId(), model, protocol, url);
        return String.valueOf(url).trim();
    }

    /**
     * 读取模型行上的接入协议（能力表里的 {@code protocol}）。
     *
     * @param providerId 供应商 ID
     * @param model      裸模型名
     * @return 协议名；读不到返回 {@code null}
     */
    private String protocolOf(String providerId, String model) {
        try {
            SysAccountChannelModel row = channelModelService.find(providerId, model);
            if (row == null || row.getCapabilities() == null) {
                return null;
            }
            for (Map<String, Object> cap : row.getCapabilities()) {
                Object protocol = cap == null ? null : cap.get("protocol");
                if (protocol != null && !String.valueOf(protocol).isBlank()) {
                    return String.valueOf(protocol).trim();
                }
            }
        } catch (Exception e) {
            log.debug("[ModelClientResolver] 读取模型协议失败 {}:{} {}", providerId, model, e.getMessage());
        }
        return null;
    }

    /**
     * 把「透传请求头」名单里的入站头带到出站请求。
     *
     * <p>名单来自 {@code extra.passthrough_request_headers}（逗号分隔或数组）；
     * 只在请求线程里可用，后台线程取不到就是没有，不报错。</p>
     *
     * @param extra   扩展配置
     * @param headers 待补齐的出站头
     */
    private void mergePassthroughRequestHeaders(Map<String, Object> extra, Map<String, String> headers) {
        List<String> names = extraStringList(extra, "passthrough_request_headers");
        if (names.isEmpty()) {
            return;
        }
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return;
            }
            for (String name : names) {
                String value = attrs.getRequest().getHeader(name);
                if (value != null && !value.isBlank()) {
                    headers.putIfAbsent(name, value);
                }
            }
        } catch (Exception e) {
            log.debug("[ModelClientResolver] 透传请求头失败: {}", e.getMessage());
        }
    }

    /**
     * 读取 extra 中的整数字段。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 数值；缺失或非法返回 0
     */
    private static int extraInt(Map<String, Object> extra, String key) {
        return (int) extraLong(extra, key);
    }

    /**
     * 读取 extra 中的对象字段。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 对象内容；非对象返回空表
     */
    private static Map<String, Object> extraMap(Map<String, Object> extra, String key) {
        if (extra == null) {
            return Map.of();
        }
        Object value = extra.get(key);
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(map.size());
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    /**
     * 读取 extra 中的字符串清单（数组或逗号/换行分隔）。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 去空白后的清单
     */
    private static List<String> extraStringList(Map<String, Object> extra, String key) {
        if (extra == null) {
            return List.of();
        }
        Object value = extra.get(key);
        if (value == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(4);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addToken(out, item == null ? null : String.valueOf(item));
            }
            return out;
        }
        for (String token : String.valueOf(value).split("[,\\n]")) {
            addToken(out, token);
        }
        return out;
    }

    /**
     * 追加一个非空 token。
     *
     * @param target 目标清单
     * @param token  原始 token
     */
    private static void addToken(List<String> target, String token) {
        if (token == null) {
            return;
        }
        String trimmed = token.trim();
        if (!trimmed.isEmpty() && !target.contains(trimmed)) {
            target.add(trimmed);
        }
    }

    /**
     * 读取 extra 中的长整数值。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 数值；缺失或非数字返回 0
     */
    private static long extraLong(Map<String, Object> extra, String key) {
        String value = extraString(extra, key);
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[ModelClientResolver] 渠道配置 {} 不是合法数字: {}", key, value);
            return 0L;
        }
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
    private boolean isDown(SysAccountChannelConnection conn) {
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
    private List<String> collectAllKeys(SysAccountChannel provider, String ownerScope) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(ProviderCredentials.apiKeys(provider.getCredentials()));
        try {
            List<SysAccountChannelConnection> connections =
                    connectionService.listPoolForScope(provider.getProviderId(), ownerScope);
            for (SysAccountChannelConnection conn : connections) {
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
     * 收集动态渠道路由（免费渠道模型池）。
     *
     * <p>路由来自若干 {@link ChannelRouteSource}：账号渠道（OAuth 连接聚合）、免登录对话渠道
     * （DuckDuckGo / Trae）等主源先用先得，兜底源（magpie 网关、免登录渠道）只在空位上补，
     * 免得网关/渠道的内置模型名盖掉真实上游。缓存 5 分钟，按 ownerScope 隔离。</p>
     *
     * @param ownerScope 归属作用域
     * @return 渠道路由表；无可用渠道时返回空 Map
     */
    public Map<String, RouteTarget> collectChannelRoutes(String ownerScope) {
        return resolvedRoutes(ownerScope).routes();
    }

    /**
     * 动态渠道路由（含兜底源标记），按 ownerScope 缓存 5 分钟。
     *
     * @param ownerScope 归属作用域
     * @return 路由快照
     */
    private CachedRoutes resolvedRoutes(String ownerScope) {
        long now = System.currentTimeMillis();
        CachedRoutes cached = routeCache.get(ownerScope);
        if (cached != null && now - cached.builtAt() < CACHE_TTL_MILLIS) {
            return cached;
        }
        Map<String, RouteTarget> routes = new LinkedHashMap<>();
        Set<String> fallbackKeys = new LinkedHashSet<>();
        applyRouteSources(routes, fallbackKeys, ownerScope);
        CachedRoutes built = new CachedRoutes(routes, fallbackKeys, now);
        routeCache.put(ownerScope, built);
        return built;
    }

    /**
     * 按优先级把所有渠道路由源叠加到目标表。
     *
     * <p>{@link ChannelRouteSource#order()} 小的先叠加；{@link ChannelRouteSource#fallbackOnly()}
     * 的源用 {@code putIfAbsent}，不覆盖已存在的模型名。单个源失败只记日志，
     * 不影响其余渠道（账号模块未引入、网关不可达都不能拖垮整张路由表）。</p>
     *
     * @param target       目标路由表（原地写入）
     * @param fallbackKeys 兜底源命中过的模型名（原地写入，可为 {@code null}）
     * @param ownerScope   归属作用域
     */
    private void applyRouteSources(Map<String, RouteTarget> target, Set<String> fallbackKeys,
                                   String ownerScope) {
        for (ChannelRouteSource source : channelRouteSources()) {
            try {
                Map<String, RouteTarget> part = source.routes(ownerScope);
                if (part == null || part.isEmpty()) {
                    continue;
                }
                if (source.fallbackOnly()) {
                    for (Map.Entry<String, RouteTarget> entry : part.entrySet()) {
                        if (target.putIfAbsent(entry.getKey(), entry.getValue()) == null
                                && fallbackKeys != null) {
                            fallbackKeys.add(entry.getKey());
                        }
                    }
                } else {
                    target.putAll(part);
                }
            } catch (Exception | LinkageError e) {
                // LinkageError：可选模块（账号模块的 KeylessChatChannel 契约）在部署里版本不一致时
                // 类加载会失败。免费渠道不能因此整张 500 —— 少一个源，其余渠道照常路由。
                log.warn("[ModelClientResolver] 渠道路由源 {} 叠加失败: {}", source.name(), e.toString());
            }
        }
    }

    /**
     * 路由源列表（按 order 升序，启动后不变）。
     *
     * @return 路由源
     */
    private List<ChannelRouteSource> channelRouteSources() {
        if (routeSources == null) {
            synchronized (this) {
                if (routeSources == null) {
                    List<ChannelRouteSource> sorted = new ArrayList<>(routeSourceBeans);
                    sorted.sort(java.util.Comparator.comparingInt(ChannelRouteSource::order));
                    routeSources = List.copyOf(sorted);
                }
            }
        }
        return routeSources;
    }

    /**
     * 合并静态路由（extra.routes）与动态渠道路由。
     *
     * <p>免费渠道供应商（{@code free-channel}）的路由由两部分组成：</p>
     * <ul>
     *   <li>静态路由：管理员在供应商 extra 中手写的 {@code routes} 配置</li>
     *   <li>动态路由：由各 {@link ChannelRouteSource} 自动收集（账号渠道 / 免登录渠道 / 网关兜底）</li>
     * </ul>
     *
     * <p>主源（有 baseUrl 与账号的真实上游）覆盖静态路由；兜底源（magpie 网关、免登录渠道）
     * 只在空位上补，不覆盖静态路由。</p>
     *
     * @param provider   供应商
     * @param ownerScope 归属作用域
     * @return 合并后的路由表
     */
    public Map<String, RouteTarget> mergedRoutes(SysAccountChannel provider, String ownerScope) {
        Map<String, RouteTarget> merged = new LinkedHashMap<>();
        Map<String, RouteTarget> staticRoutes = ProviderRouteTargets.fromExtra(provider.getExtra());
        if (staticRoutes != null) {
            merged.putAll(staticRoutes);
        }
        CachedRoutes dynamic = resolvedRoutes(ownerScope);
        for (Map.Entry<String, RouteTarget> entry : dynamic.routes().entrySet()) {
            if (dynamic.fallbackKeys().contains(entry.getKey())) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 清空渠道路由缓存（magpie provider 增删改后调用，让新模型立即生效）。
     */
    public void invalidateRouteCache() {
        routeCache.clear();
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
     * （见 {@code sys_account_channel.sys_account_channel_provider_id}）。前缀不存在时
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
     * @param routes       路由表
     * @param fallbackKeys 由兜底源贡献的模型名（合并静态路由时不得覆盖）
     * @param builtAt      构建时间戳
     */
    private record CachedRoutes(Map<String, RouteTarget> routes, Set<String> fallbackKeys,
                                long builtAt) {
    }
}

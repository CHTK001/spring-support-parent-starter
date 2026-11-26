// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.aggregate.RouterGroupChatClient;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.event.ChannelHealthProbeEvent;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
    /** 账号模块拥有的客户端装配契约。 */
    private final com.chua.starter.account.channel.service.AccountChannelChatClientService channelClients;

    /**
     * 渠道模型清单业务（账号模块的查询接口，含缓存）
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 客户端缓存：key = providerId::model
     */
    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();

    /**
     * 渠道路由缓存：key = ownerScope
     */
    private final Map<String, CachedRoutes> routeCache = new ConcurrentHashMap<>();

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
     * 为持有会话状态的 Agent 创建独立客户端，避免共享缓存实例的提示词、历史与工具互相覆盖。
     *
     * @param qualifiedModel 渠道限定模型名
     * @return 独立客户端，不可用时返回 null
     */
    public ChatClient resolveFresh(String qualifiedModel) {
        ParsedModel parsed = parse(qualifiedModel);
        if (parsed == null) {
            return null;
        }
        // Agent 的渠道是明确绑定，停用或模型下线时拒绝，不能回退到同名模型的其他渠道。
        if (parsed.providerId() == null) return null;
        channelService.requireAvailableModel(parsed.providerId(), parsed.model());
        SysAccountChannel provider = channelService.find(parsed.providerId());
        return provider == null ? null : build(provider, parsed.model(), ownerScopeOf(provider));
    }

    /**
     * 管理表单的绑定校验只查目录，不创建客户端或触发上游连接。
     * @param providerId 渠道标识
     * @param model 模型标识
     */
    public void validateBinding(String providerId, String model) {
        channelService.requireAvailableModel(providerId, model);
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
                // 前缀不是已注册供应商：冒号可能是模型名本身的一部分
                //（如 OpenRouter inclusionai/ling-3.2-flash:free），用全名反查
                String qualifiedModel = parsed.providerId() + ":" + parsed.model();
                List<SysAccountChannelModel> qualifiedCandidates =
                        channelModelService.listEnabledByModel(qualifiedModel);
                if (!qualifiedCandidates.isEmpty()) {
                    if (qualifiedCandidates.size() > 1) {
                        log.warn("[ModelClientResolver] 模型 {} 在 {} 个供应商下同名（{}），未指定供应商时取第一家；"
                                        + "若这不符合预期，请让调用方传 <providerId>:<model> 限定名",
                                qualifiedModel, qualifiedCandidates.size(),
                                qualifiedCandidates.stream().map(SysAccountChannelModel::getProviderId).toList());
                    }
                    for (SysAccountChannelModel pm : qualifiedCandidates) {
                        SysAccountChannel p = channelService.find(pm.getProviderId());
                        if (p != null && Boolean.TRUE.equals(p.getEnabled())) {
                            return p;
                        }
                    }
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
     * <p>客户端构造与账号轮换由账号模块负责：Key 的失败/成功必须
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
        return channelClients.create(provider.getProviderId(), model, ownerScope,
                ProviderRouteTargets.isFreeChannel(provider.getPlatform()) ? mergedRoutes(provider, ownerScope) : null);
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

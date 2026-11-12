// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.chat.MagpieRouteSource;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.ChannelTaskProvider;
import com.chua.starter.common.support.channel.ChannelUpstreamAdmin;
import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.common.support.text.json.Json;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 账号渠道鉴权管理控制器。
 *
 * <p>对接前端「渠道管理」页（adminOnly）。按渠道标识路由到
 * {@link AccountChannelAuthManager} SPI 实现，完成账号绑定 / 轮询 / 刷新 / 签到 /
 * 模型拉取 / 单模型开关六类动作；账号凭证以不透明 JSON 形态持久化到
 * {@code sys_ai_provider_connection.credentials} 列（{@code JacksonTypeHandler}）。</p>
 *
 * <p>所有渠道的 provider 行以 {@code auth_scheme='oauth'} 标识；渠道与 provider 行的
 * 对应关系通过 {@code providerId} = {@code channel:instance}（实例段唯一且稳定）
 * 生成，同一渠道可挂多行，互不影响。</p>
 *
 * <p>写库操作（绑定 / 刷新 / 开关 / 删除）走 {@link Transactional}，
 * 上游调用失败时不产生半成品行。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
@Slf4j
public class ApiChannelAuthController {

    /**
     * 渠道类 provider 行的 auth_scheme 值（区别于 api_key 渠道）
     */
    private static final String OAUTH_SCHEME = "oauth";

    /**
     * 主账号连接的 role 值。
     *
     * <p>缺 label 的接口（status / refresh / checkin / list-models / 路由取用）都会
     * 退化到 {@code role=default} 那条连接，因此「谁是主账号」是本字段而不是 label。</p>
     */
    private static final String DEFAULT_ROLE = "default";

    /**
     * 非主账号连接的 role 值。
     *
     * <p>历史数据里 role 一律是 {@code default}（同一渠道多个账号都叫 default），
     * 只能靠 label 区分；新逻辑把「非主」显式标成 backup，让主账号始终唯一、可预测。</p>
     */
    private static final String BACKUP_ROLE = "backup";

    private final SysAiProviderMapper providerMapper;
    private final SysAiProviderConnectionMapper connectionMapper;
    private final SysAiProviderModelMapper modelMapper;

    /**
     * Magpie 网关路由源：上游 provider 增删改后立即失效路由缓存
     */
    private final MagpieRouteSource magpieRouteSource;

    /**
     * 模型客户端解析器：同上，清掉 5 分钟的 {@code routeCache}
     */
    private final ModelClientResolver modelClientResolver;

    /**
     * 编程式事务：SSE 流程跑在独立线程池里，{@code @Transactional} 不生效，
     * 而 {@link #persistCredentials} 要连写 connection + provider 两行，
     * 中途失败会留下「有账号但渠道未启用」的半成品状态。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 全部已注册的渠道鉴权实现（按 {@code @Spi} 别名为 key）。
     * 缓存于启动期一次性加载，运行期渠道注册不变。
     */
    private volatile Map<String, AccountChannelAuthManager> channelManagers;

    /**
     * 积分查询实现（按 {@code @Spi} 别名为 key，懒加载）
     */
    private volatile Map<String, ChannelCreditProvider> creditProviders;

    /**
     * 任务实现（按 {@code @Spi} 别名为 key，懒加载）
     */
    private volatile Map<String, ChannelTaskProvider> taskProviders;

    /**
     * 网关上游管理实现（按 {@code @Spi} 别名为 key，懒加载）
     */
    private volatile Map<String, ChannelUpstreamAdmin> upstreamAdmins;

    /**
     * 网关内部的上游 provider 适配实现（按 {@code @Spi} 别名为 key，懒加载）。
     *
     * <p>渠道级 SPI 只回答「这个网关怎么连」，provider 级差异（模型字段、账号标识）
     * 由 {@link ChannelUpstreamProvider} 处理；取不到实现时按上游字段已是标准形态处理。</p>
     */
    private volatile Map<String, ChannelUpstreamProvider> upstreamProviders;

    /**
     * 网关托管索引缓存：网关连接 → 它当前的上游 provider id 集合。
     *
     * <p><b>为什么要缓存</b>：积分 / 签到 / 渠道摘要都要回答「这个渠道是不是由网关承载」，
     * 每次回答都得读一遍网关管理面。30 秒内的重复提问直接复用，避免渠道页刷新时
     * 把管理面打满；上游增删改后用 {@link #GATEWAY_INDEX_TTL_MS} 内自然过期收敛。</p>
     */
    private final Map<String, GatewayIndex> gatewayIndexes = new ConcurrentHashMap<>();

    /**
     * 网关托管索引的有效期（毫秒）。
     */
    private static final long GATEWAY_INDEX_TTL_MS = 30_000L;

    /**
     * 网关上游索引快照。
     */
    private static final class GatewayIndex {

        /**
         * 采集时刻（毫秒）。
         */
        private final long at;

        /**
         * 上游 provider id（小写，便于大小写不敏感比较）。
         */
        private final Set<String> providerIds;

        /**
         * 构造快照。
         *
         * @param at          采集时刻
         * @param providerIds provider id 集合
         */
        private GatewayIndex(long at, Set<String> providerIds) {
            this.at = at;
            this.providerIds = providerIds;
        }
    }

    /**
     * 网关托管结果：某业务渠道实际由哪个网关的哪个上游承载。
     */
    private static final class GatewayOwner {

        /**
         * 网关的上游管理实现。
         */
        private final ChannelUpstreamAdmin admin;

        /**
         * 网关连接凭据（积分 / 签到都用它）。
         */
        private final Map<String, Object> credentials;

        /**
         * 命中的上游 provider id。
         */
        private final String providerId;

        /**
         * 构造结果。
         *
         * @param admin       上游管理实现
         * @param credentials 网关连接凭据
         * @param providerId  命中的 provider id
         */
        private GatewayOwner(ChannelUpstreamAdmin admin, Map<String, Object> credentials, String providerId) {
            this.admin = admin;
            this.credentials = credentials;
            this.providerId = providerId;
        }
    }

    /**
     * SSE 登录流的执行器（守护线程，按需创建，空闲回收）。
     *
     * <p>⚠️ 不用 Spring 的公共 task executor：单次 {@code pollLogin} 可能阻塞在
     * 上游 HTTP 上（最长 15s 连接超时），放在公共池里会挤占其它异步任务。</p>
     */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger SEQ = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
// [P3C C1 豁免] 场景化创建线程，需文档化
            Thread t = new Thread(r, "channel-sse-login-" + SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 懒加载渠道实现映射（幂等）。
     *
     * @return channel → 实现
     */
    private Map<String, AccountChannelAuthManager> managers() {
        Map<String, AccountChannelAuthManager> local = channelManagers;
        if (local == null) {
            synchronized (this) {
                local = channelManagers;
                if (local == null) {
                    local = ServiceProvider.of(AccountChannelAuthManager.class).list();
                    channelManagers = local;
                }
            }
        }
        return local;
    }

    /**
     * 列出全部已注册的渠道（含未绑定账号的）。
     *
     * <p>前端渠道管理页按本接口渲染渠道卡片。每个渠道挂的账号数量、绑定状态
     * 通过 {@code connections} 子集合呈现。</p>
     *
     * @return 渠道列表，每项 { channel, displayName, loginMethod, kind（AI/SMS/EMAIL）、
     *         accountTypes（FIXED/TEMPORARY）、supportsCheckin、
     *         providerId（当前绑定的 provider 行 ID，未绑定为 null）、connections }
     */
    @GetMapping("")
    public ApiResult<List<Map<String, Object>>> listChannels() {
        // 账号数据按 userId 隔离：所有登录用户可见自己的渠道与绑定账号
        String owner = ownerId();
        Map<String, AccountChannelAuthManager> managers = managers();
        List<Map<String, Object>> items = new ArrayList<>(managers.size());
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        // ⚠️ 去重的键必须取实现自己的 channel()，不能用注册表的键：
        // SPI 框架会给每个实现额外登记一条「类简名」definition
        // （QoderCnAccountChannelAuthManager → QODERCN），与 @Spi 别名（QODER-CN）
        // 指向同一个实例，用注册表键去重会让国内变体出现两张卡片。
        for (Map.Entry<String, AccountChannelAuthManager> entry : managers.entrySet()) {
            AccountChannelAuthManager impl = entry.getValue();
            if (impl == null || impl.channel() == null || !seen.add(impl.channel())) {
                continue;
            }
            String channel = impl.channel();
            // 列表接口只按规范 id 查：路由本身大小写不敏感，不需要为别名
            //（QODERCN / QODER-CN）额外物化 provider 行。
            SysAiProvider provider = providerMapper.selectById(providerIdOf(impl));
            if (provider == null) {
                provider = requireChannelProvider(channel);
            }
            items.add(buildChannelSummary(channel, impl, provider, owner));
        }
        return ApiResult.ok(items);
    }

    /**
     * 单渠道的摘要（含该渠道当前绑定的 provider 行与其全部账号连接）。
     *
     * @param channel 渠道标识
     * @param impl 渠道实现
     * @param provider 该渠道对应的 provider 行
     * @return 摘要
     */
    private Map<String, Object> buildChannelSummary(String channel,
            AccountChannelAuthManager impl, SysAiProvider provider, String owner) {
        Map<String, Object> summary = new LinkedHashMap<>(14);
        summary.put("channel", channel);
        summary.put("displayName", impl.displayName());
        summary.put("loginMethod", impl.loginMethod());
        // 类型化维度：本接口只承载 AI 账号渠道，种类恒为 AI；
        // 账号类型由 SPI 以字符串返回，避免 AI 依赖账号模块的枚举定义。
        summary.put("kind", "AI");
        summary.put("accountTypes", impl.accountTypes().stream().toList());
        summary.put("supportsCheckin", impl.supportsCheckin());
        // magpie 有自己的每日签到定时器，自动签到任务不应重复跑
        summary.put("supportsAutoCheckin", impl.supportsAutoCheckin());
        // 网关型渠道（magpie）：上游 / 插件 / 登录 / 额度走管理面
        ChannelUpstreamAdmin upstream = upstreamAdminOf(channel);
        summary.put("supportsUpstream", upstream != null);
        // 插件与上游登录是网关各自的能力，不能按渠道名硬编码
        summary.put("supportsPlugins", upstream != null && upstream.supportsPlugins());
        summary.put("supportsSignin", upstream != null && upstream.supportsSignin());
        // 网关自己声明「承载了哪些业务渠道、各对应哪些上游 provider id」。左栏角标与
        // 「待接入」判定都读它：前端不再维护第二份对照表，网关改 provider 命名时也不会
        // 静默错位（详见 ChannelUpstreamAdmin#ownedChannels）。
        if (upstream != null) {
            summary.put("ownedChannels", upstream.ownedChannels());
        }
        // 能力标签：前端据此在渠道卡片右侧显示「积分 / 任务」入口。
        // 用独立 SPI 探测而不是问 AccountChannelAuthManager：签到与「看余额 / 做任务」
        // 是三件事，很多渠道只有其一（Cline 只有余额、Raccoon 只有自动发放）。
        // 网关承载的渠道本地通常没有自己的积分实现，但 /credit 是网关优先的
        // （见 gatewayCredit）：只看本地实现会把 Loomy 这类纯托管渠道的积分区
        // 整块藏掉，查到的余额永远显示不出来。判据与 gatewayCredit 保持同一套。
        GatewayOwner gateway = gatewayOwnerOf(channel);
        summary.put("supportsCredit", creditProviderOf(channel) != null
                || (gateway != null && creditProviderOf(gateway.admin.channel()) != null));
        summary.put("supportsTask", taskProviderOf(channel) != null);
        summary.put("providerId", provider == null ? null : provider.getProviderId());
        summary.put("enabled", provider == null ? false : Boolean.TRUE.equals(provider.getEnabled()));
        // 进入优先级：同 kind 多渠道并存时，前端可据此提示「默认数据源」
        summary.put("priority", impl.priority());
        summary.put("chatClient", false);

        // 仅当前用户自己的连接（管理员额外可见历史 NULL 系统池）
        LambdaQueryWrapper<SysAiProviderConnection> connectionWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId,
                                provider == null ? null : provider.getProviderId());
        applyOwnerFilter(connectionWrapper, owner);
        List<SysAiProviderConnection> connections = connectionMapper.selectList(connectionWrapper);
        List<Map<String, Object>> conns = new ArrayList<>(
                connections == null ? 0 : connections.size());
        if (connections != null) {
            for (SysAiProviderConnection c : connections) {
                Map<String, Object> cm = new LinkedHashMap<>(8);
                cm.put("connectionId", c.getConnectionId());
                cm.put("label", c.getLabel());
                cm.put("role", c.getRole());
                // 主账号标记：缺 label 的接口（status / list-models / refresh / checkin）
                // 都落在 role=default 那条上，前端必须能看出「默认是谁」。
                cm.put("primary", DEFAULT_ROLE.equals(c.getRole()));
                cm.put("hasCredentials", c.getCredentials() != null
                        && !c.getCredentials().isEmpty());
                cm.put("updatedAt", c.getUpdatedAt());
                cm.put("createdAt", c.getCreatedAt());
                cm.put("status", implStatusOf(c, impl));
                cm.put("expiresAt", expiresAtOf(c));
                cm.put("channel", channelOf(c, channel));
                putCheckinInfo(cm, c);
                conns.add(cm);
            }
        }
        summary.put("connections", conns);

        // 渠道级模型开关：sys_ai_provider_model 中本渠道 provider 行下的全部启用模型
        List<SysAiProviderModel> models = provider == null
                ? new ArrayList<>(0)
                : modelMapper.selectList(new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, provider.getProviderId()));
        List<Map<String, Object>> modelItems = new ArrayList<>(
                models == null ? 0 : models.size());
        if (models != null) {
            for (SysAiProviderModel m : models) {
                Map<String, Object> mm = new LinkedHashMap<>(8);
                mm.put("model", m.getModel());
                mm.put("enabled", Boolean.TRUE.equals(m.getEnabled()));
                mm.put("alias", m.getAlias());
                mm.put("contextWindow", m.getContextWindow());
                putModelMetrics(mm, m.getMetrics());
                modelItems.add(mm);
            }
        }
        summary.put("models", modelItems);
        return summary;
    }

    /**
     * 网关托管时，从网关上游 provider 行里取该渠道的模型清单。
     *
     * <p>网关里的 provider 行自带 {@code models}（网关侧维护的模型目录），
     * 它比「再调一次上游 /v1/models」更贴近用户在网关里配的那份；
     * 字段差异交给 {@link ChannelUpstreamProvider} 归一，控制器只拿标准键。</p>
     *
     * @param owner 网关承载结果
     * @return 模型清单（{@code id/name} + 可选 {@code contextWindow/free/priceInput/priceOutput/rate}）
     */
    private List<Map<String, Object>> gatewayModels(GatewayOwner owner) {
        if (owner == null) {
            return new ArrayList<>(0);
        }
        Map<String, Object> listed;
        try {
            listed = owner.admin.list(owner.credentials);
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "读取网关模型失败: " + e.getMessage(), e);
        }
        Object rawProviders = listed == null ? null : listed.get("providers");
        if (!(rawProviders instanceof List<?> providers)) {
            return new ArrayList<>(0);
        }
        Map<String, Object> provider = null;
        for (Object item : providers) {
            if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                continue;
            }
            if (!owner.providerId.equalsIgnoreCase(String.valueOf(map.get("id")))) {
                continue;
            }
            provider = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    provider.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            break;
        }
        Object rawModels = provider == null ? null : provider.get("models");
        if (!(rawModels instanceof List<?> models)) {
            return new ArrayList<>(0);
        }
        ChannelUpstreamProvider adapter = upstreamProviderOf(owner.admin.channel(), owner.providerId);
        List<Map<String, Object>> out = new ArrayList<>(models.size());
        for (Object item : models) {
            Map<String, Object> source = new LinkedHashMap<>(6);
            String id;
            String name;
            if (item instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        source.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                id = asString(source.get("id"));
                name = asString(source.get("name"));
            } else if (item != null && !String.valueOf(item).isBlank()) {
                id = String.valueOf(item).trim();
                name = id;
                source.put("id", id);
            } else {
                continue;
            }
            if (id == null || id.isBlank()) {
                id = name;
            }
            if (id == null || id.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(8);
            row.put("id", id);
            row.put("name", name == null || name.isBlank() ? id : name);
            // 适配实现只补确实读到的键（价格 / 免费 / 倍率 / 上下文），缺值不补。
            if (adapter != null) {
                row.putAll(adapter.modelMeta(source));
            }
            out.add(row);
        }
        return out;
    }

    /**
     * 把上游模型元数据翻译成 {@code sys_ai_provider_model.metrics} 的键。
     *
     * <p>metrics 的键沿用指标库口径（{@code price_input_per_musd} 等），
     * 这样渠道页与模型管理页读的是同一份数据；上游没给的项不写键。</p>
     *
     * @param meta 上游模型元数据（{@code priceInput/priceOutput/rate/free}）
     * @return metrics JSON（可为空 Map）
     */
    private static Map<String, Object> modelMetrics(Map<String, Object> meta) {
        Map<String, Object> metrics = new LinkedHashMap<>(6);
        if (meta == null || meta.isEmpty()) {
            return metrics;
        }
        Double priceInput = metricDouble(meta, "priceInput", "price_input_per_musd");
        Double priceOutput = metricDouble(meta, "priceOutput", "price_output_per_musd");
        if (priceInput != null) {
            metrics.put("price_input_per_musd", priceInput);
        }
        if (priceOutput != null) {
            metrics.put("price_output_per_musd", priceOutput);
        }
        Double rate = metricDouble(meta, "rate", "ratio", "multiplier");
        if (rate != null) {
            metrics.put("rate", rate);
        }
        Object free = meta.get("free");
        if (free instanceof Boolean value) {
            metrics.put("free", value);
        } else if (priceInput != null && priceOutput != null
                && priceInput == 0D && priceOutput == 0D) {
            metrics.put("free", Boolean.TRUE);
        }
        return metrics;
    }

    /**
     * 把模型行 metrics 里的价格 / 免费 / 倍率带进渠道摘要。
     *
     * <p>渠道页的模型胶囊此前只拿到 {@code model/enabled/alias/contextWindow}，
     * 所以前端写的 {@code m.free} / {@code m.rate} 永远是 undefined。数据本来就在
     * {@code sys_ai_provider_model.metrics}（模型管理页已在用），这里只做透出。</p>
     *
     * <p>缺值不补：metrics 里没有价格就不放价格键，避免「未知」被显示成 0 或「免费」。</p>
     *
     * @param target  渠道摘要里的模型 Map
     * @param metrics 模型行的 metrics JSON（可空）
     */
    private static void putModelMetrics(Map<String, Object> target, Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        Double priceInput = metricDouble(metrics, "price_input_per_musd", "priceInput");
        Double priceOutput = metricDouble(metrics, "price_output_per_musd", "priceOutput");
        Boolean free = metricBoolean(metrics, "free", "is_free", "isFree");
        if (free == null && priceInput != null && priceOutput != null
                && priceInput == 0D && priceOutput == 0D) {
            free = Boolean.TRUE;
        }
        if (free != null) {
            target.put("free", free);
        }
        if (priceInput != null) {
            target.put("priceInput", priceInput);
        }
        if (priceOutput != null) {
            target.put("priceOutput", priceOutput);
        }
        Double rate = metricDouble(metrics, "rate", "ratio", "multiplier");
        if (rate != null) {
            target.put("rate", rate);
        }
    }

    /**
     * 从 metrics 中取第一个可解析的数值。
     *
     * @param metrics 指标 Map
     * @param keys    候选键
     * @return 数值；没有或不可解析返回 {@code null}
     */
    private static Double metricDouble(Map<String, Object> metrics, String... keys) {
        for (String key : keys) {
            Object value = metrics.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Double.parseDouble(text.trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // 换下一个候选键
                }
            }
        }
        return null;
    }

    /**
     * 从 metrics 中取第一个可判断的布尔值。
     *
     * @param metrics 指标 Map
     * @param keys    候选键
     * @return 布尔值；没有或无法判断返回 {@code null}
     */
    private static Boolean metricBoolean(Map<String, Object> metrics, String... keys) {
        for (String key : keys) {
            Object value = metrics.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    /**
     * 渠道在 {@code sys_ai_provider} 中的行主键。
     *
     * <p>取实现的 {@code channel()} 再转大写：与历史上由 SPI 注册表 key
     * （{@code @Spi} 别名的大写形式）建出来的既有行保持一致，
     * 也避免 {@code QODERCN} 这类「只差连字符」的重复行。</p>
     *
     * @param impl 渠道实现
     * @return 规范 providerId
     */
    private static String providerIdOf(AccountChannelAuthManager impl) {
        String id = impl.channel();
        return id == null ? null : id.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 渠道级 provider 行：渠道管理统一以「渠道标识的<u>大写形式</u>」作 providerId，
     * 即一个渠道在 {@code sys_ai_provider} 中至多一行，账号全部以 connection 挂在其下。
     *
     * <p><b>为什么是大写：</b>SPI 注册表会给每个实现额外登记一条「类简名」definition
     * （{@code QoderCnAccountChannelAuthManager} → {@code QODERCN}），而
     * {@code /api/channels} 列表里的 <b>key</b> 正是这些 definition 键，首个命中即
     * {@code @Spi} 别名的大写形式。历史上这类行就是按注册表 key 建的
     * （{@code MINIMAX} / {@code QODER-CN} / {@code GEMINI} …），模型行也挂在同一个键下。
     * 这里必须沿用同一口径，否则查模型、查账号都会落空；
     * 而 {@code QODERCN} 这种「只差连字符」的重复行不应再产生。</p>
     *
     * <p>缺失时惰性物化一行（platform=大写标识、auth_scheme=oauth、base_url 空占位），
     * 避免读接口因未绑定而 404，让「未绑定账号」与「无此渠道」两个状态可区分。</p>
     *
     * @param channel 渠道标识（大小写不敏感）
     * @return provider 行（必然存在）
     */
    @Transactional
    private SysAiProvider requireChannelProvider(String channel) {
        AccountChannelAuthManager impl = resolveManager(managers(), channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的渠道: " + channel);
        }
        String providerId = providerIdOf(impl);
        SysAiProvider existing = providerMapper.selectById(providerId);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        SysAiProvider p = new SysAiProvider();
        p.setProviderId(providerId);
        p.setPlatform(providerId);
        p.setName(impl.displayName());
        p.setBaseUrl(""); // 渠道类 provider 的 base_url 由鉴权流程自行处理，落空占位
        p.setAuthScheme(OAUTH_SCHEME);
        p.setEnabled(true);
        p.setSortOrder(providerMapper.selectCount(null).intValue());
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        providerMapper.insert(p);
        log.info("[ChannelAuth] 惰性物化渠道 provider 行: {}", providerId);
        return p;
    }

    /**
     * 取承载「渠道模型配置」的 provider 行主键。
     *
     * <p>AI 账号渠道沿用 {@code channel()} 的大写形式（见
     * {@link #requireChannelProvider(String)}）；免登录对话 / 文生图渠道不在
     * {@code AccountChannelAuthManager} 注册表里，但它们同样需要在页面里逐项
     * 配置模型启用状态，因此仍用「渠道标识大写」作为 providerId 并惰性建行。
     * 两者都落在 {@code sys_ai_provider_model}，读写只有一份实现。</p>
     *
     * @param channel 渠道标识
     * @return providerId（必然存在对应的 provider 行）
     */
    @Transactional
    private String ensureChannelProviderId(String channel) {
        AccountChannelAuthManager impl = resolveManager(managers(), channel);
        if (impl != null) {
            return requireChannelProvider(channel).getProviderId();
        }
        if (channel == null || channel.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "渠道标识不能为空");
        }
        String providerId = channel.toUpperCase(java.util.Locale.ROOT);
        SysAiProvider existing = providerMapper.selectById(providerId);
        if (existing != null) {
            return providerId;
        }
        long now = System.currentTimeMillis();
        SysAiProvider p = new SysAiProvider();
        p.setProviderId(providerId);
        p.setPlatform(providerId);
        p.setName(channel);
        // 内置模型渠道没有上游地址：base_url 落空占位，模型清单由客户端提供
        p.setBaseUrl("");
        p.setAuthScheme(OAUTH_SCHEME);
        p.setEnabled(true);
        p.setSortOrder(providerMapper.selectCount(null).intValue());
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        providerMapper.insert(p);
        log.info("[ChannelAuth] 惰性物化内置模型渠道 provider 行: {}", providerId);
        return providerId;
    }

    /**
     * 发起渠道账号绑定。
     *
     * <p>调用 SPI {@link AccountChannelAuthManager#startLogin()}，
     * 返回前端轮询句柄（含 {@code handle} / {@code verifyUrl}）。
     * 不直接落库 —— 落库在 {@link #pollChannel} 成功之后完成。</p>
     *
     * @param channel 渠道标识（大小写不敏感；响应一律回传实现的规范标识）
     * @return 登录句柄
     */
    @PostMapping("/{channel}/start-login")
    public ApiResult<Map<String, Object>> startLogin(@PathVariable("channel") String channel) {
        ownerId();
        AccountChannelAuthManager impl = requireManager(channel);
        channel = impl.channel();
        Map<String, Object> handle;
        try {
            handle = impl.startLogin();
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] start-login 上游异常 channel={} cause={}",
                    channel, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 start-login 失败: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("channel", channel);
        out.put("handle", handle);
        return ApiResult.ok(out);
    }

    /**
     * 轮询登录进度，成功时把凭据落 {@code sys_ai_provider_connection}。
     *
     * <p>请求体：{@code { handle: Map, label?: String }}；
     * {@code handle} 为 {@link #startLogin} 返回的句柄 Map。成功时新建一条 connection
     * （或更新既有 label 匹配的 connection），并回写 provider 行 {@code enabled=true}。
     * 上游失败抛 {@link ApiErrorCode#UPSTREAM_ERROR}，不产生半成品行。</p>
     *
     * @param channel 渠道标识
     * @param body 请求体（含 handle）
     * @return { ok: 布尔、credentials 是否已写入 }
     */
    @PostMapping("/{channel}/poll")
    @Transactional
    public ApiResult<Map<String, Object>> pollChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        String owner = ownerId();
        AccountChannelAuthManager impl = requireManager(channel);
        Object rawHandle = body == null ? null : body.get("handle");
        Map<String, Object> handle = toHandleMap(rawHandle);
        String label = body == null ? null : asString(body.get("label"));
        if (label == null || label.isBlank()) {
            label = autoLabelOf(impl.channel(), owner);
        }

        Map<String, Object> credentials;
        try {
            credentials = impl.pollLogin(handle);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] poll 上游异常 channel={} cause={}", channel, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 poll 失败: " + e.getMessage(), e);
        }
        if (credentials == null) {
            // 仍在等待用户确认（设备码未扫码等）。
            // ⚠️ 回传 SPI 可能变异过的句柄（如 Loomy 发码后写回的 msgid/smsSent、
            // Zcode 写回的 __lastPollAt）——前端据此合并回本地暂存，
            // 否则两次 poll 之间服务端写入的状态会丢失。
            return ApiResult.ok(pendingResult(handle));
        }

        return ApiResult.ok(persistCredentials(channel, credentials, label, owner));
    }

    /**
     * 轮询的 pending 响应体（含回显句柄）。
     *
     * @param handle 可能被 SPI 变异过的句柄
     * @return { ok:false, pending:true, handle }
     */
    private Map<String, Object> pendingResult(Map<String, Object> handle) {
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", false);
        out.put("pending", true);
        out.put("handle", handle);
        return out;
    }

    /**
     * 把轮询/SSE 拿到的凭据落库（provider 行 + connection 行），并启用渠道。
     *
     * <p>从 {@code pollChannel} 抽出——SSE 端点复用同一逻辑，避免两处漂移。</p>
     *
     * @param channel     渠道标识
     * @param credentials SPI 返回的可持久化凭据
     * @param label       账号标签
     * @return { ok:true, connectionId, channel, label }
     */
    private Map<String, Object> persistCredentials(String channel,
            Map<String, Object> credentials, String label, String owner) {
        // 包进编程式事务：本方法既会被 pollChannel（已有 @Transactional）调用，
        // 也会被 SSE 线程调用（无事务）。@Transactional 自调用不生效，
        // 用 TransactionTemplate 才能保证两条写操作原子。
        return transactionTemplate.execute(status -> doPersistCredentials(channel, credentials, label, owner));
    }

    /**
     * {@link #persistCredentials} 的实际写入体，必须在事务内调用。
     *
     * @param channel     渠道标识
     * @param credentials SPI 返回的可持久化凭据
     * @param label       账号标签
     * @param owner       归属用户（账号隔离）
     * @return { ok:true, connectionId, channel, label }
     */
    private Map<String, Object> doPersistCredentials(String channel,
            Map<String, Object> credentials, String label, String owner) {
        SysAiProvider provider = requireChannelProvider(channel);
        SysAiProviderConnection conn = findOrCreateConnection(provider.getProviderId(), label, owner);
        Map<String, Object> merged = new LinkedHashMap<>(credentials);
        merged.put("channel", channel);
        conn.setCredentials(merged);
        // 角色由 findOrCreateConnection 在新建时定好（首个账号 default，其余 backup），
        // 这里不能再无条件覆盖成 default：同渠道两个 default 会让 findConnection 的
        // selectOne 抛 TooManyResultsException，也会把主账号悄悄改道。
        conn.setAuthScheme(OAUTH_SCHEME);
        conn.setLabel(label);
        conn.setUserId(owner);
        conn.setUpdatedAt(System.currentTimeMillis());
        connectionMapper.updateById(conn);

        provider.setEnabled(true);
        provider.setUpdatedAt(System.currentTimeMillis());
        providerMapper.updateById(provider);

        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("connectionId", conn.getConnectionId());
        out.put("channel", channel);
        out.put("label", label);
        return out;
    }

    /**
     * 账号绑定的 <b>SSE 轮询流</b>（替代前端定时轮询）。
     *
     * <p>前端把 {@code startLogin} 拿到的句柄推给本端点，后端按固定间隔调
     * SPI {@link AccountChannelAuthManager#pollLogin(Map)}，并逐条推送事件：</p>
     * <ul>
     *   <li>{@code pending}：仍在等待（附回显句柄，前端合并回本地暂存）；</li>
     *   <li>{@code done}：拿到凭据并已落库（附 connectionId）；</li>
     *   <li>{@code failed}：上游异常或超时（附 message）。</li>
     * </ul>
     * <p>任一终态后立即关流。整条流的存活上限由 {@code timeoutSec} 控制
     * （默认 180s），到点推 {@code failed(timeout)} 后关流。</p>
     *
     * <p>⚠️ 该端点<b>不加 {@code @Transactional}</b>：SSE 是长连接，
     * 事务会跨越整个轮询周期占住连接；落库在 {@link #persistCredentials}
     * 内部各自短事务完成。</p>
     *
     * @param channel    渠道标识
     * @param body       { handle, label, intervalMs? }
     * @param timeoutSec 流存活上限（秒）
     * @return SSE 流（text/event-stream）
     */
    @PostMapping(value = "/{channel}/sse-login", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseLogin(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body,
            @RequestParam(value = "timeoutSec", defaultValue = "180") long timeoutSec) {
        AccountChannelAuthManager impl = requireManager(channel);
        // 身份必须在请求线程内解析：SSE 工作线程没有请求上下文，
        // 提前捕获归属用户，供落库时做账号隔离。
        String owner = ownerId();
        Object rawHandle = body == null ? null : body.get("handle");
        // 逐轮轮询会在 SPI 内变异句柄（Loomy 的 msgid/smsSent、Zcode 的 __lastPollAt），
        // 故用可变副本贯穿整个流，并把副本回显给前端。
        Map<String, Object> handle = toHandleMap(rawHandle);
        String label = body == null ? null : asString(body.get("label"));
        if (label == null || label.isBlank()) {
            label = autoLabelOf(impl.channel(), owner);
        }
        long intervalMs = 2000L;
        if (body != null && body.get("intervalMs") instanceof Number n && n.longValue() >= 500L) {
            intervalMs = Math.min(n.longValue(), 10000L);
        }

        long timeoutMs = Math.max(timeoutSec, 10L) * 1000L;
        // ⚠️ emitter 自身的超时要比业务超时长：否则容器先按默认方式关流，
        // 我们这边还没来得及推 failed(TIMEOUT)，前端只能看到流断了（转圈到死）。
        // 业务超时由下面循环的 deadline 负责，兜底再留 15s 给容器。
        SseEmitter emitter = new SseEmitter(timeoutMs + 15_000L);
        String finalLabel = label;
        long finalIntervalMs = intervalMs;
        final String finalOwner = owner;

        // ⚠️ 不能用 @Transactional 或共用线程池：单次轮询可能阻塞在 HTTP 上，
        // 用独立单线程执行器并守护化，结束即关闭，避免线程泄漏。
        SSE_EXECUTOR.execute(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Map<String, Object> credentials = impl.pollLogin(handle);
                    if (credentials == null) {
                        sendEvent(emitter, "pending", pendingResult(handle));
                        try {
                            Thread.sleep(finalIntervalMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    Map<String, Object> persisted = persistCredentials(channel, credentials, finalLabel, finalOwner);
                    sendEvent(emitter, "done", persisted);
                    emitter.complete();
                    return;
                } catch (ChannelException e) {
                    Map<String, Object> err = new LinkedHashMap<>(3);
                    err.put("ok", false);
                    err.put("code", e.getCode() == null ? "UPSTREAM_ERROR" : e.getCode());
                    err.put("message", e.getMessage());
                    sendEvent(emitter, "failed", err);
                    emitter.complete();
                    return;
                } catch (ApiException e) {
                    Map<String, Object> err = new LinkedHashMap<>(3);
                    err.put("ok", false);
                    err.put("code", e.getErrorCode() == null ? "UPSTREAM_ERROR" : e.getErrorCode().name());
                    err.put("message", e.getMessage());
                    sendEvent(emitter, "failed", err);
                    emitter.complete();
                    return;
                } catch (Exception e) {
                    log.warn("[ChannelAuth] sse-login 上游异常 channel={} cause={}", channel, e.getMessage());
                    Map<String, Object> err = new LinkedHashMap<>(3);
                    err.put("ok", false);
                    err.put("code", "UPSTREAM_ERROR");
                    err.put("message", "渠道登录失败: " + e.getMessage());
                    sendEvent(emitter, "failed", err);
                    emitter.complete();
                    return;
                } catch (Throwable t) {
                    // 客户端断开（IOException）等：静默结束，不重复推。
                    emitter.completeWithError(t);
                    return;
                }
            }
            Map<String, Object> err = new LinkedHashMap<>(3);
            err.put("ok", false);
            err.put("code", "TIMEOUT");
            err.put("message", "登录等待超时（" + timeoutSec + "s），请重新发起绑定");
            sendEvent(emitter, "failed", err);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * send Event
     * @param Map<String 参数
     * @param data 参数
     * @param emitter 参数
     * @param name 字符串
     * @return 返回值
    */
    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 客户端断开：后续 complete 由调用方处理。
        }
    }

    /**
     * 单渠道、单账号（按 connectionId 精确匹配，或按 label 兜底）凭据刷新。
     *
     * <p>取该 connection 的 {@code credentials}，调 SPI {@link AccountChannelAuthManager#refresh(Map)}，
     * 成功后回写。刷新失败抛 {@link ApiErrorCode#UPSTREAM_ERROR}，既有凭据不动。</p>
     * @param channel 渠道标识
     * @param body 请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return { ok: 布尔、refreshed: 布尔 }
     */
    @PostMapping("/{channel}/refresh")
    @Transactional
    public ApiResult<Map<String, Object>> refreshChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
        }
        Map<String, Object> current = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> refreshed;
        try {
            refreshed = impl.refresh(current);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] refresh 上游异常 channel={} label={} cause={}",
                    channel, conn.getLabel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 refresh 失败: " + e.getMessage(), e);
        }
        if (refreshed == null) {
            refreshed = current;
        }
        Map<String, Object> merged = new LinkedHashMap<>(refreshed);
        merged.put("channel", channel);
        conn.setCredentials(merged);
        conn.setUpdatedAt(System.currentTimeMillis());
        connectionMapper.updateById(conn);

        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("refreshed", refreshed != current && !refreshed.equals(current));
        return ApiResult.ok(out);
    }

    /**
     * 单渠道、单账号（按 connectionId 精确匹配，或按 label 兜底）签到 / 额度领取。
     *
     * @param channel 渠道标识
     * @param body 请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return 签到结果（含 credits/message 等键，由实现决定）
     */
    @PostMapping("/{channel}/checkin")
    @Transactional
    public ApiResult<Map<String, Object>> checkinChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        // 网关优先：渠道归 magpie 承载时，以网关里的凭据为准，本地旧连接不参与。
        // 只看本地会被过期连接挡住（典型症状是「凭据缺少 access_token」）。
        ApiResult<Map<String, Object>> hosted = gatewayCheckin(channel);
        if (hosted != null) {
            return hosted;
        }
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
        }
        if (!impl.supportsCheckin()) {
            Map<String, Object> out = new LinkedHashMap<>(2);
            out.put("ok", false);
            out.put("message", "渠道不支持签到: " + channel);
            return ApiResult.ok(out);
        }
        Map<String, Object> current = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> afterCheckin;
        try {
            afterCheckin = impl.checkin(current);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] checkin 上游异常 channel={} label={} cause={}",
                    channel, conn.getLabel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 checkin 失败: " + e.getMessage(), e);
        }
        if (afterCheckin != null && afterCheckin != current) {
            Map<String, Object> merged = new LinkedHashMap<>(afterCheckin);
            merged.put("channel", channel);
            conn.setCredentials(merged);
            conn.setUpdatedAt(System.currentTimeMillis());
            connectionMapper.updateById(conn);
        }

        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        // 回传实际命中账号的 label（而不是请求里可能为空的 label），
        // 否则前端 message 会显示「签到完成：null」，用户无法确认作用到了哪个账号。
        out.put("label", conn.getLabel());
        // ⚠️ 脱敏：checkin 结果可能是完整凭据 Map（如 balance 并入），
        // 直接回传会把 access_token/refresh_token 暴露给前端。
        // 只保留签到/余额类展示键，丢弃所有凭据敏感键。
        out.put("result", checkinResult(afterCheckin));
        return ApiResult.ok(out);
    }

    /**
     * 网关托管渠道的签到 / 额度领取。
     *
     * <p>商家账号在网关里，本地连接不参与（网关优先）。借网关连接调
     * {@link AccountChannelAuthManager#checkinFor(String, Map)}，只签这一家上游，
     * 不把网关里其它渠道一起签掉。结果不写回任何本地连接。</p>
     *
     * @param channel 业务渠道标识
     * @return 有网关且能签到时的结果；否则 {@code null}（调用方按「未找到账号」处理）
     */
    private ApiResult<Map<String, Object>> gatewayCheckin(String channel) {
        GatewayOwner owner = gatewayOwnerOf(channel);
        if (owner == null) {
            return null;
        }
        AccountChannelAuthManager gatewayManager = resolveManager(managers(), owner.admin.channel());
        if (gatewayManager == null || !gatewayManager.supportsCheckin()) {
            return null;
        }
        Map<String, Object> after;
        try {
            after = gatewayManager.checkinFor(channel, owner.credentials);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] 网关签到异常 channel={} gateway={} cause={}",
                    channel, owner.admin.channel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 checkin 失败: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>(5);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("label", owner.providerId);
        out.put("managedBy", owner.admin.channel());
        out.put("managedProvider", owner.providerId);
        out.put("result", checkinResult(after));
        return ApiResult.ok(out);
    }

    /**
     * 剔除签到结果里的凭据敏感键。
     *
     * @param afterCheckin 实现返回的凭据 Map
     * @return 可回传前端的展示键
     */
    private static Map<String, Object> checkinResult(Map<String, Object> afterCheckin) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (afterCheckin != null) {
            for (Map.Entry<String, Object> e : afterCheckin.entrySet()) {
                if (!SENSITIVE_CREDENTIAL_KEYS.contains(e.getKey())) {
                    result.put(e.getKey(), e.getValue());
                }
            }
        }
        return result;
    }

    /** 凭据敏感键（checkin 结果回传前端前剔除，防 token 泄露到页面）。 */
    private static final java.util.Set<String> SENSITIVE_CREDENTIAL_KEYS = java.util.Set.of(
            "access_token", "refresh_token", "client_secret", "api_key", "uuid", "first_keyfrom",
            // Magpie 的页面 Key / 网关 Key 是管理面凭据，签到结果里会整份带回来；
            // 页面只需要 checkin_message，没有理由让密钥出现在浏览器里。
            "page_key", "gateway_key");

    /**
     * 单渠道、单账号的积分 / 余额查询（只读，不触发签到）。
     *
     * <p>与 {@code /checkin} 分开：签到是动作（每天一次），本接口是读数（随时可查）。
     * 渠道管理页的「积分」页签靠它渲染，绝不能让「看一眼余额」等于「签到一次」。</p>
     *
     * @param channel 渠道标识
     * @param body    请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return { ok, channel, label, credit: {remain,total,unit,plan,packages,error},
     *         managedBy?, managedProvider? }——后两项表示读数来自网关托管
     */
    @PostMapping("/{channel}/credit")
    public ApiResult<Map<String, Object>> creditChannel(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(6);
        // 网关优先：渠道归 magpie 承载时以网关凭据为准，本地旧连接不参与。
        // 否则过期连接会把请求挡回原生实现（例如直连 minimax 余额接口 404）。
        String account = body == null ? null : asString(body.get("account"));
        String providerScope = body == null ? null : asString(body.get("provider"));
        ApiResult<Map<String, Object>> hosted = gatewayCredit(channel, out, providerScope, account);
        if (hosted != null) {
            return hosted;
        }
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            if (label != null || connectionId != null) {
                // 明确点名的账号不存在：这是真的找不到，保留 404 语义。
                throw new ApiException(ApiErrorCode.NOT_FOUND,
                        "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
            }
            // 网关里没有该渠道上游、本地也没绑过账号。原来这里抛 404，页面只能显示
            // 「未找到渠道账号: qoder-cn / null」——null 是拼接出来的空 label，用户
            // 看不出该做什么。改为一次正常返回，并把下一步写成可操作的说明。
            Map<String, Object> credit = new LinkedHashMap<>(2);
            credit.put("error", "「" + channel + "」还没有账号：请先添加账号，或绑定登录态账号");
            out.put("ok", false);
            out.put("channel", channel);
            out.put("label", channel);
            out.put("credit", credit);
            return ApiResult.ok(out);
        }
        ChannelCreditProvider provider = creditProviderOf(channel);
        if (provider == null) {
            out.put("ok", false);
            out.put("channel", channel);
            out.put("message", "渠道不支持积分查询: " + channel);
            return ApiResult.ok(out);
        }
        Map<String, Object> credit = provider.credit(credentialsOf(conn));
        out.put("ok", credit.get("error") == null);
        out.put("channel", channel);
        out.put("label", conn.getLabel());
        out.put("credit", credit);
        return ApiResult.ok(out);
    }

    /**
     * 网关托管渠道的积分读数。
     *
     * <p><b>网关优先</b>：只要网关里已经有这个渠道的上游就以网关为准，本地旧连接
     * 不参与（数据仍保留）。网关连接凭据 → 网关的积分实现 →
     * {@link ChannelCreditProvider#creditFor(String, String, Map)} 按上游 + 账号过滤。</p>
     *
     * <p>同一上游在网关里可能登录多个账号，{@code account} 用来取某一个账号的额度卡；
     * 为空时是这一家上游的合计。账号名来自网关额度卡（{@code provider} + {@code user}），
     * 不是本地连接的 label。</p>
     *
     * @param channel 业务渠道标识
     * @param out     复用的输出 Map
     * @param provider 网关上游 id（可空；空表示该渠道全部上游）
     * @param account 上游账号（可空；空表示该上游合计）
     * @return 有网关且能读数时返回结果；否则 {@code null}（由调用方按「未找到账号」处理）
     */
    private ApiResult<Map<String, Object>> gatewayCredit(String channel, Map<String, Object> out,
                                                         String provider, String account) {
        GatewayOwner owner = gatewayOwnerOf(channel);
        if (owner == null) {
            return null;
        }
        ChannelCreditProvider creditProvider = creditProviderOf(owner.admin.channel());
        if (creditProvider == null) {
            return null;
        }
        Map<String, Object> credit = creditProvider.creditFor(channel, provider, account, owner.credentials);
        out.put("ok", credit.get("error") == null);
        out.put("channel", channel);
        // label 给前端当卡片标题：本地没有连接记录，只能用网关里的上游名 / 账号。
        out.put("label", account == null || account.isBlank() ? owner.providerId : account);
        out.put("managedBy", owner.admin.channel());
        out.put("managedProvider", owner.providerId);
        out.put("credit", credit);
        return ApiResult.ok(out);
    }

    /**
     * 单渠道、单账号的成长任务清单（只读）。
     *
     * @param channel 渠道标识
     * @param body    请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return { ok, channel, label, tasks: [ {code,title,current,target,acceptStatus,claimable,...} ] }
     */
    @PostMapping("/{channel}/tasks")
    public ApiResult<Map<String, Object>> tasksChannel(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        ChannelTaskProvider provider = taskProviderOf(channel);
        Map<String, Object> out = new LinkedHashMap<>(5);
        if (provider == null) {
            out.put("ok", false);
            out.put("channel", channel);
            out.put("message", "渠道不支持成长任务: " + channel);
            out.put("tasks", List.of());
            return ApiResult.ok(out);
        }
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        List<Map<String, Object>> tasks = provider.tasks(credentials);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("label", conn.getLabel());
        out.put("tasks", tasks);
        return ApiResult.ok(out);
    }

    /**
     * 领取单个任务奖励（唯一会产生上游副作用的端点）。
     *
     * @param channel 渠道标识
     * @param body    请求体：{ connectionId 或 label, taskCode }
     * @return { ok, channel, label, result: {ok,claimed,credit,message} }
     */
    @PostMapping("/{channel}/tasks/claim")
    public ApiResult<Map<String, Object>> claimChannelTask(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        ChannelTaskProvider provider = taskProviderOf(channel);
        if (provider == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "渠道不支持成长任务: " + channel);
        }
        String taskCode = body == null ? null : asString(body.get("taskCode"));
        if (taskCode == null || taskCode.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少 taskCode");
        }
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> result = provider.claimTask(credentials, taskCode);
        Map<String, Object> out = new LinkedHashMap<>(5);
        out.put("ok", Boolean.TRUE.equals(result.get("ok")));
        out.put("channel", channel);
        out.put("label", conn.getLabel());
        out.put("result", result);
        return ApiResult.ok(out);
    }

    /**
     * 一键做任务：报名未报名的 + 领取已达标的（幂等）。
     *
     * @param channel 渠道标识
     * @param body    请求体：{ connectionId（优先）或 label }
     * @return { ok, channel, label, summary: {accepted,claimed,credit,messages} }
     */
    @PostMapping("/{channel}/tasks/run")
    public ApiResult<Map<String, Object>> runChannelTasks(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        ChannelTaskProvider provider = taskProviderOf(channel);
        if (provider == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "渠道不支持成长任务: " + channel);
        }
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + (label == null ? connectionId : label));
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> summary = provider.runTasks(credentials);
        Map<String, Object> out = new LinkedHashMap<>(5);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("label", conn.getLabel());
        out.put("summary", summary);
        return ApiResult.ok(out);
    }

    /**
     * 单渠道、单账号（按 label 匹配）拉取可用模型列表。
     *
     * <p>成功时不直接写 {@code sys_ai_provider_model}；模型落库走
     * 既有 provider 管理接口（{@code /api/providers/{id}/models/import-all}）。
     * 本接口返回的是该账号当前可拉到的模型目录，前端可据此判断「该账号实际可用」
     * 的子集，再做模型开关。</p>
     *
     * @param channel 渠道标识
     * @param body 请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return { ok, models: [{id, name, contextWindow?}] }
     */
    @PostMapping("/{channel}/list-models")
    public ApiResult<Map<String, Object>> listModelsChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        // 网关优先：账号在网关里时模型清单也以网关为准（本地没有连接，走原生实现会报「未绑定账号」）。
        GatewayOwner owner = gatewayOwnerOf(channel);
        if (owner != null) {
            List<Map<String, Object>> hostedModels = gatewayModels(owner);
            Map<String, Object> out = new LinkedHashMap<>(5);
            out.put("ok", true);
            out.put("channel", channel);
            out.put("label", label == null || label.isBlank() ? owner.providerId : label);
            out.put("hosted", true);
            out.put("models", hostedModels);
            return ApiResult.ok(out);
        }
        AccountChannelAuthManager impl = requireManager(channel);
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        // ⚠️ 找不到账号时必须报 NOT_FOUND，不能拿空凭据继续往下走：
        // 否则上游按「无 token」返回空列表，接口回 ok=true + models=[]，
        // 前端提示「拉取到 0 个模型」—— 用户会以为渠道本身没有模型，
        // 而真实原因是账号不存在（未绑定 / label 传错）。
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "「" + impl.displayName() + "」还没有绑定账号，请先在「账号」里完成绑定，再来拉取模型");
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        List<Map<String, Object>> models;
        try {
            models = impl.listModels(credentials);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] list-models 上游异常 channel={} label={} cause={}",
                    channel, conn.getLabel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 list-models 失败: " + e.getMessage(), e);
        }
        if (models == null) {
            models = new ArrayList<>(0);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        // 回传实际命中账号的 label（connectionId 寻址时请求里没有 label），
        // 否则前端提示「按 null 拉取」会让用户无法确认拉的是哪个账号的模型。
        out.put("label", conn.getLabel());
        out.put("models", models);
        return ApiResult.ok(out);
    }

    /**
     * 将渠道 SPI {@code listModels} 返回的模型列表落库到 {@code sys_ai_provider_model}。
     *
     * <p>与 {@code /api/providers/{id}/models/import-all} 不同，本端点<b>不走</b>
     * {@code fetchModelsFromUpstream}（oauth 类 provider 的 {@code base_url} 为空，
     * 该路径会直接抛 {@code base_url 未配置}）；而是先调 SPI {@code listModels}
     * 拿到渠道真实可用模型，再逐条 upsert 到 {@code sys_ai_provider_model}。</p>
     *
     * <p>去重语义：按 ({@code providerId}, {@code model}) 唯一键判断——
     * 已存在的行保留用户改过的 enabled / alias / contextWindow，只补 null 字段；
     * 不存在的行直接插入，<b>默认不启用</b>。拉取只负责把上游模型目录同步进来，
     * 启用哪些由用户在页面上勾选——以前插入即 enabled=true，等于一次拉取就把
     * 几十个模型全部打开，用户还得逐个关掉。</p>
     *
     * @param channel 渠道标识
     * @param body 请求体：{ connectionId（优先）或 label（兜底，为空取主账号） }
     * @return { ok, channel, created, updated, total }
     */
    @PostMapping("/{channel}/import-models")
    @Transactional
    public ApiResult<Map<String, Object>> importModelsChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        SysAiProvider provider = requireChannelProvider(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        // 网关优先：账号在网关里时，模型目录也从网关的上游 provider 取，
        // 本地没有连接不代表「没绑定账号」。
        GatewayOwner owner = gatewayOwnerOf(channel);
        ChannelUpstreamProvider adapter = null;
        List<Map<String, Object>> rawModels;
        if (owner != null) {
            adapter = upstreamProviderOf(owner.admin.channel(), owner.providerId);
            rawModels = gatewayModels(owner);
        } else {
            AccountChannelAuthManager impl = requireManager(channel);
            SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
            // 同 list-models：账号不存在直接报错，避免把「没绑定」当成「0 个模型」落库成功。
            if (conn == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND,
                        "「" + impl.displayName() + "」还没有绑定账号，请先在「账号」里完成绑定，再来拉取模型");
            }
            Map<String, Object> credentials = conn.getCredentials() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
            try {
                rawModels = impl.listModels(credentials);
            } catch (ApiException e) {
                throw e;
            } catch (ChannelException e) {
                throw channelFailure(e);
            } catch (Exception e) {
                log.warn("[ChannelAuth] import-models 上游异常 channel={} label={} cause={}",
                        channel, conn.getLabel(), e.getMessage());
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "渠道 import-models 失败: " + e.getMessage(), e);
            }
        }
        if (rawModels == null) {
            rawModels = new ArrayList<>(0);
        }
        int created = 0;
        int updated = 0;
        long now = System.currentTimeMillis();
        for (Map<String, Object> raw : rawModels) {
            String modelId = asString(raw.get("id"));
            if (modelId == null || modelId.isBlank()) {
                modelId = asString(raw.get("name"));
            }
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            String modelName = modelId;
            String modelAlias = asString(raw.get("name"));
            // provider 级适配：不同上游的模型字段形态不一，统一成标准键；
            // 没有专门实现时按上游字段已标准处理（空 Map）。
            Map<String, Object> meta = adapter == null
                    ? new LinkedHashMap<>() : adapter.modelMeta(raw);
            Integer contextWindow = null;
            Object cw = meta.get("contextWindow") != null ? meta.get("contextWindow") : raw.get("contextWindow");
            if (cw instanceof Number n) {
                contextWindow = n.intValue();
            }
            Map<String, Object> metrics = modelMetrics(meta);
            SysAiProviderModel existing = modelMapper.selectOne(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, provider.getProviderId())
                            .eq(SysAiProviderModel::getModel, modelName));
            if (existing == null) {
                SysAiProviderModel m = new SysAiProviderModel();
                m.setProviderId(provider.getProviderId());
                m.setModel(modelName);
                m.setAlias(modelAlias);
                // 新拉到的模型默认不启用：拉取是「同步目录」，不是「全部上线」
                m.setEnabled(Boolean.FALSE);
                m.setContextWindow(contextWindow);
                if (!metrics.isEmpty()) {
                    m.setMetrics(metrics);
                }
                m.setSortOrder(created + updated + 1);
                m.setCreatedAt(now);
                m.setUpdatedAt(now);
                modelMapper.insert(m);
                created++;
            } else {
                boolean changed = false;
                if (contextWindow != null && existing.getContextWindow() == null) {
                    existing.setContextWindow(contextWindow);
                    changed = true;
                }
                if (modelAlias != null && !modelAlias.isBlank()
                        && (existing.getAlias() == null || existing.getAlias().isBlank())) {
                    existing.setAlias(modelAlias);
                    changed = true;
                }
                if (!metrics.isEmpty()) {
                    // 只补缺：用户改过的价格 / 倍率不能被重新拉取覆盖。
                    Map<String, Object> current = existing.getMetrics() == null
                            ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.getMetrics());
                    boolean filled = false;
                    for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                        if (current.get(entry.getKey()) == null) {
                            current.put(entry.getKey(), entry.getValue());
                            filled = true;
                        }
                    }
                    if (filled) {
                        existing.setMetrics(current);
                        changed = true;
                    }
                }
                if (changed) {
                    existing.setUpdatedAt(now);
                    // 用 (provider_id, model) 双条件更新：单列 @TableId 的 updateById
                    // 会命中其他供应商的同名模型（大小写不敏感还会撞主键）
                    modelMapper.updateByKey(existing);
                    updated++;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(6);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("created", created);
        out.put("updated", updated);
        out.put("total", rawModels.size());
        return ApiResult.ok(out);
    }

    /**
     * 单渠道、单模型的启用开关。
     *
     * <p>写入 {@code sys_ai_provider_model.enabled}。模型行缺失时报 NOT_FOUND
     * （需先通过 provider 模型管理接口录入模型名）。</p>
     *
     * @param channel 渠道标识
     * @param model 模型名
     * @param body { enabled: 布尔 }
     * @return { ok, channel, model, enabled }
     */
    @PutMapping("/{channel}/models/{model}/enabled")
    @Transactional
    public ApiResult<Map<String, Object>> setModelEnabled(
            @PathVariable("channel") String channel,
            @PathVariable("model") String model,
            @RequestBody Map<String, Object> body) {
        ownerId();
        AccountChannelAuthManager impl = requireManager(channel);
        boolean enabled = body != null
                && Boolean.TRUE.equals(body.get("enabled"));
        // ⚠️ 模型行的 provider_id 是渠道 provider 行的主键（「@Spi 别名的大写形式」，
        // 如 QODER-CN / MINIMAX），不是路由上的小写 channel。用 providerIdOf()
        // 统一口径，QODER-CN / QODERCN / qoder-cn 三个路由才会落到同一行模型。
        String providerId = providerIdOf(impl);
        SysAiProviderModel m = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, model));
        if (m == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "渠道下模型不存在: " + impl.channel() + " / " + model);
        }
        m.setEnabled(enabled);
        m.setUpdatedAt(System.currentTimeMillis());
        // 同模型名可能存在于多个供应商（列排序规则大小写不敏感），
        // updateById 只带 model 会波及别人的行，这里按真实主键更新
        modelMapper.updateByKey(m);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", impl.channel());
        out.put("model", model);
        out.put("enabled", enabled);
        return ApiResult.ok(out);
    }

    /**
     * 读取渠道的模型配置（启用状态 / 别名 / 上下文窗口 / 价格指标）。
     *
     * <p>AI 账号渠道的配置随 {@code /api/channels} 摘要一并返回；免登录对话 /
     * 文生图渠道的模型清单来自客户端内置表，摘要里没有数据库的启用状态，
     * 渠道页用本端点取回用户保存过的配置再合并。缺省的模型不会出现在这里，
     * 前端按「未启用」处理。</p>
     *
     * @param channel 渠道标识
     * @return { ok, channel, models: [{ model, enabled, alias, contextWindow, free?, priceInput?, ... }] }
     */
    @GetMapping("/{channel}/models/config")
    public ApiResult<Map<String, Object>> channelModelConfig(@PathVariable("channel") String channel) {
        String providerId = ensureChannelProviderId(channel);
        List<SysAiProviderModel> rows = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        List<Map<String, Object>> items = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (SysAiProviderModel row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("model", row.getModel());
                item.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
                item.put("alias", row.getAlias());
                item.put("contextWindow", row.getContextWindow());
                putModelMetrics(item, row.getMetrics());
                items.add(item);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("models", items);
        return ApiResult.ok(out);
    }

    /**
     * 批量启用 / 停用渠道下的模型（渠道页「保存」按钮）。
     *
     * <p>渠道页先在前端勾选，点保存才落库；这里按目标状态分两组各发一条
     * {@code UPDATE ... WHERE model IN (...)}，所以 82 个模型的改动也只产生
     * 两条 SQL、一次 HTTP，而不是前端串行发 82 个 PUT。</p>
     *
     * <p>免登录对话 / 文生图渠道的模型是客户端内置的，不能从上游拉取，
     * 但启用状态同样要能保存：它们没有 {@code AccountChannelAuthManager}，
     * 这里按「渠道标识大写」惰性物化 provider 行来承载模型配置
     * （见 {@link #ensureChannelProviderId(String)}）。</p>
     *
     * @param channel 渠道标识
     * @param body    {@code { items: [{ model, enabled }] }}
     * @return { ok, channel, updated }
     */
    @PutMapping("/{channel}/models/enabled")
    @Transactional
    public ApiResult<Map<String, Object>> setModelsEnabled(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        ownerId();
        String providerId = ensureChannelProviderId(channel);
        List<String> enable = new ArrayList<>();
        List<String> disable = new ArrayList<>();
        Object raw = body == null ? null : body.get("items");
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                Object model = row.get("model");
                if (model == null || String.valueOf(model).isBlank()) {
                    continue;
                }
                String name = String.valueOf(model);
                if (Boolean.TRUE.equals(row.get("enabled"))) {
                    enable.add(name);
                } else {
                    disable.add(name);
                }
            }
        }
        long now = System.currentTimeMillis();
        int updated = 0;
        if (!enable.isEmpty()) {
            updated += modelMapper.update(null, new LambdaUpdateWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, providerId)
                    .in(SysAiProviderModel::getModel, enable)
                    .set(SysAiProviderModel::getEnabled, true)
                    .set(SysAiProviderModel::getUpdatedAt, now));
        }
        if (!disable.isEmpty()) {
            updated += modelMapper.update(null, new LambdaUpdateWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, providerId)
                    .in(SysAiProviderModel::getModel, disable)
                    .set(SysAiProviderModel::getEnabled, false)
                    .set(SysAiProviderModel::getUpdatedAt, now));
        }
        // 内置模型渠道（免登录对话 / 文生图）从未「拉取」过，sys_ai_provider_model
        // 里可能一条行都没有，UPDATE 命中 0 行；保存后刷新又会回到默认值。
        // 因此对没有管理器的渠道补建缺失的模型行，用户第一次保存即生效并有记录。
        if (resolveManager(managers(), channel) == null) {
            updated += insertMissingModelRows(providerId, enable, disable, now);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("updated", updated);
        return ApiResult.ok(out);
    }

    /**
     * 为内置模型渠道补建缺失的模型行。
     *
     * <p>{@link #setModelsEnabled} 主体用两条批量 UPDATE 落库，依赖行已存在；
     * 免登录对话 / 文生图渠道的模型清单由客户端内置，从未走过 {@code import-models}，
     * 表里没有任何行，第一次保存就会「成功但没生效」。这里按目标状态补建。</p>
     *
     * @param providerId 渠道对应的 providerId
     * @param enable     目标为启用的模型名
     * @param disable    目标为停用的模型名
     * @param now        统一时间戳
     * @return 新建行数
     */
    private int insertMissingModelRows(String providerId, List<String> enable,
                                       List<String> disable, long now) {
        List<String> all = new ArrayList<>(enable.size() + disable.size());
        all.addAll(enable);
        all.addAll(disable);
        if (all.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        List<SysAiProviderModel> rows = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .in(SysAiProviderModel::getModel, all));
        if (rows != null) {
            for (SysAiProviderModel row : rows) {
                existing.add(row.getModel());
            }
        }
        int sort = modelMapper.selectCount(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)).intValue();
        int created = 0;
        for (String model : all) {
            if (existing.contains(model)) {
                continue;
            }
            SysAiProviderModel m = new SysAiProviderModel();
            m.setProviderId(providerId);
            m.setModel(model);
            m.setEnabled(enable.contains(model));
            m.setSortOrder(++sort);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            modelMapper.insert(m);
            created++;
        }
        return created;
    }

    /**
     * 删除渠道账号（按 label 匹配）。
     *
     * <p>删除该 connection 行；不删 provider 行（其它账号可能共用），
     * 该渠道下已无 connection 时 provider 行保留（启用状态由管理页统一开关）。</p>
     *
     * @param channel 渠道标识
     * @param body { label, connectionId? }
     * @return 成功
     */
    @PostMapping("/{channel}/delete-connection")
    @Transactional
    public ApiResult<Void> deleteConnection(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection conn = findConnectionOrThrow(channel, label, connectionId);
        connectionMapper.deleteById(conn.getConnectionId());
        // 删掉的是主账号时，把主账号角色交给剩下最早绑定的那条：
        // 否则该渠道会变成「有账号但没有 default」，缺 label 的
        // status / refresh / checkin / list-models 与路由取用全部落空。
        if (DEFAULT_ROLE.equals(conn.getRole())) {
            promoteEarliestConnection(conn.getProviderId(), conn.getUserId(), conn.getConnectionId());
        }
        return ApiResult.ok();
    }

    /**
     * 重命名渠道账号（改 connection 的 label）。
     *
     * <p>label 是账号在 {@code /api/channels} 各子接口里的<b>寻址键</b>
     * （refresh / checkin / list-models / status / delete-connection 都按它匹配）。
     * 绑定成功时是后端自动生成的渠道名 + 序号（如 {@code qoder #1}），用户无法辨认，
     * 这里提供改名入口。</p>
     *
     * <p><b>为什么优先用 connectionId 寻址：</b>label 是这次要改的那个字段，且表上没有
     * 唯一约束（历史数据里同渠道可能存在重名）。按 label 寻址时，重名会让
     * {@code findConnection} 的 {@code selectOne} 抛 {@code TooManyResultsException}；
     * 传空 label 更会被当成「取主账号」而误改别人的账号。connectionId 是主键、不可变，
     * 改名/切主一律优先用它，label 只作为旧客户端兼容的兜底。</p>
     *
     * <p>改名后立刻校验同渠道内不重名：label 重复会让寻址命中别人的账号，
     * 是数据层无法约束但业务上必须拒绝的状态。</p>
     *
     * @param channel 渠道标识
     * @param body    { connectionId?, label（旧名，兜底寻址）, newLabel（新名） }
     * @return { ok, channel, connectionId, label }
     */
    @PostMapping("/{channel}/rename-connection")
    @Transactional
    public ApiResult<Map<String, Object>> renameConnection(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        String newLabel = body == null ? null : asString(body.get("newLabel"));
        if (newLabel == null || newLabel.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "newLabel 不能为空");
        }
        newLabel = newLabel.trim();
        SysAiProviderConnection conn = findConnectionOrThrow(channel, label, connectionId);
        if (newLabel.equals(conn.getLabel())) {
            return ApiResult.ok(connectionSummaryOf(conn, impl));
        }
        String owner = ownerId();
        Long duplicated = connectionMapper.selectCount(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, conn.getProviderId())
                        .eq(SysAiProviderConnection::getLabel, newLabel)
                        .and(w -> {
                            w.eq(SysAiProviderConnection::getUserId, owner);
                            if (AiUserContext.isAdmin()) {
                                w.or().isNull(SysAiProviderConnection::getUserId);
                            }
                        }));
        if (duplicated != null && duplicated > 0L) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "该渠道下已存在同名账号: " + newLabel);
        }
        conn.setLabel(newLabel);
        conn.setUpdatedAt(System.currentTimeMillis());
        connectionMapper.updateById(conn);
        return ApiResult.ok(connectionSummaryOf(conn, impl));
    }

    /**
     * 切换主账号（把 {@code role=default} 交给指定连接）。
     *
     * <p>同一渠道的多个账号在路由层是「按顺序 failover 的 apiKeys 列表」，
     * 但 status / refresh / checkin / list-models 这些缺 label 的调用只认
     * {@code role=default} 那条。用户需要能把更健康的账号顶到主位。</p>
     *
     * <p>实现上先把该渠道当前所有连接降级为 backup，再把目标改成 default，
     * 保证「主账号全局唯一」——两个 default 会让 {@code findConnection} 的
     * {@code selectOne} 抛 {@code TooManyResultsException}。</p>
     *
     * @param channel 渠道标识
     * @param body    { connectionId?, label（兜底） }（目标账号）
     * @return { ok, channel, connectionId, label }
     */
    @PostMapping("/{channel}/set-primary")
    @Transactional
    public ApiResult<Map<String, Object>> setPrimaryConnection(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        String connectionId = body == null ? null : asString(body.get("connectionId"));
        SysAiProviderConnection target = findConnectionOrThrow(channel, label, connectionId);
        if (DEFAULT_ROLE.equals(target.getRole())) {
            return ApiResult.ok(connectionSummaryOf(target, impl));
        }
        long now = System.currentTimeMillis();
        // 先把同渠道、同归属下现有的 default 全部降级为 backup（保证主账号唯一），
        // 再把目标设为 default。不能只更新目标：遗留的两个 default 会让
        // findConnection 的 selectOne 抛 TooManyResultsException。
        LambdaUpdateWrapper<SysAiProviderConnection> demote =
                new LambdaUpdateWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, target.getProviderId())
                        .eq(SysAiProviderConnection::getRole, DEFAULT_ROLE)
                        .set(SysAiProviderConnection::getRole, BACKUP_ROLE)
                        .set(SysAiProviderConnection::getUpdatedAt, now);
        if (target.getUserId() == null) {
            demote.isNull(SysAiProviderConnection::getUserId);
        } else {
            demote.eq(SysAiProviderConnection::getUserId, target.getUserId());
        }
        connectionMapper.update(null, demote);
        target.setRole(DEFAULT_ROLE);
        target.setUpdatedAt(now);
        connectionMapper.updateById(target);
        return ApiResult.ok(connectionSummaryOf(target, impl));
    }

    /**
     * 连接的单条摘要（改名 / 切主接口的响应体，字段与列表里的 connections 项一致）。
     *
     * @param c    连接
     * @param impl 渠道实现
     * @return 摘要
     */
    private Map<String, Object> connectionSummaryOf(SysAiProviderConnection c, AccountChannelAuthManager impl) {
        Map<String, Object> cm = new LinkedHashMap<>(8);
        cm.put("connectionId", c.getConnectionId());
        cm.put("label", c.getLabel());
        cm.put("role", c.getRole());
        cm.put("primary", DEFAULT_ROLE.equals(c.getRole()));
        cm.put("hasCredentials", c.getCredentials() != null && !c.getCredentials().isEmpty());
        cm.put("updatedAt", c.getUpdatedAt());
        cm.put("createdAt", c.getCreatedAt());
        cm.put("status", implStatusOf(c, impl));
        cm.put("expiresAt", expiresAtOf(c));
        cm.put("channel", channelOf(c, impl.channel()));
        putCheckinInfo(cm, c);
        return cm;
    }

    /**
     * 把「最近一次签到」的展示字段并进连接摘要。
     *
     * <p>签到结果由各 SPI 写在自己的连接凭据上（{@code checkin_at} /
     * {@code checkin_message}），WorkBuddy、Trae、magpie 都按这个键名落库。
     * 这两个键是纯展示信息，单独取出来回给前端，免得为了显示「上次签到」
     * 把整份凭据 Map 暴露出去。</p>
     *
     * <p>缺失时不写键（而不是写 {@code null}），前端用 {@code v-if} 判断即可。</p>
     *
     * @param cm 连接摘要，就地写入 {@code checkinAt} / {@code checkinMessage}
     * @param c  连接
     */
    private static void putCheckinInfo(Map<String, Object> cm, SysAiProviderConnection c) {
        Map<String, Object> credentials = c == null ? null : c.getCredentials();
        if (credentials == null || credentials.isEmpty()) {
            return;
        }
        Long at = toEpochMillis(credentials.get("checkin_at"));
        if (at != null) {
            cm.put("checkinAt", at);
        }
        Object message = credentials.get("checkin_message");
        if (message != null) {
            String text = String.valueOf(message).trim();
            if (!text.isEmpty()) {
                cm.put("checkinMessage", text);
            }
        }
    }

    /**
     * 把指定连接之外、最早绑定的一条同归属连接提升为主账号。
     *
     * <p>只在「删掉的是主账号」时调用。没有任何剩余连接时什么都不做：该渠道就是
     * 未绑定状态，不需要 default 行。</p>
     *
     * @param providerId   提供商标识
     * @param owner        归属用户（与删除的连接一致）
     * @param excludeId    已删除的连接标识（防御性排除）
     */
    private void promoteEarliestConnection(String providerId, String owner, String excludeId) {
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .ne(SysAiProviderConnection::getConnectionId, excludeId);
        if (owner == null) {
            wrapper.isNull(SysAiProviderConnection::getUserId);
        } else {
            wrapper.eq(SysAiProviderConnection::getUserId, owner);
        }
        List<SysAiProviderConnection> rest = connectionMapper.selectList(
                wrapper.orderByAsc(SysAiProviderConnection::getCreatedAt).last("LIMIT 1"));
        if (rest == null || rest.isEmpty()) {
            return;
        }
        SysAiProviderConnection next = rest.get(0);
        next.setRole(DEFAULT_ROLE);
        next.setUpdatedAt(System.currentTimeMillis());
        connectionMapper.updateById(next);
    }

    /**
     * 渠道总开关（启停该 provider 行）。
     *
     * @param channel 渠道标识
     * @param body { enabled: 布尔 }
     * @return { ok, enabled }
     */
    @PutMapping("/{channel}/enabled")
    @Transactional
    public ApiResult<Map<String, Object>> setChannelEnabled(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        ownerId();
        SysAiProvider provider = requireChannelProvider(channel);
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        provider.setEnabled(enabled);
        provider.setUpdatedAt(System.currentTimeMillis());
        providerMapper.updateById(provider);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("enabled", enabled);
        return ApiResult.ok(out);
    }

    /**
     * 渠道级凭据状态探活（按 connectionId 精确匹配，或按 label 兜底；缺省取主账号）。
     *
     * @param channel      渠道标识
     * @param label        可选 label，缺省取默认
     * @param connectionId 可选连接主键（优先）
     * @return { ok, status: "valid"|"expired"|"invalid", label }
     */
    @GetMapping("/{channel}/status")
    public ApiResult<Map<String, Object>> statusChannel(
            @PathVariable("channel") String channel,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        AccountChannelAuthManager impl = requireManager(channel);
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            Map<String, Object> out = new LinkedHashMap<>(2);
            out.put("ok", false);
            out.put("message", "该渠道暂无绑定账号");
            return ApiResult.ok(out);
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> status;
        try {
            status = impl.status(credentials);
        } catch (ApiException e) {
            throw e;
        } catch (ChannelException e) {
            throw channelFailure(e);
        } catch (Exception e) {
            log.warn("[ChannelAuth] status 上游异常 channel={} label={} cause={}",
                    channel, conn.getLabel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 status 失败: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("status", status == null ? "unknown" : status.get("status"));
        out.put("label", conn.getLabel());
        out.put("details", status);
        return ApiResult.ok(out);
    }

    /**
     * 路由到一个已注册的渠道实现，未注册抛 404。
     *
     * @param channel 渠道标识
     * @return 实现
     */
    private AccountChannelAuthManager requireManager(String channel) {
        AccountChannelAuthManager impl = resolveManager(managers(), channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的渠道: " + channel);
        }
        return impl;
    }

    /**
     * 按渠道标识解析实现（不区分大小写）。
     *
     * <p>SPI 框架为每个实现登记两条 definition：一条键为 {@code @Spi} 别名，
     * 另一条键为类简名（如 {@code QoderCnAccountChannelAuthManager} → {@code QODERCN}）。
     * 后者不是渠道标识，但会进 {@code list()} 的键集合；按大小写不敏感匹配可让
     * {@code qoder-cn} / {@code QODER-CN} / {@code QODERCN} 都落到同一个实现。</p>
     *
     * @param managers 已注册的实现映射
     * @param channel 前端传入的渠道标识
     * @return 实现，未注册返回 {@code null}
     */
    private static AccountChannelAuthManager resolveManager(
            Map<String, AccountChannelAuthManager> managers, String channel) {
        if (channel == null) {
            return null;
        }
        AccountChannelAuthManager exact = managers.get(channel);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, AccountChannelAuthManager> entry : managers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(channel)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 当前账号归属用户（未登录抛 401）。
     *
     * @return 用户标识
     */
    private static String ownerId() {
        return AiUserContext.requireUserId();
    }

    /**
     * 积分查询 SPI 实现（按渠道标识，大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 实现；不支持时返回 {@code null}
     */
    private ChannelCreditProvider creditProviderOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        for (Map.Entry<String, ChannelCreditProvider> entry : creditProviders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(channel)) {
                return entry.getValue().supportsCredit() ? entry.getValue() : null;
            }
        }
        return null;
    }

    /**
     * 任务 SPI 实现（按渠道标识，大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 实现；不支持时返回 {@code null}
     */
    private ChannelTaskProvider taskProviderOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        for (Map.Entry<String, ChannelTaskProvider> entry : taskProviders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(channel)) {
                return entry.getValue().supportsTask() ? entry.getValue() : null;
            }
        }
        return null;
    }

    /**
     * 懒加载积分查询实现映射（幂等）。
     *
     * @return channel → 实现
     */
    private Map<String, ChannelCreditProvider> creditProviders() {
        Map<String, ChannelCreditProvider> local = creditProviders;
        if (local == null) {
            synchronized (this) {
                local = creditProviders;
                if (local == null) {
                    local = ServiceProvider.of(ChannelCreditProvider.class).list();
                    creditProviders = local;
                }
            }
        }
        return local;
    }

    /**
     * 懒加载任务实现映射（幂等）。
     *
     * @return channel → 实现
     */
    private Map<String, ChannelTaskProvider> taskProviders() {
        Map<String, ChannelTaskProvider> local = taskProviders;
        if (local == null) {
            synchronized (this) {
                local = taskProviders;
                if (local == null) {
                    local = ServiceProvider.of(ChannelTaskProvider.class).list();
                    taskProviders = local;
                }
            }
        }
        return local;
    }

    /**
     * 懒加载网关上游管理实现映射（幂等）。
     *
     * @return alias → 实现
     */
    private Map<String, ChannelUpstreamAdmin> upstreamAdmins() {
        Map<String, ChannelUpstreamAdmin> local = upstreamAdmins;
        if (local == null) {
            synchronized (this) {
                local = upstreamAdmins;
                if (local == null) {
                    local = ServiceProvider.of(ChannelUpstreamAdmin.class).list();
                    upstreamAdmins = local;
                }
            }
        }
        return local;
    }

    /**
     * 懒加载网关内部的上游 provider 适配实现映射（幂等）。
     *
     * @return alias → 实现
     */
    private Map<String, ChannelUpstreamProvider> upstreamProviders() {
        Map<String, ChannelUpstreamProvider> local = upstreamProviders;
        if (local == null) {
            synchronized (this) {
                local = upstreamProviders;
                if (local == null) {
                    local = ServiceProvider.of(ChannelUpstreamProvider.class).list();
                    upstreamProviders = local;
                }
            }
        }
        return local;
    }

    /**
     * 取网关内部某个上游的适配实现。
     *
     * <p>同一网关允许多个实现按 provider id 分工；没有专门实现时返回 {@code null}，
     * 调用方按标准字段处理。这里只做两层：渠道 → provider，不再往下套。</p>
     *
     * @param channel    网关渠道标识
     * @param providerId 上游 provider id（可空）
     * @return 适配实现；无则 {@code null}
     */
    private ChannelUpstreamProvider upstreamProviderOf(String channel, String providerId) {
        if (channel == null) {
            return null;
        }
        for (ChannelUpstreamProvider impl : upstreamProviders().values()) {
            if (impl == null || impl.channel() == null || !impl.channel().equalsIgnoreCase(channel)) {
                continue;
            }
            if (impl.supports(providerId)) {
                return impl;
            }
        }
        return null;
    }

    /**
     * 按渠道标识取网关上游管理实现，未注册抛 501。
     *
     * <p>同一实现会以「{@code @Spi} 别名」和「类简名」登记两次，故按
     * {@link ChannelUpstreamAdmin#supports(String)} 匹配，而不是直接查 Map 键。</p>
     *
     * @param channel 渠道标识
     * @return 实现
     */
    private ChannelUpstreamAdmin requireUpstreamAdmin(String channel) {
        ChannelUpstreamAdmin admin = upstreamAdminOf(channel);
        if (admin != null) {
            return admin;
        }
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "渠道 " + channel + " 不支持上游 provider 管理");
    }

    /**
     * 取网关上游管理实现，未注册返回 {@code null}（用于能力位判断）。
     *
     * @param channel 渠道标识
     * @return 实现；无则 {@code null}
     */
    private ChannelUpstreamAdmin upstreamAdminOf(String channel) {
        for (ChannelUpstreamAdmin impl : upstreamAdmins().values()) {
            if (impl != null && impl.supports(channel)) {
                return impl;
            }
        }
        return null;
    }

    /**
     * 判断某业务渠道是否由某个网关承载（网关里已有它的上游）。
     *
     * <p>只在「本地没有该渠道连接」时才需要：有连接说明用户在我们这边绑过账号，
     * 凭据就在本地，按原生实现走即可。凭据在网关里时，积分 / 签到必须借用网关
     * 连接去问管理面。</p>
     *
     * @param channel 业务渠道标识
     * @return 承载它的网关与上游；没有返回 {@code null}
     */
    private GatewayOwner gatewayOwnerOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        for (ChannelUpstreamAdmin admin : upstreamAdmins().values()) {
            if (admin == null || admin.supports(channel)) {
                continue;
            }
            List<String> wanted = admin.providerIdsOf(channel);
            if (wanted == null || wanted.isEmpty()) {
                continue;
            }
            SysAiProviderConnection conn = findTargetConnection(admin.channel(), null, null);
            if (conn == null) {
                continue;
            }
            Set<String> ids = gatewayProviderIds(admin, conn);
            if (ids.isEmpty()) {
                continue;
            }
            for (String id : wanted) {
                if (id != null && ids.contains(id.toLowerCase(Locale.ROOT))) {
                    return new GatewayOwner(admin, credentialsOf(conn), id);
                }
            }
        }
        return null;
    }

    /**
     * 读取（并缓存）某条网关连接的上游 provider id 集合。
     *
     * <p>读取失败一律当空集合：网关管理面没开 / 页面 Key 失效时，渠道页应继续可用，
     * 只是「由网关托管」的判定不成立，不能把整页拖垮。</p>
     *
     * @param admin 上游管理实现
     * @param conn  网关连接
     * @return 小写 provider id 集合
     */
    private Set<String> gatewayProviderIds(ChannelUpstreamAdmin admin, SysAiProviderConnection conn) {
        String key = admin.channel() + "@" + conn.getConnectionId() + "@" + conn.getUpdatedAt();
        long now = System.currentTimeMillis();
        GatewayIndex cached = gatewayIndexes.get(key);
        if (cached != null && now - cached.at < GATEWAY_INDEX_TTL_MS) {
            return cached.providerIds;
        }
        Set<String> ids = new HashSet<>();
        try {
            Map<String, Object> listed = admin.list(credentialsOf(conn));
            Object raw = listed == null ? null : listed.get("providers");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && map.get("id") != null) {
                        ids.add(String.valueOf(map.get("id")).toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[ChannelAuth] 读取网关上游失败 gateway={} cause={}",
                    admin.channel(), e.getMessage());
        }
        gatewayIndexes.put(key, new GatewayIndex(now, ids));
        return ids;
    }

    /**
     * 取连接凭据的可变副本。
     *
     * @param conn 连接
     * @return 凭据副本（原凭据为空时返回空 Map）
     */
    private static Map<String, Object> credentialsOf(SysAiProviderConnection conn) {
        return conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
    }

    /**
     * 把 common 的渠道异常翻成本模块的契约异常，保留错误码与根因。
     *
     * <p>账号模块的实现抛的是 common 的 {@link ChannelException}；若不显式翻译，
     * 会被 {@code catch (Exception)} 兜底成 {@code UPSTREAM_ERROR}（502），
     * {@code INVALID_PARAM} / {@code NOT_FOUND} / {@code NOT_IMPLEMENTED} 全部丢失。</p>
     *
     * @param e 渠道异常
     * @return 本模块的契约异常
     */
    private static ApiException channelFailure(ChannelException e) {
        ApiErrorCode code;
        try {
            code = ApiErrorCode.valueOf(e.getCode());
        } catch (RuntimeException ignored) {
            code = ApiErrorCode.UPSTREAM_ERROR;
        }
        return new ApiException(code, e.getMessage(), e);
    }

    /**
     * 给连接查询施加账号隔离条件：
     * 普通用户只能看到自己的连接；管理员额外可见历史 {@code user_id IS NULL} 的系统池行
     * （渠道管理改造前的绑定即此类）。
     *
     * @param wrapper 连接查询包装器
     * @param owner   当前用户标识
     */
    private void applyOwnerFilter(LambdaQueryWrapper<SysAiProviderConnection> wrapper, String owner) {
        wrapper.and(w -> {
            w.eq(SysAiProviderConnection::getUserId, owner);
            if (AiUserContext.isAdmin()) {
                w.or().isNull(SysAiProviderConnection::getUserId);
            }
        });
    }

    /**
     * 按 channel + label 定位 connection：
     * label 非空时按 label 精确匹配；为空时取「主账号优先、其次最早绑定」那条。
     *
     * @param channel 渠道标识
     * @param label 账号标签（可空）
     * @return connection，未找到返回 {@code null}
     */
    private SysAiProviderConnection findConnection(String channel, String label) {
        SysAiProvider provider = requireChannelProvider(channel);
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, provider.getProviderId());
        // 账号隔离：只能定位当前用户自己的连接
        applyOwnerFilter(wrapper, ownerId());
        if (label != null && !label.isBlank()) {
            wrapper.eq(SysAiProviderConnection::getLabel, label.trim());
        } else {
            // 缺 label 时取「主账号优先，其次最早绑定」。
            // ⚠️ 必须 role 降序：字符串序里 "backup" < "default"，升序会把备账号
            // 选成默认，与 set-primary 的意图正好相反。
            wrapper.orderByDesc(SysAiProviderConnection::getRole)
                    .orderByAsc(SysAiProviderConnection::getCreatedAt);
        }
        wrapper.last("LIMIT 1");
        return connectionMapper.selectOne(wrapper);
    }

    /**
     * 为「刷新 / 签到 / 拉模型 / 导入模型」这类<b>读写同一个账号</b>的接口定位连接：
     * 给了 {@code connectionId} 就精确命中，否则退回按 {@code label} 的旧口径
     * （{@code label} 为空时取主账号，保持既有行为）。
     *
     * <p>为什么读路径也要支持 connectionId：这些接口会把新凭据写回被选中的那一条，
     * 只按 label 寻址时，历史上 label 为空的老连接会被静默回退成「主账号」——
     * 用户在 A 账号上点刷新，实际刷新的是主账号，且没有任何报错。</p>
     *
     * @param channel      渠道标识
     * @param label        账号标签（可空）
     * @param connectionId 连接主键（可空，优先）
     * @return connection，未找到返回 {@code null}
     */
    private SysAiProviderConnection findTargetConnection(String channel, String label, String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return findConnection(channel, label);
        }
        SysAiProvider provider = requireChannelProvider(channel);
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, provider.getProviderId())
                        .eq(SysAiProviderConnection::getConnectionId, connectionId.trim());
        applyOwnerFilter(wrapper, ownerId());
        return connectionMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    /**
     * 按 {@code connectionId}（优先）或 {@code label} 定位一条<b>属于当前用户的</b>连接，
     * 找不到直接抛 404。
     *
     * <p>专供「改名 / 切主 / 删除」这类<b>写操作</b>：这些动作作用的对象必须唯一确定，
     * 不能像 read 路径那样「缺 label 就回退到主账号」——那会让前端拿到空 label 时的
     * 一次误点，把用户的主账号改名或删掉。故这里两者都为空直接拒绝，而不是静默取默认。</p>
     *
     * @param channel      渠道标识
     * @param label        账号标签（兼容旧调用方，可空）
     * @param connectionId 连接主键（可空）
     * @return 必然存在的连接
     */
    private SysAiProviderConnection findConnectionOrThrow(String channel, String label, String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            if (label == null || label.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "必须指定 connectionId 或 label，不能用未指定账号的调用改主账号");
            }
            SysAiProviderConnection byLabel = findConnection(channel, label);
            if (byLabel == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND,
                        "未找到渠道账号: " + channel + " / " + label);
            }
            return byLabel;
        }
        SysAiProviderConnection conn = findTargetConnection(channel, label, connectionId);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + connectionId);
        }
        return conn;
    }

    /**
     * 查找或新建一条 connection：label 非空时精确匹配； 为空时按「该渠道下 role=default」匹配；都不存在则新建。
     * @param label 账号标签（可空）
     * @param owner 字符串
     * @param providerId provider 行 ID
     * @return 必然存在的 connection
    */
    private SysAiProviderConnection findOrCreateConnection(String providerId, String label, String owner) {
        if (label != null && !label.isBlank()) {
            SysAiProviderConnection byLabel = connectionMapper.selectOne(
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, providerId)
                            .eq(SysAiProviderConnection::getLabel, label.trim())
                            .eq(SysAiProviderConnection::getUserId, owner)
                            .last("LIMIT 1"));
            if (byLabel != null) {
                return byLabel;
            }
        } else {
            SysAiProviderConnection byRole = connectionMapper.selectOne(
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, providerId)
                            .eq(SysAiProviderConnection::getRole, DEFAULT_ROLE)
                            .eq(SysAiProviderConnection::getUserId, owner)
                            .last("LIMIT 1"));
            if (byRole != null) {
                return byRole;
            }
        }
        long now = System.currentTimeMillis();
        SysAiProviderConnection created = new SysAiProviderConnection();
        created.setConnectionId(java.util.UUID.randomUUID().toString());
        created.setProviderId(providerId);
        // 主账号唯一：该渠道当前没有 default 时新账号接任主位，否则作为 backup 挂上。
        created.setRole(hasDefaultConnection(providerId, owner) ? BACKUP_ROLE : DEFAULT_ROLE);
        created.setLabel(label);
        created.setUserId(owner);
        created.setAuthScheme(OAUTH_SCHEME);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        connectionMapper.insert(created);
        return created;
    }

    /**
     * 指定渠道 / 归属下是否已有主账号连接。
     *
     * @param providerId 提供商标识
     * @param owner      归属用户（可空 = 系统池）
     * @return true 表示已有 {@code role=default} 的连接
     */
    private boolean hasDefaultConnection(String providerId, String owner) {
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, DEFAULT_ROLE);
        if (owner == null) {
            wrapper.isNull(SysAiProviderConnection::getUserId);
        } else {
            wrapper.eq(SysAiProviderConnection::getUserId, owner);
        }
        Long count = connectionMapper.selectCount(wrapper);
        return count != null && count > 0L;
    }

    /**
     * 未指定 label 时给新账号起的自动名。
     *
     * <p>旧实现是 {@code "账号 " + (currentTimeMillis() % 1000000)}：同一毫秒内并发绑定两个
     * 账号会撞名，而 label 是 refresh / checkin / status / delete 的寻址键，撞名会命中
     * 别人的账号（{@code findConnection} 的 {@code selectOne} 还会直接抛
     * {@code TooManyResultsException}）。这里改成「渠道名 + 该渠道已有账号数 + 1」，
     * 人类可读且可预测；仍撞名时（历史数据里删过账号）继续向后找空位。</p>
     *
     * @param channel 渠道标识（实现回传的规范标识）
     * @param owner   归属用户
     * @return 未被占用的账号名
     */
    private String autoLabelOf(String channel, String owner) {
        String providerId = channel == null ? null : channel.toUpperCase(java.util.Locale.ROOT);
        String prefix = channel == null || channel.isBlank() ? "账号" : channel;
        long index = 1L;
        for (int guard = 0; guard < 100; guard++) {
            String candidate = prefix + " #" + index;
            if (!labelExists(providerId, candidate, owner)) {
                return candidate;
            }
            index++;
        }
        // 极端情况（同名占满 100 个）：退回时间戳，保证一定能落库。
        return prefix + " #" + System.currentTimeMillis();
    }

    /**
     * 指定渠道 / 归属下 label 是否已被占用。
     *
     * <p>只按 providerId 统计，不物化 provider 行：自动名是在 poll 之前生成的，
     * 绑定还没成功，不该因为「想查个重名」就提前落一条 provider。</p>
     *
     * @param providerId 提供商标识（渠道标识的大写形式）
     * @param label      待检查的账号名
     * @param owner      归属用户（可空 = 系统池）
     * @return true 表示已存在同名连接
     */
    private boolean labelExists(String providerId, String label, String owner) {
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getLabel, label);
        if (owner == null) {
            wrapper.isNull(SysAiProviderConnection::getUserId);
        } else {
            wrapper.eq(SysAiProviderConnection::getUserId, owner);
        }
        Long count = connectionMapper.selectCount(wrapper);
        return count != null && count > 0L;
    }

    /**
     * 渠道 connection 的状态摘要：取 credentials 经 impl.status() 的 {@code status} 键。 出错时返回 "unknown"，前端仍展示连接本身。
     * @param c 参数
     * @param impl 参数
     * @return 返回值
    */
    private String implStatusOf(SysAiProviderConnection c, AccountChannelAuthManager impl) {
        if (c == null || c.getCredentials() == null || c.getCredentials().isEmpty()) {
            return "unbound";
        }
        try {
            Map<String, Object> status = impl.status(c.getCredentials());
            Object v = status == null ? null : status.get("status");
            return v instanceof String s ? s : "unknown";
        } catch (Exception e) {
            log.debug("[ChannelAuth] 渠道 status 探测失败 label={} cause={}",
                    c.getLabel(), e.getMessage());
            return "unknown";
        }
    }

    /**
     * 凭据到期时间（毫秒），供前端按「剩余时长」排序 / 展示。
     *
     * <p>不从 {@code impl.status()} 取：那条路径要发上游探活请求，列表页对十几个账号
     * 逐个探活会把 GET /api/channels 拖成秒级。这里只读本地凭据里的
     * {@code expires_at / expiresAt / expiry} 三个键（各 SPI 实现口径不同），
     * 统一归一化成毫秒；缺失时返回 {@code null}，前端显示「—」。</p>
     *
     * @param c 连接
     * @return 到期毫秒，未知返回 {@code null}
     */
    private static Long expiresAtOf(SysAiProviderConnection c) {
        Map<String, Object> credentials = c == null ? null : c.getCredentials();
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        // 键名按各 SPI 实现的真实写入口径收集（逐个核对过源码）：
        //   Minimax / Qoder / Raccoon → expire_time（毫秒）
        //   Trae / Lobsterai / Loomy   → expires_at （毫秒）
        //   Cline                      → expiresAt （毫秒，取 accessToken 打包体）
        //   Gemini                     → expiry    （RFC3339 字符串，如 2026-11-02T10:00:00Z）
        // 漏掉 expire_time 会让一半渠道的「到期」永远显示为空。
        for (String key : new String[]{"expires_at", "expiresAt", "expire_time", "expireTime", "expiry"}) {
            Object raw = credentials.get(key);
            if (raw == null) {
                continue;
            }
            Long value = toEpochMillis(raw);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 把凭据里的到期值归一化成毫秒时间戳。
     *
     * <p>各 SPI 实现的写入口径不一致，这里统一吃三种形态：数字/数字串（秒或毫秒）、
     * RFC3339 时间串（Gemini 的 {@code expiry}）。无法识别时返回 {@code null}，
     * 由调用方继续试下一个键，而不是把 {@code 0} 当"已过期"误导前端。</p>
     *
     * @param raw 凭据中的原始值
     * @return 毫秒时间戳，无法识别返回 {@code null}
     */
    private static Long toEpochMillis(Object raw) {
        if (raw instanceof Number n) {
            return normalizeEpoch(n.longValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return normalizeEpoch(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            // 不是数字 → 可能是 RFC3339 / ISO-8601，交给下面的解析。
        }
        try {
            return java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (java.time.format.DateTimeParseException ignored) {
            // OffsetDateTime 对无时区串会失败，再试一次 Instant（"2026-11-02T10:00:00Z"）。
        }
        try {
            return java.time.Instant.parse(text).toEpochMilli();
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 秒 → 毫秒归一化。
     *
     * <p>阈值取 1e12：10 位秒级时间戳（约 2001~2286 年）远小于它，13 位毫秒级
     * 时间戳远大于它。不归一化前端会把秒级值算成 1970 年。</p>
     *
     * @param value 原始数值
     * @return 毫秒时间戳；非正值返回 {@code null}
     */
    private static Long normalizeEpoch(long value) {
        if (value <= 0L) {
            return null;
        }
        return value < 1_000_000_000_000L ? value * 1000L : value;
    }

    /**
     * 连接实际归属的渠道标识。
     *
     * <p>优先用凭据里落库的 {@code channel}（绑定 / 刷新时由本控制器写入），
     * 缺失时退回该 provider 所属渠道，保证前端按渠道渲染时的 key 一致。</p>
     *
     * @param c            连接
     * @param fallbackChannel 兜底渠道标识
     * @return 渠道标识
     */
    private static String channelOf(SysAiProviderConnection c, String fallbackChannel) {
        Map<String, Object> credentials = c == null ? null : c.getCredentials();
        Object stored = credentials == null ? null : credentials.get("channel");
        return stored instanceof String s && !s.isBlank() ? s : fallbackChannel;
    }

    /**
     * 从请求体取字符串字段；非字符串返回 空。
     *
     * @param value 任意值
     * @return 字符串或 空
     */
    private static String asString(Object value) {
        return value instanceof String s ? s : (value == null ? null : value.toString());
    }

    /**
     * 把 {@code body.handle} 归一化为 {@code Map<String,Object>}。
     *
     * <p>前端 {@code startLogin} 返回的句柄 Map 经 JSON 往返后，通常已是
     * {@link Map}（Jackson 反序列化）；若被序列化为字符串则再解析一次。
     * 任何无法识别的形态都返回空 Map（由渠道 SPI 自行判空报错）。</p>
     *
     * @param raw 原始句柄
     * @return 归一化句柄 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toHandleMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                Map<String, Object> parsed = Json.getJsonObject(s);
                return parsed == null ? new LinkedHashMap<>() : parsed;
            } catch (Exception e) {
                log.debug("[ChannelAuth] handle 字符串解析失败: {}", e.getMessage());
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    // ==================== 网关上游 provider 管理 ====================

    /**
     * 网关型渠道的上游 provider 清单（如 magpie 管理面 {@code GET /api/providers}）。
     *
     * <p>只对声明了 {@link ChannelUpstreamAdmin} 的渠道开放：其它渠道没有
     * 「网关内再挂 provider」这层。凭据一律脱敏返回，编辑时留空即表示保留原 key。</p>
     *
     * <p>网关管理面未开放 / 不可达时 {@code ok=false} + {@code message} 给出可操作原因，
     * 前端据此提示，不影响数据面（对话 / 模型 / 配额）。</p>
     *
     * @param channel      渠道标识
     * @param connectionId 网关连接主键（可空，缺省取主账号）
     * @param label        网关连接标签（可空，兼容旧寻址）
     * @return { ok, channel, baseUrl, pageUrl, manageable, providers, message }
     */
    @GetMapping("/{channel}/providers")
    public ApiResult<Map<String, Object>> listUpstreamProviders(
            @PathVariable("channel") String channel,
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam(value = "label", required = false) String label) {
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin, connectionId, label);
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("ok", true);
        out.put("channel", admin.channel());
        Map<String, Object> address = admin.describe(credentials);
        out.put("baseUrl", address.get("baseUrl"));
        out.put("pageUrl", address.get("pageUrl"));
        try {
            Map<String, Object> listed = admin.list(credentials);
            Object providers = listed == null ? null : listed.get("providers");
            out.put("providers", providers == null ? List.of() : providers);
            out.put("manageable", true);
        } catch (ChannelException e) {
            out.put("ok", false);
            out.put("manageable", false);
            out.put("providers", List.of());
            out.put("message", e.getMessage() == null ? "网关管理接口不可用" : e.getMessage());
        }
        return ApiResult.ok(out);
    }

    /**
     * 新增 / 覆盖 / 重命名网关上游 provider。
     *
     * <p>请求体：{@code { connectionId?, label?, provider: {...}, isNew?, from? }}；
     * {@code provider} 缺失时把请求体其余字段当作 provider 字段。具体的 body 形态
     * 由渠道实现决定，本控制器只负责取凭据与转发。</p>
     *
     * @param channel 渠道标识
     * @param body    请求体
     * @return { ok, channel, result }
     */
    @PostMapping("/{channel}/providers")
    public ApiResult<Map<String, Object>> saveUpstreamProvider(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.save(credentials, safeBody);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        refreshMagpieRoutes();
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 删除网关上游 provider。
     *
     * <p>请求体：{@code { connectionId?, label?, id }}。</p>
     *
     * @param channel 渠道标识
     * @param body    请求体
     * @return { ok, channel, result }
     */
    @PostMapping("/{channel}/providers/delete")
    public ApiResult<Map<String, Object>> deleteUpstreamProvider(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.delete(credentials, asString(safeBody.get("id")));
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        refreshMagpieRoutes();
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 网关上游 provider 的分项动作（如 {@code detect} / {@code models} / {@code test}）。
     *
     * <p>前端用「拉模型」按钮一次性带出 provider 可用模型，避免用户手抄。
     * 动作白名单由渠道实现维护，免得路径段被拼成任意上游调用。</p>
     *
     * @param channel 渠道标识
     * @param action  动作名
     * @param body    请求体（provider 字段）
     * @return { ok, channel, result }
     */
    @PostMapping("/{channel}/providers/{action}")
    public ApiResult<Map<String, Object>> upstreamProviderAction(
            @PathVariable("channel") String channel,
            @PathVariable("action") String action,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.action(credentials, action, safeBody);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 网关插件清单（可选同时读市场）。
     *
     * <p>插件是网关侧的扩展：上游登录方式、签到、模型目录都可能来自插件。
     * {@code market=true} 会访问 npm / GitHub，较慢，前端应单独触发。</p>
     *
     * @param channel      渠道标识
     * @param connectionId 网关连接主键（可空）
     * @param label        网关连接标签（可空）
     * @param market       是否同时读市场
     * @return { ok, channel, result: { plugins, bun, mirror, registry, listings? } }
     */
    @GetMapping("/{channel}/plugins")
    public ApiResult<Map<String, Object>> listUpstreamPlugins(
            @PathVariable("channel") String channel,
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "market", required = false, defaultValue = "false") boolean market) {
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin, connectionId, label);
        Map<String, Object> result;
        try {
            result = admin.plugins(credentials, market);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 网关插件动作：安装 / 卸载 / 升级 / 停用 / 选项 / 换源。
     *
     * <p>{@code add} 首次会下载 Bun 再 npm 安装，magpie 自己给了 5 分钟超时；
     * 调用方（前端）必须按长任务处理，不要按普通接口的短超时判失败。</p>
     *
     * @param channel 渠道标识
     * @param op      动作名
     * @param body    { connectionId?, label?, spec, off?, options?, provider? }
     * @return { ok, channel, result }
     */
    @PostMapping("/{channel}/plugins/{op}")
    public ApiResult<Map<String, Object>> upstreamPluginAction(
            @PathVariable("channel") String channel,
            @PathVariable("op") String op,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.pluginAction(credentials, op, safeBody);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        String normalized = op == null ? "" : op.trim().toLowerCase();
        // 插件动作等于在目标机执行 npm 安装 / 卸载，属于需要留痕的运维操作：
        // 记到服务端日志，便于事后追溯是谁在什么时候装了什么。
        log.info("[ChannelAuth] 插件动作 channel={} op={} spec={} off={}",
                admin.channel(), normalized, asString(safeBody.get("spec")), safeBody.get("off"));
        if (!"registry".equals(normalized) && !"mirror".equals(normalized)
                && !"check".equals(normalized)) {
            refreshMagpieRoutes();
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 管理面额度卡（含签到状态与插件账号）。
     *
     * @param channel      渠道标识
     * @param connectionId 网关连接主键（可空）
     * @param label        网关连接标签（可空）
     * @param provider     只取该 provider（可空）
     * @param user         只取该账号（可空）
     * @return { ok, channel, result: { quotas:[...] } }
     */
    @GetMapping("/{channel}/quotas")
    public ApiResult<Map<String, Object>> upstreamQuotas(
            @PathVariable("channel") String channel,
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "user", required = false) String user) {
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin, connectionId, label);
        Map<String, Object> result;
        try {
            result = admin.quotas(credentials, provider, user);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 让网关重新读一张额度卡。
     *
     * @param channel      渠道标识
     * @param connectionId 网关连接主键（可空）
     * @param label        网关连接标签（可空）
     * @param provider     要刷新的 provider（必填）
     * @param user         要刷新的账号（可空）
     * @return { ok, channel, result: { quotas:[...] } }
     */
    @PostMapping("/{channel}/quotas/refresh")
    public ApiResult<Map<String, Object>> refreshUpstreamQuota(
            @PathVariable("channel") String channel,
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam("provider") String provider,
            @RequestParam(value = "user", required = false) String user) {
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin, connectionId, label);
        Map<String, Object> result;
        try {
            result = admin.refreshQuota(credentials, provider, user);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 发起一次上游登录（内置 provider 或插件 provider）。
     *
     * <p>登录会话由网关自己持有，凭据不经过本服务：这里只把
     * {@code {id, url, state}} 交给前端，由前端打开授权页并轮询状态。</p>
     *
     * @param channel 渠道标识
     * @param body    { connectionId?, label?, plugin?, provider?, method?, inputs?, key?, agent? }
     * @return { ok, channel, result: { id, url, state, ... } }
     */
    @PostMapping("/{channel}/signin")
    public ApiResult<Map<String, Object>> startUpstreamSignin(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.startSignin(credentials, safeBody);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 插件登录前的一问：逐题收集答案，问题问完再发起登录。
     *
     * @param channel 渠道标识
     * @param body    { connectionId?, label?, provider, method?, inputs?, key?, value? }
     * @return { ok, channel, result: { prompt?, inputs?, error? } }
     */
    @PostMapping("/{channel}/signin/prompt")
    public ApiResult<Map<String, Object>> upstreamSigninPrompt(
            @PathVariable("channel") String channel,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        Map<String, Object> result;
        try {
            result = admin.signinPrompt(credentials, safeBody);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 查询登录会话状态（内置与插件共用一个轮询端点）。
     *
     * @param channel      渠道标识
     * @param id           登录会话 id
     * @param connectionId 网关连接主键（可空）
     * @param label        网关连接标签（可空）
     * @return { ok, channel, result: { state, url, user?, error? } }
     */
    @GetMapping("/{channel}/signin/{id}")
    public ApiResult<Map<String, Object>> upstreamSigninStatus(
            @PathVariable("channel") String channel,
            @PathVariable("id") String id,
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam(value = "label", required = false) String label) {
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin, connectionId, label);
        Map<String, Object> result;
        try {
            result = admin.signinStatus(credentials, id);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), result);
    }

    /**
     * 取消登录会话。
     *
     * @param channel 渠道标识
     * @param id      登录会话 id
     * @param body    { connectionId?, label? }
     * @return { ok, channel, result: {} }
     */
    @PostMapping("/{channel}/signin/{id}/cancel")
    public ApiResult<Map<String, Object>> cancelUpstreamSignin(
            @PathVariable("channel") String channel,
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? new LinkedHashMap<>() : body;
        ChannelUpstreamAdmin admin = requireUpstreamAdmin(channel);
        Map<String, Object> credentials = requireUpstreamCredentials(admin,
                asString(safeBody.get("connectionId")), asString(safeBody.get("label")));
        try {
            admin.cancelSignin(credentials, id);
        } catch (ChannelException e) {
            throw channelFailure(e);
        }
        return upstreamResult(admin.channel(), new LinkedHashMap<>());
    }

    /**
     * 取出该渠道实现对应的网关凭据。
     *
     * @param admin        已解析的渠道实现
     * @param connectionId 连接主键（可空）
     * @param label        连接标签（可空）
     * @return 该连接的凭据（必然非空）
     */
    private Map<String, Object> requireUpstreamCredentials(ChannelUpstreamAdmin admin,
                                                          String connectionId, String label) {
        SysAiProviderConnection conn = findTargetConnection(admin.channel(), label, connectionId);
        if (conn == null || conn.getCredentials() == null || conn.getCredentials().isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "尚未绑定 " + admin.channel() + " 网关，请先在「账号」页绑定网关地址与 key");
        }
        return conn.getCredentials();
    }

    /**
     * 上游管理调用的统一返回信封。
     *
     * @param channel 渠道标识
     * @param result  上游响应
     * @return { ok, channel, result }
     */
    private static ApiResult<Map<String, Object>> upstreamResult(String channel, Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>(3);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("result", result == null ? new LinkedHashMap<>() : result);
        return ApiResult.ok(out);
    }

    /**
     * 让 magpie 路由缓存立即失效（新增 / 删除 provider 后新模型马上可路由）。
     */
    private void refreshMagpieRoutes() {
        try {
            magpieRouteSource.invalidate();
            modelClientResolver.invalidateRouteCache();
        } catch (Exception e) {
            log.debug("[ChannelAuth] 清理 magpie 路由缓存失败: {}", e.getMessage());
        }
    }

}
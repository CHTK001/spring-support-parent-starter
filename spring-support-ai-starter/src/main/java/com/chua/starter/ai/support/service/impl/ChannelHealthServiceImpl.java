// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.event.ChannelHealthProbeEvent;
import com.chua.starter.account.channel.service.AccountChannelConnectionService;
import com.chua.starter.account.channel.service.AccountChannelHealthService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.ChannelHealth;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.ai.support.service.ChannelHealthService;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.ai.support.util.ProviderCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 渠道健康探针服务实现。
 *
 * <p>对标 Sub2API Channel Monitor：以「提供者 + 连接」为粒度，对每个渠道发轻量探针
 * （OAuth 渠道走 {@link AccountChannelAuthManager#status()}，API Key 渠道走
 * {@code GET {baseUrl}/v1/models}），把成功/失败/延迟/限流汇正成状态机（UNKNOWN /
 * ONLINE / WARNING / DOWN / RATE_LIMITED），供路由层消费（下线通道断路保护、恢复后自动上浮）。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelHealthServiceImpl implements ChannelHealthService {

    /**
     * 渠道健康记录（账号模块）：记录怎么读、怎么落库
     */
    private final AccountChannelHealthService healthService;
    /**
     * 渠道业务（账号模块）：渠道行与启用口径
     */
    private final AccountChannelService channelService;
    /**
     * 渠道连接业务（账号模块）：探针要覆盖全量连接
     */
    private final AccountChannelConnectionService connectionService;
    private final GlobalSettingReader settingReader;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 渠道健康探针分组
     */
    private static final String GROUP = "health";

    /**
     * 探针总开关（默认开）
     */
    private static final String SETTING_ENABLE = "channel_health_probe_enabled";
    /**
     * 探针间隔秒数
     */
    private static final String SETTING_INTERVAL_SEC = "channel_health_probe_interval_sec";
    /**
     * 连续失败至下线数（默认 3）
     */
    private static final String SETTING_FAILURE_TO_DOWN = "channel_health_failure_to_down";
    /**
     * 连续成功至恢复数（默认 2）
     */
    private static final String SETTING_DOWN_TO_RECOVER = "channel_health_down_to_recover";
    /**
     * 延迟警告门限（毫秒，默认 3000）
     */
    private static final String SETTING_WARNING_LATENCY = "channel_health_warning_latency_ms";
    /**
     * 探针超时（秒，默认 10）
     */
    private static final String SETTING_TIMEOUT_SEC = "channel_health_probe_timeout_sec";
    /**
     * 命中限流状态码（默认 429）
     */
    private static final String SETTING_RATE_LIMITED_STATUS = "channel_health_rate_limited_status";
    /**
     * 404 时回退到源站根路径探针（区分端点错误与通道不可达）
     */
    private static final String SETTING_ROOT_FALLBACK = "channel_health_probe_root_fallback";

    /**
     * 默认探针间隔秒数
     */
    private static final int DEFAULT_INTERVAL_SEC = 60;
    /**
     * 最小探针间隔秒数（30s）
     */
    private static final int MIN_INTERVAL_SEC = 30;
    /**
     * 默认连续失败至下线数
     */
    private static final int DEFAULT_FAILURE_TO_DOWN = 3;
    /**
     * 默认连续成功至恢复数
     */
    private static final int DEFAULT_DOWN_TO_RECOVER = 2;
    /**
     * 默认延迟警告门限（毫秒）
     */
    private static final int DEFAULT_WARNING_LATENCY_MS = 3000;
    /**
     * 默认探针超时秒数
     */
    private static final int DEFAULT_TIMEOUT_SEC = 10;
    /**
     * 探针 URL 的模型列表端点
     */
    private static final String PROBE_PATH = "/v1/models";
    /**
     * 404 回退的源站根路径探针
     */
    private static final String FALLBACK_PATH = "/";

    /**
     * 过期令牌续期的冷却时间（毫秒）：refresh_token 失效时不至于每次探针都打上游
     */
    private static final long REFRESH_COOLDOWN_MILLIS = 5 * 60 * 1000L;

    /**
     * 凭据状态：可用（渠道实现 {@code status()} 返回的 state）
     */
    private static final String CREDENTIAL_VALID = "valid";

    /**
     * 凭据状态：已到期，可按 refresh_token 续期
     */
    private static final String CREDENTIAL_EXPIRED = "expired";

    /**
     * 渠道鉴权 SPI 实现映射（懒加载，首次调用时从 SPI 注册表拉取）
     */
    private volatile Map<String, AccountChannelAuthManager> channelManagers;

    /**
     * 可路由判定的 1 秒本地快照（路由解析每请求每连接调用一次，避免打爆 DB）
     */
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> routableCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(1, TimeUnit.SECONDS)
                    .maximumSize(4096)
                    .build();

    /**
     * 最近一次续期尝试时刻：connectionId → 毫秒时间戳（失败冷却用，见 {@link #REFRESH_COOLDOWN_MILLIS}）
     */
    private final Map<String, Long> refreshAttempts = new ConcurrentHashMap<>();

    // ==================== 设置读取 ====================

    /**
     * 探针总开关（默认开）
     *
     * @return true 表示启用
     */
    @Override
    public boolean enabled() {
        return settingReader.getBool(GROUP, SETTING_ENABLE, true);
    }

    /**
     * 探针间隔秒数（最小 30 秒）
     *
     * @return 间隔秒数
     */
    @Override
    public int intervalSec() {
        int interval = settingReader.getInt(GROUP, SETTING_INTERVAL_SEC, DEFAULT_INTERVAL_SEC);
        return Math.max(interval, MIN_INTERVAL_SEC);
    }

    /**
     * 连续失败至下线数
     *
     * @return 失败次数阈值
     */
    @Override
    public int failureToDown() {
        return settingReader.getInt(GROUP, SETTING_FAILURE_TO_DOWN, DEFAULT_FAILURE_TO_DOWN);
    }

    /**
     * 连续成功至恢复数
     *
     * @return 成功次数阈值
     */
    @Override
    public int downToRecover() {
        return settingReader.getInt(GROUP, SETTING_DOWN_TO_RECOVER, DEFAULT_DOWN_TO_RECOVER);
    }

    /**
     * 延迟警告门限（毫秒）
     *
     * @return 毫秒数
     */
    @Override
    public int warningLatencyMs() {
        return settingReader.getInt(GROUP, SETTING_WARNING_LATENCY, DEFAULT_WARNING_LATENCY_MS);
    }

    /**
     * 探针超时秒数
     *
     * @return 秒数
     */
    @Override
    public int probeTimeoutSec() {
        return settingReader.getInt(GROUP, SETTING_TIMEOUT_SEC, DEFAULT_TIMEOUT_SEC);
    }

    /**
     * 命中限流状态码
     *
     * @return 状态码
     */
    @Override
    public int rateLimitedStatus() {
        return settingReader.getInt(GROUP, SETTING_RATE_LIMITED_STATUS, 429);
    }

    /**
     * 404 是否回退到源站根路径探针
     *
     * @return true 表示回退
     */
    @Override
    public boolean rootFallback() {
        return settingReader.getBool(GROUP, SETTING_ROOT_FALLBACK, true);
    }

    // ==================== 查询 ====================

    /**
     * 列出全部渠道的健康状态（按 provider → connection）
     *
     * @return 状态列表
     */
    @Override
    public List<ChannelHealth> listStates() {
        return healthService.listAll();
    }

    /**
     * 列出指定供应商的渠道健康状态
     *
     * @param providerId 供应商标识
     * @return 状态列表
     */
    @Override
    public List<ChannelHealth> listByProvider(String providerId) {
        return healthService.listByProvider(providerId);
    }

    /**
     * 按连接标识取健康状态
     *
     * @param connectionId 连接标识
     * @return 健康状态；未探测时返回 null
     */
    @Override
    public ChannelHealth getState(String connectionId) {
        return healthService.find(connectionId);
    }

    /**
     * 按连接标识取健康状态（未探测时返回 UNKNOWN 默认行，供路由层使用）
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    public ChannelHealth state(String connectionId) {
        return Optional.ofNullable(healthService.find(connectionId))
                .orElseGet(() -> channelState(connectionId));
    }

    /**
     * 构造 UNKNOWN 默认健康记录行（库中无记录时兜底）。
     *
     * @param connectionId 连接标识
     * @return 健康记录行（state=UNKNOWN）
     */
    private ChannelHealth channelState(String connectionId) {
        ChannelHealth h = new ChannelHealth();
        h.setId(connectionId);
        h.setState(STATE_UNKNOWN);
        return h;
    }

    /**
     * 是否已下线
     *
     * @param connectionId 连接标识
     * @return true 表示仍在线下（路由层应剔除）
     */
    @Override
    public boolean isDown(String connectionId) {
        return STATE_DOWN.equals(state(connectionId).getState());
    }

    /**
     * 连接是否可用（在线 / 警告 / 未探测均可用，DOWN 与限流态不可用）
     *
     * @param connectionId 连接标识
     * @return true 表示可用
     */
    @Override
    public boolean isHealthy(String connectionId) {
        String s = state(connectionId).getState();
        return !STATE_DOWN.equals(s) && !STATE_RATE_LIMITED.equals(s);
    }

    /**
     * 综合探针状态与人工熔断开关，判定该连接是否可路由。
     *
     * <p>路由解析（{@link com.chua.starter.ai.support.chat.ModelClientResolver}）在每次
     * 建客户端时对每个连接调用本方法，因此结果做 1 秒本地快照：探针轮询本身是 30 秒级，
     * 人工开关 1 秒生效对运维完全够用，而每条请求的数据库往返被摊薄到接近 0。</p>
     *
     * @param connectionId 连接标识
     * @return true 表示可路由
     */
    @Override
    public boolean isRoutable(String connectionId) {
        if (StringUtils.isBlank(connectionId)) {
            return true;
        }
        Boolean cached = routableCache.getIfPresent(connectionId);
        if (cached != null) {
            return cached;
        }
        boolean routable = computeRoutable(connectionId);
        routableCache.put(connectionId, routable);
        return routable;
    }

    /**
     * 实际判定（无缓存）。
     *
     * @param connectionId 连接标识
     * @return true 表示可路由
     */
    private boolean computeRoutable(String connectionId) {
        try {
            ChannelHealth h = state(connectionId);
            String circuit = h.getCircuitState();
            if (CIRCUIT_OPEN.equalsIgnoreCase(circuit)) {
                return false;
            }
            if (CIRCUIT_CLOSED.equalsIgnoreCase(circuit)) {
                return true;
            }
            return !STATE_DOWN.equals(h.getState()) && !STATE_RATE_LIMITED.equals(h.getState());
        } catch (Exception e) {
            // 判定失败放行：熔断器/探针自身不得成为故障点
            log.warn("[ChannelHealth] 路由判定失败 connection={}，放行: {}", connectionId, e.getMessage());
            return true;
        }
    }

    /**
     * 设定人工熔断状态。
     *
     * @param connectionId 连接标识
     * @param state        AUTO / OPEN / CLOSED（大小写不敏感，非法值按 AUTO 处理）
     * @param reason       原因说明，可为空
     * @return 设定后的状态行
     */
    @Override
    public ChannelHealth updateCircuitState(String connectionId, String state, String reason) {
        SysAccountChannelConnection conn = connection(connectionId);
        String normalized = normalizeCircuitState(state);
        ChannelHealth h = initOrCreate(connectionId, null, conn);
        long now = System.currentTimeMillis();
        String previous = h.getCircuitState();
        h.setCircuitState(normalized);
        h.setCircuitReason(StringUtils.isBlank(reason) ? null : reason.trim());
        h.setCircuitUpdatedAt(now);
        h.setUpdatedAt(now);
        saveHealth(h);
        routableCache.invalidate(connectionId);
        log.info("[ChannelHealth] 人工熔断开关 connection={} {} -> {} 原因={}",
                connectionId, previous, normalized, h.getCircuitReason());
        // 失效路由缓存，令强制下线/放行立即生效（否则最多等 5 分钟 TTL）
        eventPublisher.publishEvent(new ChannelHealthProbeEvent(this, downConnections(), 0, false));
        return h;
    }

    /**
     * 归一化人工熔断状态。
     *
     * @param state 原始值
     * @return AUTO / OPEN / CLOSED
     */
    private static String normalizeCircuitState(String state) {
        if (StringUtils.isBlank(state)) {
            return CIRCUIT_AUTO;
        }
        String value = state.trim().toUpperCase();
        if (CIRCUIT_OPEN.equals(value)) {
            return CIRCUIT_OPEN;
        }
        if (CIRCUIT_CLOSED.equals(value)) {
            return CIRCUIT_CLOSED;
        }
        return CIRCUIT_AUTO;
    }

    /**
     * 取全部下线的连接标识（路由层剔除）
     *
     * @return 下线的连接标识集合
     */
    @Override
    public Set<String> downConnections() {
        return healthService.connectionIdsByState(STATE_DOWN);
    }

    /**
     * 按连接标识取统计摘要：total + 各状态数
     *
     * @param providerId 供应商标识
     * @return 统计
     */
    @Override
    public Map<String, Integer> statByProvider(String providerId) {
        Map<String, Integer> stat = new LinkedHashMap<>();
        stat.put("total", 0);
        for (String s : List.of(STATE_UNKNOWN, STATE_ONLINE, STATE_WARNING, STATE_DOWN, STATE_RATE_LIMITED)) {
            stat.put(s, 0);
        }
        List<ChannelHealth> list = listByProvider(providerId);
        stat.put("total", list.size());
        for (ChannelHealth h : list) {
            stat.merge(h.getState() == null ? STATE_UNKNOWN : h.getState(), 1, Integer::sum);
        }
        return stat;
    }

    // ==================== 探针 ====================

    /**
     * 手动触发一次探针（管理页「立即探针」）
     *
     * @param connectionId 连接标识
     * @return 探针结果状态
     */
    @Override
    public ChannelHealth probe(String connectionId) {
        SysAccountChannelConnection conn = connection(connectionId);
        SysAccountChannel provider = provider(conn.getProviderId());
        try {
            probeConnection(provider, conn);
        } catch (Exception e) {
            markFailure(connectionId, 0, StringUtils.defaultString(e.getMessage(), ""), null, null);
        }
        return getState(connectionId);
    }

    /**
     * 手动触发全部渠道探针（管理页「全部探针」）
     *
     * @return 探针完成的连接数
     */
    @Override
    public int probeAll() {
        int probed = 0;
        for (SysAccountChannel provider : providers()) {
            for (SysAccountChannelConnection conn : connections(provider)) {
                try {
                    probeConnection(provider, conn);
                    probed++;
                } catch (Exception e) {
                    log.warn("[ChannelHealthProbe] 探针失败 provider={} connection={} : {}",
                            provider.getProviderId(), conn.getConnectionId(), e.getMessage());
                }
            }
        }
        Set<String> down = downConnections();
        if (!down.isEmpty()) {
            log.warn("[ChannelHealthProbe] 通道探针下线 channels={}", down);
        }
        eventPublisher.publishEvent(new ChannelHealthProbeEvent(this, down, probed, true));
        log.info("[ChannelHealthProbe] 渠道探针完成 connections={}", probed);
        return probed;
    }

    /**
     * 供应商维度的全部探针
     *
     * @param providerId 供应商标识
     * @return 探针完成的连接数
     */
    public int probeByProvider(String providerId) {
        SysAccountChannel provider = provider(providerId);
        int probed = 0;
        for (SysAccountChannelConnection conn : connections(provider)) {
            try {
                probeConnection(provider, conn);
                probed++;
            } catch (Exception e) {
                log.warn("[ChannelHealthProbe] 探针失败 provider={} connection={} : {}",
                        providerId, conn.getConnectionId(), e.getMessage());
            }
        }
        return probed;
    }

    /**
     * 按渠道（平台标识）触发探针：遍历该渠道下全部启用供应商的连接并逐个探活。
     *
     * <p>按渠道独立间隔的定时任务（时间轮）用此入口，避免每次都全量探一遍：
     * 不同渠道的上游限流阈值与凭据寿命不同，探针节奏本就应该分开。</p>
     *
     * @param channel 渠道平台标识
     * @return 探针完成的连接数
     */
    @Override
    public int probeByChannel(String channel) {
        if (StringUtils.isBlank(channel)) {
            return 0;
        }
        int probed = 0;
        for (SysAccountChannel provider : providers()) {
            if (!channel.equalsIgnoreCase(provider.getPlatform())) {
                continue;
            }
            for (SysAccountChannelConnection conn : connections(provider)) {
                try {
                    probeConnection(provider, conn);
                    probed++;
                } catch (Exception e) {
                    log.warn("[ChannelHealthProbe] 探针失败 channel={} connection={} : {}",
                            channel, conn.getConnectionId(), e.getMessage());
                }
            }
        }
        if (probed > 0) {
            log.debug("[ChannelHealthProbe] 按渠道探针完成 channel={} connections={}", channel, probed);
        }
        return probed;
    }

    /**
     * 已启用的供应商
     *
     * @return 供应商列表
     */
    private List<SysAccountChannel> providers() {
        return channelService.listEnabled();
    }

    /**
     * 连接列表
     *
     * @param provider 供应商
     * @return 连接列表
     */
    private List<SysAccountChannelConnection> connections(SysAccountChannel provider) {
        // 探针要覆盖全量连接（含其它账号绑定的），不能用带归属过滤的 list
        return connectionService.listAll(provider.getProviderId());
    }

    /**
     * 按连接标识解析连接
     *
     * @param connectionId 连接标识
     * @return 连接
     */
    private SysAccountChannelConnection connection(String connectionId) {
        SysAccountChannelConnection conn = connectionService.find(connectionId);
        if (conn == null || conn.getProviderId() == null) {
            throw new IllegalArgumentException("找不到此连接: " + connectionId);
        }
        return conn;
    }

    /**
     * 按供应商 ID 查已启用的供应商，不存在时抛异常。
     *
     * @param providerId 供应商 ID
     * @return 已启用的供应商
     */
    private SysAccountChannel provider(String providerId) {
        SysAccountChannel provider = channelService.find(providerId);
        if (provider == null || provider.getEnabled() == null || !provider.getEnabled()) {
            throw new IllegalArgumentException("找不到已启用的供应商: " + providerId);
        }
        return provider;
    }

    /**
     * 本次探针单次执行：authScheme 分发到 OAuth 或 HTTP 探针
     *
     * @param provider 供应商
     * @param conn 连接
     */
    private void probeConnection(SysAccountChannel provider, SysAccountChannelConnection conn) {
        String connectionId = conn.getConnectionId();
        String scheme = authScheme(provider, conn);
        try {
            ChannelHealth h = initOrCreate(connectionId, provider, conn);
            if ("oauth".equals(scheme)) {
                probeOauth(provider, conn, h);
            } else {
                probeHttp(provider, conn, h);
            }
        } catch (Exception e) {
            markFailure(connectionId, 0, StringUtils.defaultString(e.getMessage(), ""), null, null);
        }
    }

    // ==================== OAuth 探针 ====================

    /**
     * OAuth 渠道探针：用凭据状态检查（免费接口）作为探活
     *
     * @param provider 供应商
     * @param conn 连接
     * @param h 健康记录行
     */
    private void probeOauth(SysAccountChannel provider, SysAccountChannelConnection conn, ChannelHealth h) {
        String connectionId = conn.getConnectionId();
        if (conn.getCredentials() == null || conn.getCredentials().isEmpty()) {
            markUnknown(connectionId, provider, conn);
            return;
        }
        AccountChannelAuthManager impl = manager(conn.getProviderId());
        if (impl == null) {
            // 找不到的 SPI 实现（无白名单的渠道），不标记为故障
            markUnknown(connectionId, provider, conn);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            Map<String, Object> status = impl.status(conn.getCredentials());
            String state = stateOf(status);
            // 令牌过期是 OAuth 渠道的常态，不是故障：先按 refresh_token 续期再判。
            // 不续期的话探针每次都记一次失败，连续 3 次后连接被置 DOWN，
            // 路由层（ModelClientResolver）就会把整条渠道的模型从免费渠道里剔除 ——
            // 表现为「渠道管理里模型是开着的，免费渠道里一个都看不到」。
            if (CREDENTIAL_EXPIRED.equals(state)) {
                Map<String, Object> renewed = renewCredentials(conn, impl);
                if (renewed != null) {
                    status = impl.status(renewed);
                    state = stateOf(status);
                    if (CREDENTIAL_VALID.equals(state)) {
                        // 续期成功即复位失败计数：否则 DOWN 状态要再连续成功 N 次才解除，
                        // 模型会白等几个探针周期。
                        resetState(connectionId);
                    }
                }
            }
            long latency = System.currentTimeMillis() - start;
            if (CREDENTIAL_VALID.equals(state)) {
                markSuccess(connectionId, 200, (int) latency, provider, conn);
            } else {
                markFailure(connectionId, 0, "auth status: " + state, provider, conn);
            }
        } catch (Exception e) {
            markFailure(connectionId, 0, StringUtils.defaultString(e.getMessage(), ""), null, null);
        }
    }

    /**
     * 用 refresh_token 换新凭据并落库。
     *
     * <p>失败（refresh_token 被吊销、渠道不支持续期）只记日志并进入冷却，
     * 由探针按 {@link #REFRESH_COOLDOWN_MILLIS} 节流，不阻断其它渠道。</p>
     *
     * @param conn 连接（续期成功后原地更新并写回库）
     * @param impl 渠道实现
     * @return 新凭据；续期失败或处于冷却期返回 {@code null}
     */
    private Map<String, Object> renewCredentials(SysAccountChannelConnection conn,
                                                 AccountChannelAuthManager impl) {
        String connectionId = conn.getConnectionId();
        long now = System.currentTimeMillis();
        Long last = refreshAttempts.get(connectionId);
        if (last != null && now - last < REFRESH_COOLDOWN_MILLIS) {
            return null;
        }
        refreshAttempts.put(connectionId, now);
        try {
            Map<String, Object> renewed = impl.refresh(conn.getCredentials());
            if (renewed == null || renewed.isEmpty()) {
                log.warn("[ChannelHealthProbe] 续期返回空凭据 connection={} channel={}",
                        connectionId, conn.getProviderId());
                return null;
            }
            conn.setCredentials(renewed);
            conn.setUpdatedAt(now);
            // 续期只写凭证列，整行 update 会连归属/状态一起覆盖
            connectionService.updateCredentials(connectionId, renewed);
            // 立即让路由与客户端缓存失效，新模型不用等 5 分钟 TTL
            eventPublisher.publishEvent(
                    new ChannelHealthProbeEvent(this, downConnections(), 0, false));
            log.info("[ChannelHealthProbe] 令牌续期成功 connection={} channel={}",
                    connectionId, conn.getProviderId());
            return renewed;
        } catch (Exception e) {
            log.warn("[ChannelHealthProbe] 令牌续期失败 connection={} channel={} : {}",
                    connectionId, conn.getProviderId(), e.getMessage());
            return null;
        }
    }

    /**
     * 取渠道状态里的 {@code state} 字段。
     *
     * @param status 渠道状态
     * @return 状态字符串（{@code valid} / {@code expired} / {@code invalid} 等）；缺失返回空串
     */
    private static String stateOf(Map<String, Object> status) {
        Object state = status == null ? null : status.get("state");
        return state == null ? "" : String.valueOf(state);
    }

    // ==================== HTTP 探针 ====================

    /**
     * HTTP 探针：{ @code GET {baseUrl}/v1/models}（API Key / Bearer / 无认证），
     * 探活成功 → ONLINE，失败 → WARNING / DOWN
     *
     * @param provider 供应商
     * @param conn 连接
     * @param h 健康记录行
     */
    private void probeHttp(SysAccountChannel provider, SysAccountChannelConnection conn, ChannelHealth h) {
        String connectionId = conn.getConnectionId();
        String baseUrl = baseUrl(conn, provider);
        if (baseUrl == null || baseUrl.isBlank()) {
            markUnknown(connectionId, provider, conn);
            return;
        }
        int timeoutSec = probeTimeoutSec();
        long start = System.currentTimeMillis();
        String url = modelsUrl(baseUrl);
        String header = authHeader(conn, provider);
        int status = 0;
        String error = null;
        String urlUsed = url;
        try {
            ClientResponse resp = send(url, header, provider.getProxy(), timeoutSec);
            status = resp.getStatusCode();
            if (status == 404 && rootFallback()) {
                // 端点可能不是 OpenAI 兼容路径：回退到源站根路径，区分开端点错误
                urlUsed = baseUrl;
                resp = send(baseUrl, header, provider.getProxy(), timeoutSec);
                status = resp.getStatusCode();
            }
        } catch (Exception e) {
            error = StringUtils.defaultString(e.getMessage(), "");
        }
        int latency = (int) (System.currentTimeMillis() - start);
        if (error != null) {
            markFailure(connectionId, 0, "probe (" + urlUsed + "): " + error, provider, conn);
            return;
        }
        if (status < 300) {
            markSuccess(connectionId, status, latency, provider, conn);
        } else {
            markFailure(connectionId, status, "HTTP " + status, provider, conn);
        }
    }

    // ==================== 标记 ====================

    /**
     * 标记未知（无凭据/无地址/找不到 SPI 的渠道，不判为故障）
     *
     * @param connectionId 连接标识
     * @param provider 供应商
     * @param conn 连接
     */
    private void markUnknown(String connectionId, SysAccountChannel provider, SysAccountChannelConnection conn) {
        ChannelHealth h = initOrCreate(connectionId, provider, conn);
        h.setState(STATE_UNKNOWN);
        h.setUpdatedAt(System.currentTimeMillis());
        saveHealth(h);
    }

    /**
     * 一次成功探活
     *
     * @param connectionId 连接标识
     * @param status HTTP 状态码
     * @param latency 延迟（毫秒）
     */
    @Override
    public void markSuccess(String connectionId, int status, int latency) {
        markSuccess(connectionId, status, latency, null, null);
    }

    /**
     * 一次失败探活
     *
     * @param connectionId 连接标识
     * @param status HTTP 状态码（失败时可抛异常，传 0）
     * @param error 错误信息
     */
    @Override
    public void markFailure(String connectionId, int status, String error) {
        markFailure(connectionId, status, error, null, null);
    }

    /**
     * 设定该连接的探针间隔（0 = 走全局设置，否则 30/60/300）
     *
     * @param connectionId 连接标识
     * @param interval 间隔秒数（30/60/300）
     */
    @Override
    public void updateInterval(String connectionId, int interval) {
        SysAccountChannelConnection conn = connection(connectionId);
        ChannelHealth h = initOrCreate(connectionId, null, conn);
        h.setIntervalSeconds(Math.max(0, interval));
        h.setUpdatedAt(System.currentTimeMillis());
        saveHealth(h);
    }

    /**
     * 重置连接的健康状态（启动后手动复位）
     *
     * @param connectionId 连接标识
     */
    @Override
    public void resetState(String connectionId) {
        SysAccountChannelConnection conn = connection(connectionId);
        ChannelHealth h = initOrCreate(connectionId, null, conn);
        h.setState(STATE_UNKNOWN);
        h.setFailures(0L);
        h.setSuccesses(0L);
        h.setConsecutiveFailures(0);
        h.setConsecutiveSuccesses(0);
        h.setTotalFailures(0L);
        h.setLastProbeAt(null);
        h.setLastProbeStatus(null);
        h.setLastProbeLatency(null);
        h.setLastProbeError(null);
        h.setAverageLatency(null);
        h.setUpdatedAt(System.currentTimeMillis());
        saveHealth(h);
        routableCache.invalidate(connectionId);
    }

    /**
     * 记录一次探针成功。
     *
     * @param connectionId 连接标识
     * @param status       HTTP 状态码
     * @param latency      延迟（毫秒）
     * @param provider     供应商（可空）
     * @param conn         连接（可空）
     */
    private void markSuccess(String connectionId, int status, int latency,
                             SysAccountChannel provider, SysAccountChannelConnection conn) {
        mark(connectionId, true, status, latency, null, provider, conn);
    }

    /**
     * 记录一次探针失败。
     *
     * @param connectionId 连接标识
     * @param status       HTTP 状态码
     * @param error        错误信息
     * @param provider     供应商（可空）
     * @param conn         连接（可空）
     */
    private void markFailure(String connectionId, int status, String error,
                             SysAccountChannel provider, SysAccountChannelConnection conn) {
        mark(connectionId, false, status, 0, error, provider, conn);
    }

    /**
     * 状态机核心：按探针结果更新连通
     *
     * @param connectionId 连接标识
     * @param ok 是否成功
     * @param status HTTP 状态码
     * @param latency 延迟（毫秒）
     * @param error 错误信息
     * @param provider 供应者
     * @param conn 连接
     */
    private void mark(String connectionId, boolean ok, int status, int latency, String error,
                      SysAccountChannel provider, SysAccountChannelConnection conn) {
        ChannelHealth h = initOrCreate(connectionId, provider, conn);

        boolean rateLimited = isRateLimited(status);
        boolean wasDown = STATE_DOWN.equals(h.getState());
        boolean wasRateLimited = STATE_RATE_LIMITED.equals(h.getState());

        long now = System.currentTimeMillis();
        h.setLastProbeAt(now);
        h.setLastProbeStatus(status);
        h.setLastProbeLatency(latency);
        h.setLastProbeError(error);
        h.setUpdatedAt(now);

        if (rateLimited) {
            // 命中小限流：计入失败次数并标记限流态，不清连续失败计数
            h.setFailures(h.getFailures() + 1);
            h.setTotalFailures(h.getTotalFailures() + 1);
            h.setConsecutiveFailures(h.getConsecutiveFailures() + 1);
            h.setState(STATE_RATE_LIMITED);
        } else if (ok) {
            h.setSuccesses(h.getSuccesses() + 1);
            h.setConsecutiveSuccesses(h.getConsecutiveSuccesses() + 1);
            h.setConsecutiveFailures(0);
            // 指数加权平均延迟
            int avg = Optional.ofNullable(h.getAverageLatency()).orElse(0);
            long count = h.getSuccesses();
            h.setAverageLatency(avg == 0 ? latency : (int) Math.round(((long) avg * (count - 1) + latency) / count));
            if (wasDown) {
                // 恢复：连续成功达阈值
                h.setState(h.getConsecutiveSuccesses() >= downToRecover() ? STATE_ONLINE : STATE_DOWN);
            } else if (wasRateLimited) {
                // 限流态后成功：恢复在线
                h.setState(STATE_ONLINE);
            } else if (latency > warningLatencyMs()) {
                h.setState(STATE_WARNING);
            } else {
                h.setState(STATE_ONLINE);
            }
        } else {
            h.setFailures(h.getFailures() + 1);
            h.setTotalFailures(h.getTotalFailures() + 1);
            h.setConsecutiveFailures(h.getConsecutiveFailures() + 1);
            h.setConsecutiveSuccesses(0);
            if (h.getConsecutiveFailures() >= failureToDown()) {
                h.setState(STATE_DOWN);
            } else {
                h.setState(STATE_WARNING);
            }
        }
        saveHealth(h);
        routableCache.invalidate(connectionId);
    }

    // ==================== 探针执行 ====================

    /**
     * 发起一次 HTTP GET 探针请求。
     *
     * @param url        目标 URL
     * @param header     Authorization 头（可空）
     * @param proxy      代理地址（host:port，可空）
     * @param timeoutSec 连接/读取超时（秒）
     * @return 响应
     * @throws Exception 请求失败
     */
    private ClientResponse send(String url, String header, String proxy, int timeoutSec) throws Exception {
        try {
            HttpClientBuilder builder = HttpClientFactory.of(url);
            if (StringUtils.isNotBlank(header)) {
                builder.authorization(header);
            }
            if (StringUtils.isNotBlank(proxy)) {
                try {
                    URI u = new URI(proxy);
                    builder.proxy(u.getHost(), u.getPort());
                } catch (Exception e) {
                    log.warn("[ChannelHealth] 代理地址解析失败 {}: {}", proxy, e.getMessage());
                }
            }
            builder.connectTimeout(TimeUnit.SECONDS.toMillis(timeoutSec));
            builder.readTimeout(TimeUnit.SECONDS.toMillis(timeoutSec));
            return builder.get();
        } catch (Exception e) {
            log.warn("[ChannelHealth] 探针失败 {}: {}", url, e.getMessage());
            throw e;
        }
    }

    // ==================== 辅助 ====================

    /**
     * 取来源站地址，connection 覆盖 supplier
     *
     * @param conn 连接
     * @param provider 供应商
     * @return 地址
     */
    private String baseUrl(SysAccountChannelConnection conn, SysAccountChannel provider) {
        String baseUrl = Optional.ofNullable(conn.getBaseUrl())
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> provider.getBaseUrl());
        return StringUtils.isBlank(baseUrl) ? null : baseUrl.trim();
    }

    /**
     * 组装探活认证头
     *
     * @param conn 连接
     * @param provider 供应商
     * @return 认证头值（无凭据时返回 null）
     */
    private String authHeader(SysAccountChannelConnection conn, SysAccountChannel provider) {
        String apiKey = ProviderCredentials.firstApiKey(
                Optional.ofNullable(conn.getCredentials()).orElseGet(provider::getCredentials));
        return StringUtils.isBlank(apiKey) ? null : "Bearer " + apiKey;
    }

    /**
     * 构建模型列表端点
     *
     * @param baseUrl 源站
     * @return 探针地址
     */
    private static String modelsUrl(String baseUrl) {
        String url = baseUrl.trim();
        if (url.endsWith("/v1beta/openai/")) {
            return url + "models";
        }
        if (url.endsWith("/v1beta/openai")) {
            return url + "/models";
        }
        if (url.matches(".*(/v1|/v1-beta|/v2|/v3|/v1beta)$")) {
            return url + "/models";
        }
        return url + PROBE_PATH;
    }

    /**
     * 认证方案：connection 覆盖 supplier
     *
     * @param provider 供应商
     * @param conn 连接
     * @return 认证方案
     */
    private static String authScheme(SysAccountChannel provider, SysAccountChannelConnection conn) {
        String scheme = conn.getAuthScheme();
        if (StringUtils.isNotBlank(scheme)) {
            return scheme;
        }
        return provider.getAuthScheme();
    }

    /**
     * 限流是否到达：被配置的限流状态码标记
     *
     * @param status 状态码
     * @return true 为限流
     */
    private boolean isRateLimited(int status) {
        return status == rateLimitedStatus();
    }

    /**
     * 渠道鉴权 SPI 实现（懒加载，幂等）
     *
     * @param providerId 供应商标识
     * @return SPI 实现；未找到返回 null
     */
    private AccountChannelAuthManager manager(String providerId) {
        Map<String, AccountChannelAuthManager> managers = channelManagers();
        // platform → channel 映射：SPI 的 channel() 通常就是 providerId（如 cline/qoder/trae/gemini）
        String channel = null;
        for (Map.Entry<String, AccountChannelAuthManager> e : managers.entrySet()) {
            if (e.getValue() != null && providerId.equalsIgnoreCase(e.getValue().channel())) {
                channel = e.getKey();
                break;
            }
        }
        return channel == null ? null : managers.get(channel);
    }

    /**
     * 渠道鉴权 SPI 实现列表（懒加载，幂等）。
     *
     * @return platform → 渠道鉴权实现映射
     */
    private Map<String, AccountChannelAuthManager> channelManagers() {
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
     * 初始化或创建健康记录行（新行补全元数据）
     *
     * @param connectionId 连接标识
     * @param provider 供应商
     * @param conn 连接
     * @return 健康记录行
     */
    private ChannelHealth initOrCreate(String connectionId, SysAccountChannel provider, SysAccountChannelConnection conn) {
        ChannelHealth h = healthService.find(connectionId);
        if (h != null) {
            return h;
        }
        if (provider == null || conn == null) {
            if (conn == null) {
                conn = connectionService.find(connectionId);
                provider = conn == null ? null : channelService.find(conn.getProviderId());
            } else {
                // 之前这里错把 connectionId 当 providerId 查（永远查不到），
                // 导致人工熔断/手工探针新建的健康行缺 provider_id 与 channel，按渠道聚合时丢行。
                provider = channelService.find(conn.getProviderId());
            }
        }
        h = new ChannelHealth();
        h.setId(connectionId);
        h.setCreatedAt(System.currentTimeMillis());
        h.setUpdatedAt(h.getCreatedAt());
        h.setState(STATE_UNKNOWN);
        if (provider != null) {
            h.setProviderId(provider.getProviderId());
        }
        if (conn != null) {
            h.setConnectionId(conn.getConnectionId());
            if (provider != null) {
                h.setChannel(provider.getPlatform());
            }
            h.setLabel(conn.getLabel());
        }
        return h;
    }

    /**
     * 保存健康记录（不存在则插入，存在则更新）。
     *
     * @param h 健康记录行
     */
    private void saveHealth(ChannelHealth h) {
        healthService.save(h);
    }
}
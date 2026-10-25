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
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final SysAiProviderMapper providerMapper;
    private final SysAiProviderConnectionMapper connectionMapper;
    private final SysAiProviderModelMapper modelMapper;

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
     * SSE 登录流的执行器（守护线程，按需创建，空闲回收）。
     *
     * <p>⚠️ 不用 Spring 的公共 task executor：单次 {@code pollLogin} 可能阻塞在
     * 上游 HTTP 上（最长 15s 连接超时），放在公共池里会挤占其它异步任务。</p>
     */
    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger SEQ = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
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
     * @return 渠道列表，每项 { channel, displayName, loginMethod, supportsCheckin,
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
        Map<String, Object> summary = new LinkedHashMap<>(12);
        summary.put("channel", channel);
        summary.put("displayName", impl.displayName());
        summary.put("loginMethod", impl.loginMethod());
        summary.put("supportsCheckin", impl.supportsCheckin());
        summary.put("providerId", provider == null ? null : provider.getProviderId());
        summary.put("enabled", provider == null ? false : Boolean.TRUE.equals(provider.getEnabled()));

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
                cm.put("hasCredentials", c.getCredentials() != null
                        && !c.getCredentials().isEmpty());
                cm.put("updatedAt", c.getUpdatedAt());
                cm.put("status", implStatusOf(c, impl));
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
                Map<String, Object> mm = new LinkedHashMap<>(4);
                mm.put("model", m.getModel());
                mm.put("enabled", Boolean.TRUE.equals(m.getEnabled()));
                mm.put("alias", m.getAlias());
                mm.put("contextWindow", m.getContextWindow());
                modelItems.add(mm);
            }
        }
        summary.put("models", modelItems);
        return summary;
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
            label = "账号 " + (System.currentTimeMillis() % 1000000);
        }

        Map<String, Object> credentials;
        try {
            credentials = impl.pollLogin(handle);
        } catch (ApiException e) {
            throw e;
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
        conn.setRole("default");
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
            label = "账号 " + (System.currentTimeMillis() % 1000000);
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

    /** 推一条 SSE 事件（客户端已断开时静默）。 */
    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 客户端断开：后续 complete 由调用方处理。
        }
    }

    /**
     * 单渠道、单账号（按 label 匹配）凭据刷新。
     *
     * <p>取该 connection 的 {@code credentials}，调 SPI {@link AccountChannelAuthManager#refresh(Map)}，
     * 成功后回写。刷新失败抛 {@link ApiErrorCode#UPSTREAM_ERROR}，既有凭据不动。</p>
     * @param channel 渠道标识
     * @param body 请求体：{ label }（匹配 connection label）
     * @return { ok: 布尔、refreshed: 布尔 }
     */
    @PostMapping("/{channel}/refresh")
    @Transactional
    public ApiResult<Map<String, Object>> refreshChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        SysAiProviderConnection conn = findConnection(channel, label);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + label);
        }
        Map<String, Object> current = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> refreshed;
        try {
            refreshed = impl.refresh(current);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ChannelAuth] refresh 上游异常 channel={} label={} cause={}",
                    channel, label, e.getMessage());
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
     * 单渠道、单账号（按 label 匹配）签到 / 额度领取。
     *
     * @param channel 渠道标识
     * @param body 请求体：{ label }
     * @return 签到结果（含 credits/message 等键，由实现决定）
     */
    @PostMapping("/{channel}/checkin")
    @Transactional
    public ApiResult<Map<String, Object>> checkinChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        if (!impl.supportsCheckin()) {
            Map<String, Object> out = new LinkedHashMap<>(2);
            out.put("ok", false);
            out.put("message", "渠道不支持签到: " + channel);
            return ApiResult.ok(out);
        }
        String label = body == null ? null : asString(body.get("label"));
        SysAiProviderConnection conn = findConnection(channel, label);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + label);
        }
        Map<String, Object> current = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        Map<String, Object> afterCheckin;
        try {
            afterCheckin = impl.checkin(current);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ChannelAuth] checkin 上游异常 channel={} label={} cause={}",
                    channel, label, e.getMessage());
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
        out.put("label", label);
        // ⚠️ 脱敏：checkin 结果可能是完整凭据 Map（如 balance 并入），
        // 直接回传会把 access_token/refresh_token 暴露给前端。
        // 只保留签到/余额类展示键，丢弃所有凭据敏感键。
        Map<String, Object> result = new LinkedHashMap<>();
        if (afterCheckin != null) {
            for (Map.Entry<String, Object> e : afterCheckin.entrySet()) {
                if (!SENSITIVE_CREDENTIAL_KEYS.contains(e.getKey())) {
                    result.put(e.getKey(), e.getValue());
                }
            }
        }
        out.put("result", result);
        return ApiResult.ok(out);
    }

    /** 凭据敏感键（checkin 结果回传前端前剔除，防 token 泄露到页面）。 */
    private static final java.util.Set<String> SENSITIVE_CREDENTIAL_KEYS = java.util.Set.of(
            "access_token", "refresh_token", "client_secret", "api_key", "uuid", "first_keyfrom");

    /**
     * 单渠道、单账号（按 label 匹配）拉取可用模型列表。
     *
     * <p>成功时不直接写 {@code sys_ai_provider_model}；模型落库走
     * 既有 provider 管理接口（{@code /api/providers/{id}/models/import-all}）。
     * 本接口返回的是该账号当前可拉到的模型目录，前端可据此判断「该账号实际可用」
     * 的子集，再做模型开关。</p>
     *
     * @param channel 渠道标识
     * @param body 请求体：{ label }
     * @return { ok, models: [{id, name, contextWindow?}] }
     */
    @PostMapping("/{channel}/list-models")
    public ApiResult<Map<String, Object>> listModelsChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        SysAiProviderConnection conn = findConnection(channel, label);
        // ⚠️ 找不到账号时必须报 NOT_FOUND，不能拿空凭据继续往下走：
        // 否则上游按「无 token」返回空列表，接口回 ok=true + models=[]，
        // 前端提示「拉取到 0 个模型」—— 用户会以为渠道本身没有模型，
        // 而真实原因是账号不存在（未绑定 / label 传错）。
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号，请先绑定: " + channel + " / " + label);
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        List<Map<String, Object>> models;
        try {
            models = impl.listModels(credentials);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ChannelAuth] list-models 上游异常 channel={} label={} cause={}",
                    channel, label, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 list-models 失败: " + e.getMessage(), e);
        }
        if (models == null) {
            models = new ArrayList<>(0);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", channel);
        out.put("label", label);
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
     * 不存在的行直接插入（enabled 默认 true）。</p>
     *
     * @param channel 渠道标识
     * @param body 请求体：{ label }
     * @return { ok, channel, created, updated, total }
     */
    @PostMapping("/{channel}/import-models")
    @Transactional
    public ApiResult<Map<String, Object>> importModelsChannel(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        AccountChannelAuthManager impl = requireManager(channel);
        SysAiProvider provider = requireChannelProvider(channel);
        String label = body == null ? null : asString(body.get("label"));
        SysAiProviderConnection conn = findConnection(channel, label);
        // 同 list-models：账号不存在直接报错，避免把「没绑定」当成「0 个模型」落库成功。
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号，请先绑定: " + channel + " / " + label);
        }
        Map<String, Object> credentials = conn.getCredentials() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(conn.getCredentials());
        List<Map<String, Object>> rawModels;
        try {
            rawModels = impl.listModels(credentials);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ChannelAuth] import-models 上游异常 channel={} label={} cause={}",
                    channel, label, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 import-models 失败: " + e.getMessage(), e);
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
            Integer contextWindow = null;
            Object cw = raw.get("contextWindow");
            if (cw instanceof Number n) {
                contextWindow = n.intValue();
            }
            SysAiProviderModel existing = modelMapper.selectOne(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, provider.getProviderId())
                            .eq(SysAiProviderModel::getModel, modelName));
            if (existing == null) {
                SysAiProviderModel m = new SysAiProviderModel();
                m.setProviderId(provider.getProviderId());
                m.setModel(modelName);
                m.setAlias(modelAlias);
                m.setEnabled(Boolean.TRUE);
                m.setContextWindow(contextWindow);
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
                if (changed) {
                    existing.setUpdatedAt(now);
                    modelMapper.updateById(existing);
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
        modelMapper.updateById(m);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("channel", impl.channel());
        out.put("model", model);
        out.put("enabled", enabled);
        return ApiResult.ok(out);
    }

    /**
     * 删除渠道账号（按 label 匹配）。
     *
     * <p>删除该 connection 行；不删 provider 行（其它账号可能共用），
     * 该渠道下已无 connection 时 provider 行保留（启用状态由管理页统一开关）。</p>
     *
     * @param channel 渠道标识
     * @param body { label }
     * @return 成功
     */
    @PostMapping("/{channel}/delete-connection")
    @Transactional
    public ApiResult<Void> deleteConnection(
            @PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        requireManager(channel);
        String label = body == null ? null : asString(body.get("label"));
        SysAiProviderConnection conn = findConnection(channel, label);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到渠道账号: " + channel + " / " + label);
        }
        connectionMapper.deleteById(conn.getConnectionId());
        return ApiResult.ok();
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
     * 渠道级凭据状态探活（取该渠道默认 connection，无默认时取第一条）。
     *
     * @param channel 渠道标识
     * @param label 可选 label，缺省取默认
     * @return { ok, status: "valid"|"expired"|"invalid", label }
     */
    @GetMapping("/{channel}/status")
    public ApiResult<Map<String, Object>> statusChannel(
            @PathVariable("channel") String channel,
            @RequestParam(value = "label", required = false) String label) {
        AccountChannelAuthManager impl = requireManager(channel);
        SysAiProviderConnection conn = findConnection(channel, label);
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
        } catch (Exception e) {
            log.warn("[ChannelAuth] status 上游异常 channel={} label={} cause={}",
                    channel, label, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道 status 失败: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("ok", true);
        out.put("status", status == null ? "unknown" : status.get("status"));
        out.put("label", label);
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
     * label 非空时按 label 精确匹配；为空时取该渠道下第一个 connection。
     *
     * @param channel 渠道标识
     * @param label 账号标签（可空）
     * @return connection，未找到返回 空
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
            wrapper.orderByAsc(SysAiProviderConnection::getRole)
                    .orderByAsc(SysAiProviderConnection::getCreatedAt);
        }
        wrapper.last("LIMIT 1");
        return connectionMapper.selectOne(wrapper);
    }

    /**
     * 查找或新建一条 connection：label 非空时精确匹配；
     * 为空时按「该渠道下 role=default」匹配；都不存在则新建。
     *
     * @param providerId provider 行 ID
     * @param label 账号标签（可空）
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
                            .eq(SysAiProviderConnection::getRole, "default")
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
        created.setRole("default");
        created.setLabel(label);
        created.setUserId(owner);
        created.setAuthScheme(OAUTH_SCHEME);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        connectionMapper.insert(created);
        return created;
    }

    /**
     * 渠道 connection 的状态摘要：取 credentials 经 impl.status() 的 {@code status} 键。
     * 出错时返回 "unknown"，前端仍展示连接本身。
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
}

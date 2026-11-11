// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.MagpieSupport;
import com.chua.starter.common.support.http.ChannelHttp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Magpie 统一网关的渠道鉴权实现（API key 渠道）。
 *
 * <p>Magpie 不是「一家上游」，而是把 Codex / Claude / Gemini / OpenAI 兼容服务聚到
 * 一个网关上的路由器。因此本实现只绑定<b>网关本身</b>：</p>
 * <ol>
 *   <li>用户在渠道页填「网关地址 + 网关 key」（也可直接粘 {@code magpie web} 打印的链接，
 *       其 {@code ?k=} 会被拆成页面 Key）；</li>
 *   <li>{@link #pollLogin} 探活 {@code GET /v1/models}（数据面，证明网关真的在跑）；</li>
 *   <li>凭据落库 {@code {base_url, gateway_key, page_url, page_key}}，
 *       模型清单 / 配额走数据面，上游管理与签到走管理面；</li>
 *   <li>{@link #toRouteTarget} 把网关转成一条 OpenAI 兼容路由，
 *       交给 {@code FreeChannelChatClient} 按模型名路由。</li>
 * </ol>
 *
 * <p><b>与其它渠道的区别</b>：magpie 的模型 id 形如 {@code provider/model}，
 * 选择哪家上游、哪个账号是 magpie 自己内部的第二层路由，本实现不参与；
 * 我们只负责「把请求送到网关」和「把网关里有什么告诉 UI」。</p>
 *
 * <p><b>签到</b>：magpie 自己在 {@code /api/usage/*} 下实现了 WorkBuddy(CN)、Trae(CN)、
 * MiniMax(CN)、Qoder 四家的每日签到。本实现把「签到」做成对这四个端点的一次聚合调用：
 * 有账号的上游会真的签到，没有账号的上游返回空列表，不算失败。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieAccountChannelAuthManager implements AccountChannelAuthManager {

    /**
     * 绑定句柄里的地址键。
     */
    static final String HANDLE_BASE_URL = "baseUrl";

    /**
     * 绑定句柄里的 key 键（前端沿用 API key 渠道的通用入参名）。
     */
    static final String HANDLE_API_KEY = "apiKey";

    /**
     * 绑定句柄里的页面地址键（管理面，可空）。
     */
    static final String HANDLE_PAGE_URL = "pageUrl";

    /**
     * 绑定句柄里的页面 Key 键（管理面必需）。
     */
    static final String HANDLE_PAGE_KEY = "pageKey";

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    @Override
    public String displayName() {
        return "Magpie 统一网关";
    }

    @Override
    public String loginMethod() {
        return "api-key";
    }

    /**
     * {@inheritDoc}
     *
     * <p>没有可跳转的授权页：返回带默认网关地址的句柄，前端渲染「地址 + key」两个输入框。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> handle = new LinkedHashMap<>(6);
        handle.put("handle", "magpie-api-key");
        handle.put("loginMethod", "api-key");
        handle.put(HANDLE_BASE_URL, MagpieSupport.DEFAULT_BASE_URL);
        handle.put(HANDLE_PAGE_URL, MagpieSupport.DEFAULT_ADMIN_URL);
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>入参：</p>
     * <ul>
     *   <li>{@code baseUrl} 网关地址；<b>也可直接粘 magpie web 打印的整条链接</b>
     *       （{@code http://127.0.0.1:3430/?k=xxx}），此时其 {@code ?k=} 被拆成页面 Key，
     *       数据面按端口规则还原成 3425；</li>
     *   <li>{@code apiKey} 网关 key（可空：本机 magpie 数据面不校验）；</li>
     *   <li>{@code pageKey} 页面 Key（管理面用，可空——只影响上游管理与签到）；</li>
     *   <li>{@code pageUrl} 页面地址（可空，缺省按 baseUrl 推导）。</li>
     * </ul>
     *
     * <p>只探活数据面 {@code /v1/models}：它才是「网关真的在跑」的证据。
     * 管理面是否可用留给「上游」页签按需探测，不因页面没开而拒绝绑定。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "Magpie 登录句柄缺失");
        }
        String entered = MagpieSupport.readString(handle, HANDLE_BASE_URL, "base_url", "url");
        String apiKey = MagpieSupport.readString(handle, HANDLE_API_KEY, "gatewayKey", "gateway_key");
        String pageKey = MagpieSupport.readString(handle, HANDLE_PAGE_KEY, "page_key", "webKey", "web_key");
        String pageUrl = MagpieSupport.readString(handle, HANDLE_PAGE_URL, "page_url", "adminUrl", "admin_url");

        // 整条启动链接：?k= 就是页面 Key
        String linkKey = MagpieSupport.linkKey(entered);
        if (MagpieSupport.isBlank(pageKey) && linkKey != null) {
            pageKey = linkKey;
        }
        String entry = MagpieSupport.normalize(MagpieSupport.stripQuery(entered));
        // 粘成 3430 时按「页面地址」理解，数据面还原到 3425，避免把 /v1 打到页面端口
        if (MagpieSupport.isPageUrl(entry)) {
            pageUrl = MagpieSupport.isBlank(pageUrl) ? entry : pageUrl;
            entry = MagpieSupport.gatewayForPage(entry);
        } else if (MagpieSupport.isBlank(pageUrl)) {
            pageUrl = MagpieSupport.gatewayToPage(entry);
        }
        String baseUrl = MagpieSupport.normalize(entry);
        String normalizedPageUrl = MagpieSupport.normalizePage(pageUrl);

        ProbeResult probe = probe(baseUrl, apiKey);
        if (!probe.ok()) {
            throw new ChannelException(ChannelException.CODE_UPSTREAM_ERROR,
                    "Magpie 网关探活失败（" + baseUrl + "）: " + ChannelHttp.truncate(probe.detail()));
        }
        Map<String, Object> credential = new LinkedHashMap<>(6);
        credential.put("base_url", baseUrl);
        credential.put("gateway_key", apiKey == null ? "" : apiKey.trim());
        credential.put("page_url", normalizedPageUrl);
        credential.put("page_key", pageKey == null ? "" : pageKey.trim());
        credential.put("nickname", nickname(apiKey, pageKey));
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>网关 key 无续期机制。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ChannelException(ChannelException.CODE_NOT_IMPLEMENTED,
                "Magpie 网关 key 不可续期，如失效请在 Magpie 的 Gateway keys 页重新签发");
    }

    /**
     * {@inheritDoc}
     *
     * <p>探活只看数据面 {@code GET /v1/models}：401/403 判 invalid，
     * 网络失败判 unknown（不把「没开机」当成「key 失效」）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String baseUrl = MagpieSupport.baseUrl(credentials);
        String apiKey = MagpieSupport.gatewayKey(credentials);
        ProbeResult probe = probe(baseUrl, apiKey);
        if (probe.ok()) {
            return status("valid", "探活成功");
        }
        if (probe.unauthorized()) {
            return status("invalid", "网关拒绝（401/403）: " + ChannelHttp.truncate(probe.detail()));
        }
        return status("unknown", "网关不可达: " + ChannelHttp.truncate(probe.detail()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 自己实现了 WorkBuddy(CN)、Trae(CN)、MiniMax(CN)、Qoder 的每日签到，
     * 统一挂在管理面 {@code /api/usage/*-checkin}；这里如实声明支持，
     * 实际动作见 {@link #checkin(Map)}。</p>
     */
    @Override
    public boolean supportsCheckin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 自己就有每日签到定时器（{@code KeepPluginsCheckedIn} /
     * {@code KeepWorkBuddyCheckedIn}，逐 provider 由 {@code settings.CheckinOff} 控制），
     * 本服务再跑一遍只会重复打上游。因此自动签到任务跳过本渠道，
     * 页面上的「立即签到」仍然走 {@link #checkin(Map)}。</p>
     */
    @Override
    public boolean supportsAutoCheckin() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 provider 是否已被插件接管分流：</p>
     * <ul>
     *   <li>内置（未被接管）→ {@code POST /api/usage/{vendor}-checkin}；</li>
     *   <li>已被插件接管（{@code move.state=plugin}）→
     *       {@code POST /api/usage/plugin-checkin}，必须显式传 provider；</li>
     *   <li>某家没有账号 → 返回空列表，不算失败；</li>
     *   <li>某家已经签到过 → magpie 自己吞掉重复，仍返回结果行；</li>
     *   <li>全部失败（页面没开 / 页面 Key 失效）→ 抛异常，把可操作的提示交给 UI。</li>
     * </ul>
     *
     * <p>分流很关键：装 WorkBuddy 插件后内置 {@code workbuddy} 会被
     * {@code provider.HandOver} 移交，此时再打内置端点只会查不到账号。</p>
     *
     * @param credentials 网关凭据
     * @return 更新后的凭据（并入 {@code checkin_message} / {@code checkin_at}）
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        return checkinFor(null, credentials);
    }

    /**
     * {@inheritDoc}
     *
     * <p>只签 {@code vendor} 指定的那一家：网关托管渠道（Qoder / Trae / WorkBuddy …）
     * 在页面上各占一行，用户在 Qoder 点签到，不该把其它几家一起签掉。
     * {@code vendor} 为空时保持旧行为（签全部内置签到上游）。</p>
     *
     * @param vendor      业务渠道标识（可空）
     * @param credentials 网关凭据
     * @return 更新后的凭据
     */
    @Override
    public Map<String, Object> checkinFor(String vendor, Map<String, Object> credentials) {
        List<String> targets;
        if (MagpieSupport.isBlank(vendor)) {
            targets = MagpieSupport.CHECKIN_VENDORS;
        } else {
            String wanted = vendor.trim().toLowerCase(java.util.Locale.ROOT);
            targets = MagpieSupport.CHECKIN_VENDORS.stream()
                    .filter(v -> v.equals(wanted))
                    .toList();
            if (targets.isEmpty()) {
                throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                        "Magpie 不支持「" + vendor + "」的签到（仅 workbuddy / trae / minimax / qoder）");
            }
        }
        Map<String, Object> updated = credentials == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials);
        List<String> messages = new ArrayList<>(4);
        Map<String, Boolean> pluginProviders = pluginProviderStates(credentials);
        int pressed = 0;
        int failed = 0;
        String lastError = null;
        for (String target : targets) {
            try {
                boolean viaPlugin = Boolean.TRUE.equals(pluginProviders.get(target));
                List<Map<String, Object>> rows = viaPlugin
                        ? MagpieAdminClient.pluginCheckin(credentials, target)
                        : MagpieAdminClient.checkin(credentials, target);
                int accounts = rows.size();
                if (accounts > 0) {
                    pressed++;
                    messages.add(target + (viaPlugin ? "（插件）" : "") + " 处理 " + accounts + " 个账号");
                }
            } catch (ChannelException e) {
                failed++;
                lastError = e.getMessage();
            }
        }
        if (pressed == 0 && failed == targets.size()) {
            throw new ChannelException(ChannelException.CODE_UPSTREAM_ERROR,
                    "Magpie 签到失败：" + ChannelHttp.truncate(lastError));
        }
        if (pressed == 0) {
            messages.add(MagpieSupport.isBlank(vendor)
                    ? "网关里没有支持签到的上游账号"
                    : "网关里没有「" + vendor + "」的签到账号");
        }
        // 只落摘要：上游返回的原始行可能带账号明细，没必要（也不应该）塞进连接凭据。
        updated.put("checkin_message", String.join("；", messages));
        updated.put("checkin_at", System.currentTimeMillis());
        return updated;
    }

    /**
     * {@inheritDoc}
     *
     * <p>读 {@code GET /v1/models}：magpie 返回的是全量聚合模型（{@code provider/model}），
     * 失败返回空列表，不抛异常。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String baseUrl = MagpieSupport.baseUrl(credentials);
        String apiKey = MagpieSupport.gatewayKey(credentials);
        List<Map<String, Object>> source;
        try {
            source = MagpieAdminClient.models(baseUrl, apiKey);
        } catch (Exception e) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(source.size());
        for (Map<String, Object> item : source) {
            Object idRaw = item.get("id");
            if (idRaw == null || String.valueOf(idRaw).isBlank()) {
                continue;
            }
            String id = String.valueOf(idRaw);
            Map<String, Object> model = new LinkedHashMap<>(4);
            model.put("id", id);
            Object name = item.get("name");
            model.put("name", name instanceof String text && !text.isBlank() ? text : id);
            String owner = item.get("owned_by") instanceof String text && !text.isBlank()
                    ? text : providerOf(id);
            if (owner != null) {
                model.put("ownedBy", owner);
            }
            result.add(model);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>转成一条 OpenAI 兼容路由：数据面基址 {@code <网关>/v1}，key 为空时用本机占位值。
     * 多账号切换由 magpie 自己内部完成，这里不开 {@code apiKeys} 聚合。</p>
     */
    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String baseUrl = MagpieSupport.baseUrl(credentials);
        String apiKey = MagpieSupport.effectiveKey(MagpieSupport.gatewayKey(credentials));
        return RouteTarget.builder()
                .provider("openai")
                .baseUrl(MagpieSupport.v1Url(baseUrl))
                .apiKey(apiKey)
                .authScheme("api_key")
                .autoSwitchOnRateLimit(true)
                .build();
    }

    // ── 私有辅助 ──

    /**
     * 读出哪些 provider 已被插件接管（{@code move.state=plugin}）。
     *
     * <p>读不到列表时返回空 Map：签到退回内置端点，行为与旧版一致，
     * 真正的失败会由签到调用自己报出来。</p>
     *
     * @param credentials 网关凭据
     * @return provider id → 是否插件接管
     */
    private static Map<String, Boolean> pluginProviderStates(Map<String, Object> credentials) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        try {
            for (Map<String, Object> provider : MagpieAdminClient.providers(credentials)) {
                Object id = provider.get("id");
                if (id == null) {
                    continue;
                }
                boolean plugin = false;
                Object move = provider.get("move");
                if (move instanceof Map<?, ?> map) {
                    Object state = map.get("state");
                    plugin = state != null && "plugin".equalsIgnoreCase(String.valueOf(state));
                }
                states.put(String.valueOf(id), plugin);
            }
        } catch (Exception ignored) {
            // 管理面不可用时退回内置端点，由签到调用给出可读错误
        }
        return states;
    }

    /**
     * 探活结果。
     *
     * @param ok           是否可达
     * @param unauthorized 是否为 401/403
     * @param detail       失败详情
     */
    private record ProbeResult(boolean ok, boolean unauthorized, String detail) {
    }

    /**
     * 探活：只问数据面模型清单。
     *
     * @param baseUrl 网关根地址
     * @param apiKey  网关 key（可空）
     * @return 探活结果
     */
    private ProbeResult probe(String baseUrl, String apiKey) {
        try {
            ChannelHttp.getJson(MagpieSupport.normalize(baseUrl) + MagpieSupport.MODELS_PATH,
                    MagpieSupport.dataHeaders(apiKey));
            return new ProbeResult(true, false, null);
        } catch (ChannelException modelFailure) {
            return new ProbeResult(false, isUnauthorized(modelFailure), modelFailure.getMessage());
        }
    }

    /**
     * 连接显示名：网关 key 脱敏优先；没配网关 key（本机直连）时用页面 Key 尾号，
     * 两者都没有就退回渠道名，避免账号列表出现空标签。
     *
     * @param apiKey  网关 key（可空）
     * @param pageKey 页面 Key（可空）
     * @return 展示名
     */
    private static String nickname(String apiKey, String pageKey) {
        if (!MagpieSupport.isBlank(apiKey)) {
            return MagpieSupport.maskKey(apiKey);
        }
        if (!MagpieSupport.isBlank(pageKey)) {
            return "Magpie " + MagpieSupport.maskKey(pageKey);
        }
        return "Magpie";
    }

    /**
     * 是否 401/403（{@code ChannelHttp} 的非 2xx 消息里带状态码）。
     *
     * @param e 异常
     * @return true 表示鉴权失败
     */
    private static boolean isUnauthorized(ChannelException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("-> 401") || message.contains("-> 403");
    }

    /**
     * 从模型 id 里取 provider 段（{@code provider/model}）。
     *
     * @param id 模型 id
     * @return provider 段；无斜杠返回 {@code null}
     */
    private static String providerOf(String id) {
        int slash = id.indexOf('/');
        return slash > 0 ? id.substring(0, slash) : null;
    }

    /**
     * 状态摘要（同时写 {@code status} 与 {@code state}，兼容两套读取口径）。
     *
     * @param state  状态
     * @param detail 说明
     * @return 状态 Map
     */
    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("status", state);
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }
}

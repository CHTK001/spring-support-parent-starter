// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorkBuddy（腾讯 CodeBuddy / WorkBuddy 国际版）账号渠道鉴权管理器。
 *
 * <p>协议对齐 {@code workbuddy2api-panel} 的 <b>panel 内嵌登录流</b>
 * （{@code internal/panel/login.go}）：panel 进程内代理官方设备授权，本类只与 panel 交互，
 * 不直接面对腾讯 upstream 的设备授权端点。</p>
 *
 * <ol>
 *   <li>{@code startLogin}：{@code POST {panel}/panel/api/login/start}
 *       （body {@code {"realm":"cn|global"}}）→ {@code {ok,url,state,realm}}，
 *       把 {@code url} 作为 {@code verifyUrl} 交给前端打开；{@code state} 是后续轮询句柄。</li>
 *   <li>{@code pollLogin}：{@code GET {panel}/panel/api/login/poll?state=<state>}；
 *       未完成返回 {@code {done:false}} → 本方法返回 {@code null} 继续等；
 *       完成返回 {@code {done:true,uid,nickname,realm,credits,credits_total,checkin_message}}，
 *       本方法据此组装凭据并把 auth 文件落盘到 {@code {authDir}/workbuddy-<uid>.json}。</li>
 *   <li>{@code refresh}：{@code POST {upstream}/v2/plugin/auth/token/refresh}（{@code X-Refresh-Token}）。</li>
 *   <li>{@code status}：探活 {@code GET {upstream}/v3/config}（不消耗积分）。</li>
 *   <li>{@code checkin}：CN 走 {@code POST {billing}/v2/billing/meter/daily-checkin}；
 *       global 无 CN 签到体系，改为「注册激活 + 一次性 trial 加油包领取」。</li>
 * </ol>
 *
 * <p><b>账号域（realm）路由</b>（与 {@code login.go} 的 {@code loginEndpoints} 一致）：</p>
 * <ul>
 *   <li>{@code cn}（缺省）→ chat {@code https://copilot.tencent.com}、
 *       billing {@code https://www.codebuddy.cn}、Origin/Referer {@code https://www.codebuddy.cn}；</li>
 *   <li>{@code global} → chat/billing {@code https://www.workbuddy.ai}、
 *       Origin/Referer {@code https://www.workbuddy.ai}。</li>
 * </ul>
 *
 * <p><b>安全</b>：上游返回的 {@code uid} 会被拼进文件名，必须先过 {@link #validUID(String)}
 * （只放行 {@code [A-Za-z0-9_-]} 且长度 ≤ 64），否则 {@code ../} 之类可造成路径穿越。</p>
 *
 * <p><b>配置</b>（系统属性 → 环境变量 → 默认值）：</p>
 * <ul>
 *   <li>{@code chua.workbuddy.panel-url} / {@code WORKBUDDY_PANEL_URL}：
 *       panel 地址，默认 {@code http://127.0.0.1:3000}；</li>
 *   <li>{@code chua.workbuddy.auth-dir} / {@code WORKBUDDY_AUTH_DIR}：
 *       auth 文件目录，默认 {@code ~/.workbuddy2api/auths}；</li>
 *   <li>{@code chua.workbuddy.realm} / {@code WORKBUDDY_REALM}：
 *       发起登录时的账号域，{@code cn}（默认）或 {@code global}。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Spi("workbuddy")
public class WorkBuddyAccountChannelAuthManager implements AccountChannelAuthManager {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "workbuddy";

    /**
     * panel 缺省监听地址
     */
    static final String DEFAULT_PANEL = "http://127.0.0.1:3000";

    /**
     * 国内版 chat / 设备授权上游
     */
    static final String BASE_CN = "https://copilot.tencent.com";

    /**
     * 国际版上游（chat / billing / 注册激活同域）
     */
    static final String BASE_GLOBAL = "https://www.workbuddy.ai";

    /**
     * 国内版 billing / 成长中心域
     */
    static final String BILLING_CN = "https://www.codebuddy.cn";

    /**
     * 国内版 Origin / Referer 基础域
     */
    static final String ORIGIN_CN = "https://www.codebuddy.cn";

    /**
     * 国际版 Origin / Referer 基础域
     */
    static final String ORIGIN_GLOBAL = "https://www.workbuddy.ai";

    /**
     * 国内版 web 成长中心 Origin
     */
    static final String WEB_ORIGIN_CN = "https://www.workbuddy.cn";

    /**
     * 客户端版本段（对齐官方桌面端分发包）
     */
    static final String CLIENT_VERSION = "5.5.4";

    /**
     * 内置 CLI 版本段
     */
    static final String CLI_VERSION = "2.137.1";

    /**
     * {@code /v3/config} 要求的 IDE 版 UA
     */
    static final String IDE_UA = "CodeBuddyIDE/4.12.0 CodeBuddy/4.12.0";

    /**
     * 国际版注册页 web UA
     */
    static final String WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 登录轮询间隔（毫秒）：panel 前端每 3s 轮询一次
     */
    static final long POLL_INTERVAL_MS = 3_000L;

    /**
     * 句柄键：上一次轮询时刻
     */
    static final String HANDLE_LAST_POLL_AT = "__lastPollAt";

    /**
     * 句柄键：轮询间隔（毫秒），测试可覆写以加速
     */
    public static final String HANDLE_POLL_INTERVAL_MS = "__pollIntervalMs";

    /**
     * 句柄键：账号域
     */
    static final String HANDLE_REALM = "realm";

    /**
     * 登录会话有效期（毫秒）：超时未完成的 state 会被回收
     */
    static final long STATE_TTL_MILLIS = 15 * 60 * 1000L;

    /**
     * 国际化域名后缀（判定 auth 文件 realm 归属）
     */
    static final String GLOBAL_DOMAIN_SUFFIX = "workbuddy.ai";

    /**
     * global 注册可选地区白名单（对齐 panel 前端静态兜底集）
     */
    static final List<String> REGION_WHITELIST =
            List.of("HK", "MO", "SG", "TH", "PH", "MY", "ID");

    /**
     * trial 已领取业务码（幂等，非错误）
     */
    static final int TRIAL_ALREADY_CLAIMED = 14051;

    /**
     * 企业号不支持个人签到业务码
     */
    static final int ENTERPRISE_UNSUPPORTED = 10001;

    /**
     * state → 会话（realm + 创建时间）。panel 侧同样进程内维护，本类留一份副本用于
     * 兜底判定与 TTL 回收；poll 成功后即删除。
     */
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();

    /**
     * 登录会话（state 维度的 realm 与创建时刻）。
     *
     * @param realm   账号域
     * @param created 创建时刻（毫秒）
     */
    private record LoginSession(String realm, long created) {
    }

    // ==================== SPI 基本属性 ====================

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String displayName() {
        return "WorkBuddy（腾讯）";
    }

    /**
     * {@inheritDoc}
     *
     * <p>panel 发起的是官方设备/浏览器授权，用户打开 {@code verifyUrl} 完成登录，
     * 服务端轮询取回账号，与 {@code browser-device} 体验一致。</p>
     */
    @Override
    public String loginMethod() {
        return "browser-device";
    }

    @Override
    public int priority() {
        return 10;
    }

    // ==================== 日志 ====================

    /**
     * {@inheritDoc}
     *
     * <p>POST panel 的 {@code login/start}，body 携带账号域；panel 返回
     * {@code {ok,url,state,realm}}。本方法把 {@code url} 作为 {@code verifyUrl}，
     * {@code state} 作为句柄，并顺带回收超时会话。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        String realm = resolveRealm(cfg("realm", "cn"));
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("realm", realm);
        String response = ChannelHttp.postJson(
                panelOrigin() + "/panel/api/login/start", Json.toJson(body), jsonHeaders(realm));
        Map<String, Object> envelope = parseJson(response);
        String url = readString(envelope, "url");
        String state = readString(envelope, "state");
        if (isBlank(url) || isBlank(state)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy panel 发起登录返回不完整（缺 url / state）: " + ChannelHttp.truncate(response));
        }
        purgeExpiredSessions();
        sessions.put(state, new LoginSession(realm, System.currentTimeMillis()));
        Map<String, Object> handle = new LinkedHashMap<>(8);
        handle.put("handle", state);
        handle.put("verifyUrl", url);
        handle.put("loginUrl", url);
        handle.put(HANDLE_REALM, realm);
        handle.put("startedAt", System.currentTimeMillis());
        handle.put(HANDLE_POLL_INTERVAL_MS, POLL_INTERVAL_MS);
        log.info("[workbuddy] 发起 panel 登录 realm={} state={}...", realm, abbreviate(state));
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code done=false} 返回 {@code null}（继续等）；{@code done=true} 组装凭据并
     * 落盘 auth 文件。未知/过期 state 抛 {@link ApiErrorCode#INVALID_PARAM} 提示重新发起。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        String state = handle == null ? null : readString(handle, "handle");
        if (isBlank(state)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "WorkBuddy 登录句柄缺失 handle(state)");
        }
        long now = System.currentTimeMillis();
        Long lastPollAt = toLong(handle.get(HANDLE_LAST_POLL_AT));
        Long interval = toLong(handle.get(HANDLE_POLL_INTERVAL_MS));
        if (interval == null) {
            interval = POLL_INTERVAL_MS;
        }
        if (lastPollAt != null && now - lastPollAt < interval) {
            return null;
        }
        handle.put(HANDLE_LAST_POLL_AT, now);

        String realm = handleRealm(state, handle);
        String response;
        try {
            response = ChannelHttp.getJson(
                    panelOrigin() + "/panel/api/login/poll?state=" + urlEncode(state),
                    jsonHeaders(realm));
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("404") || message.contains("unknown or expired")) {
                sessions.remove(state);
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "WorkBuddy 登录会话已失效，请重新发起绑定", e);
            }
            throw e;
        }
        Map<String, Object> envelope = parseJson(response);
        if (!Boolean.TRUE.equals(envelope.get("done"))) {
            return null;
        }
        String uid = readString(envelope, "uid");
        if (isBlank(uid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy panel 登录完成但未返回 uid: " + ChannelHttp.truncate(response));
        }
        if (!validUID(uid)) {
            // uid 来自上游却被用于拼文件名，未校验会被 ../ 路径穿越利用
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 上游返回的 uid 含非法字符，拒绝落盘（防路径穿越）");
        }
        String effectiveRealm = resolveRealm(firstNonBlank(readString(envelope, "realm"), realm));
        Map<String, Object> credential = new LinkedHashMap<>(16);
        credential.put("access_token", readString(envelope, "access_token"));
        credential.put("uid", uid);
        credential.put("nickname", readString(envelope, "nickname"));
        credential.put(HANDLE_REALM, effectiveRealm);
        credential.put("credits", envelope.get("credits"));
        credential.put("credits_total", envelope.get("credits_total"));
        String checkinMessage = readString(envelope, "checkin_message");
        if (!isBlank(checkinMessage)) {
            credential.put("checkin_message", checkinMessage);
        }
        credential.put("base_url", upstreamOrigin(effectiveRealm));
        writeAuthFile(uid, credential);
        sessions.remove(state);
        log.info("[workbuddy] panel 登录完成 uid={} nickname={} realm={}",
                uid, credential.get("nickname"), effectiveRealm);
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code refresh_token} 走 {@code POST /v2/plugin/auth/token/refresh}
     * （{@code X-Refresh-Token} 只允许出现在该端点）。响应
     * {@code {code:0,data:{accessToken,refreshToken,expiresIn,domain}}}。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        String refreshToken = readString(credentials, "refresh_token");
        if (isBlank(refreshToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "WorkBuddy 凭据缺少 refresh_token，无法续期");
        }
        String realm = credentialRealm(credentials);
        Map<String, String> headers = commonHeaders(realm);
        headers.put("X-Refresh-Token", refreshToken);
        headers.put("X-Auth-Refresh-Source", "plugin");
        String response;
        try {
            response = ChannelHttp.postJson(
                    upstreamOrigin(realm) + "/v2/plugin/auth/token/refresh", null, headers);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403") || message.contains("12153")) {
                throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                        "WorkBuddy 登录态已过期，请重新登录", e);
            }
            throw e;
        }
        Map<String, Object> envelope = parseJson(response);
        Integer code = toInt(envelope.get("code"));
        if (code != null && code != 0) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "WorkBuddy 续期失败: code=" + code + " msg=" + readString(envelope, "msg"));
        }
        Map<String, Object> data = envelope.get("data") instanceof Map<?, ?> rawData
                ? asMap(rawData) : new LinkedHashMap<>();
        String accessToken = readString(data, "accessToken");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED,
                    "WorkBuddy 续期响应缺少 accessToken，请重新登录");
        }
        Map<String, Object> refreshed = new LinkedHashMap<>();
        if (credentials != null) {
            refreshed.putAll(credentials);
        }
        refreshed.put("access_token", accessToken);
        String newRefresh = readString(data, "refreshToken");
        if (!isBlank(newRefresh)) {
            refreshed.put("refresh_token", newRefresh);
        }
        Long expiresIn = toLong(data.get("expiresIn"));
        if (expiresIn != null && expiresIn > 0 && expiresIn < 10L * 365 * 24 * 3600) {
            refreshed.put("expire_time", System.currentTimeMillis() + expiresIn * 1000L);
        }
        String domain = readString(data, "domain");
        if (!isBlank(domain)) {
            refreshed.put("domain", domain);
        }
        refreshed.put(HANDLE_REALM, realm);
        refreshed.put("base_url", upstreamOrigin(realm));
        writeAuthFileSafely(readString(refreshed, "uid"), refreshed);
        return refreshed;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期优先；否则探活 {@code GET {upstream}/v3/config}：2xx 且 {@code code=0}
     * 判 {@code valid}，401/403 判 {@code invalid}，网络失败按本地有效期视为可用。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return statusOf("invalid", "凭据缺少 access_token");
        }
        Long expireTime = toLong(credentials.get("expire_time"));
        if (expireTime != null && expireTime <= System.currentTimeMillis()) {
            return statusOf("expired", "访问令牌已到期");
        }
        String realm = credentialRealm(credentials);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("X-CodeBuddy-Request", "1");
        headers.put("X-Product", "SaaS");
        headers.put("User-Agent", IDE_UA);
        headers.put("X-Domain", realmHost(realm));
        headers.put("Authorization", "Bearer " + accessToken.trim());
        String uid = readString(credentials, "uid");
        if (!isBlank(uid)) {
            headers.put("X-User-Id", uid);
        }
        try {
            String body = ChannelHttp.getJson(upstreamOrigin(realm) + "/v3/config", headers);
            Map<String, Object> envelope = parseJson(body);
            Integer code = toInt(envelope.get("code"));
            if (code != null && code != 0) {
                return statusOf("invalid", "探活返回业务错误 code=" + code);
            }
            return statusOf("valid", "探活成功");
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.contains("403")) {
                return statusOf("invalid", "探活 401/403: " + ChannelHttp.truncate(message));
            }
            return statusOf("valid", "探活失败（网络），按本地有效期视为可用: "
                    + ChannelHttp.truncate(message));
        }
    }

    @Override
    public boolean supportsCheckin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>签到分域：</p>
     * <ul>
     *   <li>CN 非企业号：{@code POST {billing}/v2/billing/meter/daily-checkin}；企业号跳过
     *       （上游对企业号返回 {@code code=10001}「企业账号不支持该操作」）；</li>
     *   <li>global：无 CN 签到体系，改为「注册激活（幂等）+ trial 加油包领取（14051 = 已领）」。</li>
     * </ul>
     * <p>业务侧「今天已签到」等非 0 业务码不视为失败，只把消息并入凭据返回。</p>
     */
    @Override
    public Map<String, Object> checkin(Map<String, Object> credentials) {
        Map<String, Object> updated = credentials == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials);
        String accessToken = readString(updated, "access_token");
        if (isBlank(accessToken)) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "WorkBuddy 签到需要 access_token");
        }
        String realm = credentialRealm(updated);
        List<String> messages = new ArrayList<>(2);
        if ("global".equals(realm)) {
            checkinGlobal(updated, messages);
        } else if (isBlank(readString(updated, "enterprise_id"))) {
            String message = dailyCheckin(updated, realm);
            if (!isBlank(message)) {
                messages.add(message);
            }
        } else {
            messages.add("企业号不支持每日签到，已跳过");
        }
        // 自动任务：接受成长任务并领取已达标奖励（幂等，失败只记消息）
        if (!"global".equals(realm)) {
            messages.addAll(autoTasks(updated, realm));
        }
        long[] resource = balance(updated, realm);
        if (resource != null) {
            updated.put("credits", resource[0]);
            updated.put("credits_total", resource[1]);
        }
        updated.put("checkin_message", String.join("；", messages));
        updated.put("checkin_at", System.currentTimeMillis());
        return updated;
    }

    /**
     * {@inheritDoc}
     *
     * <p>模型目录取 {@code GET {upstream}/v3/config}（需 IDE 版 UA），解析
     * {@code data.models[]}；失败时回落到 {@code /console/enterprises/personal/models}
     * 的 {@code agents[cli].models} 子集。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return List.of();
        }
        String realm = credentialRealm(credentials);
        String uid = readString(credentials, "uid");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("X-CodeBuddy-Request", "1");
        headers.put("X-Product", "SaaS");
        headers.put("User-Agent", IDE_UA);
        headers.put("X-Domain", realmHost(realm));
        headers.put("Authorization", "Bearer " + accessToken.trim());
        if (!isBlank(uid)) {
            headers.put("X-User-Id", uid);
        }
        List<Map<String, Object>> models = v3Models(headers, realm);
        if (!models.isEmpty()) {
            return models;
        }
        return consoleModels(headers, realm);
    }

    /**
     * {@inheritDoc}
     *
     * <p>路由指向本渠道上游（{@code workbuddy} SPI），凭据为 panel 下发的
     * {@code access_token}；账号域决定 baseUrl，多账号 429 由上层按
     * {@code apiKeys} 聚合 failover。</p>
     */
    @Override
    public RouteTarget toRouteTarget(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return null;
        }
        String realm = credentialRealm(credentials);
        String baseUrl = readString(credentials, "base_url");
        if (isBlank(baseUrl)) {
            baseUrl = upstreamOrigin(realm);
        }
        if (isBlank(baseUrl)) {
            return null;
        }
        return RouteTarget.builder()
                .provider(CHANNEL)
                .baseUrl(baseUrl)
                .apiKey(accessToken)
                .authScheme("bearer")
                .headers(realmHeaders(realm, readString(credentials, "uid")))
                .autoSwitchOnRateLimit(true)
                .build();
    }

    /**
     * 自动任务：接受成长任务 + 领取已达标奖励（幂等）。
     *
     * <p>端点（对齐 panel 的 {@code internal/upstream/tasks.go}）：</p>
     * <ul>
     *   <li>{@code GET {chatBase}/v2/activity/growth/tasks} → {@code data.tasks[]}
     *       （含 {@code accept_status} 与 {@code progress{current,target}}）；</li>
     *   <li>{@code POST {chatBase}/v2/activity/growth/tasks/accept}
     *       （body {@code {"task_codes":[...]}}）— 报名，不产生进度；</li>
     *   <li>{@code POST {webBase}/activity/growth/tasks/{code}/claim} — 领奖
     *       （任务码在路径里，重复领取返回 {@code already_claimed=true}）。</li>
     * </ul>
     * <p>进度由服务端行为事件点亮，故「接受」后本轮通常仍不可领——不视为失败。</p>
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return 结果消息（无任务时为空列表）
     */
    private List<String> autoTasks(Map<String, Object> credentials, String realm) {
        List<String> messages = new ArrayList<>(2);
        List<Map<String, Object>> tasks;
        try {
            String body = ChannelHttp.getJson(
                    billingOrigin(realm) + "/v2/activity/growth/tasks",
                    billingHeaders(credentials, realm));
            Map<String, Object> envelope = parseJson(body);
            Integer code = toInt(envelope.get("code"));
            if (code != null && code != 0) {
                messages.add("任务列表获取失败: code=" + code);
                return messages;
            }
            tasks = taskRows(envelope.get("data"));
        } catch (ApiException e) {
            messages.add("任务列表获取失败: " + ChannelHttp.truncate(e.getMessage()));
            return messages;
        }
        List<String> notAccepted = new ArrayList<>(4);
        List<String> claimable = new ArrayList<>(4);
        for (Map<String, Object> task : tasks) {
            String taskCode = readString(task, "task_code");
            if (isBlank(taskCode)) {
                continue;
            }
            String acceptStatus = readString(task, "accept_status");
            if ("not_accepted".equalsIgnoreCase(acceptStatus) || isBlank(acceptStatus)) {
                notAccepted.add(taskCode);
                continue;
            }
            if ("claimed".equalsIgnoreCase(acceptStatus)) {
                continue;
            }
            long[] progress = taskProgress(task);
            if (progress[1] > 0 && progress[0] >= progress[1]) {
                claimable.add(taskCode);
            }
        }
        if (!notAccepted.isEmpty()) {
            try {
                acceptTasks(credentials, realm, notAccepted);
                messages.add("已接受 " + notAccepted.size() + " 个成长任务");
            } catch (ApiException e) {
                messages.add("接受任务失败: " + ChannelHttp.truncate(e.getMessage()));
            }
        }
        int claimed = 0;
        for (String taskCode : claimable) {
            try {
                if (claimTask(credentials, realm, taskCode)) {
                    claimed++;
                }
            } catch (ApiException e) {
                log.debug("[workbuddy] 领取任务奖励失败 code={}: {}",
                        taskCode, ChannelHttp.truncate(e.getMessage()));
            }
        }
        if (claimed > 0) {
            messages.add("已领取 " + claimed + " 个任务奖励");
        }
        return messages;
    }

    /**
     * 接受成长任务（幂等）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @param taskCodes   任务码
     */
    private void acceptTasks(Map<String, Object> credentials, String realm, List<String> taskCodes) {
        Map<String, Object> request = new LinkedHashMap<>(1);
        request.put("task_codes", taskCodes);
        String body = ChannelHttp.postJson(
                billingOrigin(realm) + "/v2/activity/growth/tasks/accept",
                Json.toJson(request), billingHeaders(credentials, realm));
        Map<String, Object> envelope = parseJson(body);
        Integer code = toInt(envelope.get("code"));
        if (code != null && code != 0) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "接受成长任务失败: code=" + code + " msg=" + readString(envelope, "msg"));
        }
    }

    /**
     * 领取单个任务奖励（web 成长中心端点，{@code x-client-platform: web}）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @param taskCode    任务码
     * @return 本次是否新领到奖励
     */
    private boolean claimTask(Map<String, Object> credentials, String realm, String taskCode) {
        Map<String, String> headers = billingHeaders(credentials, realm);
        headers.put("Origin", WEB_ORIGIN_CN);
        headers.put("Referer", WEB_ORIGIN_CN + "/profile/growth-center");
        headers.put("x-client-platform", "web");
        String body = ChannelHttp.postJson(
                webOrigin(realm) + "/activity/growth/tasks/" + urlEncode(taskCode) + "/claim",
                null, headers);
        Map<String, Object> envelope = parseJson(body);
        Integer code = toInt(envelope.get("code"));
        if (code != null && code != 0) {
            return false;
        }
        Map<String, Object> data = envelope.get("data") instanceof Map<?, ?> raw
                ? asMap(raw) : envelope;
        // already_claimed=true 表示幂等重复领取，不算新增奖励
        if (Boolean.TRUE.equals(data.get("already_claimed"))) {
            return false;
        }
        return true;
    }

    /**
     * 解析任务列表（{@code data.tasks[]}）。
     *
     * @param data 响应 data 节点
     * @return 任务行
     */
    private static List<Map<String, Object>> taskRows(Object data) {
        if (data instanceof Map<?, ?> map && map.get("tasks") instanceof List<?> items) {
            return asRows(items);
        }
        return List.of();
    }

    /**
     * 解析单个任务进度（优先 {@code progress{current,target}}，回落平铺字段）。
     *
     * @param task 任务行
     * @return {@code [current, target]}
     */
    private static long[] taskProgress(Map<String, Object> task) {
        Long current = toLong(task.get("current"));
        Long target = toLong(task.get("target"));
        if (task.get("progress") instanceof Map<?, ?> progress) {
            Long nestedCurrent = toLong(progress.get("current"));
            Long nestedTarget = toLong(progress.get("target"));
            if (nestedCurrent != null) {
                current = nestedCurrent;
            }
            if (nestedTarget != null) {
                target = nestedTarget;
            }
        }
        return new long[]{
                current == null ? 0L : current,
                target == null ? 0L : target};
    }

    // ==================== 签到 ====================

    /**
     * CN 每日签到。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return 结果消息；成功且无附加信息时返回 {@code null}
     */
    private String dailyCheckin(Map<String, Object> credentials, String realm) {
        Map<String, String> headers = billingHeaders(credentials, realm);
        String url = billingOrigin(realm) + "/v2/billing/meter/daily-checkin";
        String response;
        try {
            response = ChannelHttp.postJson(url, "{}", headers);
        } catch (ApiException e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 每日签到请求失败: " + ChannelHttp.truncate(e.getMessage()), e);
        }
        Map<String, Object> envelope = parseJson(response);
        Integer code = toInt(envelope.get("code"));
        if (code == null || code == 0) {
            return "每日签到成功";
        }
        if (code == ENTERPRISE_UNSUPPORTED) {
            return "企业账号不支持该操作，已跳过签到";
        }
        String msg = readString(envelope, "msg");
        return "签到未成功（幂等可忽略）: code=" + code + (isBlank(msg) ? "" : " " + msg);
    }

    /**
     * global 签到：注册激活 + trial 加油包。
     *
     * @param credentials 凭据
     * @param messages    结果消息累加器
     */
    private void checkinGlobal(Map<String, Object> credentials, List<String> messages) {
        String uid = readString(credentials, "uid");
        String accessToken = readString(credentials, "access_token");
        if (isBlank(uid) || isBlank(accessToken)) {
            messages.add("global 签到缺少 uid / access_token");
            return;
        }
        try {
            if (completeRegistration(uid, accessToken)) {
                messages.add("注册激活完成");
            } else {
                messages.add("注册已激活");
            }
        } catch (ApiException e) {
            messages.add("注册激活失败: " + ChannelHttp.truncate(e.getMessage()));
        }
        try {
            if (claimTrial(uid, accessToken)) {
                messages.add("trial 加油包已领取");
            } else {
                messages.add("trial 已领取过");
            }
        } catch (ApiException e) {
            messages.add("trial 领取失败: " + ChannelHttp.truncate(e.getMessage()));
        }
    }

    /**
     * global 注册激活（幂等）：先查状态，需要地区时按白名单首个（HK）提交后复查。
     *
     * @param uid         uid
     * @param accessToken 访问令牌
     * @return 本次是否新激活
     */
    private boolean completeRegistration(String uid, String accessToken) {
        RegisterStatus status = registrationStatus(uid, accessToken);
        if (status.activated()) {
            return false;
        }
        if (!status.needsRegion()) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "注册未激活: " + status.message());
        }
        List<Map<String, Object>> countries = fetchCountries(accessToken);
        if (countries.isEmpty()) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "注册可选地区为空，无法提交地区");
        }
        Map<String, Object> country = countries.get(0);
        submitRegion(accessToken, country);
        RegisterStatus after = registrationStatus(uid, accessToken);
        if (!after.activated()) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "提交地区后仍未激活: " + after.message());
        }
        return true;
    }

    /**
     * 查询 global 注册状态。
     *
     * @param uid         uid
     * @param accessToken 访问令牌
     * @return 注册状态
     */
    private RegisterStatus registrationStatus(String uid, String accessToken) {
        Map<String, String> headers = registerHeaders(accessToken);
        headers.put("X-User-Id", uid);
        String url = BASE_GLOBAL + "/auth/realms/copilot/overseas/user/register?userId=" + urlEncode(uid);
        String body = ChannelHttp.getJson(url, headers);
        Map<String, Object> envelope = parseJson(body);
        Integer code = toInt(envelope.get("code"));
        String msg = readString(envelope, "msg");
        if (code != null && code == 200) {
            return new RegisterStatus(true, false, msg);
        }
        boolean regionRequired = (code != null && code == 500)
                || (msg != null && msg.toLowerCase(Locale.ROOT).contains("region required"));
        return new RegisterStatus(false, regionRequired, msg);
    }

    /**
     * 拉取 global 注册可选地区，按白名单顺序过滤。
     *
     * @param accessToken 访问令牌
     * @return 地区列表（元素含 code / enName / iso2）
     */
    private List<Map<String, Object>> fetchCountries(String accessToken) {
        String body = ChannelHttp.postJson(
                BASE_GLOBAL + "/billing/area/get-country-code",
                Json.toJson(Map.of("filterForbidden", 1)),
                registerHeaders(accessToken));
        Map<String, Object> envelope = parseJson(body);
        Object data = envelope.get("data");
        // data 是 JSON 字符串（双层信封），也可能是对象，两种都尝试
        if (data instanceof String text && !text.isBlank()) {
            data = parseJson(text);
        }
        List<Map<String, Object>> list = new ArrayList<>(8);
        if (data instanceof Map<?, ?> map) {
            Object inner = map.get("data");
            if (inner instanceof Map<?, ?> nested && nested.get("list") instanceof List<?> items) {
                list.addAll(asRows(items));
            } else if (map.get("list") instanceof List<?> items) {
                list.addAll(asRows(items));
            }
        }
        if (list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> ordered = new ArrayList<>(REGION_WHITELIST.size());
        for (String code : REGION_WHITELIST) {
            for (Map<String, Object> row : list) {
                if (code.equalsIgnoreCase(readString(row, "ISO2"))
                        || code.equalsIgnoreCase(readString(row, "iso2"))) {
                    ordered.add(row);
                    break;
                }
            }
        }
        return ordered.isEmpty() ? list : ordered;
    }

    /**
     * 提交 global 注册地区（幂等）。
     *
     * @param accessToken 访问令牌
     * @param country     地区行
     */
    private void submitRegion(String accessToken, Map<String, Object> country) {
        Map<String, Object> attributes = new LinkedHashMap<>(3);
        attributes.put("countryCode", List.of(orEmpty(readString(country, "Code"))));
        attributes.put("countryFullName", List.of(orEmpty(readString(country, "EnName"))));
        attributes.put("countryName", List.of(orEmpty(readString(country, "ISO2"))));
        String body = ChannelHttp.postJson(
                BASE_GLOBAL + "/console/login/account",
                Json.toJson(Map.of("attributes", attributes)),
                registerHeaders(accessToken));
        Map<String, Object> envelope = parseJson(body);
        Integer code = toInt(envelope.get("code"));
        if (code != null && code != 0) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "提交注册地区失败: code=" + code + " msg=" + readString(envelope, "msg"));
        }
    }

    /**
     * 领取 global 一次性 trial 加油包（14051 = 已领过，按幂等成功处理）。
     *
     * @param uid         uid
     * @param accessToken 访问令牌
     * @return 本次是否新领取
     */
    private boolean claimTrial(String uid, String accessToken) {
        Map<String, String> headers = registerHeaders(accessToken);
        headers.put("X-User-Id", uid);
        String response;
        try {
            response = ChannelHttp.postJson(
                    BASE_GLOBAL + "/billing/ide/trial", null, headers);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("\"code\":14051") || message.contains("code=14051")) {
                return false;
            }
            throw e;
        }
        Map<String, Object> envelope = parseJson(response);
        Integer code = toInt(envelope.get("code"));
        if (code != null && code == TRIAL_ALREADY_CLAIMED) {
            return false;
        }
        if (code != null && code != 0) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "trial 领取失败: code=" + code + " msg=" + readString(envelope, "msg"));
        }
        return true;
    }

    /**
     * 查询账号余额（个人：{@code get-user-resource}；企业：{@code get-enterprise-user-usage}）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return {@code [剩余, 总额]}；查询失败返回 {@code null}
     */
    private long[] balance(Map<String, Object> credentials, String realm) {
        try {
            Map<String, String> headers = billingHeaders(credentials, realm);
            if (!isBlank(readString(credentials, "enterprise_id"))) {
                String body = ChannelHttp.postJson(
                        billingOrigin(realm) + "/v2/billing/meter/get-enterprise-user-usage",
                        "{}", headers);
                return enterpriseBalance(parseJson(body));
            }
            Map<String, Object> request = new LinkedHashMap<>(6);
            request.put("PageNumber", 1);
            request.put("PageSize", 100);
            request.put("ProductCode", "p_tcaca");
            request.put("Status", List.of(0, 3));
            String body = ChannelHttp.postJson(
                    billingOrigin(realm) + "/v2/billing/meter/get-user-resource",
                    Json.toJson(request), headers);
            return personalBalance(parseJson(body));
        } catch (ApiException e) {
            log.debug("[workbuddy] 余额查询失败: {}", ChannelHttp.truncate(e.getMessage()));
            return null;
        }
    }

    /**
     * 解析个人资源包余额（{@code Response.Data.Accounts[]} 聚合）。
     *
     * @param envelope 响应信封
     * @return {@code [剩余, 总额]}；结构异常返回 {@code null}
     */
    private static long[] personalBalance(Map<String, Object> envelope) {
        if (!(envelope.get("Response") instanceof Map<?, ?> response)
                || !(response.get("Data") instanceof Map<?, ?> data)
                || !(data.get("Accounts") instanceof List<?> accounts)) {
            return null;
        }
        long remain = 0L;
        long total = 0L;
        for (Object item : accounts) {
            if (!(item instanceof Map<?, ?> account)) {
                continue;
            }
            Long cycleRemain = toLong(account.get("CycleCapacityRemain"));
            Long capacityRemain = toLong(account.get("CapacityRemain"));
            Long cycleSize = toLong(account.get("CycleCapacitySize"));
            Long capacitySize = toLong(account.get("CapacitySize"));
            long usedRemain = firstNonNull(cycleRemain, capacityRemain, 0L);
            long usedSize = firstNonNull(cycleSize, capacitySize, usedRemain);
            if (usedRemain < 0) {
                usedRemain = 0L;
            }
            if (usedSize < usedRemain) {
                usedSize = usedRemain;
            }
            remain += usedRemain;
            total += usedSize;
        }
        return new long[]{remain, total};
    }

    /**
     * 解析企业配额（{@code credit} 是本周期已用，{@code limitNum} 是分配额度）。
     *
     * @param envelope 响应信封
     * @return {@code [剩余, 总额]}；结构异常返回 {@code null}
     */
    private static long[] enterpriseBalance(Map<String, Object> envelope) {
        Object data = envelope.get("data");
        Map<String, Object> node = data instanceof Map<?, ?> map ? asMap(map) : envelope;
        Long credit = toLong(node.get("credit"));
        Long limit = firstNonNullLong(toLong(node.get("limitNum")), toLong(node.get("limit_num")));
        if (limit == null) {
            return null;
        }
        if (limit < 0) {
            // 不限量：与 panel 口径一致，剩余给足够大的值，总额记 -1 表示「不限」
            return new long[]{1L << 40, -1L};
        }
        long used = credit == null ? 0L : Math.round(credit);
        long remain = limit - used;
        return new long[]{Math.max(remain, 0L), limit};
    }

    // ==================== auth 文件 ====================

    /**
     * 落盘 auth 文件（嵌套形，与 panel 的 {@code auths/} 目录格式一致）。
     *
     * @param uid        账号标识（已通过 {@link #validUID(String)}）
     * @param credential 凭据
     */
    private void writeAuthFile(String uid, Map<String, Object> credential) {
        if (!validUID(uid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "WorkBuddy 账号标识非法，拒绝落盘: " + uid);
        }
        Path dir = authDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建 WorkBuddy 认证目录失败: " + e.getMessage(), e);
        }
        Map<String, Object> auth = new LinkedHashMap<>(6);
        auth.put("accessToken", readString(credential, "access_token"));
        auth.put("refreshToken", readString(credential, "refresh_token"));
        Long expireTime = toLong(credential.get("expire_time"));
        auth.put("expiresAt", expireTime == null ? 0L : expireTime / 1000L);
        auth.put("domain", readString(credential, "domain"));
        auth.put("realm", credentialRealm(credential));
        Map<String, Object> account = new LinkedHashMap<>(4);
        account.put("uid", uid);
        account.put("enterpriseId", readString(credential, "enterprise_id"));
        account.put("nickname", readString(credential, "nickname"));
        Map<String, Object> document = new LinkedHashMap<>(4);
        document.put("auth", auth);
        document.put("account", account);
        Path target = dir.resolve("workbuddy-" + uid + ".json");
        Path temp = dir.resolve("workbuddy-" + uid + ".json.tmp");
        try {
            // tmp + rename：避免进程中断留下半截 auth 文件
            Files.writeString(temp, Json.toJson(document) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "写入 WorkBuddy 认证文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 尽力落盘（续期路径用：写盘失败不应让续期结果丢失）。
     *
     * @param uid        账号标识
     * @param credential 凭据
     */
    private void writeAuthFileSafely(String uid, Map<String, Object> credential) {
        if (!validUID(uid)) {
            return;
        }
        try {
            writeAuthFile(uid, credential);
        } catch (ApiException e) {
            log.warn("[workbuddy] 续期后写盘失败 uid={}: {}", uid, e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 回收超时登录会话（防「开弹窗走开」的 state 滞留）。
     */
    private void purgeExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().created() > STATE_TTL_MILLIS);
    }

    /**
     * 解析轮询时的账号域：优先内存会话，其次句柄，最后按配置兜底。
     *
     * @param state  登录 state
     * @param handle 句柄
     * @return 账号域
     */
    private String handleRealm(String state, Map<String, Object> handle) {
        LoginSession session = sessions.get(state);
        if (session != null) {
            return session.realm();
        }
        return resolveRealm(firstNonBlank(readString(handle, HANDLE_REALM), cfg("realm", "cn")));
    }

    /**
     * 归一化账号域：仅 {@code global} 保留，其余视为 {@code cn}。
     *
     * @param realm 原始值
     * @return 归一化结果
     */
    private static String resolveRealm(String realm) {
        return "global".equalsIgnoreCase(realm == null ? "" : realm.trim()) ? "global" : "cn";
    }

    /**
     * 从凭据推断账号域：显式 {@code realm} → {@code domain}/{@code base_url} 后缀 → cn。
     *
     * @param credentials 凭据
     * @return 账号域
     */
    private static String credentialRealm(Map<String, Object> credentials) {
        String realm = readString(credentials, HANDLE_REALM);
        if (!isBlank(realm)) {
            return resolveRealm(realm);
        }
        String domain = readString(credentials, "domain");
        if (!isBlank(domain) && domain.contains(GLOBAL_DOMAIN_SUFFIX)) {
            return "global";
        }
        String baseUrl = readString(credentials, "base_url");
        if (!isBlank(baseUrl) && baseUrl.contains(GLOBAL_DOMAIN_SUFFIX)) {
            return "global";
        }
        return "cn";
    }

    /**
     * 上游 chat 根地址（可覆写，供测试指向 mock）。
     *
     * @param realm 账号域
     * @return 根地址
     */
    protected String upstreamOrigin(String realm) {
        return "global".equals(realm) ? BASE_GLOBAL : BASE_CN;
    }

    /**
     * billing / 成长中心根地址（CN 与 chat 不同域）。
     *
     * @param realm 账号域
     * @return 根地址
     */
    protected String billingOrigin(String realm) {
        return "global".equals(realm) ? BASE_GLOBAL : BILLING_CN;
    }

    /**
     * web 成长中心根地址（领奖端点，CN 为 {@code www.workbuddy.cn}）。
     *
     * @param realm 账号域
     * @return 根地址
     */
    protected String webOrigin(String realm) {
        return "global".equals(realm) ? BASE_GLOBAL : WEB_ORIGIN_CN;
    }

    /**
     * panel 地址（可覆写，供测试指向 mock）。
     *
     * @return panel 根地址
     */
    protected String panelOrigin() {
        String url = cfg("panel-url", DEFAULT_PANEL).trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * auth 文件目录（可覆写，供测试指向临时目录）。
     *
     * @return 目录
     */
    protected Path authDir() {
        String configured = cfg("auth-dir", "");
        if (!isBlank(configured)) {
            return Path.of(configured.trim());
        }
        return Path.of(System.getProperty("user.home"), ".workbuddy2api", "auths");
    }

    /**
     * panel 请求头（panel 自身要求 JSON + 同域 Origin）。
     *
     * @param realm 账号域
     * @return 请求头
     */
    private Map<String, String> jsonHeaders(String realm) {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Origin", panelOrigin());
        headers.put("User-Agent", userAgent(realm));
        return headers;
    }

    /**
     * 上游公共请求头（对齐 {@code panel/internal/upstream/headers.go} 的 CommonHeaders）。
     *
     * @param realm 账号域
     * @return 请求头
     */
    private Map<String, String> commonHeaders(String realm) {
        boolean global = "global".equals(realm);
        String origin = global ? ORIGIN_GLOBAL : ORIGIN_CN;
        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Origin", origin);
        headers.put("Referer", origin + "/");
        headers.put("User-Agent", userAgent(realm));
        headers.put("X-CodeBuddy-Request", "1");
        headers.put("Accept-Language", global ? "en-US" : "zh-CN");
        return headers;
    }

    /**
     * billing 域请求头（对齐 {@code BillingHeaders}：单段 UA + 账号域头）。
     *
     * @param credentials 凭据
     * @param realm       账号域
     * @return 请求头
     */
    private Map<String, String> billingHeaders(Map<String, Object> credentials, String realm) {
        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("X-CodeBuddy-Request", "1");
        headers.put("Accept-Language", "global".equals(realm) ? "en-US" : "zh-CN");
        headers.put("User-Agent", "WorkBuddy/" + CLIENT_VERSION);
        headers.put("Authorization", "Bearer " + readString(credentials, "access_token"));
        String uid = readString(credentials, "uid");
        if (!isBlank(uid)) {
            headers.put("X-User-Id", uid);
        }
        String enterpriseId = readString(credentials, "enterprise_id");
        if (!isBlank(enterpriseId)) {
            headers.put("X-Enterprise-Id", enterpriseId);
            headers.put("X-Tenant-Id", enterpriseId);
        }
        String domain = readString(credentials, "domain");
        if (!isBlank(domain)) {
            headers.put("X-Domain", domain);
        }
        return headers;
    }

    /**
     * global 注册激活域请求头（web 端形态）。
     *
     * @param accessToken 访问令牌
     * @return 请求头
     */
    private static Map<String, String> registerHeaders(String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>(6);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("User-Agent", WEB_UA);
        headers.put("Origin", BASE_GLOBAL);
        headers.put("Referer", BASE_GLOBAL + "/");
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }

    /**
     * 路由目标额外请求头（账号域声明与账号标识，供 {@code workbuddy} 客户端复用）。
     *
     * @param realm 账号域
     * @param uid   uid
     * @return 请求头；uid 为空时只返回域名头
     */
    private static Map<String, String> realmHeaders(String realm, String uid) {
        Map<String, String> headers = new LinkedHashMap<>(2);
        headers.put("X-Domain", realmHost(realm));
        if (!isBlank(uid)) {
            headers.put("X-User-Id", uid);
        }
        return headers;
    }

    /**
     * 账号域对应的 X-Domain 主机名。
     *
     * @param realm 账号域
     * @return 主机名
     */
    private static String realmHost(String realm) {
        return "global".equals(realm) ? "www.workbuddy.ai" : "copilot.tencent.com";
    }

    /**
     * 按账号域组装 UA（global 平台段为 {@code WorkBuddy AI}，送错段会被上游风控）。
     *
     * @param realm 账号域
     * @return UA
     */
    private static String userAgent(String realm) {
        String platform = "global".equals(realm) ? "WorkBuddy AI" : "WorkBuddy";
        return "WorkBuddy/" + CLIENT_VERSION + " " + platform + "/" + CLIENT_VERSION
                + " CLI/" + CLI_VERSION;
    }

    /**
     * 拉取 {@code /v3/config} 的模型目录。
     *
     * @param headers 请求头
     * @param realm   账号域
     * @return 模型列表；失败返回空
     */
    private List<Map<String, Object>> v3Models(Map<String, String> headers, String realm) {
        try {
            String body = ChannelHttp.getJson(upstreamOrigin(realm) + "/v3/config", headers);
            Map<String, Object> envelope = parseJson(body);
            Integer code = toInt(envelope.get("code"));
            if (code == null || code != 0) {
                return List.of();
            }
            if (!(envelope.get("data") instanceof Map<?, ?> data)
                    || !(data.get("models") instanceof List<?> items)) {
                return List.of();
            }
            List<Map<String, Object>> models = new ArrayList<>(items.size());
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                String id = readString(asMap(raw), "id");
                if (isBlank(id)) {
                    continue;
                }
                models.add(modelRow(id, asMap(raw), "maxInputTokens", "maxOutputTokens"));
            }
            return models;
        } catch (ApiException e) {
            log.debug("[workbuddy] /v3/config 模型拉取失败: {}", ChannelHttp.truncate(e.getMessage()));
            return List.of();
        }
    }

    /**
     * 回落拉取 {@code /console/enterprises/personal/models} 的 cli agent 模型子集。
     *
     * @param headers 请求头
     * @param realm   账号域
     * @return 模型列表；失败返回空
     */
    private List<Map<String, Object>> consoleModels(Map<String, String> headers, String realm) {
        try {
            String body = ChannelHttp.getJson(
                    upstreamOrigin(realm) + "/console/enterprises/personal/models", headers);
            Map<String, Object> envelope = parseJson(body);
            if (!(envelope.get("data") instanceof Map<?, ?> data)) {
                return List.of();
            }
            List<String> cliModels = new ArrayList<>(16);
            if (data.get("agents") instanceof List<?> agents) {
                for (Object item : agents) {
                    Map<String, Object> agent = item instanceof Map<?, ?> map ? asMap(map) : null;
                    if (agent == null || !"cli".equals(readString(agent, "name"))) {
                        continue;
                    }
                    if (agent.get("models") instanceof List<?> list) {
                        for (Object model : list) {
                            if (model instanceof String text && !text.isBlank()) {
                                cliModels.add(text);
                            }
                        }
                    }
                }
            }
            if (cliModels.isEmpty()) {
                return List.of();
            }
            Map<String, Map<String, Object>> catalog = new LinkedHashMap<>(32);
            if (data.get("models") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    Map<String, Object> model = asMap(raw);
                    String id = readString(model, "id");
                    if (!isBlank(id)) {
                        catalog.put(id, model);
                    }
                }
            }
            List<Map<String, Object>> models = new ArrayList<>(cliModels.size());
            for (String id : cliModels) {
                Map<String, Object> model = catalog.get(id);
                models.add(model == null
                        ? modelRow(id, Map.of(), "maxInputTokens", "maxOutputTokens")
                        : modelRow(id, model, "maxInputTokens", "maxOutputTokens"));
            }
            return models;
        } catch (ApiException e) {
            log.debug("[workbuddy] console 模型拉取失败: {}", ChannelHttp.truncate(e.getMessage()));
            return List.of();
        }
    }

    /**
     * 组装单条模型行。
     *
     * @param id         模型标识
     * @param model      上游模型节点
     * @param contextKey 上下文窗口字段名
     * @param outputKey  最大输出字段名
     * @return 模型行
     */
    private static Map<String, Object> modelRow(String id, Map<String, Object> model,
                                               String contextKey, String outputKey) {
        Map<String, Object> row = new LinkedHashMap<>(5);
        row.put("id", id);
        String name = readString(model, "name");
        row.put("name", isBlank(name) ? id : name);
        Long context = toLong(model.get(contextKey));
        if (context != null && context > 0) {
            row.put("contextWindow", context);
        }
        Long output = toLong(model.get(outputKey));
        if (output != null && output > 0) {
            row.put("maxOutputTokens", output);
        }
        String description = readString(model, "descriptionZh");
        if (!isBlank(description)) {
            row.put("description", description);
        }
        return row;
    }

    /**
     * 校验 uid 是否可安全用于拼文件名。
     *
     * <p>只放行 {@code [A-Za-z0-9_-]}（腾讯侧 uid 实测为 UUID 形态），长度上限 64 兜底
     * 异常超长串；拒绝 {@code .} / {@code /} / {@code \} 等路径字符与空串。</p>
     *
     * @param uid 上游返回的账号标识
     * @return 可安全使用时返回 {@code true}
     */
    static boolean validUID(String uid) {
        if (uid == null || uid.isEmpty() || uid.length() > 64) {
            return false;
        }
        for (int i = 0; i < uid.length(); i++) {
            char c = uid.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * 注册状态。
     *
     * @param activated   是否已激活
     * @param needsRegion 是否需要补地区
     * @param message     上游消息
     */
    private record RegisterStatus(boolean activated, boolean needsRegion, String message) {
    }

    /**
     * 配置读取（系统属性 → 环境变量 → 默认值）。
     *
     * @param key      配置键（不含前缀）
     * @param fallback 默认值
     * @return 生效值
     */
    private static String cfg(String key, String fallback) {
        String value = System.getProperty("chua.workbuddy." + key);
        if (value == null || value.isBlank()) {
            String envKey = "WORKBUDDY_" + key.toUpperCase(Locale.ROOT).replace('-', '_');
            value = System.getenv(envKey);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * 解析 JSON（失败返回空 Map）。
     *
     * @param text JSON 文本
     * @return 解析结果
     */
    private static Map<String, Object> parseJson(String text) {
        if (isBlank(text)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = Json.getJsonObject(text);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Map 键归一化为字符串键。
     *
     * @param raw 原始 Map
     * @return 字符串键 Map
     */
    private static Map<String, Object> asMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    /**
     * 把 List 中的 Map 元素转为字符串键 Map。
     *
     * @param items 原始列表
     * @return 行列表
     */
    private static List<Map<String, Object>> asRows(List<?> items) {
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                rows.add(asMap(map));
            }
        }
        return rows;
    }

    /**
     * 渠道状态 Map。
     *
     * @param state  状态
     * @param detail 说明
     * @return 状态 Map
     */
    private static Map<String, Object> statusOf(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    /**
     * 读取非空字符串。
     *
     * @param source 源 Map
     * @param key    键
     * @return 值；不存在或空串返回 {@code null}
     */
    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * 判断空串。
     *
     * @param value 字符串
     * @return 空或 null 返回 {@code true}
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 空串归一化。
     *
     * @param value 原值
     * @return 原值；{@code null} 时返回空串
     */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 取首个非空字符串。
     *
     * @param values 候选值
     * @return 首个非空值；全空返回 {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 转为 Long。
     *
     * @param value 原始值
     * @return 长整数；无法转换返回 {@code null}
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 转为 Integer。
     *
     * @param value 原始值
     * @return 整数；无法转换返回 {@code null}
     */
    private static Integer toInt(Object value) {
        Long result = toLong(value);
        return result == null ? null : result.intValue();
    }

    /**
     * 取首个非空 Long。
     *
     * @param values 候选值
     * @return 首个非空值；全空返回 {@code null}
     */
    private static Long firstNonNullLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 取首个非空 Long（带默认值）。
     *
     * @param first      首选
     * @param second     次选
     * @param defaultVal 默认值
     * @return 生效值
     */
    private static long firstNonNull(Long first, Long second, long defaultVal) {
        if (first != null) {
            return first;
        }
        return second == null ? defaultVal : second;
    }

    /**
     * URL 编码。
     *
     * @param value 原文
     * @return 编码结果；编码失败返回原文
     */
    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * state 摘要（日志里只露前 8 位）。
     *
     * @param state 原文
     * @return 摘要
     */
    private static String abbreviate(String state) {
        if (state == null) {
            return "";
        }
        return state.length() <= 8 ? state : state.substring(0, 8);
    }
}

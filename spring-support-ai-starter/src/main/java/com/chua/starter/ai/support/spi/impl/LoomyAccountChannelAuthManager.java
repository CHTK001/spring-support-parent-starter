package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loomy（讯飞办公助手）账号渠道鉴权管理器实现。
 *
 * <p>协议对齐 {@code dsh-our-free-model} 的 {@code loomy-wechat-login.ts} /
 * {@code loomy-wechat.ts} / {@code loomy-oauth.ts} / {@code loomy-sign.ts}，
 * <b>微信扫码登录</b>——与其它浏览器授权渠道保持一致，不再在页面里收集手机号：</p>
 * <ol>
 *   <li>{@code startLogin} 拉取微信开放平台授权页，从 HTML 内嵌内容提取二维码
 *       {@code uuid}，返回 {@code verifyUrl}（官方授权页，前端自动打开）+
 *       {@code qrImage}（二维码图片 data URL，前端可直接渲染）+ {@code uuid}；</li>
 *   <li>{@code pollLogin} 长轮询 {@code long.open.weixin.qq.com/connect/l/qrconnect}，
 *       微信确认（errcode=405）拿到一次性 {@code code} 后调
 *       {@code /login/thirdAccount/bind/auth}；该微信已绑手机号（bind=1）走
 *       {@code bind/skip} 直接换 {@code session}；未绑（bind=0）则明确报错，
 *       引导用户先在官网完成手机号绑定后再回来扫码。</li>
 * </ol>
 *
 * <p>⚠️ 三个易踩点（源码注释原样保留）：</p>
 * <ul>
 *   <li>账号端点认证是 <b>HMAC-SHA1 签名</b>（{@code Authorization: account {ak}:{sig}}），
 *       不是 Bearer；签名字符串 9 段 {@code \n} 连接，后两段（x-* 头）恒为空串，
 *       末尾两个换行不能去掉；</li>
 *   <li>body 必须<b>先序列化成字符串</b>，签名与发送共用同一字符串——二次序列化改变键序/空格
 *       会导致签名失效；</li>
 *   <li>讯飞账号端点<b>业务失败恒 HTTP 200</b>，成败只能读 body 的 {@code code}
 *       （成功 {@code 000000}），只看状态码会把「登录已失效」误判成成功。</li>
 * </ul>
 *
 * <p>微信长轮询状态以授权页内嵌官方 JS 为准：408 待扫码 / 404 已扫码待确认（继续轮询）/
 * 405 已确认（本帧带 {@code wx_code}）/ 403 取消 / 402 二维码失效。</p>
 *
 * <p>⚠️ 无 refresh 端点：{@code session} 是登录时声明 14 天有效期的，凭据里没有
 * refresh_token，到期只能重新扫码登录（{@link #refresh} 抛 {@code NOT_IMPLEMENTED}）。
 * 每日额度由服务端在 {@code first-login} 发放、onboarding 任务一次性领取，
 * 均非「签到」动作，故 {@code supportsCheckin=false}。</p>
 *
 * <p>凭据落库：{@code access_token}(session) + {@code userid} + {@code phone}（微信路径为空）+
 * {@code expires_at}（登录时刻 + 14 天本地推算，服务端响应不带到期时间）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Spi("loomy")
public class LoomyAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 业务端点基址（推理 / 模型 / 积分 / onboarding）。 */
    static final String API_BASE = "https://loomyad.xunfei.cn";
    /** 讯飞账号（CAccount）基址（微信绑定/跳步端点全挂它下面）。 */
    static final String ACCOUNT_BASE = "https://account.xfinfr.com";
    /** 业务成功码。 */
    static final String OK_CODE = "000000";

    /** 讯飞账号 AccessKey（取自 Loomy 客户端 .env.prod，公开值，仅用于签名，不含用户数据）。 */
    static final String ACCESS_KEY_ID = "2thryby66wxi53sk";
    static final String ACCESS_KEY_SECRET = "zsak6eadrbawz683wf5r3m2snrwj868r";
    /** 讯飞 appId（账号请求体 base.appid）。 */
    static final String APP_ID = "GM3LOOMY";
    /** 客户端 UA（照抄官方：Windows 上也是 macOS，不要改）。 */
    static final String USER_AGENT = "Loomy|Desktop|Electron|macOS";

    /** 登录会话有效期（秒）= 14 天（服务端只接受声明，响应不带到期时间）。 */
    static final long SESSION_TTL_SECONDS = 1_209_600L;

    /** 微信开放平台「网站应用」AppID（取自 Loomy 客户端 .env.prod）。 */
    static final String WECHAT_APP_ID = "wx18d60be432287cf8";
    /** 微信授权回调地址：必须是官方地址（域名白名单校验），实际返回 404，仅作占位。 */
    static final String WECHAT_REDIRECT_URI = "https://loomy.xunfei.cn/oauth/wechat/callback";
    /** 微信长轮询单次 HTTP 超时（毫秒）：不做 25s 慢速挂起，交给 SSE 循环驱动反复轮询。 */
    static final long WECHAT_POLL_HTTP_TIMEOUT_MS = 15_000L;
    /** 微信浏览器 UA：微信对空 UA / 爬虫 UA 可能拒绝。 */
    static final String WECHAT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 句柄：微信二维码 uuid。 */
    static final String HANDLE_UUID = "uuid";
    /** 句柄：微信长轮询最近一次 errcode（作 next 的 last 参数）。 */
    static final String HANDLE_LAST_ERRCODE = "lastErrcode";

    /** 微信授权页二维码 uuid 提取正则（img src 主路径）。 */
    static final Pattern WECHAT_UUID_IMG_PATTERN =
            Pattern.compile("/connect/qrcode/([A-Za-z0-9_\\-=+/]+)");
    /** 微信授权页二维码 uuid 提取正则（长轮询 URL 兜底路径）。 */
    static final Pattern WECHAT_UUID_POLL_PATTERN =
            Pattern.compile("l/qrconnect\\?uuid=([A-Za-z0-9_\\-=+/]+)");
    /** 微信长轮询 errcode 读取正则。 */
    static final Pattern WECHAT_POLL_ERRCODE_PATTERN =
            Pattern.compile("wx_errcode\\s*=\\s*(\\d+)");
    /** 微信长轮询 wx_code（一次性授权码）读取正则。 */
    static final Pattern WECHAT_POLL_CODE_PATTERN =
            Pattern.compile("wx_code\\s*=\\s*'([^']*)'");
    /** 微信二维码 uuid 字符集下限（微信未承诺长度，收紧只会静默失效）。 */
    static final Pattern WECHAT_UUID_VALID =
            Pattern.compile("^[A-Za-z0-9_\\-=+/]{6,64}$");

    @Override
    public String channel() {
        return "loomy";
    }

    @Override
    public String displayName() {
        return "Loomy（讯飞）";
    }

    @Override
    public String loginMethod() {
        return "qr-code";
    }

    /**
     * {@inheritDoc}
     *
     * <p>拉取微信开放平台授权页，从 HTML {@code <img class="js_qrcode_img">} 内嵌的
     * {@code uuid} 提取二维码标识，返回：</p>
     * <ul>
     *   <li>{@code verifyUrl}：官方微信授权页（前端自动打开，用户手机扫码）；</li>
     *   <li>{@code qrImage}：二维码图片 data URL（前端弹窗内联渲染，扫码渠道一致的观感）；</li>
     *   <li>{@code uuid} + {@code lastErrcode}：长轮询上下文，回传给 {@link #pollLogin}。</li>
     * </ul>
     */
    @Override
    public Map<String, Object> startLogin() {
        String state = UUID.randomUUID().toString().replace("-", "");
        String authUrl = buildWechatAuthUrl(state);
        String html;
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", WECHAT_UA);
            headers.put("Referer", "https://open.weixin.qq.com/");
            html = ChannelHttp.getJson(authUrl, headers);
        } catch (ApiException e) {
            if (ChannelHttp.isRateLimited(e)) {
                throw e;
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "微信授权页拉取失败: " + e.getMessage());
        }
        String uuid = extractWechatUuid(html);
        if (isBlank(uuid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "微信授权页未包含二维码 uuid（页面结构可能已变化）");
        }
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "loomy-wechat");
        handle.put("loginMethod", loginMethod());
        handle.put(HANDLE_UUID, uuid);
        handle.put(HANDLE_LAST_ERRCODE, "");
        handle.put("startedAt", System.currentTimeMillis());
        handle.put("verifyUrl", authUrl);
        handle.put("qrImageUrl", buildWechatQrImageUrl(uuid));
        try {
            handle.put("qrImage", wechatQrDataUrl(uuid));
        } catch (ApiException e) {
            log.warn("[Loomy] 微信二维码图片拉取失败，弹窗仍可打开官方授权页: {}", e.getMessage());
        }
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>微信扫码状态机（按授权页内嵌官方 JS，{@code switch(window.wx_errcode)}）：</p>
     * <ol>
     *   <li>408 待扫码 / 404 已扫码待确认 → 返回 {@code null} 继续等待；</li>
     *   <li><b>405</b> 已确认且本帧携带 {@code wx_code} → 用 code 调
     *       {@code bind/auth} 换 rcode；该微信<b>已绑手机号</b>（bind=1）直接
     *       {@code bind/skip} 换 {@code session} 返回凭据；<b>未绑</b>（bind=0）
     *       抛错引导先在官网绑定手机号（页面不收集手机号）；</li>
     *   <li>403 用户取消 / 402 二维码失效 → 抛 {@link ApiException} 终止流。</li>
     * </ol>
     *
     * <p>805（网络异常）与未知 errcode 一律按「继续等待」处理，不误判失败。</p>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, HANDLE_UUID))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "Loomy 登录句柄缺失 uuid，请重新发起绑定");
        }
        String uuid = readString(handle, HANDLE_UUID);
        String last = readString(handle, HANDLE_LAST_ERRCODE);
        WechatPollResult polled = wechatPollOnce(uuid, last == null ? "" : last);
        String errcode = polled.errcode;
        handle.put(HANDLE_LAST_ERRCODE, isBlank(errcode) ? "" : errcode);

        switch (errcode) {
            case "405": {
                // 已确认：本帧必须有 wx_code，否则继续等（绝不拿空 code 换 session）。
                if (isBlank(polled.code)) {
                    return null;
                }
                return completeWechatLogin(polled.code);
            }
            case "403":
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "你已取消微信授权，请重新发起绑定");
            case "402":
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "二维码已失效，请点「重新发起」获取新二维码");
            case "404":
                // 已扫码待确认：继续轮询。
                return null;
            default:
                // 408 / 网络异常 / 未知：继续等待（长轮询偶发失败不代表流程失败）。
                return null;
        }
    }

    /**
     * 用微信一次性 code 完成登录：bindAuth → bind=1 时 bindSkip 换 session。
     *
     * @param code 微信一次性授权码
     * @return 可持久化凭据
     */
    private Map<String, Object> completeWechatLogin(String code) {
        Map<String, Object> auth = bindAuth(code);
        String rcode = readString(auth, "rcode");
        if (isBlank(rcode)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "Loomy 微信授权响应缺少 rcode");
        }
        // bind 缺失归 0（走防错：未绑手机号必须明确引导，避免拿到无手机号的账号）
        boolean bound = "1".equals(String.valueOf(auth.get("bind")));
        if (!bound) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "该微信账号尚未绑定讯飞手机号：请先用手机号在 Loomy 客户端/官网完成绑定，"
                            + "再回来扫码登录");
        }
        Map<String, Object> data = bindSkip(rcode);
        String session = readString(data, "session");
        String userid = readLooseString(data, "userid");
        if (isBlank(session) || isBlank(userid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Loomy bindSkip 响应缺少 session / userid");
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", session);
        credential.put("userid", userid);
        credential.put("phone", "");
        credential.put("wechat", true);
        credential.put("expires_at", System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000);
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ Loomy 没有任何 refresh 端点（session 是登录时声明 14 天得到的，
     * 凭据里没有 refresh_token），到期只能重新微信扫码登录。</p>
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> credentials) {
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED,
                "Loomy 凭据不可续期（无 refresh 端点），请重新登录");
    }

    /**
     * {@inheritDoc}
     *
     * <p>本地到期判定：{@code expires_at}（毫秒字符串）+ 60s 余量；
     * 无法解析时不判过期（服务端 100002 由调用方识别）。</p>
     */
    @Override
    public Map<String, Object> status(Map<String, Object> credentials) {
        String accessToken = readString(credentials, "access_token");
        if (isBlank(accessToken)) {
            return status("invalid", "凭据缺少 access_token（session）");
        }
        Long expiresAt = toLong(credentials.get("expires_at"));
        if (expiresAt != null && expiresAt - 60_000L <= System.currentTimeMillis()) {
            return status("expired", "讯飞 session 已到期（14 天），请重新登录");
        }
        return status("valid", "本地有效期判定可用（session 无 refresh，到期须重登）");
    }

    @Override
    public boolean supportsCheckin() {
        // 每日额度由服务端在 first-login 发放（登录成功后自动触发，非独立签到动作）；
        // onboarding 任务是一次性领取（幂等，领完即止）。均非「每日签到」语义，不自动触发。
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>远端模型目录：{@code GET {apiBase}/api/v1/models}，只认小写 {@code token} 请求头
     * （带 {@code Authorization: Bearer} 会被判「缺少 token」）；过滤 {@code type=chat}，
     * 失败返回空列表不抛错。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        String token = readString(credentials, "access_token");
        if (isBlank(token)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("token", token);
            String response = ChannelHttp.getJson(API_BASE + "/api/v1/models", headers);
            Map<String, Object> payload = parseJson(response);
            Map<String, Object> data = payload.get("data") instanceof Map<?, ?> d
                    ? (Map<String, Object>) d : payload;
            Object list = data.get("list") != null ? data.get("list") : data.get("models");
            if (list instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> entry
                            && "chat".equals(String.valueOf(entry.get("type")))) {
                        Map<String, Object> model = new LinkedHashMap<>();
                        model.put("id", String.valueOf(entry.get("id")));
                        model.put("name", entry.get("name") instanceof String n && !n.isBlank()
                                ? n : String.valueOf(entry.get("id")));
                        if (entry.get("context_length") instanceof Number n) {
                            model.put("contextWindow", n.intValue());
                        }
                        model.put("isFree", true);
                        result.add(model);
                    }
                }
            }
        } catch (ApiException ignored) {
            // 远端目录拉取失败不致命（返回空列表，上层可走兜底模型表）。
        }
        return result;
    }

    // ── 私有辅助 ──

    /** 微信长轮询单次结果。 */
    private record WechatPollResult(String status, String code, String errcode) {
    }

    /**
     * 微信第 1 步：用微信 code 换 rcode，并得知是否已绑手机号。
     *
     * @param code 微信一次性授权码
     * @return { bind, rcode, … }；bind=1 已绑手机号（可直接 bindSkip），0 未绑
     */
    private Map<String, Object> bindAuth(String code) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("tcode", Map.of("code", code));
        param.put("type", "wx");
        return postAccount("/login/thirdAccount/bind/auth", param);
    }

    /** 微信第 4 步（bind=1 时走）：跳过绑定，直接换 session。 */
    private Map<String, Object> bindSkip(String rcode) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("rcode", rcode);
        param.put("expire", SESSION_TTL_SECONDS);
        return postAccount("/login/thirdAccount/bind/skip", param);
    }

    /**
     * 拼微信授权页 URL（redirect_uri 必须是官方地址，微信校验域名白名单）。
     *
     * @param state 32 位随机串（微信原样回传，本实现不依赖）
     * @return 官方授权页地址
     */
    private static String buildWechatAuthUrl(String state) {
        return "https://open.weixin.qq.com/connect/qrconnect"
                + "?appid=" + URLEncoder.encode(WECHAT_APP_ID, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(WECHAT_REDIRECT_URI, StandardCharsets.UTF_8)
                + "&response_type=code&scope=snsapi_login"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "#wechat_redirect";
    }

    /** 拼微信二维码图片地址。 */
    private static String buildWechatQrImageUrl(String uuid) {
        return "https://open.weixin.qq.com/connect/qrcode/" + URLEncoder.encode(uuid, StandardCharsets.UTF_8);
    }

    /**
     * 从授权页 HTML 提取二维码 uuid（页面直接内嵌，无需执行 JS）。
     *
     * @param html 授权页 HTML
     * @return uuid；提取不到返回空串
     */
    private static String extractWechatUuid(String html) {
        if (isBlank(html)) {
            return "";
        }
        // 主路径：img src 里的 /connect/qrcode/<uuid>
        Matcher img = WECHAT_UUID_IMG_PATTERN.matcher(html);
        if (img.find()) {
            String candidate = img.group(1);
            if (candidate != null && WECHAT_UUID_VALID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        // 兜底：长轮询 URL 里的 ?uuid=<uuid>
        Matcher poll = WECHAT_UUID_POLL_PATTERN.matcher(html);
        if (poll.find()) {
            String candidate = poll.group(1);
            if (candidate != null && WECHAT_UUID_VALID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return "";
    }

    /**
     * 下载微信二维码图片并转 data URL（实测 JPEG；PNG / GIF 三种魔数都认）。
     *
     * @param uuid 二维码 uuid
     * @return data URL；内容不是已知图片格式时抛错
     */
    private static String wechatQrDataUrl(String uuid) {
        String url = buildWechatQrImageUrl(uuid);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", WECHAT_UA)
                    .header("Referer", "https://open.weixin.qq.com/")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = ChannelHttp.CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "微信二维码返回 HTTP " + resp.statusCode());
            }
            byte[] bytes = resp.body();
            String mime = detectImageMime(bytes);
            if (mime == null || bytes.length < 200) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "微信二维码响应不是图片（可能是错误页）");
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "微信二维码下载失败: " + e.getMessage());
        }
    }

    /** 按魔数识别图片格式；未知返回 null。 */
    private static String detectImageMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0x89 && (bytes[1] & 0xff) == 0x50
                && (bytes[2] & 0xff) == 0x4e && (bytes[3] & 0xff) == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0x47 && (bytes[1] & 0xff) == 0x49
                && (bytes[2] & 0xff) == 0x46) {
            return "image/gif";
        }
        return null;
    }

    /**
     * 执行一次微信长轮询。
     *
     * <p>网络异常返回 {@code error} 状态而不抛错（长轮询偶发失败不该终止流程）。</p>
     *
     * @param uuid  二维码 uuid
     * @param last  上一次 errcode（无则空串）
     * @return 轮询结果
     */
    private static WechatPollResult wechatPollOnce(String uuid, String last) {
        String query = "uuid=" + URLEncoder.encode(uuid, StandardCharsets.UTF_8)
                + (isBlank(last) ? "" : "&last=" + URLEncoder.encode(last, StandardCharsets.UTF_8))
                + "&_=" + System.currentTimeMillis();
        String url = "https://long.open.weixin.qq.com/connect/l/qrconnect?" + query;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(WECHAT_POLL_HTTP_TIMEOUT_MS))
                    .header("User-Agent", WECHAT_UA)
                    .header("Referer", buildWechatAuthUrl(""))
                    .GET()
                    .build();
            HttpResponse<String> resp = ChannelHttp.CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() == null ? "" : resp.body();
            Matcher codeMatcher = WECHAT_POLL_ERRCODE_PATTERN.matcher(body);
            String errcode = codeMatcher.find() ? codeMatcher.group(1) : "";
            Matcher codeValue = WECHAT_POLL_CODE_PATTERN.matcher(body);
            String code = codeValue.find() ? codeValue.group(1) : "";
            if ("405".equals(errcode)) {
                // 405 = 已确认，wx_code 就在这一帧；405 却没带 code 归 scanned 继续等。
                return isBlank(code)
                        ? new WechatPollResult("scanned", "", errcode)
                        : new WechatPollResult("confirmed", code, errcode);
            }
            if ("404".equals(errcode)) {
                // 已扫码待确认：继续轮询。
                return new WechatPollResult("scanned", "", errcode);
            }
            if ("403".equals(errcode)) {
                return new WechatPollResult("cancelled", "", errcode);
            }
            if ("402".equals(errcode)) {
                return new WechatPollResult("expired", "", errcode);
            }
            // 408 与未知值一律归 waiting（保守：绝不误判成功）。
            return new WechatPollResult("waiting", "", errcode);
        } catch (Exception e) {
            return new WechatPollResult("error", "", "network");
        }
    }

    /** 发一次讯飞账号端点请求并拆信封。
     *
     * <p>body 序列化一次、签名与发送共用；HTTP 恒 200，成败读 body 的
     * {@code code}（成功 {@code 000000}，容忍数字/字符串两种形态），
     * 失败透传 {@code desc}。</p>
     */
    private Map<String, Object> postAccount(String path, Map<String, Object> param) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("base", accountBase());
        body.put("param", param);
        String serialized = Json.toJSONString(body);
        Map<String, String> headers = signHeaders("POST", path, serialized);
        String response = ChannelHttp.postJson(ACCOUNT_BASE + path, serialized, headers);
        Map<String, Object> payload = parseJson(response);
        String code = readCode(payload);
        if (!OK_CODE.equals(code)) {
            String desc = readLooseString(payload, "desc");
            if (desc == null || desc.isBlank()) {
                desc = readLooseString(payload, "message");
            }
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "讯飞账号请求失败（" + path + "）: "
                            + (desc != null && !desc.isBlank() ? desc : "业务错误 " + code));
        }
        return payload.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
    }

    /** 读信封 code（容忍数字/字符串两种下发形态），缺失返回空串。 */
    private static String readCode(Map<String, Object> payload) {
        Object value = payload.get("code");
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number n) {
            return String.valueOf(n.intValue());
        }
        return "";
    }

    /** 读宽松字符串（容忍数字下发的字段，如 msgid / desc）。 */
    private static String readLooseString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        if (value instanceof Number n) {
            return String.valueOf(n);
        }
        return null;
    }

    /** 账号请求体 base 段（appid/modelid/version/devid/ua/traceid，traceid 每次新生成）。 */
    private Map<String, Object> accountBase() {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("appid", APP_ID);
        base.put("modelid", "Web");
        base.put("version", "1.0.0");
        base.put("devid", "web");
        base.put("ua", USER_AGENT);
        base.put("traceid", UUID.randomUUID().toString().replace("-", ""));
        return base;
    }

    /**
     * 讯飞账号 HMAC-SHA1 签名头（逐字节复刻 {@code loomy-sign.ts}）。
     *
     * <p>待签名字符串 9 段 {@code \n} 连接：
     * {@code METHOD\nESCAPED_PATH\nESCAPED_QUERY\nContent-MD5\nContent-Type\nDate\nNonce\n\n}
     * —— 后两段（x-* 头）恒为空串，故字符串以两个换行结尾，<b>不能去掉尾随换行</b>。</p>
     */
    private Map<String, String> signHeaders(String method, String path, String body) {
        String date = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        String nonce = UUID.randomUUID().toString();
        String contentMd5 = contentMd5(body);
        String stringToSign = String.join("\n",
                method.toUpperCase(),
                escapedPath(path),
                "",               // 无 query
                contentMd5,
                "application/json",
                date,
                nonce,
                "",               // signed headers（不发 x-* 头）
                "");              // canonicalized headers
        String signature = hmacSha1Base64(stringToSign, ACCESS_KEY_SECRET);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "account " + ACCESS_KEY_ID + ":" + signature);
        headers.put("Date", date);
        headers.put("Nonce", nonce);
        headers.put("Content-Type", "application/json");
        if (!contentMd5.isEmpty()) {
            headers.put("Content-MD5", contentMd5);
        }
        return headers;
    }

    /** Content-MD5（base64）；空 body 返回<b>空串</b>而非空串的 md5（与官方 sign.js 一致）。 */
    private static String contentMd5(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return Base64.getEncoder().encodeToString(md5.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "MD5 算法不可用", e);
        }
    }

    /** RFC3986 转义：URLEncoder + 补转 {@code ! ' ( ) *}，与 JS 侧 {@code encodeURIComponent} 对齐。 */
    private static String escapeRfc3986(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("!", "%21")
                .replace("'", "%27")
                .replace("(", "%28")
                .replace(")", "%29")
                .replace("*", "%2A");
    }

    /** 构建 ESCAPED_PATH：补前导 {@code /}、剥末尾 {@code /}，逐段转义。 */
    private static String escapedPath(String path) {
        String clean = path.startsWith("/") ? path : "/" + path;
        if (clean.length() > 1 && clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : clean.split("/")) {
            sb.append('/');
            if (!segment.isEmpty()) {
                sb.append(escapeRfc3986(segment));
            }
        }
        return sb.toString();
    }

    /** HMAC-SHA1 摘要 base64。 */
    private static String hmacSha1Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "HMAC-SHA1 计算失败: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> status(String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    private static Map<String, Object> parseJson(String response) {
        Map<String, Object> map = Json.getJsonObject(response);
        return map != null ? map : new LinkedHashMap<>();
    }

    private static String readString(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.common.support.text.json.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loomy（讯飞办公助手）账号渠道鉴权管理器实现。
 *
 * <p>协议（对齐 {@code dsh-our-free-model} 的 {@code loomy-oauth.ts} / {@code loomy-sign.ts} /
 * {@code loomy.ts}，<b>短信验证码登录</b>——全部 10 个渠道里唯一不走浏览器 URL 的）：</p>
 * <ol>
 *   <li>{@code startLogin} 无参：返回含 {@code loginMethod=sms} 的句柄，前端据此渲染
 *       「手机号 + 验证码」表单；</li>
 *   <li>{@code pollLogin} 按 {@code handle.smsSent}/{@code handle.smsPhone} 判断当前阶段：
 *       未发码时收 {@code phone} → {@code POST {accountBase}/login/phone/sendMsgCode}
 *       （取 {@code msgid}）；已发码时收 {@code smsCode} →
 *       {@code POST {accountBase}/login/phone/checkCode} → {@code {session, userid}}。</li>
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
 * <p>⚠️ 无 refresh 端点：{@code session} 是登录时声明 14 天有效期的，凭据里没有
 * refresh_token，到期只能重新短信登录（{@link #refresh} 抛 {@code NOT_IMPLEMENTED}）。
 * 每日额度由服务端在 {@code first-login} 发放、onboarding 任务一次性领取，
 * 均非「签到」动作，故 {@code supportsCheckin=false}。</p>
 *
 * <p>凭据落库：{@code access_token}(session) + {@code userid} + {@code phone} +
 * {@code expires_at}（登录时刻 + 14 天本地推算，服务端响应不带到期时间）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("loomy")
public class LoomyAccountChannelAuthManager implements AccountChannelAuthManager {

    /** 业务端点基址（推理 / 模型 / 积分 / onboarding）。 */
    static final String API_BASE = "https://loomyad.xunfei.cn";
    /** 讯飞账号（CAccount）基址（短信登录端点全挂它下面）。 */
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

    /** 短信验证码有效期（秒）。 */
    static final long SMS_CODE_TTL_SECONDS = 300;
    /** 登录会话有效期（秒）= 14 天（服务端只接受声明，响应不带到期时间）。 */
    static final long SESSION_TTL_SECONDS = 1_209_600L;

    /** 句柄：手机号（发码/校验两阶段都要带回）。 */
    static final String HANDLE_PHONE = "phone";
    /** 句柄：验证码 msgid（sendMsgCode 返回，checkCode 必须原样带回）。 */
    static final String HANDLE_MSGID = "msgid";
    /** 句柄：已发码标记。 */
    static final String HANDLE_SMS_SENT = "smsSent";
    /** pollLogin 入参：手机号。 */
    static final String POLL_KEY_PHONE = "phone";
    /** pollLogin 入参：用户输入的短信验证码。 */
    static final String POLL_KEY_SMS_CODE = "smsCode";

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
        return "sms";
    }

    /**
     * {@inheritDoc}
     *
     * <p>短信登录没有可打开的 URL，{@code startLogin} 直接返回带
     * {@code loginMethod=sms} 标记的句柄，前端渲染手机号输入框；
     * 发码与校验都在 {@link #pollLogin} 按阶段完成。</p>
     */
    @Override
    public Map<String, Object> startLogin() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("handle", "loomy-sms");
        handle.put("loginMethod", "sms");
        handle.put("startedAt", System.currentTimeMillis());
        return handle;
    }

    /**
     * {@inheritDoc}
     *
     * <p>按入参自包含判定阶段（句柄不跨请求保持，阶段由前端驱动）：</p>
     * <ol>
     *   <li>入参有 {@code smsCode}：校验阶段——{@code phone} 与 {@code msgid} 须已在句柄里
     *       （发码成功后由调用方写回），调 {@code checkCode}，成功返回可持久化凭据 Map；
     *       验证码错误/过期透传服务端 {@code desc} 并<b>清除</b>句柄的 {@code msgid}，
     *       让用户重新走发码；</li>
     *   <li>否则有 {@code phone}：发码阶段——调 {@code sendMsgCode} 拿 {@code msgid}
     *       写回句柄（调用方下轮 poll 须带上），返回 null（等待用户输码）；</li>
     *   <li>都没有：继续等待（返回 null）。</li>
     * </ol>
     */
    @Override
    public Map<String, Object> pollLogin(Map<String, Object> handle) {
        if (handle == null || isBlank(readString(handle, "handle"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "Loomy 登录句柄缺失 handle");
        }
        String smsCode = readString(handle, POLL_KEY_SMS_CODE);
        String phone = readString(handle, POLL_KEY_PHONE);
        if (isBlank(smsCode)) {
            if (isBlank(phone)) {
                // 前端尚未提交手机号 → 继续等待。
                return null;
            }
            phone = phone.trim();
            String msgid = sendSmsCode(phone);
            handle.put(HANDLE_PHONE, phone);
            handle.put(HANDLE_MSGID, msgid);
            handle.put(HANDLE_SMS_SENT, true);
            handle.put("smsCodeExpiresAt",
                    System.currentTimeMillis() + SMS_CODE_TTL_SECONDS * 1000);
            return null;
        }

        // 校验阶段：phone / msgid 必须已由发码阶段写回句柄。
        if (isBlank(phone)) {
            phone = readString(handle, HANDLE_PHONE);
        }
        String msgid = readString(handle, HANDLE_MSGID);
        if (isBlank(phone) || isBlank(msgid)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "Loomy 登录句柄缺少手机号或 msgid，请先获取验证码再提交");
        }
        smsCode = smsCode.trim();
        String session;
        String userid;
        try {
            Map<String, Object> data = checkCode(phone, smsCode, msgid);
            session = readString(data, "session");
            // userid 可能以数字形态下发（18 位数字串），用宽松读取。
            userid = readLooseString(data, "userid");
        } catch (ApiException e) {
            // 上游限流：用户手里仍握着这条验证码，不能清掉 msgid 逼他重新发码。
            // ChannelHttp 已按 Retry-After 退避过，这里直接返回继续等。
            if (ChannelHttp.isRateLimited(e)) {
                return null;
            }
            // 验证码错误/过期：清除发码标记，让用户重新发码。
            handle.remove(HANDLE_SMS_SENT);
            handle.remove(HANDLE_MSGID);
            throw e;
        }
        if (isBlank(session)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "Loomy 登录响应缺少 session");
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("access_token", session);
        if (isBlank(userid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "Loomy 登录响应缺少 userid");
        }
        credential.put("userid", userid);
        credential.put("phone", phone);
        // 服务端响应不带到期时间，本地按登录时刻 + 14 天推算。
        credential.put("expires_at", System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000);
        return credential;
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ Loomy 没有任何 refresh 端点（session 是登录时声明 14 天得到的，
     * 凭据里没有 refresh_token），到期只能重新短信登录。</p>
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

    /** 发码端点：{@code POST /login/phone/sendMsgCode}，返回 msgid。 */
    private String sendSmsCode(String phone) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("ccode", "86");
        param.put("phone", phone);
        param.put("expire", SMS_CODE_TTL_SECONDS);
        Map<String, Object> data = postAccount("/login/phone/sendMsgCode", param);
        String msgid = readLooseString(data, "msgid");
        if (isBlank(msgid)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "Loomy 发码响应缺少 msgid: " + ChannelHttp.truncate(Json.toJSONString(data)));
        }
        return msgid;
    }

    /** 校验端点：{@code POST /login/phone/checkCode}，返回 {@code {session, userid}}。 */
    private Map<String, Object> checkCode(String phone, String smsCode, String msgid) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("ccode", "86");
        param.put("phone", phone);
        param.put("mcode", smsCode);
        param.put("msgid", msgid);
        param.put("expire", SESSION_TTL_SECONDS);
        return postAccount("/login/phone/checkCode", param);
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

package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 百度千帆（百度智能云）控制台余额实现（账户中心 get_myaccount 接口）。
 *
 * <p>对接 {@code POST https://console.bce.baidu.com/api/account/get_myaccount?locale=zh-cn}。
 * 千帆的推理 API 域名（qianfan.baidubce.com）与账户计费域名不同根，且该接口认
 * <b>控制台登录态 Cookie</b> 而非 AK/SK 签名：实测需要 {@code bce-user-info} 与
 * {@code bce-sessionid} 两个 Cookie 缺一不可，并附带 {@code csrftoken} 请求头
 * （取值即 {@code bce-user-info} 去引号）。请求体为空 JSON {@code {}}，方法 POST。</p>
 *
 * <p>余额密钥（{@code extra.credit_key}）口径：<b>{@code bce-user-info值#bce-sessionid值}</b>。
 * 分隔符用 {@code #}：{@code bce-user-info} 的值里含 {@code |}，而多 Key 列表按
 * 换行 / 逗号 / 分号切分，这两类字符都不能当分隔符；{@code bce-sessionid} 是纯
 * 十六进制，两个值里都不会出现 {@code #}。也兼容直接粘贴含两个 Cookie 的完整
 * Cookie 串（按 Cookie 名解析）。</p>
 *
 * <p>响应为 {@code result} 包裹结构（2026-11 实测）：
 * {@code cashBalance}（现金余额）、{@code debt}（欠费金额）、
 * {@code couponBalance}（代金券余额）、{@code rebateBalance}（返利余额），
 * 单位人民币元。读数行为：parts[0]=现金余额，欠费 / 代金券 / 返利等其余读数
 * 进前端指标格原样展示。</p>
 *
 * <p>登录态失效时上游返回 HTTP 200 + {@code success=false} 信封
 * （{@code message.global=登录凭证已过期} 或 {@code message.redirect=登录页}），
 * 这里把原因透传给页面提示重新提取 Cookie，而不是报「未识别的响应结构」。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@Spi("qianfan")
public class QianfanCreditQueryProvider implements CreditQueryProvider {

    /**
     * 百度智能云账户中心余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://console.bce.baidu.com/api/account/get_myaccount?locale=zh-cn";

    /**
     * 余额密钥两段式的分隔符：{@code bce-user-info} 值里含 {@code |}（不能再用），
     * 多 Key 列表按换行 / 逗号 / 分号切分（也不能用）；两个 Cookie 值里都不含 {@code #}
     */
    private static final String KEY_SEPARATOR = "#";

    /**
     * 登录态主 Cookie 名：值形如带引号的时间戳签名串，同时是 csrftoken 的来源
     */
    private static final String USER_INFO_COOKIE = "bce-user-info";

    /**
     * 登录态会话 Cookie 名：纯十六进制会话标识
     */
    private static final String SESSION_COOKIE = "bce-sessionid";

    /**
     * 令牌承载头：两个 Cookie 拼成标准 Cookie 头
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * 防跨站请求伪造头：值 = bce-user-info 去引号，与 Cookie 严格配套
     */
    private static final String CSRF_HEADER = "csrftoken";

    /**
     * 余额接口固定 POST
     */
    private static final String METHOD_POST = "POST";

    /**
     * 请求体：空 JSON 对象
     */
    private static final String EMPTY_JSON = "{}";

    /**
     * 按常见字段名依次试探；显式配了表达式时以表达式为准。
     * 首选实测结构（2026-11 验证）：一次带出 现金余额 / 欠费 / 代金券 / 返利，
     * 首段算主余额，其余读数按原样展示；旧版扁平结构留作兜底
     */
    private static final List<String> DEFAULT_EXPRESSIONS = List.of(
            "¥result.cashBalance; ¥result.debt; ¥result.couponBalance; ¥result.rebateBalance",
            "¥result.cashBalance; ¥result.debt; ¥result.couponBalance",
            "¥result.cashBalance; ¥result.debt",
            "¥result.cashBalance",
            "¥cashBalance");

    /**
     * 字段名 → 页面展示名：键是字段路径的最后一段
     */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "cashBalance", "现金余额",
            "debt", "欠费金额",
            "couponBalance", "代金券余额",
            "rebateBalance", "返利余额");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "qianfan";
    }

    /**
     * 按百度智能云控制台口径查询余额。
     *
     * <p>鉴权走 Cookie 会话：令牌原值（{@code bce-user-info#bce-sessionid}）由本实现
     * 拆开包装成 Cookie 头，并派生 {@code csrftoken} 头；不走 Bearer，也不走渠道
     * 鉴权前缀（推理 Key 与控制台登录态不同源）。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            applyConsoleCookieAuth(query);
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            String envelopeError = envelopeError(raw);
            if (envelopeError != null) {
                return CreditResult.fail(name(), envelopeError);
            }
            if (!blank(query.getExpression())) {
                return translate(parse(query.getExpression(), raw, query.getBilling()));
            }
            return translate(probeDefaults(raw, query.getBilling()));
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 把余额密钥装进控制台 Cookie：拆出两个 Cookie 值，拼 Cookie 头并派生 csrftoken。
     *
     * <p>密钥为空或格式不对时直接抛错，让页面给出可操作的提示（去哪取、怎么拼），
     * 而不是发一个必然失败的请求。</p>
     *
     * @param query 请求
     */
    private static void applyConsoleCookieAuth(CreditQuery query) {
        String token = query.getToken();
        if (blank(token)) {
            throw new IllegalStateException("未配置余额密钥：请在百度智能云控制台登录后提取 "
                    + USER_INFO_COOKIE + " 与 " + SESSION_COOKIE + " 两个 Cookie，按 值" + KEY_SEPARATOR + "值 填入");
        }
        String[] cookies = splitKey(token.trim());
        if (cookies == null) {
            throw new IllegalStateException("余额密钥格式不正确：应为 " + USER_INFO_COOKIE
                    + " 值" + KEY_SEPARATOR + SESSION_COOKIE + " 值，或粘贴含这两个 Cookie 的完整 Cookie 串");
        }
        query.setMethod(METHOD_POST);
        if (blank(query.getBody())) {
            query.setBody(EMPTY_JSON);
        }
        query.setTokenHeader(COOKIE_HEADER);
        query.setToken(USER_INFO_COOKIE + "=" + cookies[0] + "; " + SESSION_COOKIE + "=" + cookies[1]);
        query.header(CSRF_HEADER, cookies[0].replace("\"", ""));
    }

    /**
     * 拆分余额密钥：{@code bce-user-info值#bce-sessionid值} 两段式，或含两个
     * Cookie 的完整 Cookie 串（按 Cookie 名解析，兼容分号 / 空格差异）。
     *
     * @param token 余额密钥原文
     * @return [bce-user-info 值, bce-sessionid 值]；解析不出两段时为 null
     */
    private static String[] splitKey(String token) {
        if (token.contains(USER_INFO_COOKIE + "=")) {
            String userInfo = null;
            String sessionId = null;
            for (String piece : token.split(";")) {
                int eq = piece.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String cookieName = piece.substring(0, eq).trim();
                String cookieValue = piece.substring(eq + 1).trim();
                if (USER_INFO_COOKIE.equals(cookieName)) {
                    userInfo = cookieValue;
                } else if (SESSION_COOKIE.equals(cookieName)) {
                    sessionId = cookieValue;
                }
            }
            if (!blank(userInfo) && !blank(sessionId)) {
                return new String[]{userInfo, sessionId};
            }
            return null;
        }
        int idx = token.indexOf(KEY_SEPARATOR);
        if (idx > 0 && idx < token.length() - 1) {
            String userInfo = token.substring(0, idx).trim();
            String sessionId = token.substring(idx + 1).trim();
            if (!blank(userInfo) && !blank(sessionId)) {
                return new String[]{userInfo, sessionId};
            }
        }
        return null;
    }

    /**
     * 识别上游的业务错误信封：{@code success=false} 时把原因带回去
     * （典型如登录态过期的「登录凭证已过期」或登录页跳转），页面能给出准确提示。
     *
     * @param raw 原始响应
     * @return 错误描述；不是业务错误为 null
     */
    private static String envelopeError(String raw) {
        if (blank(raw)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root.has("success") && !root.path("success").asBoolean(true)) {
                JsonNode message = root.path("message");
                if (message.isTextual() && !message.asText().isBlank()) {
                    return "百度智能云返回失败：" + message.asText();
                }
                String global = message.path("global").asText("");
                if (!global.isBlank()) {
                    return "百度智能云返回失败：" + global;
                }
                if (message.has("redirect")) {
                    return "登录凭证已过期，请重新登录百度智能云控制台并更新余额密钥";
                }
                return "百度智能云返回失败（登录态可能已过期，请重新提取 Cookie）";
            }
        } catch (Exception ignored) {
            // 不是 JSON 或无信封结构时交回探测逻辑处理
        }
        return null;
    }

    /**
     * 显式表达式解析。
     *
     * @param expression 表达式
     * @param raw        原始响应
     * @param billing    计费币种
     * @return 结果
     */
    private CreditResult parse(String expression, String raw, String billing) {
        CreditResult result = CreditExpressions.parse(expression, raw, name(), billing);
        if (result.isOk()) {
            result.setSource(name());
        }
        return result;
    }

    /**
     * 响应结构未公开文档化：按常见字段名逐个试探，第一条能解析的胜出。
     *
     * @param raw     原始响应
     * @param billing 计费币种
     * @return 结果
     */
    private CreditResult probeDefaults(String raw, String billing) {
        String lastError = null;
        for (String candidate : DEFAULT_EXPRESSIONS) {
            CreditResult result = CreditExpressions.parse(candidate, raw, name(), billing);
            if (result.isOk()) {
                result.setSource(name());
                return result;
            }
            lastError = result.getError();
        }
        return CreditResult.fail(name(), "未识别的余额响应结构，请改用「自定义映射」指定字段"
                + (lastError == null ? "" : "；" + lastError));
    }

    /**
     * 把千帆字段名翻成页面展示名。
     *
     * <p>就地改写标签：认不出的字段原样保留，不影响用户自己写的表达式口径。</p>
     *
     * @param result 查询结果
     * @return 同一结果
     */
    private static CreditResult translate(CreditResult result) {
        if (result == null || !result.isOk() || result.getParts() == null) {
            return result;
        }
        for (BalancePart part : result.getParts()) {
            String label = part.getLabel();
            if (label == null || label.isBlank()) {
                continue;
            }
            String tail = label.substring(label.lastIndexOf('.') + 1);
            String friendly = FIELD_LABELS.get(tail);
            if (friendly != null) {
                part.setLabel(friendly);
            }
        }
        return result;
    }

    /**
     * 空判断。
     *
     * @param text 文本
     * @return 为空为 true
     */
    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}

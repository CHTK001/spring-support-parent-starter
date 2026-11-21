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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基元律动（TokenRhythm）余额实现（控制台用量汇总接口）。
 *
 * <p>平台控制台接口 {@code GET https://tokenrhythm.studio/api/usage-summary} 返回
 * 账户余额读数（币种 CNY）。鉴权不走 {@code Authorization}，而是浏览器登录态
 * Cookie：{@code tr_session=<令牌>}，令牌即「余额密钥」里填的值。模型推理侧的
 * {@code sk_xxx} API Key 不是会话 Cookie，查余额会 401，不要混用。</p>
 *
 * <p>响应为 {@code {code, message, data:{...}}} 信封，余额字段在 {@code data} 内
 * （availableBalanceCny / frozenBalanceCny / expiringBalanceCny / costCny）。
 * 字段名未在官方文档公开，默认按候选表达式逐条试探，命中即带出全部读数；
 * 都不中时提示改走「自定义映射」。调用次数与 token 用量（data.calls /
 * inputTokens / outputTokens）作为附加读数一并带出。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Spi("tokenrhythm")
public class TokenRhythmCreditQueryProvider implements CreditQueryProvider {

    /**
     * 余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://tokenrhythm.studio/api/usage-summary";

    /**
     * 余额接口路径（供应商行配了上游根地址时按它拼接）
     */
    private static final String SUMMARY_PATH = "/api/usage-summary";

    /**
     * 令牌所在的 Cookie 头
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * 门户登录态 Cookie 名
     */
    private static final String TOKEN_COOKIE = "tr_session";

    /**
     * 门户登录页（「账号登录」入口）
     */
    private static final String LOGIN_PAGE_URL = "https://tokenrhythm.studio/login";

    /**
     * 静默续期回访页：控制台首页（已登录态的落地页）
     */
    private static final String RENEW_PAGE_URL = "https://tokenrhythm.studio/";

    /**
     * 按常见字段名依次试探；显式配了表达式时以表达式为准。
     * 首选 data 包裹的驼峰字段（CNY 后缀），其后兜底扁平变体与单字段
     */
    private static final List<String> DEFAULT_EXPRESSIONS = List.of(
            "¥data.availableBalanceCny; ¥data.frozenBalanceCny; ¥data.expiringBalanceCny; ¥data.costCny",
            "¥data.availableBalanceCny",
            "¥data.balanceCny",
            "¥availableBalanceCny; ¥frozenBalanceCny; ¥expiringBalanceCny; ¥costCny",
            "¥balanceCny"
    );

    /**
     * TokenRhythm 字段 → 页面展示名：读数行直接给中文标签，前端不必再猜字段含义。
     * 键是字段路径的最后一段，默认探测与自定义表达式共用
     */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("availableBalanceCny", "余额"),
            Map.entry("balanceCny", "余额"),
            Map.entry("frozenBalanceCny", "冻结"),
            Map.entry("expiringBalanceCny", "即将过期"),
            Map.entry("costCny", "累计消费"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "tokenrhythm";
    }

    /**
     * 「账号登录」入口页：用户在真实登录页登录后，由内嵌浏览器（CDP）监听页面登录态。
     *
     * @return TokenRhythm 门户登录页
     */
    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 登录态所在的 Cookie 名：内嵌浏览器在页面 Cookie 里读到它即认为登录成功，
     * 其值就是「余额密钥」（{@link #query} 用它组装 Cookie 头）。
     *
     * @return Cookie 名
     */
    @Override
    public String loginCookieName() {
        return TOKEN_COOKIE;
    }

    /**
     * 支持余额令牌静默续期：令牌是门户登录态 Cookie，预置旧 Cookie 回访门户时
     * 会话仍被接受；平台轮换令牌后新值仍在同名 Cookie 里，按登录截获钩子读回。
     *
     * @return true
     */
    @Override
    public boolean supportsTokenRenewal() {
        return true;
    }

    /**
     * 静默续期的回访页：控制台首页（已登录态的落地页）。
     *
     * @param baseUrl 供应商行配置的上游根地址（本实现登录态固定在门户主域，忽略该参数）
     * @return TokenRhythm 控制台首页
     */
    @Override
    public String renewPageUrl(String baseUrl) {
        return RENEW_PAGE_URL;
    }

    /**
     * 按 TokenRhythm 控制台口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            applyCookieAuth(query);
            String url = resolveUrl(query.getUrl());
            String raw = CreditHttp.fetch(query, url);
            CreditResult result;
            if (!blank(query.getExpression())) {
                result = CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
                if (result.isOk()) {
                    result.setSource(name());
                }
            } else {
                result = probeDefaults(raw, query.getBilling());
            }
            if (result.isOk()) {
                attachUsage(raw, result);
            }
            return translate(result);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 解析余额接口地址。
     *
     * <p>未配置用默认地址；已配到余额接口原样用；只配了上游根地址（可能带
     * {@code /v1} 推理前缀）时剥掉前缀拼余额路径。</p>
     *
     * @param url 请求地址（可空）
     * @return 完整余额接口地址
     */
    private static String resolveUrl(String url) {
        if (blank(url)) {
            return DEFAULT_URL;
        }
        String text = url.trim();
        if (text.contains(SUMMARY_PATH)) {
            return text;
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("/v1")) {
            text = text.substring(0, text.length() - 3);
        }
        return CreditHttp.join(text, SUMMARY_PATH);
    }

    /**
     * 响应结构未在文档公开：按常见字段名逐个试探，第一条能解析的胜出。
     *
     * <p>命中后即翻成页面展示名（{@link #translate}）。包内可见：测试直接喂
     * 真实响应样例，不走网络。</p>
     *
     * @param raw     原始响应
     * @param billing 计费币种
     * @return 结果
     */
    CreditResult probeDefaults(String raw, String billing) {
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
     * 附加读数：调用次数与 token 用量（data.calls / inputTokens / outputTokens，
     * 兼容根级扁平字段）。解析失败不影响余额主结果。
     *
     * @param raw    原始响应
     * @param result 余额结果（就地补 extra）
     */
    private static void attachUsage(String raw, CreditResult result) {
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode data = root.path("data").isObject() ? root.path("data") : root;
            Map<String, Object> extra = new LinkedHashMap<>(4);
            putCount(extra, "calls", data.path("calls"));
            putCount(extra, "input_tokens", data.path("inputTokens"));
            putCount(extra, "output_tokens", data.path("outputTokens"));
            if (!extra.isEmpty()) {
                result.setExtra(extra);
            }
        } catch (Exception ignored) {
            // 附加读数缺失不影响余额结论
        }
    }

    /**
     * 附加读数取数：数值或纯数字文本才收。
     *
     * @param extra 结果清单
     * @param key   键
     * @param node  值节点
     */
    private static void putCount(Map<String, Object> extra, String key, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isNumber()) {
            extra.put(key, node.asLong());
            return;
        }
        if (node.isTextual() && node.asText().trim().matches("\\d+")) {
            extra.put(key, Long.parseLong(node.asText().trim()));
        }
    }

    /**
     * 把 TokenRhythm 字段名翻成页面展示名。
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
            String friendly = FIELD_LABELS.get(label.substring(label.lastIndexOf('.') + 1));
            if (friendly != null) {
                part.setLabel(friendly);
            }
        }
        return result;
    }

    /**
     * 把令牌装进 Cookie：TokenRhythm 控制台认的是 {@code tr_session}，
     * 不是 Authorization。调用方已显式指定请求头时尊重调用方。
     *
     * @param query 请求
     */
    private static void applyCookieAuth(CreditQuery query) {
        String token = query.getToken();
        if (token == null || token.isBlank()) {
            return;
        }
        if (query.getTokenHeader() != null && !query.getTokenHeader().isBlank()) {
            return;
        }
        query.setTokenHeader(COOKIE_HEADER);
        query.setToken(TOKEN_COOKIE + "=" + token.trim());
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

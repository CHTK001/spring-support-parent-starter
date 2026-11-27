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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 书生（InternAI）平台余额实现（discovery.intern-ai.org.cn 墨点余额接口）。
 *
 * <p>对接 {@code GET https://discovery.intern-ai.org.cn/api/tokenplan/v1/credits/balance}，
 * 鉴权为标准 {@code Authorization: Bearer <令牌>}。注意这里的令牌是<b>门户会话
 * Cookie {@code uaa-token}</b>（用户在 discovery.intern-ai.org.cn 登录后页面持有的
 * 凭据），不是 {@code discovery-api.intern-ai.org.cn} 的 TOKENPLAN_API_KEY：两者
 * 不同源，门户余额接口不认 API Key。会话 Cookie 可由页面上的「账号登录」流程
 * 自动截获：登录入口 {@code /token-plan/home} 未登录时自动跳转 OpenXLab SSO，
 * 登录回跳后种下 {@code uaa-token} Cookie（见 {@link #loginPageUrl()} 与
 * {@link #loginCookieName()}）。</p>
 *
 * <p>响应为 {@code {code, msg, data}} 信封（成功 {@code code===0}），{@code data}
 * 里是墨点余额与两个用量窗口（2026-11 前端 bundle 逆向确认）：
 * {@code available_credits}（可用墨点）、{@code total_credits} / {@code limit_credits}
 * （总量）、{@code used_credits}（已用），以及 {@code usage_windows["5h"|"7d"]}
 * 两个滚动窗口（各带 {@code limit_credits / used_credits / remaining_credits /
 * next_recover_at}）。读数行为：parts[0]=可用墨点（主读数），其后是总量 / 已用与
 * 两个窗口的剩余 / 已用。计费单位为「墨点」（平台积分，不是货币金额）。</p>
 *
 * <p>令牌失效时上游返回非 0 错误码，这里把错误码与原因透传给页面，而不是报
 * 「未识别的响应结构」。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("shusheng")
public class ShushengCreditQueryProvider implements CreditQueryProvider {

    /**
     * 平台门户余额接口默认地址（与对话 API discovery-api 不同源）
     */
    private static final String DEFAULT_URL = "https://discovery.intern-ai.org.cn/api/tokenplan/v1/credits/balance";

    /**
     * 登录页地址：页面上的「账号登录」按钮据此打开内置浏览器。
     * 未登录访问会自动 302 到 OpenXLab SSO 登录页，登录后回跳本页种下 uaa-token
     */
    private static final String LOGIN_PAGE_URL = "https://discovery.intern-ai.org.cn/token-plan/home";

    /**
     * 登录成功后余额令牌所在的 Cookie 名（js-cookie 读取的明文键）；
     * 内嵌浏览器轮询页面 Cookie 读到该字段即认为登录成功
     */
    private static final String LOGIN_COOKIE_NAME = "uaa-token";

    /**
     * 读数单位：墨点（平台积分，按模型价折算 token，不是货币金额）
     */
    private static final String CREDIT_UNIT = "墨点";

    /**
     * extra.pools 里「用量窗口池」的展示名：书生不是多资源池，5 小时 / 7 天
     * 两个滚动窗口合在一个池里展示（与前端资源池视图的「池 × 窗口」形状对齐）
     */
    private static final String USAGE_POOL_NAME = "墨点用量";

    /**
     * 7 天滚动窗口的展示名（与前端窗口标签一致）
     */
    private static final String WINDOW_7D_LABEL = "7天窗口";

    /**
     * 5 小时滚动窗口的展示名（与前端窗口标签一致）
     */
    private static final String WINDOW_5H_LABEL = "5小时窗口";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "shusheng";
    }

    /**
     * 「账号登录」入口页：用户在真实登录页登录后，内嵌浏览器（CDP）轮询
     * 页面 Cookie 里的 {@code uaa-token} 并回填令牌。
     *
     * @return 书生门户「墨点计划」首页（未登录自动跳 SSO 登录页）
     */
    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 登录成功后余额令牌所在的 Cookie 名。
     *
     * @return Cookie 名 {@code uaa-token}
     */
    @Override
    public String loginCookieName() {
        return LOGIN_COOKIE_NAME;
    }

    /**
     * 按书生平台口径查询墨点余额。
     *
     * <p>鉴权走标准 Bearer：{@code CreditHttp} 对空请求头默认补
     * {@code Authorization: Bearer <uaa-token>}，调用方传来的令牌是门户会话
     * Cookie 原值。显式配了表达式时以表达式为准，否则按信封 + 用量窗口
     * 结构读数。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            String envelopeError = envelopeError(raw);
            if (envelopeError != null) {
                return CreditResult.fail(name(), envelopeError);
            }
            if (!blank(query.getExpression())) {
                return parse(query.getExpression(), raw, query.getBilling());
            }
            return parseBalance(raw);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
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
     * 按信封 + 用量窗口结构解析：data 里带可用墨点与 5 小时 / 7 天两个滚动窗口。
     *
     * <p>除扁平读数 {@code parts} 外，把余额与窗口的原始结构放进
     * {@link CreditResult#getExtra()}（available / total / used / pools / unit），
     * 前端据此按窗口口径渲染专属余额视图；{@code raw} 有截断，结构化数据以
     * extra 为准。pools 里每个窗口是一个「池」（name + window_7d / window_5h 的
     * limit / used / remaining），与前端资源池视图的形状对齐。包可见以便单测
     * 直接喂样例、不走网络。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parseBalance(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail("shusheng", "响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            return CreditResult.fail("shusheng", "未识别的余额响应结构，请改用「自定义映射」指定字段");
        }
        List<BalancePart> parts = new ArrayList<>();
        appendPart(parts, "墨点余额", data.path("available_credits"));
        appendPart(parts, "墨点总量", data.path("total_credits"));
        appendPart(parts, "已用墨点", data.path("used_credits"));
        JsonNode windows = data.path("usage_windows");
        Map<String, Object> usagePool = null;
        if (windows.isObject()) {
            usagePool = new LinkedHashMap<>();
            usagePool.put("name", USAGE_POOL_NAME);
            Map<String, Object> w7d = windowMap(windows.path("7d"));
            if (!w7d.isEmpty()) {
                usagePool.put("window_7d", w7d);
            }
            appendWindow(parts, WINDOW_7D_LABEL, windows.path("7d"));
            Map<String, Object> w5h = windowMap(windows.path("5h"));
            if (!w5h.isEmpty()) {
                usagePool.put("window_5h", w5h);
            }
            appendWindow(parts, WINDOW_5H_LABEL, windows.path("5h"));
        }
        if (parts.isEmpty()) {
            return CreditResult.fail("shusheng", "响应里没有可读的余额数值");
        }
        CreditResult result = CreditResult.ok("shusheng", parts, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        putDecimal(extra, "available_credits", data.path("available_credits"));
        putDecimal(extra, "total_credits", data.path("total_credits"));
        putDecimal(extra, "limit_credits", data.path("limit_credits"));
        putDecimal(extra, "used_credits", data.path("used_credits"));
        if (CollectionUtils.isNotEmpty(usagePool)) {
            extra.put("pools", List.of(usagePool));
        }
        extra.put("unit", CREDIT_UNIT);
        result.setExtra(extra);
        return result;
    }

    /**
     * 把一个用量窗口整理成前端窗口视图的形状（limit / used / remaining / next_recover_at）。
     *
     * @param node 窗口节点（limit_credits / used_credits / remaining_credits）
     * @return 窗口视图；窗口节点无效时为空 Map
     */
    private static Map<String, Object> windowMap(JsonNode node) {
        Map<String, Object> window = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return window;
        }
        putDecimal(window, "limit", node.path("limit_credits"));
        putDecimal(window, "used", node.path("used_credits"));
        putDecimal(window, "remaining", node.path("remaining_credits"));
        String nextRecover = node.path("next_recover_at").asText("").trim();
        if (!nextRecover.isEmpty()) {
            window.put("next_recover_at", nextRecover);
        }
        return window;
    }

    /**
     * 追加一个窗口的剩余 / 已用两段读数。
     *
     * @param parts  读数列表
     * @param window 窗口展示名（7天窗口 / 5小时窗口）
     * @param node   窗口节点（limit_credits / used_credits / remaining_credits）
     */
    private static void appendWindow(List<BalancePart> parts, String window, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        appendPart(parts, window + "剩余", node.path("remaining_credits"));
        appendPart(parts, window + "已用", node.path("used_credits"));
    }

    /**
     * 追加一段读数；数值取不到时跳过。
     *
     * @param parts 读数列表
     * @param label 段名
     * @param node  数值节点
     */
    private static void appendPart(List<BalancePart> parts, String label, JsonNode node) {
        BigDecimal value = decimalOf(node);
        if (value != null) {
            BalancePart part = new BalancePart();
            part.setLabel(label);
            part.setAmount(value);
            part.setCurrency(CREDIT_UNIT);
            parts.add(part);
        }
    }

    /**
     * 往 Map 里放一个可解析的数值（extra 结构化数据用）。
     *
     * @param target 目标 Map
     * @param key    键名
     * @param node   数值节点
     */
    private static void putDecimal(Map<String, Object> target, String key, JsonNode node) {
        BigDecimal value = decimalOf(node);
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * 识别上游的业务错误信封：{@code code} 非 0 且 {@code data} 为空时直接把
     * 错误码与原因带回去（典型如会话 Cookie 失效的 401），页面能给出准确提示。
     * 包可见以便单测直接喂样例、不走网络。
     *
     * @param raw 原始响应
     * @return 错误描述；不是业务错误为 null
     */
    static String envelopeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode code = root.path("code");
            if (!root.has("data") || root.path("data").isNull() || root.path("data").isMissingNode()) {
                if (code.isInt() && code.asInt() != 0) {
                    String msg = root.path("msg").asText("");
                    return "上游返回错误 code=" + code.asInt() + (msg.isBlank() ? "" : "：" + msg);
                }
            }
        } catch (Exception ignored) {
            // 不是 JSON 或无信封结构时交回解析逻辑处理
        }
        return null;
    }

    /**
     * 取节点数值；上游可能把数值编码成字符串，这里统一转成数值。
     *
     * @param node 节点
     * @return 数值；无法解析时返回空
     */
    private static BigDecimal decimalOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String text = node.asText().trim().replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
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

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

/**
 * MiniMax 平台余额实现（Coding Plan 配额口径）。
 *
 * <p>对接 {@code GET https://www.minimaxi.com/v1/api/openplatform/coding_plan/remains}，
 * 鉴权走<b>登录会话 Cookie</b>（探活实证：无会话时上游返回
 * {@code base_resp.status_code=1004 "cookie is missing, log in again"}，而非 401）。
 * 这里的令牌是<b>平台登录会话令牌</b>（用户登录 platform.minimax.cn 控制台后页面
 * 持有的凭据），可由页面上的「账号登录」流程自动截获（见 {@link #loginPageUrl()}）。
 * 会话 Cookie 的确切名以真实登录观测为准，请求侧先按常见会话 Cookie 名多候选
 * 兜底包装（见 {@link #applyCookieAuth}）；API Key（sk-…）是否可用以实测为准。
 * 响应为 {@code {base_resp:{status_code,status_msg}, model_remains:[…]}}，
 * 每个模型带当前窗口总量与用量。注意上游字段的坑：
 * {@code current_interval_usage_count} 实为<b>剩余</b>次数（不是已用），
 * 已用 = {@code total} − 该值；周窗口字段（{@code current_weekly_remaining_percent} 等）
 * 在无周限额的套餐上可能缺省。</p>
 *
 * <p>读数行为：每个模型依次给出 剩余 / 已用 / 周剩余百分比，parts[0] = 第一个模型的
 * 剩余次数（主读数）。计数单位为「次」（Coding Plan 按调用次数配额，不是金额）。
 * 逐模型原始结构放进 {@link CreditResult#getExtra()}（mode=coding-plan），前端据此
 * 渲染 MiniMax 专属视图。</p>
 *
 * <p>实现标识用 {@code minimax-platform} 而不是 {@code minimax}：account-starter 的旧
 * 渠道体系已有一个 {@code @Spi("minimax")} 的余额实现（agent.minimax.cn 账户余额口径），
 * 两个实现若同名，{@code CreditQueries.find} 会按类路径顺序命中其中一个，两边都会踩错
 * （依赖铁律 R4：同一能力两份实现 = SPI 命中错实现）。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("minimax-platform")
public class MinimaxPlatformCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识：避开 account 侧旧渠道体系已占用的 {@code minimax}
     */
    static final String NAME = "minimax-platform";

    /**
     * Coding Plan 配额接口默认地址（CN 站；国际站为 www.minimax.io 口径）
     */
    private static final String DEFAULT_URL = "https://www.minimaxi.com/v1/api/openplatform/coding_plan/remains";

    /**
     * 令牌所在的 Cookie 头
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * 会话 Cookie 名主候选（{@link #loginCookieName()} 与 Cookie 包装共用）
     */
    static final String PRIMARY_COOKIE = "jwt";

    /**
     * 会话 Cookie 名候选：确切名待真实登录观测收敛，请求侧先全部拼上，
     * 上游认哪个取哪个，多余候选会被忽略
     */
    private static final List<String> COOKIE_CANDIDATES = List.of(
            PRIMARY_COOKIE, "token", "access_token", "session_id", "session",
            "sid", "minimax_session", "minimax_token", "user_token", "auth_token");

    /**
     * 「账号登录」入口页：控制台首页未登录时会自动跳转统一登录页
     * （account.minimax.cn/unified-login），登录成功后回到控制台，
     * 会话令牌落在控制台域的存储里
     */
    private static final String LOGIN_PAGE_URL = "https://platform.minimax.cn/console/home";

    /**
     * 读数单位：调用次数（Coding Plan 按次数配额，不是金额）
     */
    private static final String COUNT_UNIT = "次";

    /**
     * 模型名取不到时的兜底展示名
     */
    private static final String FALLBACK_MODEL_NAME = "模型";

    /**
     * 响应 映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 「账号登录」入口页：用户在 MiniMax 控制台登录后，内嵌浏览器按截获口径
     * 取会话令牌并回填「余额密钥」。
     *
     * @return MiniMax 控制台首页（未登录自动跳统一登录页）
     */
    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 「账号登录」成功后，余额密钥所在的会话 Cookie 名（主候选）。
     *
     * <p>MiniMax 控制台是 OAuth2 跳转登录（unified-login → auth/callback → console/home），
     * 会话凭据大概率由回调以 Set-Cookie 落在 Cookie 里：内嵌浏览器在页面 Cookie 里读到
     * 该字段即认为登录成功，其值自动回填「余额密钥」。确切 Cookie 名以真实登录观测为准，
     * 当前先取主候选；观测确认后收敛为确切值。</p>
     *
     * @return 会话 Cookie 名主候选
     */
    @Override
    public String loginCookieName() {
        return PRIMARY_COOKIE;
    }

    /**
     * 按 MiniMax Coding Plan 口径查询余额。
     *
     * <p>鉴权走登录会话 Cookie（见 {@link #applyCookieAuth}）：显式配了请求头时
     * 以调用方为准。显式配了表达式时以表达式为准，否则按 {@code model_remains[]}
     * 结构逐模型读数。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            applyCookieAuth(query);
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            if (!blank(query.getExpression())) {
                CreditResult result = CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
                if (result.isOk()) {
                    result.setSource(name());
                }
                return result;
            }
            return parsePlan(raw);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 把令牌装进 Cookie：MiniMax 配额接口认登录会话 Cookie，不是 Authorization。
     * 确切 Cookie 名待真实登录观测，先按 {@link #COOKIE_CANDIDATES} 多候选
     * 一起拼（{@code jwt=<t>; token=<t>; …}），上游认哪个取哪个；调用方已显式
     * 指定请求头时尊重调用方。
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
        String trimmed = token.trim();
        StringBuilder cookie = new StringBuilder();
        for (String name : COOKIE_CANDIDATES) {
            if (cookie.length() > 0) {
                cookie.append("; ");
            }
            cookie.append(name).append('=').append(trimmed);
        }
        query.setTokenHeader(COOKIE_HEADER);
        query.setToken(cookie.toString());
    }

    /**
     * 按 Coding Plan 结构解析：信封错误透传，逐模型给出 剩余 / 已用 / 周剩余百分比。
     *
     * <p>包可见以便单测直接喂样例、不走网络。逐模型结构化行放进
     * {@link CreditResult#getExtra()}（mode=coding-plan），raw 有截断，结构化
     * 数据以 extra 为准。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parsePlan(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail(NAME, "响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode base = root.path("base_resp");
        int statusCode = base.path("status_code").asInt(0);
        if (statusCode != 0) {
            String message = base.path("status_msg").asText("");
            return CreditResult.fail(NAME, "上游返回错误 code=" + statusCode
                    + (message.isBlank() ? "" : "：" + message));
        }
        JsonNode models = root.path("model_remains");
        if (!models.isArray() || models.isEmpty()) {
            return CreditResult.fail(NAME, "未识别的余额响应结构，请改用「自定义映射」指定字段");
        }
        List<BalancePart> parts = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>(models.size());
        for (JsonNode model : models) {
            String label = modelName(model);
            BigDecimal remain = decimal(model.path("current_interval_usage_count"));
            BigDecimal total = decimal(model.path("total"));
            BigDecimal used = remain != null && total != null ? total.subtract(remain) : null;
            BigDecimal weeklyPercent = decimal(model.path("current_weekly_remaining_percent"));
            if (remain != null) {
                parts.add(part(label + " 剩余", remain, COUNT_UNIT));
            }
            if (used != null) {
                parts.add(part(label + " 已用", used, COUNT_UNIT));
            }
            if (weeklyPercent != null) {
                parts.add(part(label + " 周剩余", weeklyPercent, "%"));
            }
            rows.add(rowOf(model, label, total, remain, used, weeklyPercent));
        }
        if (parts.isEmpty()) {
            return CreditResult.fail(NAME, "配额响应里没有可读的数值");
        }
        CreditResult result = CreditResult.ok(NAME, parts, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("mode", "coding-plan");
        extra.put("unit", COUNT_UNIT);
        extra.put("models", rows);
        result.setExtra(extra);
        return result;
    }

    /**
     * 组装一个模型的结构化明细（{@code extra.models} 的一行）。
     *
     * <p>只保留有值的键，避免把缺省字段以 null 带给前端；重置时间原样透传。</p>
     *
     * @param model     模型节点
     * @param label     模型展示名
     * @param total     窗口总量
     * @param remain    剩余次数
     * @param used      已用次数
     * @param weeklyPct 周剩余百分比
     * @return 行数据
     */
    private static Map<String, Object> rowOf(JsonNode model, String label, BigDecimal total,
                                             BigDecimal remain, BigDecimal used, BigDecimal weeklyPct) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("model", label);
        if (total != null) {
            row.put("total", total);
        }
        if (remain != null) {
            row.put("remain", remain);
        }
        if (used != null) {
            row.put("used", used);
        }
        if (weeklyPct != null) {
            row.put("weekly_remaining_percent", weeklyPct);
        }
        String remainsTime = model.path("remains_time").asText("");
        if (!remainsTime.isBlank()) {
            row.put("remains_time", remainsTime);
        }
        String weeklyRemainsTime = model.path("weekly_remains_time").asText("");
        if (!weeklyRemainsTime.isBlank()) {
            row.put("weekly_remains_time", weeklyRemainsTime);
        }
        return row;
    }

    /**
     * 模型名：优先 model 字段，取不到用兜底名。
     *
     * @param model 模型节点
     * @return 模型展示名
     */
    private static String modelName(JsonNode model) {
        String name = model.path("model").asText("").trim();
        return name.isEmpty() ? FALLBACK_MODEL_NAME : name;
    }

    /**
     * 组装一段读数。
     *
     * @param label    段名
     * @param amount   数值
     * @param currency 单位（次 / %）
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount, String currency) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount.stripTrailingZeros());
        part.setCurrency(currency);
        return part;
    }

    /**
     * 取节点数值；文本按数字解析（去掉千分位）。
     *
     * @param node 节点
     * @return 数值；缺失或无法解析时为 null
     */
    private static BigDecimal decimal(JsonNode node) {
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

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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sub2api 系中转站的余额实现。
 *
 * <p>对接 sub2api 的订阅口径 {@code GET {base}/api/v1/subscriptions}，鉴权为用户
 * 登录会话 JWT（{@code Authorization: Bearer <JWT>}，可由「账号登录」流程自动截获，
 * 见 {@link #loginPageUrl(String)}）；API Key（sk-）查不了该接口。响应为
 * {@code {code, message, data:[订阅]}}，每个订阅内嵌分组 {@code group} 的
 * 日 / 周 / 月额度上限，订阅自身带对应已用（均为 USD）：余额按「月额度 − 月已用」
 * 计算，月额度缺省时降级周、日；分组未配额度（按量计费订阅，如 Codex 套餐）时
 * 算不出「剩余」，按成功返回逐订阅明细（只带已用），两段余额读数留空，不把已用
 * 当余额。配了额度的订阅汇总成两段读数：parts[0]=剩余额度、parts[1]=已用额度，
 * 总量即额度上限。</p>
 *
 * <p>同时把逐订阅明细（分组 / 状态 / 起订 / 到期 / 窗口上限与用量）放进结果的 {@code extra}
 * 附加数据（{@code mode=subscription}），前端余额面板据此渲染 sub2api 专属的订阅视图。</p>
 *
 * <p>上游返回 {@code code != 0}（如令牌失效）时把错误码与原因透传给页面，而不是报
 * 「未识别的响应结构」。页面显式配了表达式时以表达式为准（对订阅响应取数）。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("sub2api")
public class Sub2ApiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 订阅余额接口路径（登录会话 JWT 口径）
     */
    private static final String SUBSCRIPTIONS_PATH = "/api/v1/subscriptions";

    /**
     * 登录页路径：各部署自带，拼在站点根地址后
     */
    private static final String LOGIN_PAGE_PATH = "/login";

    /**
     * 登录成功后要截获的响应地址特征：登录发令牌的接口
     */
    private static final String LOGIN_CAPTURE_URL_PATTERN = "api/v1/auth/login";

    /**
     * 令牌在登录响应体里的 JSON 路径
     */
    private static final String LOGIN_TOKEN_JSON_PATH = "data.access_token";

    /**
     * 站点根地址可能带的接口前缀（长前缀优先）
     */
    private static final String[] API_PREFIXES = {"/api/v1", "/v1", "/api"};

    /**
     * 额度币种（sub2api 的额度 / 用量字段均以美元计）
     */
    private static final String CURRENCY = "USD";

    /**
     * 读数小数位
     */
    private static final int SCALE_DIGITS = 6;

    /**
     * 响应 映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "sub2api";
    }

    /**
     * 「账号登录」入口页：自建部署没有固定域名，按供应商行的上游地址推出。
     *
     * @param baseUrl 供应商行的上游根地址（可能带 {@code /api/v1} 之类接口前缀）
     * @return 登录页地址；地址缺失时为 null
     */
    @Override
    public String loginPageUrl(String baseUrl) {
        String base = stripApiPrefixes(baseUrl);
        return base == null ? null : CreditHttp.join(base, LOGIN_PAGE_PATH);
    }

    /**
     * 登录响应地址特征：只截获发令牌的登录接口，不收整站流量。
     *
     * @return 地址特征正则
     */
    @Override
    public String loginCaptureUrlPattern() {
        return LOGIN_CAPTURE_URL_PATTERN;
    }

    /**
     * 令牌在登录响应体里的位置：{@code data.access_token}。
     *
     * @return 令牌 JSON 路径
     */
    @Override
    public String loginTokenJsonPath() {
        return LOGIN_TOKEN_JSON_PATH;
    }

    /**
     * 按 sub2api 订阅口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String base = stripApiPrefixes(query.getUrl());
            String url = CreditHttp.join(base, SUBSCRIPTIONS_PATH);
            String raw = CreditHttp.fetch(query, url);
            if (query.getExpression() != null && !query.getExpression().isBlank()) {
                return CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
            }
            return parseSubscriptions(raw);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 解析订阅列表：信封错误透传；配了额度的订阅按「月 → 周 → 日」窗口算剩余后
     * 汇总成两段读数，分组未配额度时只回逐订阅明细（{@code mode=subscription}，
     * 余额读数留空）。
     *
     * @param raw 原始响应
     * @return 结果
     */
    private CreditResult parseSubscriptions(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail(name(), "响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode code = root.path("code");
        if (code.isNumber() && code.asInt() != 0) {
            String message = root.path("message").asText("");
            return CreditResult.fail(name(), "上游返回错误 code=" + code.asInt()
                    + (message.isBlank() ? "" : "：" + message));
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return CreditResult.fail(name(), "未返回订阅列表，无法计算余额");
        }
        BigDecimal remain = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        boolean hasRemain = false;
        boolean hasUsed = false;
        List<Map<String, Object>> subscriptions = new ArrayList<>(data.size());
        String windowKey = null;
        boolean mixedWindow = false;
        for (JsonNode subscription : data) {
            Window window = windowOf(subscription);
            subscriptions.add(subscriptionRow(subscription, window));
            if (window != null) {
                if (windowKey == null) {
                    windowKey = window.key();
                } else if (!windowKey.equals(window.key())) {
                    mixedWindow = true;
                }
                remain = remain.add(window.limit().subtract(window.usage()));
                used = used.add(window.usage());
                hasRemain = true;
                hasUsed = true;
                continue;
            }
            // 分组未配额度：剩余算不出，已用仍可汇总
            BigDecimal usage = firstUsage(subscription);
            if (usage != null) {
                used = used.add(usage);
                hasUsed = true;
            }
        }
        if (!hasRemain) {
            if (!hasUsed) {
                return CreditResult.fail(name(), "订阅未返回额度与用量，无法计算余额");
            }
            // 分组未配额度上限（按量计费订阅，如 Codex 套餐）：没有「剩余额度」可算，
            // 不能把已用当余额返回；按成功回逐订阅明细（只带已用），两段读数留空，
            // 前端按 mode=subscription 渲染订阅视图，也不会再落进 billing 余额探测
            CreditResult result = CreditResult.okData(name(), raw);
            result.setCurrency(CURRENCY);
            result.setExtra(subscriptionExtra(subscriptions, "", false));
            return result;
        }
        List<BalancePart> parts = new ArrayList<>(2);
        parts.add(part("剩余额度", remain));
        if (hasUsed) {
            parts.add(part("已用额度", used));
        }
        CreditResult result = CreditResult.ok(name(), parts, raw);
        // 供应商专属结构化数据：面板据此渲染 sub2api 的「逐订阅」视图
        // （分组 / 状态 / 到期 / 窗口上限与用量），两段读数契约保持不变
        result.setExtra(subscriptionExtra(subscriptions,
                mixedWindow ? "mixed" : (windowKey == null ? "" : windowKey), mixedWindow));
        return result;
    }

    /**
     * 组装订阅制的结构化附加数据：面板据此渲染 sub2api 的「逐订阅」视图
     * （分组 / 状态 / 起订 / 到期 / 窗口上限与用量）。
     *
     * @param subscriptions 逐订阅明细
     * @param window        汇总窗口标识（monthly / weekly / daily / mixed；无窗口为空串）
     * @param mixedWindow   各订阅窗口不一致
     * @return 附加数据
     */
    private static Map<String, Object> subscriptionExtra(List<Map<String, Object>> subscriptions,
                                                         String window, boolean mixedWindow) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("mode", "subscription");
        extra.put("currency", CURRENCY);
        extra.put("window", mixedWindow ? "mixed" : window);
        extra.put("subscriptions", subscriptions);
        return extra;
    }

    /**
     * 组装一个订阅的结构化明细（{@code extra.subscriptions} 的一行）。
     *
     * <p>只保留有值的键，避免把缺省字段以 null 带给前端；金额为 USD。</p>
     *
     * @param subscription 订阅节点
     * @param window       命中的额度窗口；分组未配额度时为 null
     * @return 行数据
     */
    private static Map<String, Object> subscriptionRow(JsonNode subscription, Window window) {
        Map<String, Object> row = new LinkedHashMap<>();
        String group = subscription.path("group").path("name").asText("");
        if (!group.isBlank()) {
            row.put("group", group);
        }
        String status = subscription.path("status").asText("");
        if (!status.isBlank()) {
            row.put("status", status);
        }
        String expiresAt = subscription.path("expires_at").asText("");
        if (!expiresAt.isBlank()) {
            row.put("expires_at", expiresAt);
        }
        // 起订时间一并透传：前端余额面板的「到期倒计时环」用它算订阅总周期
        String startsAt = subscription.path("starts_at").asText("");
        if (!startsAt.isBlank()) {
            row.put("starts_at", startsAt);
        }
        if (window != null) {
            row.put("window", window.key());
            row.put("limit", window.limit());
            row.put("usage", window.usage());
            row.put("remain", window.limit().subtract(window.usage()));
            return row;
        }
        BigDecimal usage = firstUsage(subscription);
        if (usage != null) {
            row.put("usage", usage);
        }
        return row;
    }

    /**
     * 取该订阅可算剩余的额度窗口：月 → 周 → 日，取第一个配了上限的。
     *
     * <p>上限在订阅的 {@code group} 里，已用在订阅自身；已用缺失按 0 计。</p>
     *
     * @param subscription 订阅节点
     * @return 额度窗口；分组未配额度时为 null
     */
    private static Window windowOf(JsonNode subscription) {
        JsonNode group = subscription.path("group");
        BigDecimal monthlyLimit = decimal(group.path("monthly_limit_usd"));
        if (monthlyLimit != null) {
            return new Window("monthly", monthlyLimit, orZero(decimal(subscription.path("monthly_usage_usd"))));
        }
        BigDecimal weeklyLimit = decimal(group.path("weekly_limit_usd"));
        if (weeklyLimit != null) {
            return new Window("weekly", weeklyLimit, orZero(decimal(subscription.path("weekly_usage_usd"))));
        }
        BigDecimal dailyLimit = decimal(group.path("daily_limit_usd"));
        if (dailyLimit != null) {
            return new Window("daily", dailyLimit, orZero(decimal(subscription.path("daily_usage_usd"))));
        }
        return null;
    }

    /**
     * 分组未配额度时取该订阅的已用（月 → 周 → 日）。
     *
     * @param subscription 订阅节点
     * @return 已用金额；未返回用量为 null
     */
    private static BigDecimal firstUsage(JsonNode subscription) {
        BigDecimal monthly = decimal(subscription.path("monthly_usage_usd"));
        if (monthly != null) {
            return monthly;
        }
        BigDecimal weekly = decimal(subscription.path("weekly_usage_usd"));
        if (weekly != null) {
            return weekly;
        }
        return decimal(subscription.path("daily_usage_usd"));
    }

    /**
     * 组装一段读数。
     *
     * @param label  段名
     * @param amount 金额
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount.setScale(SCALE_DIGITS, RoundingMode.HALF_UP).stripTrailingZeros());
        part.setCurrency(CURRENCY);
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
     * 空值当 0。
     *
     * @param value 数值
     * @return 原值；null 时为 0
     */
    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 剥掉地址尾部的接口前缀，得到站点根地址。
     *
     * <p>供应商行里的上游地址可能是 {@code https://site}、{@code https://site/api}、
     * {@code https://site/v1} 或 {@code https://site/api/v1}，都能推出站点根。</p>
     *
     * @param url 原始地址
     * @return 站点根地址；地址缺失时为 null
     */
    private static String stripApiPrefixes(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : API_PREFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        return text.isBlank() ? null : text;
    }

    /**
     * 一个额度窗口：窗口标识与上限 / 已用（USD）。
     *
     * @param key   窗口标识（monthly / weekly / daily）
     * @param limit 额度上限
     * @param usage 已用
     */
    private record Window(String key, BigDecimal limit, BigDecimal usage) {
    }
}

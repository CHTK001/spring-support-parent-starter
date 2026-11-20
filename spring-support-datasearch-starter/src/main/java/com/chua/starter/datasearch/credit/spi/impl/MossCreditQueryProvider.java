package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

import java.util.List;
import java.util.Map;

/**
 * Moss 非官方余额实现（platform.moss.cn 门户接口）。
 *
 * <p>官方文档未公开余额接口，这里对接的是门户页在用的非官方端点
 * {@code GET https://platform.moss.cn/portal-api/maas/credits/usage-summary}。
 * 鉴权不走 {@code Authorization}，而是浏览器 Cookie：
 * {@code moss_studio_prod_access_token=<令牌>}，令牌即「余额密钥」里填的值。</p>
 *
 * <p>响应为扁平结构（无 data 包裹），已实测验证：{@code display_available_points}
 * 即门户展示余额（如 "44.00"，元），{@code available_points} 为积分原始值
 * （100 积分 = 1 元）；同源还有冻结、本月消费、日均消费、预计可用天数等读数，
 * 默认探测一次性全部带出，前端按字段展示。仍保留常见字段名兜底试探，
 * 都不中时提示改走「自定义映射」，把真实字段映射出来。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Spi("moss")
public class MossCreditQueryProvider implements CreditQueryProvider {

    /**
     * 非官方余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://platform.moss.cn/portal-api/maas/credits/usage-summary";

    /**
     * 令牌所在的 Cookie 头
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * 门户登录态 Cookie 名
     */
    private static final String TOKEN_COOKIE = "moss_studio_prod_access_token";

    /**
     * 静默续期回访页：门户首页（已登录态的落地页，登录页在会话有效时拿不到令牌轮换）
     */
    private static final String RENEW_PAGE_URL = "https://platform.moss.cn/";

    /**
     * 按常见字段名依次试探；显式配了表达式时以表达式为准。
     * 首选门户实测字段（2026-11 验证）：一次带出 余额 / 本月已用 / 冻结 / 日均消费 / 预计可用天数，
     * 前一段算剩余、第二段算已用，其余读数按原样展示；data.* 留作兜底
     */
    private static final List<String> DEFAULT_EXPRESSIONS = List.of(
            "¥display_available_points; ¥display_month_charged_points; ¥display_frozen_points;"
                    + " ¥display_average_daily_charged_points; estimated_available_days",
            "¥display_available_points",
            "¥display_total_remaining_points",
            "available_points / 100",
            "total_remaining_points / 100",
            "data.total_credits; data.used_credits",
            "data.available_credits; data.used_credits",
            "data.remaining_credits; data.used_credits",
            "data.total_balance; data.used_balance",
            "data.balance; data.used",
            "data.totalBalance; data.usedBalance",
            "data.available; data.used",
            "data.total; data.used",
            "data.balance"
    );

    @Override
    public String name() {
        return "moss";
    }

    /**
     * 「账号登录」入口页：用户在真实登录页登录后，由内嵌浏览器（CDP）监听页面登录态。
     *
     * @return Moss 门户登录页
     */
    @Override
    public String loginPageUrl() {
        return "https://platform.moss.cn/login";
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
     * 静默续期的回访页：门户首页（已登录态的落地页）。
     *
     * @param baseUrl 供应商行配置的上游根地址（本实现登录态固定在门户主域，忽略该参数）
     * @return Moss 门户首页
     */
    @Override
    public String renewPageUrl(String baseUrl) {
        return RENEW_PAGE_URL;
    }

    /**
     * 按 Moss 门户口径查询余额。
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
                return translate(parse(query.getExpression(), raw, query.getBilling()));
            }
            return probeDefaults(raw, query.getBilling());
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
     * 响应结构未公开：按常见字段名逐个试探，第一条能解析的胜出。
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
                return translate(result);
            }
            lastError = result.getError();
        }
        return CreditResult.fail(name(), "未识别的余额响应结构，请改用「自定义映射」指定字段"
                + (lastError == null ? "" : "；" + lastError));
    }

    /**
     * 预计可用天数的单位（不是货币，页面原样展示）
     */
    private static final String UNIT_DAY = "天";

    /**
     * Moss 门户字段 → 页面展示名：读数行直接给中文标签，前端不必再猜字段含义。
     * 键是字段路径的最后一段，默认探测与自定义表达式共用
     */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("display_available_points", "余额"),
            Map.entry("available_points", "余额"),
            Map.entry("display_total_remaining_points", "余额"),
            Map.entry("total_remaining_points", "余额"),
            Map.entry("display_frozen_points", "冻结"),
            Map.entry("frozen_points", "冻结"),
            Map.entry("display_month_charged_points", "本月已用"),
            Map.entry("month_charged_points", "本月已用"),
            Map.entry("display_average_daily_charged_points", "日均消费"),
            Map.entry("average_daily_charged_points", "日均消费"),
            Map.entry("estimated_available_days", "预计可用"));

    /**
     * 把 Moss 字段名翻成页面展示名，并给「预计可用天数」补上天数单位。
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
            if ("预计可用".equals(friendly) && (part.getCurrency() == null || part.getCurrency().isBlank())) {
                part.setCurrency(UNIT_DAY);
            }
        }
        return result;
    }

    /**
     * 把令牌装进 Cookie：Moss 门户认的是 {@code moss_studio_prod_access_token}，
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

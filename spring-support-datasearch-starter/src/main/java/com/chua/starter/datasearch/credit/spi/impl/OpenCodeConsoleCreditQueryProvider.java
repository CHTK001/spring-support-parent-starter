package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenCode 控制台现金余额实现（Zen 按量计费的美元余额）。
 *
 * <p>Zen 现金余额官方没有提供任何 API（upstream #10448 仍 open），控制台网页读的是
 * 同源接口 {@code GET https://opencode.ai/console/api/billing/status}——属<b>非公开
 * 文档 API</b>。鉴权不用 API Key（带 Key 会 403），用浏览器登录控制台后的会话 Cookie
 * {@code __Host-console_session}：前端为 Effect HttpApiClient，fetch 一律
 * {@code credentials:'same-origin'}，纯 Cookie 会话鉴权，GET 无 CSRF 头要求，
 * 带 Cookie 直调即可。</p>
 *
 * <p>响应为控制台 bundle 里的 {@code BillingStatus} 结构：
 * {@code {payLater, billingMode, mode, balanceMicroCents, creditLimitMicroCents?,
 * availableMicroCents, inferenceAllowanceMicroCents?, canPurchaseCredits,
 * canEnableAutoRecharge}}，金额单位 <b>1 USD = 1e8 microCents</b>（官方 #44189
 * {@code formatBalance = balance / 1e8} 互证）。这里取 {@code balanceMicroCents}
 * 换算成美元读数，其余字段进 {@code extra}；字段缺失按「非公开接口结构可能已变更」
 * 如实报错，不编造读数。401 = Cookie 失效、403 = 无 Billing 权限，分开提示。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("opencode-console")
public class OpenCodeConsoleCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识
     */
    static final String NAME = "opencode-console";

    /**
     * 控制台计费状态端点（控制台同源接口，非公开文档 API）
     */
    private static final String DEFAULT_URL = "https://opencode.ai/console/api/billing/status";

    /**
     * 控制台会话 Cookie 名（__Host- 前缀，浏览器登录 opencode.ai/console 后写入）
     */
    static final String SESSION_COOKIE = "__Host-console_session";

    /**
     * Cookie 请求头名
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * 1 USD 对应的 microCents 数（控制台 formatBalance = balance / 1e8）
     */
    private static final BigDecimal MICRO_PER_UNIT = BigDecimal.valueOf(100_000_000L);

    /**
     * 错误片段截取长度
     */
    private static final int SNIPPET_LIMIT = 200;

    /**
     * 响应映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 会话 Cookie 名：内嵌浏览器在页面 Cookie 里读到它即认为控制台已登录，
     * 其值就是「余额密钥」（{@link #query} 用它组装 Cookie 头）。
     *
     * @return 控制台会话 Cookie 名
     */
    @Override
    public String loginCookieName() {
        return SESSION_COOKIE;
    }

    /**
     * 查控制台现金余额。
     *
     * <p>鉴权为会话 Cookie：调用方把 Cookie 值（或完整 Cookie 头）放进 token 字段，
     * 这里统一装进 {@code Cookie} 请求头。401 / 403 的语义分开提示。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少控制台会话 Cookie：请登录 opencode.ai/console 后把浏览器里的 "
                        + SESSION_COOKIE + " Cookie 值填入「余额密钥」（API Key 查不了现金余额）");
            }
            applyCookieAuth(query);
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            ClientResponse response = CreditHttp.exchange(query, url);
            int status = response.getStatusCode();
            String body = response.getBodyString();
            if (status == 401) {
                return CreditResult.fail(NAME, "控制台会话 Cookie 无效或已过期（HTTP 401）:"
                        + "请重新登录 opencode.ai/console 后更新「余额密钥」");
            }
            if (status == 403) {
                return CreditResult.fail(NAME, "无权读取控制台计费信息（HTTP 403）:"
                        + "当前会话没有 Billing 权限，或该接口已调整");
            }
            if (status != 200) {
                return CreditResult.fail(NAME, "HTTP " + status + " " + snippet(body));
            }
            List<BalancePart> parts = new ArrayList<>(1);
            Map<String, Object> extra = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>(1);
            parseBilling(body, parts, extra, errors);
            if (parts.isEmpty()) {
                return CreditResult.fail(NAME, String.join("；", errors));
            }
            CreditResult result = CreditResult.ok(NAME, parts, body);
            result.setExtra(extra);
            return result;
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 解析控制台计费状态响应：{@code balanceMicroCents} 换算美元，其余字段进 extra。
     *
     * <p>{@code balanceMicroCents} 缺失或不可读时只报错、不产出读数——这是非公开接口，
     * 结构变更时宁可如实失败，不编造 $0。</p>
     *
     * @param body   原始响应
     * @param parts  读数出口
     * @param extra  附加数据出口
     * @param errors 错误出口
     */
    void parseBilling(String body, List<BalancePart> parts,
                      Map<String, Object> extra, List<String> errors) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            errors.add("控制台计费响应不是合法 JSON: " + e.getMessage());
            return;
        }
        if (!root.isObject()) {
            errors.add("控制台计费响应不是 JSON 对象");
            return;
        }
        JsonNode balanceNode = root.path("balanceMicroCents");
        BigDecimal micro = balanceNode.isNumber() ? decimalOf(balanceNode) : null;
        if (micro == null) {
            errors.add("控制台计费响应缺少 balanceMicroCents 字段（非公开接口，结构可能已变更）");
            return;
        }
        parts.add(part("Zen 现金余额", micro.movePointLeft(8), "USD"));
        extra.put("balance_micro_cents", micro);
        extra.put("billing_mode", root.path("billingMode").asText(null));
        extra.put("mode", root.path("mode").asText(null));
        extra.put("pay_later", root.path("payLater").asBoolean(false));
        putUsd(extra, "available", root.path("availableMicroCents"));
        putUsd(extra, "credit_limit", root.path("creditLimitMicroCents"));
        putUsd(extra, "inference_allowance", root.path("inferenceAllowanceMicroCents"));
        extra.put("can_purchase_credits", root.path("canPurchaseCredits").asBoolean(false));
        extra.put("can_enable_auto_recharge", root.path("canEnableAutoRecharge").asBoolean(false));
    }

    /**
     * microCents 金额字段进 extra：原始值 + 换算后的美元值（字段缺失时跳过）。
     *
     * @param extra 附加数据出口
     * @param key   字段名（产出 {@code key_micro_cents} 与 {@code key_usd} 两个键）
     * @param node  响应里的 microCents 节点
     */
    private static void putUsd(Map<String, Object> extra, String key, JsonNode node) {
        if (!node.isNumber()) {
            return;
        }
        BigDecimal micro = decimalOf(node);
        if (micro == null) {
            return;
        }
        extra.put(key + "_micro_cents", micro);
        extra.put(key + "_usd", micro.movePointLeft(8));
    }

    /**
     * 把令牌装进 Cookie 头：控制台认的是 {@code __Host-console_session}，
     * 不是 Authorization。调用方已显式指定请求头时尊重调用方。
     *
     * @param query 请求
     */
    private static void applyCookieAuth(CreditQuery query) {
        if (query.getTokenHeader() != null && !query.getTokenHeader().isBlank()) {
            return;
        }
        query.setTokenHeader(COOKIE_HEADER);
        query.setToken(cookieOf(query.getToken()));
    }

    /**
     * Cookie 归一化：裸值包装成会话 Cookie，完整 Cookie 头原样保留。
     *
     * <p>用户常直接从浏览器 DevTools 复制：只复制值时补上 Cookie 名；复制了整行
     * （可能还带着其它 Cookie）时原样使用，服务端按名字自取。</p>
     *
     * @param token 余额密钥原文
     * @return Cookie 请求头值
     */
    static String cookieOf(String token) {
        String trimmed = token.trim();
        if (trimmed.indexOf('=') >= 0) {
            return trimmed;
        }
        return SESSION_COOKIE + "=" + trimmed;
    }

    /**
     * 组一段读数。
     *
     * @param label    段名
     * @param amount   金额
     * @param currency 币种或单位
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount, String currency) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(currency);
        return part;
    }

    /**
     * 数字节点 → BigDecimal（数字节点的 asText 是无损失的十进制串）。
     *
     * @param node 数字节点
     * @return 数值；不是合法数字为 null
     */
    private static BigDecimal decimalOf(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截取响应片段用于错误提示。
     *
     * @param text 响应
     * @return 单行片段
     */
    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= SNIPPET_LIMIT ? oneLine : oneLine.substring(0, SNIPPET_LIMIT);
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

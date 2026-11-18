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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic 官方预付余额实现。
 *
 * <p>Anthropic 没有公开的 API-Key 余额接口，控制台网页读的是同源非公开接口
 * {@code GET https://platform.claude.com/api/organizations/{org_id}/prepaid/credits}，
 * 鉴权用浏览器登录控制台后的会话 Cookie {@code sessionKey}（形如
 * {@code sk-ant-sid01-…}），API Key 查不了。组织 ID 可预先嵌入余额地址，也可留空由
 * {@code GET https://platform.claude.com/api/organizations} 自动发现（取第一个
 * {@code capabilities} 含 {@code api} 的组织）。</p>
 *
 * <p>响应里 {@code amount} 为<b>最小货币单位</b>（minor units），实际金额要除以
 * {@code 10^exponent}：exponent 取 {@code balance.credits.exponent} /
 * {@code balance.money.exponent}（缺省 2，即美分 → 美元）；币种取根级 {@code currency}
 * （缺省 USD）。401 = Cookie 失效、403 = 无该组织权限，分开提示。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("anthropic")
public class AnthropicCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识
     */
    static final String NAME = "anthropic";

    /**
     * 预付余额端点模板（{org_id} 由配置或自动发现补齐）
     */
    private static final String CREDITS_URL_TEMPLATE =
            "https://platform.claude.com/api/organizations/{org_id}/prepaid/credits";

    /**
     * 组织列表端点（自动发现 org_id 用）
     */
    private static final String ORG_LIST_URL = "https://platform.claude.com/api/organizations";

    /**
     * 组织 ID 占位符
     */
    private static final String ORG_PLACEHOLDER = "{org_id}";

    /**
     * 控制台会话 Cookie 名
     */
    static final String SESSION_COOKIE = "sessionKey";

    /**
     * Cookie 请求头名
     */
    private static final String COOKIE_HEADER = "Cookie";

    /**
     * exponent 兜底：响应未给时按美分（1/100 美元）处理
     */
    private static final int DEFAULT_EXPONENT = 2;

    /**
     * exponent 合法区间（官方取值 0–4）
     */
    private static final int MIN_EXPONENT = 0;

    private static final int MAX_EXPONENT = 4;

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
     * 其值就是「余额密钥」。
     *
     * @return 控制台会话 Cookie 名
     */
    @Override
    public String loginCookieName() {
        return SESSION_COOKIE;
    }

    /**
     * 查 Anthropic 预付余额。
     *
     * <p>鉴权为会话 Cookie（{@code sessionKey}）；组织 ID 按余额地址的三种形态解析
     * （完整地址 / 裸组织 ID / 占位符），都没给时自动发现。401 / 403 分开提示。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少 Anthropic 控制台会话 Cookie：请登录 platform.claude.com "
                        + "后把浏览器里的 " + SESSION_COOKIE + " Cookie 值填入「余额密钥」"
                        + "（API Key 查不了预付余额）");
            }
            applyCookieAuth(query);
            String configured = blank(query.getUrl()) ? null : query.getUrl().trim();
            if (configured != null && configured.contains(ORG_PLACEHOLDER)) {
                String orgId = discoverOrgId(query);
                if (orgId == null) {
                    return CreditResult.fail(NAME, "余额地址含 {org_id} 占位符，"
                            + "但未能从控制台自动发现可用组织");
                }
                return fetchCredits(query, configured.replace(ORG_PLACEHOLDER, orgId));
            }
            if (configured != null && configured.contains("/prepaid/credits")) {
                // 完整余额地址（组织 ID 已嵌入）：原样使用
                return fetchCredits(query, configured);
            }
            if (configured != null) {
                // 裸组织 ID：按默认端点模板补齐
                return fetchCredits(query, CREDITS_URL_TEMPLATE.replace(ORG_PLACEHOLDER, configured));
            }
            String orgId = discoverOrgId(query);
            if (orgId == null) {
                return CreditResult.fail(NAME, "未能从控制台自动发现含 API 权限的组织："
                        + "请登录 platform.claude.com 确认账号可用，"
                        + "或把组织 ID / 完整余额地址填入「余额地址」");
            }
            return fetchCredits(query, CREDITS_URL_TEMPLATE.replace(ORG_PLACEHOLDER, orgId));
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 自动发现组织 ID：GET /api/organizations，取第一个 {@code capabilities}
     * 含 {@code api} 的组织。401 / 403 分开提示。
     *
     * @param query 请求（已带 Cookie 鉴权）
     * @return 组织 ID；发现不了为 null
     * @throws IOException 响应不可读
     */
    private String discoverOrgId(CreditQuery query) throws IOException {
        ClientResponse response = CreditHttp.exchange(query, ORG_LIST_URL);
        int status = response.getStatusCode();
        String body = response.getBodyString();
        if (status == 401) {
            throw new IllegalStateException("控制台会话 Cookie 无效或已过期（HTTP 401）:"
                    + "请重新登录 platform.claude.com 后更新「余额密钥」");
        }
        if (status == 403) {
            throw new IllegalStateException("无权读取控制台组织列表（HTTP 403）："
                    + "当前会话 Cookie 可能没有组织访问权限");
        }
        if (status != 200) {
            throw new IllegalStateException("读取组织列表失败：HTTP " + status + " " + snippet(body));
        }
        JsonNode root = MAPPER.readTree(body);
        if (!root.isArray()) {
            throw new IllegalStateException("组织列表响应不是 JSON 数组（非公开接口，结构可能已变更）");
        }
        for (JsonNode org : root) {
            if (hasApiCapability(org.path("capabilities")) && org.path("id").isTextual()) {
                return org.path("id").asText().trim();
            }
        }
        return null;
    }

    /**
     * 判断 capabilities 数组里是否含 {@code api}（控制台按它区分可开 API 的组织）。
     *
     * @param capabilities capabilities 节点
     * @return 含 api 为 true
     */
    private static boolean hasApiCapability(JsonNode capabilities) {
        if (!capabilities.isArray()) {
            return false;
        }
        for (JsonNode capability : capabilities) {
            if ("api".equalsIgnoreCase(capability.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 请求预付余额端点并解析：{@code amount}（最小货币单位）按 exponent 折算成主单位。
     *
     * @param query 请求（已带 Cookie 鉴权）
     * @param url   完整余额地址
     * @return 结果；查不到时 error 有值
     * @throws IOException 响应不可读
     */
    private CreditResult fetchCredits(CreditQuery query, String url) throws IOException {
        ClientResponse response = CreditHttp.exchange(query, url);
        int status = response.getStatusCode();
        String body = response.getBodyString();
        if (status == 401) {
            return CreditResult.fail(NAME, "控制台会话 Cookie 无效或已过期（HTTP 401）:"
                    + "请重新登录 platform.claude.com 后更新「余额密钥」");
        }
        if (status == 403) {
            return CreditResult.fail(NAME, "无权读取该组织的预付余额（HTTP 403）:"
                    + "会话 Cookie 可能没有该组织的访问权限");
        }
        if (status != 200) {
            return CreditResult.fail(NAME, "HTTP " + status + " " + snippet(body));
        }
        JsonNode root = MAPPER.readTree(body);
        JsonNode amountNode = root.path("amount");
        if (!amountNode.isValueNode() || amountNode.isNull()) {
            return CreditResult.fail(NAME, "预付余额响应缺少 amount 字段（非公开接口，结构可能已变更）");
        }
        BigDecimal minor;
        try {
            minor = new BigDecimal(amountNode.asText().trim());
        } catch (NumberFormatException e) {
            return CreditResult.fail(NAME, "预付余额响应的 amount 不是合法数字: " + amountNode.asText());
        }
        int exponent = exponentOf(root);
        String currency = root.path("currency").asText(null);
        if (blank(currency)) {
            currency = root.path("balance").path("money").path("currency").asText("USD");
        }
        List<BalancePart> parts = new ArrayList<>(1);
        parts.add(part("预付余额", minor.movePointLeft(exponent), currency));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("amount_minor", minor);
        extra.put("exponent", exponent);
        CreditResult result = CreditResult.ok(NAME, parts, body);
        result.setExtra(extra);
        return result;
    }

    /**
     * 读金额折算指数：优先 {@code balance.credits.exponent}，其次
     * {@code balance.money.exponent}，越界或缺省回落 2（美分 → 美元）。
     *
     * @param root 余额响应根节点
     * @return 折算指数（0–4）
     */
    static int exponentOf(JsonNode root) {
        JsonNode credits = root.path("balance").path("credits").path("exponent");
        JsonNode money = root.path("balance").path("money").path("exponent");
        int exponent = credits.isInt() ? credits.asInt() : (money.isInt() ? money.asInt() : DEFAULT_EXPONENT);
        if (exponent < MIN_EXPONENT || exponent > MAX_EXPONENT) {
            return DEFAULT_EXPONENT;
        }
        return exponent;
    }

    /**
     * 把令牌装进 Cookie 头：控制台认的是 {@code sessionKey}，不是 Authorization。
     * 调用方已显式指定请求头时尊重调用方。
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
     * @param currency 币种
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

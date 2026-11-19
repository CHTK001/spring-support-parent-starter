package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OpenCode 控制台余额查询验证，全程不 mock：
 *
 * <p>解析逻辑按官方控制台 bundle 的 {@code BillingStatus} 真实结构喂样例，不发网络
 * 请求；链路用例直接请求 opencode.ai 官方控制台端点
 * （{@code /console/api/billing/status}，控制台同源接口、非公开文档 API）。</p>
 *
 * <p>真实会话 Cookie 通过环境变量 {@code OPENCODE_CONSOLE_COOKIE} 提供（浏览器登录
 * opencode.ai/console 后的 {@code __Host-console_session} 值或完整 Cookie 头），
 * 缺失时按项目惯例跳过；探活 API Key 通过 {@code OPENCODE_TEST_KEY} 提供
 * （该 Key 无 Billing 权限，用于验证 403 语义）。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class OpenCodeConsoleCreditQueryProviderTest {

    private final OpenCodeConsoleCreditQueryProvider provider = new OpenCodeConsoleCreditQueryProvider();

    /**
     * Cookie 归一化：裸值包装成会话 Cookie，完整 Cookie 头原样保留。
     */
    @Test
    void cookieNormalization() {
        assertEquals("__Host-console_session=abc123",
                OpenCodeConsoleCreditQueryProvider.cookieOf("abc123"));
        assertEquals("__Host-console_session=abc123",
                OpenCodeConsoleCreditQueryProvider.cookieOf("  abc123  "));
        assertEquals("__Host-console_session=abc123",
                OpenCodeConsoleCreditQueryProvider.cookieOf("__Host-console_session=abc123"));
        // 用户从 DevTools 复制整行 Cookie（带多个 Cookie）时原样保留
        assertEquals("a=1; __Host-console_session=abc123; b=2",
                OpenCodeConsoleCreditQueryProvider.cookieOf("a=1; __Host-console_session=abc123; b=2"));
        assertEquals("__Host-console_session",
                provider.loginCookieName());
    }

    /**
     * 按官方 BillingStatus 结构喂样例：balanceMicroCents 换算美元，其余字段进 extra。
     */
    @Test
    void parseBillingReadsBalanceAndExtra() {
        String body = "{"
                + "\"payLater\":false,"
                + "\"billingMode\":\"credits\","
                + "\"mode\":\"standard\","
                + "\"balanceMicroCents\":1234567890,"
                + "\"creditLimitMicroCents\":null,"
                + "\"availableMicroCents\":1234567890,"
                + "\"inferenceAllowanceMicroCents\":500000000,"
                + "\"canPurchaseCredits\":true,"
                + "\"canEnableAutoRecharge\":false"
                + "}";
        List<BalancePart> parts = new ArrayList<>(1);
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>(1);
        provider.parseBilling(body, parts, extra, errors);

        assertTrue(errors.isEmpty(), "不应有解析错误: " + errors);
        assertEquals(1, parts.size());
        assertEquals("Zen 现金余额", parts.get(0).getLabel());
        assertEquals("USD", parts.get(0).getCurrency());
        // 1234567890 microCents = 12.3456789 USD（1 USD = 1e8 microCents）
        assertEquals(0, new BigDecimal("12.3456789").compareTo(parts.get(0).getAmount()));
        assertEquals("credits", extra.get("billing_mode"));
        assertEquals(Boolean.FALSE, extra.get("pay_later"));
        assertEquals(0, new BigDecimal("12.3456789").compareTo((BigDecimal) extra.get("available_usd")));
        // creditLimitMicroCents=null：缺失字段跳过，不产出读数
        assertFalse(extra.containsKey("credit_limit_usd"));
        assertEquals(0, new BigDecimal("5").compareTo((BigDecimal) extra.get("inference_allowance_usd")));
        assertEquals(Boolean.TRUE, extra.get("can_purchase_credits"));
        assertEquals(Boolean.FALSE, extra.get("can_enable_auto_recharge"));
    }

    /**
     * balanceMicroCents 缺失（非公开接口结构变更）时如实报错，不编造 $0。
     */
    @Test
    void parseBillingRejectsMissingBalanceWithoutFabrication() {
        List<BalancePart> parts = new ArrayList<>(1);
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>(1);
        provider.parseBilling("{\"billingMode\":\"credits\"}", parts, extra, errors);
        assertTrue(parts.isEmpty(), "缺字段时不得编造读数");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("balanceMicroCents"));

        // 非 JSON 与非对象响应同样只报错
        provider.parseBilling("not-json", new ArrayList<>(), new LinkedHashMap<>(), errors);
        assertTrue(errors.size() >= 2);

        // 缺令牌：快速失败，提示 Cookie 口径
        CreditResult missing = provider.query(new CreditQuery());
        assertFalse(missing.isOk());
        assertNotNull(missing.getError());
        assertTrue(missing.getError().contains("Cookie"));
    }

    /**
     * 真实端点、无效会话 Cookie：官方返回 401，实现如实失败并说明 Cookie 失效。
     */
    @Test
    void realEndpointRejectsInvalidSessionCookie() {
        CreditQuery query = new CreditQuery();
        query.setKind("opencode-console");
        query.setToken("__Host-console_session=probe-invalid-cookie");
        CreditResult result = provider.query(query);
        assertFalse(result.isOk(), "无效 Cookie 不可能查到余额");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("401"), "实际错误: " + result.getError());
    }

    /**
     * 真实端点、API Key 直调（Bearer）：官方探测为 403（无 Billing 权限），实现如实失败。
     * Key 通过环境变量 OPENCODE_TEST_KEY 提供，缺失时跳过。
     */
    @Test
    void realEndpointWithApiKeyIsForbidden() {
        String key = System.getenv("OPENCODE_TEST_KEY");
        assumeTrue(key != null && !key.isBlank(), "未提供 OPENCODE_TEST_KEY，跳过真实 403 链路");
        CreditQuery query = new CreditQuery();
        query.setKind("opencode-console");
        query.setToken(key.trim());
        query.setTokenHeader("Authorization");
        CreditResult result = provider.query(query);
        assertFalse(result.isOk(), "API Key 无 Billing 权限，不应查到控制台余额");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("403"), "实际错误: " + result.getError());
    }

    /**
     * 真实端点、真实会话 Cookie：能读到 Zen 现金余额（美元）。
     * Cookie 通过环境变量 OPENCODE_CONSOLE_COOKIE 提供，缺失时跳过；
     * 会话失效（401/403）是预期内状态，断言错误信息如实，不当成测试失败。
     */
    @Test
    void realEndpointWithSessionCookie() {
        String cookie = System.getenv("OPENCODE_CONSOLE_COOKIE");
        assumeTrue(cookie != null && !cookie.isBlank(), "未提供 OPENCODE_CONSOLE_COOKIE，跳过真实控制台链路");
        CreditQuery query = new CreditQuery();
        query.setKind("opencode-console");
        query.setToken(cookie.trim());
        CreditResult result = provider.query(query);
        if (!result.isOk()) {
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("401") || result.getError().contains("403"),
                    "会话失效之外的原因: " + result.getError());
            return;
        }
        assertEquals("opencode-console", result.getSource());
        assertFalse(result.getParts().isEmpty());
        assertEquals("Zen 现金余额", result.getParts().get(0).getLabel());
        assertEquals("USD", result.getParts().get(0).getCurrency());
        assertNotNull(result.getParts().get(0).getAmount());
    }
}

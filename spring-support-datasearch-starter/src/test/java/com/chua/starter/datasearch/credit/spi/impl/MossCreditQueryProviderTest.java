package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Moss 门户余额解析验证，全程不 mock：
 *
 * <p>解析逻辑直接喂门户真实响应样例（2026-11 实测
 * {@code GET https://platform.moss.cn/portal-api/maas/credits/usage-summary}），
 * 不发网络请求；链路用例请求真实端点，验证 Cookie 会话口径
 * （{@code moss_studio_prod_access_token}，非 Authorization）。</p>
 *
 * <p>真实会话 Cookie 通过环境变量 {@code MOSS_CREDIT_COOKIE} 提供（令牌原值，
 * 不含 Cookie 名前缀），缺失时跳过；会话失效（401）是预期内状态，不当成测试失败。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
class MossCreditQueryProviderTest {

    private final MossCreditQueryProvider provider = new MossCreditQueryProvider();

    /** 2026-11 实测响应样例：扁平结构（无 data 包裹），积分原始值与展示串并存 */
    private static final String REAL_RESPONSE = """
            {"tenant_id":83946,"days":7,"display_month_charged_points":"6.00","average_daily_charged_points":85.71428571428571,"display_average_daily_charged_points":"0.86","available_points":4400,"display_available_points":"44.00","frozen_points":0,"display_frozen_points":"0.00","total_remaining_points":4400,"display_total_remaining_points":"44.00","estimated_available_days":51.333333333333336}""";

    /**
     * 真实样例：一次带出 余额 / 本月已用 / 冻结 / 日均消费 / 预计可用天数 五段读数，
     * 币种 CNY，预计可用天数单位是「天」。
     */
    @Test
    void parsesRealUsageSummaryIntoLabeledParts() {
        CreditResult result = provider.probeDefaults(REAL_RESPONSE, null);
        assertTrue(result.isOk(), "真实样例应能解析: " + result.getError());
        assertEquals("moss", result.getSource());
        assertEquals(5, result.getParts().size(), "应带出全部 5 段读数: " + result.getParts());

        BalancePart balance = result.getParts().get(0);
        assertEquals("余额", balance.getLabel());
        assertEquals("CNY", balance.getCurrency());
        assertEquals(0, new BigDecimal("44.00").compareTo(balance.getAmount()));

        assertEquals("本月已用", result.getParts().get(1).getLabel());
        assertEquals("CNY", result.getParts().get(1).getCurrency());
        assertEquals(0, new BigDecimal("6.00").compareTo(result.getParts().get(1).getAmount()));

        assertEquals("冻结", result.getParts().get(2).getLabel());
        assertEquals(0, new BigDecimal("0").compareTo(result.getParts().get(2).getAmount()));

        assertEquals("日均消费", result.getParts().get(3).getLabel());
        assertEquals(0, new BigDecimal("0.86").compareTo(result.getParts().get(3).getAmount()));

        BalancePart days = result.getParts().get(4);
        assertEquals("预计可用", days.getLabel());
        assertEquals("天", days.getCurrency(), "预计可用天数不是货币，单位应为天");
        assertEquals(0, new BigDecimal("51.333333").compareTo(days.getAmount()));

        // 主余额口径 = 第一段（余额 44.00 CNY）
        assertEquals(0, new BigDecimal("44.00").compareTo(result.getAmount()));
        assertEquals("CNY", result.getCurrency());
    }

    /**
     * 门户改版丢掉 display_* 展示串时，回落到积分原始值 / 100（100 积分 = 1 元）。
     */
    @Test
    void fallsBackToRawPointsDividedBy100WhenDisplayFieldsMissing() {
        String legacy = "{\"available_points\":4400,\"total_remaining_points\":4400,\"frozen_points\":0}";
        CreditResult result = provider.probeDefaults(legacy, null);
        assertTrue(result.isOk(), "积分原始值兜底应能解析: " + result.getError());
        assertEquals(1, result.getParts().size());
        BalancePart part = result.getParts().get(0);
        assertEquals("余额", part.getLabel());
        assertEquals(0, new BigDecimal("44").compareTo(part.getAmount()));
        // 兜底表达式无货币前缀、未配计费币种：币种留空，不编造
        assertNull(part.getCurrency());
    }

    /**
     * 结构完全对不上时如实失败，提示改走「自定义映射」，不编造读数。
     */
    @Test
    void unrecognizedStructureAsksForCustomMapping() {
        CreditResult result = provider.probeDefaults("{\"foo\":1}", null);
        assertFalse(result.isOk());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("自定义映射"), "实际错误: " + result.getError());
    }

    /**
     * 真实端点、无效会话 Cookie：门户返回 401，实现如实失败并说明 Cookie 失效。
     */
    @Test
    void realEndpointRejectsInvalidSessionCookie() {
        CreditQuery query = new CreditQuery();
        query.setKind("moss");
        query.setToken("probe-invalid-cookie");
        CreditResult result = provider.query(query);
        assertFalse(result.isOk(), "无效 Cookie 不可能查到余额");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("401"), "实际错误: " + result.getError());
    }

    /**
     * 真实端点、真实会话 Cookie：能读到门户余额（元）。
     * Cookie 通过环境变量 MOSS_CREDIT_COOKIE 提供，缺失时跳过；
     * 会话失效（401/403）是预期内状态，断言错误信息如实，不当成测试失败。
     */
    @Test
    void realEndpointWithSessionCookieReturnsBalance() {
        String cookie = System.getenv("MOSS_CREDIT_COOKIE");
        assumeTrue(cookie != null && !cookie.isBlank(), "未提供 MOSS_CREDIT_COOKIE，跳过真实门户链路");
        CreditQuery query = new CreditQuery();
        query.setKind("moss");
        query.setToken(cookie.trim());
        CreditResult result = provider.query(query);
        if (!result.isOk()) {
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("401") || result.getError().contains("403"),
                    "会话失效之外的原因: " + result.getError());
            return;
        }
        assertEquals("moss", result.getSource());
        assertFalse(result.getParts().isEmpty());
        assertEquals("余额", result.getParts().get(0).getLabel());
        assertEquals("CNY", result.getParts().get(0).getCurrency());
        assertNotNull(result.getParts().get(0).getAmount());
        assertTrue(result.getParts().get(0).getAmount().signum() >= 0);
    }
}

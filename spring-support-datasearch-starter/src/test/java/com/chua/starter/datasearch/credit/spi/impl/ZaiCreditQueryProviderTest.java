package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * z.ai 余额解析测试：直接喂平台真实响应结构样例，不发任何网络请求。
 *
 * <p>覆盖：国际站配额窗口（多类型分组、窗口标签、百分比、重置时间）、
 * 剩余缺省时用上限减已用推算、国内站「未订阅 Coding Plan」降级标记、
 * 余额响应（availableBalance 优先 / balance 回退）、非法 JSON 失败路径，
 * 以及 merge 的「无订阅 + 有余额」「双失败」合并行为。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class ZaiCreditQueryProviderTest {

    /** 国际站配额响应样例：Token 两个窗口（5 小时 / 每周）+ MCP 一个窗口 */
    private static final String QUOTA_RESPONSE = """
            {
              "success": true,
              "code": 200,
              "data": {
                "planName": "GLM Coding Plan",
                "limits": [
                  {"type": "TOKENS_LIMIT", "unit": 3, "number": 5, "usage": 120,
                   "currentValue": 1000, "remaining": 880, "percentage": 12,
                   "nextResetTime": "2026-11-18T17:00:00Z"},
                  {"type": "TOKENS_LIMIT", "unit": 6, "number": 1, "usage": 500,
                   "currentValue": 5000, "remaining": 4500, "percentage": 10,
                   "nextResetTime": "2026-11-23T00:00:00Z"},
                  {"type": "TIME_LIMIT", "unit": 3, "number": 5, "usage": 2,
                   "currentValue": 10, "remaining": 8, "percentage": 20,
                   "nextResetTime": "2026-11-18T17:00:00Z"}
                ]
              }
            }
            """;

    /** 国内站未订阅 Coding Plan 的响应样例（实测口径） */
    private static final String NO_PLAN_RESPONSE = """
            {"success": false, "code": 500, "msg": "当前用户不存在coding plan"}
            """;

    /** 国内站账户余额响应样例 */
    private static final String BALANCE_RESPONSE = """
            {
              "success": true,
              "data": {"balance": 100.50, "availableBalance": "88.25", "rechargeAmount": 100,
                       "giveAmount": 0.50, "totalSpendAmount": 12.25, "frozenBalance": 0}
            }
            """;

    @Test
    void parsesQuotaWindowsGroupedByType() {
        CreditResult result = ZaiCreditQueryProvider.parseQuota(QUOTA_RESPONSE);
        assertTrue(result.isOk());
        assertEquals("zai", result.getSource());
        // parts[0] = 第一个窗口（Token 5 小时）的剩余
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("880")));
        assertEquals("tokens", result.getCurrency());
        Map<String, Object> extra = result.getExtra();
        assertEquals("zai", extra.get("mode"));
        // Token + MCP 混合类型：不写全局 unit（各池单位不同）
        assertNull(extra.get("unit"));
        assertEquals("GLM Coding Plan", extra.get("plan"));
        assertEquals("Coding Plan 配额", extra.get("pools_title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) extra.get("pools");
        assertEquals(2, pools.size());
        // 第一个池：Token 配额，两个窗口
        Map<String, Object> tokenPool = pools.get(0);
        assertEquals("Token 配额", tokenPool.get("name"));
        assertEquals("TOKENS_LIMIT", tokenPool.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tokenWindows = (List<Map<String, Object>>) tokenPool.get("windows");
        assertEquals(2, tokenWindows.size());
        assertEquals("5 小时", tokenWindows.get(0).get("label"));
        assertEquals("每周", tokenWindows.get(1).get("label"));
        assertEquals(0, new BigDecimal("880").compareTo((BigDecimal) tokenWindows.get(0).get("remaining")));
        assertEquals(0, new BigDecimal("4500").compareTo((BigDecimal) tokenWindows.get(1).get("remaining")));
        assertEquals("2026-11-18T17:00:00Z", tokenWindows.get(0).get("reset"));
        // 第二个池：MCP 配额
        Map<String, Object> mcpPool = pools.get(1);
        assertEquals("MCP 配额", mcpPool.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mcpWindows = (List<Map<String, Object>>) mcpPool.get("windows");
        assertEquals(1, mcpWindows.size());
        // MCP 窗口：剩余 8 / 上限 10 = 80%
        assertEquals(0, new BigDecimal("80").compareTo((BigDecimal) mcpWindows.get(0).get("percent")));
    }

    @Test
    void derivesRemainingFromLimitAndUsage() {
        String raw = """
                {"success": true, "data": {"limits": [
                  {"type": "TOKENS_LIMIT", "unit": 3, "number": 5, "usage": 250, "currentValue": 1000}
                ]}}
                """;
        CreditResult result = ZaiCreditQueryProvider.parseQuota(raw);
        assertTrue(result.isOk());
        // 剩余 = 1000 - 250 = 750，占比 75%；纯 Token 池带全局 unit
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("750")));
        Map<String, Object> extra = result.getExtra();
        assertEquals("tokens", extra.get("unit"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) extra.get("pools");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) pools.get(0).get("windows");
        assertEquals(0, new BigDecimal("750").compareTo((BigDecimal) windows.get(0).get("remaining")));
        assertEquals(0, new BigDecimal("75").compareTo((BigDecimal) windows.get(0).get("percent")));
    }

    @Test
    void marksNoPlanAsFailureForDegrading() {
        CreditResult result = ZaiCreditQueryProvider.parseQuota(NO_PLAN_RESPONSE);
        assertTrue(!result.isOk());
        // merge 依赖该标记识别「未订阅」，把失败降级成「仅余额」
        assertTrue(result.getError().contains(ZaiCreditQueryProvider.NO_PLAN_MARK));
    }

    @Test
    void failsOnOtherUpstreamErrors() {
        CreditResult result = ZaiCreditQueryProvider.parseQuota(
                "{\"success\": false, \"code\": 401, \"msg\": \"invalid api key\"}");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("401"));
        assertTrue(result.getError().contains("invalid api key"));
    }

    @Test
    void failsOnIllegalJson() {
        CreditResult result = ZaiCreditQueryProvider.parseQuota("not-json");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("JSON"));
    }

    @Test
    void parsesBalanceWithAvailableFirst() {
        CreditResult result = ZaiCreditQueryProvider.parseBalance(BALANCE_RESPONSE);
        assertTrue(result.isOk());
        assertEquals(1, result.getParts().size());
        BalancePart part = result.getParts().get(0);
        assertEquals("可用余额", part.getLabel());
        assertEquals(0, part.getAmount().compareTo(new BigDecimal("88.25")));
        assertEquals("CNY", part.getCurrency());
    }

    @Test
    void parsesBalanceFallbackToBalanceField() {
        String raw = "{\"success\": true, \"data\": {\"balance\": \"66.00\"}}";
        CreditResult result = ZaiCreditQueryProvider.parseBalance(raw);
        assertTrue(result.isOk());
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("66.00")));
    }

    @Test
    void mergeDegradesToBalanceOnlyWhenNoPlan() {
        CreditResult quota = ZaiCreditQueryProvider.parseQuota(NO_PLAN_RESPONSE);
        CreditResult balance = ZaiCreditQueryProvider.parseBalance(BALANCE_RESPONSE);
        CreditResult merged = ZaiCreditQueryProvider.merge(quota, balance, true);
        assertTrue(merged.isOk());
        // 主读数 = 可用余额，未订阅信息放进 extra
        assertEquals(0, merged.getAmount().compareTo(new BigDecimal("88.25")));
        assertEquals("CNY", merged.getCurrency());
        assertEquals("未开通", merged.getExtra().get("coding_plan"));
        assertEquals("zai", merged.getExtra().get("mode"));
        // 失败的配额不该把 pools 带进来
        assertNull(merged.getExtra().get("pools"));
    }

    @Test
    void mergeKeepsQuotaWindowsWithoutBalance() {
        CreditResult quota = ZaiCreditQueryProvider.parseQuota(QUOTA_RESPONSE);
        CreditResult merged = ZaiCreditQueryProvider.merge(quota, null, false);
        assertTrue(merged.isOk());
        assertNotNull(merged.getExtra().get("pools"));
        assertEquals("GLM Coding Plan", merged.getExtra().get("plan"));
        assertEquals(0, merged.getAmount().compareTo(new BigDecimal("880")));
    }

    @Test
    void mergeFailsWhenBothSourcesFail() {
        CreditResult quota = ZaiCreditQueryProvider.parseQuota("{\"success\": false, \"code\": 401, \"msg\": \"bad key\"}");
        CreditResult merged = ZaiCreditQueryProvider.merge(quota, null, false);
        assertTrue(!merged.isOk());
        assertTrue(merged.getError().contains("配额"));
        assertTrue(merged.getError().contains("余额"));
    }
}

package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SenseNova 余额解析测试：直接喂平台真实响应结构样例，不发任何网络请求。
 *
 * <p>覆盖：plan + 双资源池（数值为字符串型 token 数）、纯数字窗口、
 * 空 pools 失败路径、非法 JSON 失败路径、无可读数值失败路径，以及
 * extra 结构化数据（plan / pools / unit）的透出。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class SenseNovaCreditQueryProviderTest {

    /** 2026-11 实测 pool-usage 响应样例：两个资源池，数值都是字符串型 token 数 */
    private static final String REAL_RESPONSE = """
            {
              "plan": {"id": "plan-001", "name": "Token Plan", "type": "tokenplan"},
              "pools": [
                {
                  "id": "pool-1",
                  "name": "通用模型池",
                  "model_ids": ["nova-2.0", "nova-2.1"],
                  "window_5h": {"limit": "100000", "used": "20000", "remaining": "80000", "reset_at": "2026-11-18T17:00:00Z"},
                  "window_7d": {"limit": "2000000", "used": "500000", "remaining": "1500000", "reset_at": "2026-11-20T00:00:00Z"},
                  "grant_balance": "3000"
                },
                {
                  "id": "pool-2",
                  "name": "推理模型池",
                  "model_ids": ["nova-thinker"],
                  "window_5h": {"limit": "50000", "used": "10000", "remaining": "40000", "reset_at": "2026-11-18T17:00:00Z"},
                  "window_7d": {"limit": "800000", "used": "200000", "remaining": "600000", "reset_at": "2026-11-20T00:00:00Z"},
                  "grant_balance": "0"
                }
              ]
            }
            """;

    /**
     * 真实样例：每池依次 7天剩余 / 7天已用 / 5小时剩余 / 5小时已用 / 赠送余额，
     * parts[0] = 第一个池的 7 天剩余（主读数），币种口径 tokens。
     */
    @Test
    void parsesRealPoolUsageResponse() {
        CreditResult result = SenseNovaCreditQueryProvider.parsePools(REAL_RESPONSE);
        assertTrue(result.isOk(), "真实样例应解析成功: " + result.getError());
        assertEquals("sensenova", result.getSource());
        List<BalancePart> parts = result.getParts();
        assertEquals(10, parts.size(), "两池 ×（2 窗口 × 剩余/已用）+ 2 赠送余额 = 10 段");

        BalancePart first = parts.get(0);
        assertEquals("通用模型池 7天剩余", first.getLabel());
        assertEquals(0, first.getAmount().compareTo(new java.math.BigDecimal("1500000")));
        assertEquals("tokens", first.getCurrency());
        assertEquals(first.getAmount(), result.getAmount(), "主读数取 parts[0]");

        assertEquals("通用模型池 7天已用", parts.get(1).getLabel());
        assertEquals(0, parts.get(1).getAmount().compareTo(new java.math.BigDecimal("500000")));
        assertEquals("通用模型池 5小时剩余", parts.get(2).getLabel());
        assertEquals(0, parts.get(2).getAmount().compareTo(new java.math.BigDecimal("80000")));
        assertEquals("通用模型池 5小时已用", parts.get(3).getLabel());
        assertEquals(0, parts.get(3).getAmount().compareTo(new java.math.BigDecimal("20000")));
        assertEquals("通用模型池 赠送余额", parts.get(4).getLabel());
        assertEquals(0, parts.get(4).getAmount().compareTo(new java.math.BigDecimal("3000")));
        assertEquals("推理模型池 7天剩余", parts.get(5).getLabel());
        assertEquals(0, parts.get(5).getAmount().compareTo(new java.math.BigDecimal("600000")));
        // grant_balance 为 "0" 也是合法读数，不能被当成取不到而丢掉
        assertEquals("推理模型池 赠送余额", parts.get(9).getLabel());
        assertEquals(0, parts.get(9).getAmount().compareTo(java.math.BigDecimal.ZERO));
    }

    /**
     * extra 透出套餐与资源池结构，供前端按 SenseNova 口径渲染专属视图。
     */
    @Test
    void exposesStructuredExtraForFrontend() {
        CreditResult result = SenseNovaCreditQueryProvider.parsePools(REAL_RESPONSE);
        assertTrue(result.isOk());
        Map<String, Object> extra = result.getExtra();
        assertNotNull(extra, "成功结果必须带 extra 结构化数据");
        assertEquals("tokens", extra.get("unit"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) extra.get("plan");
        assertNotNull(plan, "plan 应透出");
        assertEquals("Token Plan", plan.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) extra.get("pools");
        assertNotNull(pools);
        assertEquals(2, pools.size());
        assertEquals("通用模型池", pools.get(0).get("name"));
        assertEquals("pool-2", pools.get(1).get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> window7d = (Map<String, Object>) pools.get(0).get("window_7d");
        assertEquals("1500000", window7d.get("remaining"), "窗口原始字段按字符串原样保留");
    }

    /**
     * 窗口数值为纯数字（非字符串）时同样可读。
     */
    @Test
    void parsesNumericWindowValues() {
        String raw = """
                {"plan": {"id": "p", "name": "N", "type": "tokenplan"},
                 "pools": [{"id": "pool-1", "name": "池A",
                   "window_7d": {"limit": 1000, "used": 250, "remaining": 750},
                   "window_5h": {"limit": 100, "used": 25, "remaining": 75},
                   "grant_balance": 5}]}
                """;
        CreditResult result = SenseNovaCreditQueryProvider.parsePools(raw);
        assertTrue(result.isOk());
        assertEquals(5, result.getParts().size());
        assertEquals(0, result.getParts().get(0).getAmount().compareTo(new java.math.BigDecimal("750")));
    }

    /**
     * 空 pools：不是余额结构，明确失败并提示改用自定义映射。
     */
    @Test
    void failsOnEmptyPools() {
        CreditResult result = SenseNovaCreditQueryProvider.parsePools(
                "{\"plan\": {\"id\": \"p\", \"name\": \"N\", \"type\": \"tokenplan\"}, \"pools\": []}");
        assertTrue(!result.isOk());
        assertNotNull(result.getError(), "失败结果 error 必有值");
        assertTrue(result.getError().contains("未识别的余额响应结构"));
    }

    /**
     * 非法 JSON：失败并带原因，不抛异常。
     */
    @Test
    void failsOnInvalidJson() {
        CreditResult result = SenseNovaCreditQueryProvider.parsePools("<html>gateway</html>");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("响应不是合法 JSON"));
        assertEquals("sensenova", result.getSource());
    }

    /**
     * 池里一个可读数值都没有：失败并说明原因。
     */
    @Test
    void failsWhenNoReadableValue() {
        String raw = "{\"pools\": [{\"id\": \"pool-1\", \"name\": \"池A\"}]}";
        CreditResult result = SenseNovaCreditQueryProvider.parsePools(raw);
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("没有可读的用量数值"));
    }
}

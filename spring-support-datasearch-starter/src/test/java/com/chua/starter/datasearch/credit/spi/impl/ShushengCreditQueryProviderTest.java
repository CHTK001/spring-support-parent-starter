package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
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
 * 书生（InternAI）余额解析测试：直接喂平台真实响应结构样例，不发任何网络请求。
 *
 * <p>覆盖：信封 + 墨点余额 + 5 小时 / 7 天双窗口（数值与字符串型数字）、
 * 业务错误信封透传、非法 JSON 失败路径、缺 data / 无数值失败路径，以及
 * extra 结构化数据（available / pools / unit）的透出与 SPI 登录约定。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class ShushengCreditQueryProviderTest {

    /** 2026-11 前端 bundle 逆向确认的余额响应结构：信封 + data + 双用量窗口 */
    private static final String REAL_RESPONSE = """
            {
              "code": 0,
              "msg": "success",
              "data": {
                "available_credits": 9,
                "total_credits": 10,
                "limit_credits": 10,
                "used_credits": 1,
                "remaining_ratio": 0.9,
                "status": "active",
                "usage_windows": {
                  "5h": {"enabled": true, "limit_credits": 4, "used_credits": 1,
                         "remaining_credits": 3, "next_recover_at": "2026-11-18T18:00:00Z"},
                  "7d": {"enabled": true, "limit_credits": 10, "used_credits": 1,
                         "remaining_credits": 9, "next_recover_at": "2026-11-20T00:00:00Z"}
                }
              }
            }
            """;

    private final ShushengCreditQueryProvider provider = new ShushengCreditQueryProvider();

    @Test
    void parsesBalanceAndUsageWindows() {
        CreditResult result = ShushengCreditQueryProvider.parseBalance(REAL_RESPONSE);
        assertTrue(result.isOk(), result.getError());
        assertNotNull(result.getParts());
        // 主读数：可用墨点
        BalancePart head = result.getParts().get(0);
        assertEquals("墨点余额", head.getLabel());
        assertEquals(0, head.getAmount().compareTo(new BigDecimal("9")));
        assertEquals("墨点", head.getCurrency());
        // 双窗口读数：7 天 / 5 小时的剩余与已用
        assertEquals(new BigDecimal("9"), part(result, "7天窗口剩余").getAmount());
        assertEquals(new BigDecimal("1"), part(result, "7天窗口已用").getAmount());
        assertEquals(new BigDecimal("3"), part(result, "5小时窗口剩余").getAmount());
        assertEquals(new BigDecimal("1"), part(result, "5小时窗口已用").getAmount());
        // extra 结构化数据：余额 + 用量窗口池 + 单位
        Map<String, Object> extra = result.getExtra();
        assertNotNull(extra);
        assertEquals(new BigDecimal("9"), extra.get("available_credits"));
        assertEquals("墨点", extra.get("unit"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) extra.get("pools");
        assertNotNull(pools);
        assertEquals(1, pools.size());
        Map<String, Object> pool = pools.get(0);
        assertEquals("墨点用量", pool.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> w7d = (Map<String, Object>) pool.get("window_7d");
        assertEquals(new BigDecimal("10"), w7d.get("limit"));
        assertEquals(new BigDecimal("9"), w7d.get("remaining"));
        @SuppressWarnings("unchecked")
        Map<String, Object> w5h = (Map<String, Object>) pool.get("window_5h");
        assertEquals(new BigDecimal("4"), w5h.get("limit"));
        assertEquals(new BigDecimal("3"), w5h.get("remaining"));
    }

    @Test
    void parsesStringNumbers() {
        String raw = """
                {"code":0,"msg":"success","data":{
                  "available_credits":"12.5","total_credits":"100","used_credits":"87.5",
                  "usage_windows":{"7d":{"limit_credits":"100","used_credits":"87.5","remaining_credits":"12.5"}}
                }}
                """;
        CreditResult result = ShushengCreditQueryProvider.parseBalance(raw);
        assertTrue(result.isOk(), result.getError());
        assertEquals(0, result.getParts().get(0).getAmount().compareTo(new BigDecimal("12.5")));
        Map<String, Object> extra = result.getExtra();
        assertEquals(new BigDecimal("12.5"), extra.get("available_credits"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) extra.get("pools");
        @SuppressWarnings("unchecked")
        Map<String, Object> w7d = (Map<String, Object>) pools.get(0).get("window_7d");
        assertEquals(new BigDecimal("12.5"), w7d.get("remaining"));
    }

    @Test
    void envelopeErrorSurfacesUpstreamCode() {
        String error = ShushengCreditQueryProvider.envelopeError(
                "{\"code\":401,\"msg\":\"unauthorized\",\"data\":null}");
        assertNotNull(error);
        assertTrue(error.contains("code=401"), error);
        assertTrue(error.contains("unauthorized"), error);
        assertNull(ShushengCreditQueryProvider.envelopeError(REAL_RESPONSE));
        assertNull(ShushengCreditQueryProvider.envelopeError("not-json"));
        assertNull(ShushengCreditQueryProvider.envelopeError(null));
    }

    @Test
    void invalidJsonFails() {
        CreditResult result = ShushengCreditQueryProvider.parseBalance("not-json");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("不是合法 JSON"), result.getError());
    }

    @Test
    void missingDataFails() {
        CreditResult result = ShushengCreditQueryProvider.parseBalance("{\"code\":0,\"msg\":\"success\"}");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("未识别的余额响应结构"), result.getError());
    }

    @Test
    void noReadableNumbersFail() {
        CreditResult result = ShushengCreditQueryProvider.parseBalance(
                "{\"code\":0,\"msg\":\"success\",\"data\":{\"status\":\"active\"}}");
        assertTrue(!result.isOk());
        assertTrue(result.getError().contains("没有可读的余额数值"), result.getError());
    }

    @Test
    void spiContract() {
        assertEquals("shusheng", provider.name());
        assertEquals("uaa-token", provider.loginCookieName());
        assertTrue(provider.loginPageUrl().startsWith("https://discovery.intern-ai.org.cn/"));
        assertNull(provider.loginStorageKey());
        assertNull(provider.loginCaptureUrlPattern());
        assertNull(provider.loginTokenJsonPath());
        CreditQuery query = new CreditQuery();
        query.setKind("shusheng");
        assertTrue(provider.supports(query));
    }

    /**
     * 按标签找一段读数。
     *
     * @param result 查询结果
     * @param label  段名
     * @return 读数
     */
    private static BalancePart part(CreditResult result, String label) {
        return result.getParts().stream()
                .filter(p -> label.equals(p.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少读数: " + label));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OpenCode 余额查询验证，全程不 mock：
 *
 * <p>解析逻辑按项目惯例（同 {@code ZaiCreditQueryProviderTest}）直接喂官方真实响应
 * 样例，不发网络请求；链路用例持真实 API Key 直接请求 opencode.ai 官方端点
 * （{@code /zen/go/v1/usage} 与 {@code /console/api/policies/current-budgets}）。
 * Key 通过系统属性 {@code opencode.test.key} 或环境变量 {@code OPENCODE_TEST_KEY}
 * 提供，缺失时按 Assumption 跳过——不造假服务器、不编造读数。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class OpenCodeCreditQueryProviderTest {

    /** 官方用量端点真实响应样例（Go 账号实测，anomalyco/opencode#43983） */
    private static final String USAGE_OK = """
            {"usage":{"rolling":{"status":"ok","percent":1,"resetsAt":"2026-09-30T17:15:45.084Z"},
            "weekly":{"status":"ok","percent":25,"resetsAt":"2026-10-05T00:00:00.000Z"},
            "monthly":{"status":"ok","percent":29,"resetsAt":"2026-10-21T07:45:46.000Z"}}}
            """;

    /** 预算端点真实响应样例（官方 console-budgets 类型定义口径） */
    private static final String BUDGETS_OK = """
            [{"ruleID":"team-monthly","limitDollars":100,"spentDollars":37.5,
            "scope":"workspace","per":"monthly","shared":false,
            "resetsAt":"2026-12-01T00:00:00.000Z"}]
            """;

    /** 无 Go 订阅工作区的真实 403 响应体（EntitlementError） */
    private static final String ENTITLEMENT_403 =
            "{\"type\":\"error\",\"error\":{\"type\":\"EntitlementError\"}}";

    /** 预算端点 403 的真实响应体（服务账号 Key 或无 Budgets:Read 权限） */
    private static final String FORBIDDEN_403 = "{\"_tag\":\"Forbidden\"}";

    private final OpenCodeCreditQueryProvider provider = new OpenCodeCreditQueryProvider();

    /**
     * 真实测试 Key：系统属性优先，其次环境变量；都没有返回 null（链路用例跳过）。
     */
    private static String testKey() {
        String key = System.getProperty("opencode.test.key");
        if (key == null || key.isBlank()) {
            key = System.getenv("OPENCODE_TEST_KEY");
        }
        return key == null || key.isBlank() ? null : key.trim();
    }

    /**
     * 组装真实链路查询：官方用量端点 + 官方预算端点（同源派生），真实 Key Bearer。
     */
    private static CreditQuery realQuery(String key) {
        CreditQuery query = new CreditQuery();
        query.setKind("opencode");
        query.setProviderId("opencode");
        query.setOriginUrl("https://opencode.ai/zen/v1");
        query.setUrl("https://opencode.ai/zen/go/v1/usage");
        query.setToken(key);
        return query;
    }

    /**
     * 真实链路：官方端点 + 真实 Key，结果必须如实。
     *
     * <p>查到数就给结构正确的读数；查不到（如纯 Zen PAYG Key 两端点 403）就把
     * 两种 403 语义（无 Go 订阅 / 无 Budgets:Read 权限）讲清楚，绝不编造余额。</p>
     */
    @Test
    void realEndpointsReturnHonestResults() {
        String key = testKey();
        assumeTrue(key != null, "未提供 opencode.test.key / OPENCODE_TEST_KEY，跳过真实链路用例");
        CreditResult result = provider.query(realQuery(key));
        if (result.isOk()) {
            assertEquals("opencode", result.getSource());
            assertFalse(result.getParts().isEmpty());
            assertTrue(result.getExtra().get("notes").toString().contains("upstream #10448"),
                    () -> "Zen 余额不可查说明必须如实给出: " + result.getExtra());
        } else {
            assertTrue(result.getParts().isEmpty(), "失败时不得返回编造读数");
            assertTrue(result.getError().contains("无 Go 订阅"), () -> result.getError());
            assertTrue(result.getError().contains("Budgets:Read"), () -> result.getError());
            assertTrue(result.getError().contains("仅控制台网页可见"), () -> result.getError());
        }
    }

    /**
     * 缺 API Key：快速失败，一个请求都不发（纯逻辑，不依赖网络）。
     */
    @Test
    void missingKeyFailsFast() {
        CreditQuery noKey = realQuery(" ");
        CreditResult result = provider.query(noKey);
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("缺少 API Key"), () -> result.getError());
    }

    /**
     * 解析官方用量样例：三窗口「已用百分比」翻成「剩余百分比」，明细进 extra.go_usage。
     */
    @Test
    void parseUsageTurnsUsedPercentIntoRemaining() {
        List<BalancePart> parts = new ArrayList<>();
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        provider.parseUsage(USAGE_OK, parts, extra, errors);
        assertTrue(errors.isEmpty(), () -> "样例应可解析: " + errors);
        assertEquals(3, parts.size());
        assertEquals("Go 剩余（滚动）", parts.get(0).getLabel());
        assertEquals(0, parts.get(0).getAmount().compareTo(new BigDecimal("99")));
        assertEquals("Go 剩余（本周）", parts.get(1).getLabel());
        assertEquals(0, parts.get(1).getAmount().compareTo(new BigDecimal("75")));
        assertEquals("Go 剩余（本月）", parts.get(2).getLabel());
        assertEquals(0, parts.get(2).getAmount().compareTo(new BigDecimal("71")));
        assertEquals("%", parts.get(0).getCurrency());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) extra.get("go_usage");
        assertEquals(3, windows.size());
        assertEquals("rolling", windows.get(0).get("window"));
        assertEquals(0, ((BigDecimal) windows.get(0).get("used_percent")).compareTo(BigDecimal.ONE));
        assertEquals("2026-09-30T17:15:45.084Z", windows.get(0).get("resets_at"));
    }

    /**
     * 解析 403 错误体：不是用量结构，如实报错，不编造窗口。
     */
    @Test
    void parseUsageRejectsErrorBody() {
        List<BalancePart> parts = new ArrayList<>();
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        provider.parseUsage(ENTITLEMENT_403, parts, extra, errors);
        assertTrue(parts.isEmpty(), "错误体不得解析出读数");
        assertTrue(extra.isEmpty());
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("usage"), () -> errors.get(0));
    }

    /**
     * 解析预算样例：上限 − 已花 = 剩余，美元口径，明细进 extra.budgets。
     */
    @Test
    void parseBudgetsComputesRemaining() {
        List<BalancePart> parts = new ArrayList<>();
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        provider.parseBudgets(BUDGETS_OK, parts, extra, errors);
        assertTrue(errors.isEmpty(), () -> "样例应可解析: " + errors);
        assertEquals(1, parts.size());
        assertEquals("预算剩余（team-monthly）", parts.get(0).getLabel());
        assertEquals(0, parts.get(0).getAmount().compareTo(new BigDecimal("62.5")));
        assertEquals("USD", parts.get(0).getCurrency());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> budgets = (List<Map<String, Object>>) extra.get("budgets");
        assertEquals(1, budgets.size());
        assertEquals("team-monthly", budgets.get(0).get("rule_id"));
        assertEquals(0, ((BigDecimal) budgets.get(0).get("remaining")).compareTo(new BigDecimal("62.5")));
        assertEquals(Boolean.FALSE, budgets.get(0).get("shared"));
    }

    /**
     * 解析 403 错误体：不是预算数组，如实报错，不编造预算。
     */
    @Test
    void parseBudgetsRejectsErrorBody() {
        List<BalancePart> parts = new ArrayList<>();
        Map<String, Object> extra = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        provider.parseBudgets(FORBIDDEN_403, parts, extra, errors);
        assertTrue(parts.isEmpty(), "错误体不得解析出读数");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("数组"), () -> errors.get(0));
    }

    /**
     * 预算端点同源派生：跟用量端点同协议 + 域名 + 端口，解析失败回落官方默认。
     */
    @Test
    void budgetsUrlFollowsUsageOrigin() {
        assertEquals("https://opencode.ai/console/api/policies/current-budgets",
                OpenCodeCreditQueryProvider.budgetsUrlOf("https://opencode.ai/zen/go/v1/usage"));
        assertEquals("http://127.0.0.1:8787/console/api/policies/current-budgets",
                OpenCodeCreditQueryProvider.budgetsUrlOf("http://127.0.0.1:8787/zen/go/v1/usage"));
        assertEquals("https://opencode.ai/console/api/policies/current-budgets",
                OpenCodeCreditQueryProvider.budgetsUrlOf("not-a-url"));
    }
}

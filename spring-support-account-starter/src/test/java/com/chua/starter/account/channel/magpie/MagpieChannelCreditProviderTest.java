// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MagpieChannelCreditProvider#snapshot(List)} 的聚合测试。
 *
 * <p>重点守住两条口径：</p>
 * <ol>
 *   <li>不同上游的额度单位（requests / token / currency）不能相加，否则会报出错误的余额；</li>
 *   <li>读不到的字段保持「缺失」，不当成 0（magpie 的 display 常是 {@code 1.2k / 3k} 这类文案）。</li>
 * </ol>
 *
 * @since 4.0.0.53
 * @author CH
 */
class MagpieChannelCreditProviderTest {

    @Test
    @DisplayName("网关没有配额卡时给 error，而不是 0")
    void emptyQuotasReportError() {
        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(List.of());
        assertNotNull(result.get("error"));
        assertNull(result.get("remain"));
        assertNull(result.get("packages"));
    }

    @Test
    @DisplayName("不同单位分别累加：顶部只报主单位，明细保留各自 display")
    void differentUnitsAreNotSummedTogether() {
        List<Map<String, Object>> quotas = new ArrayList<>();
        quotas.add(quota("codex", "codex", "subscription", null, List.of(
                window("5h", null, 12, 20, "requests"))));
        quotas.add(quota("claude", "claude", "subscription", null, List.of(
                window("weekly", "1.2k / 3k", null, null, "token"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(12L, result.get("remain"));
        assertEquals(20L, result.get("total"));
        assertEquals("requests", result.get("unit"));
        assertEquals(2, packages(result).size());
        // 1.2k / 3k 不能解析成数值，原样保留在 display 里
        assertEquals("1.2k / 3k", packages(result).get(1).get("display"));
        assertNull(packages(result).get(1).get("remain"));
        String warning = String.valueOf(result.get("warning"));
        assertTrue(warning.contains("单位不一致"), warning);
    }

    @Test
    @DisplayName("同一单位的多个窗口可以相加")
    void sameUnitWindowsAreSummed() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                window("5h", null, 5, 20, "requests"),
                window("weekly", null, 5, 20, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(10L, result.get("remain"));
        assertEquals(40L, result.get("total"));
        assertEquals("requests", result.get("unit"));
        assertNull(result.get("warning"));
    }

    @Test
    @DisplayName("余额窗口用 limit - amount 反推剩余，并标记超额")
    void exhaustedWindowIsFlagged() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                windowWithAmount("5h", 20, 20, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(0L, result.get("remain"));
        assertEquals(Boolean.TRUE, result.get("exceeded"));
    }

    @Test
    @DisplayName("余额卡按货币口径，金额字符串去掉符号与千分位")
    void balanceCardUsesCurrencyUnit() {
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("provider", "openrouter");
        quota.put("name", "openrouter");
        quota.put("kind", "balance");
        quota.put("balance", "$1,234.6");
        quota.put("until", 1_700_000_000L);

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(List.of(quota));

        assertEquals(1235L, result.get("remain"));
        assertEquals("currency", result.get("unit"));
        assertEquals(1_700_000_000_000L, result.get("expiresAt"));
        assertEquals("openrouter", packages(result).get(0).get("name"));
    }

    @Test
    @DisplayName("上游卡片自带 error 时降级为 warning，不影响其它卡")
    void quotaErrorBecomesWarning() {
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("provider", "gemini");
        broken.put("kind", "subscription");
        broken.put("error", "token expired");

        List<Map<String, Object>> quotas = new ArrayList<>();
        quotas.add(quota("codex", "codex", "subscription", null, List.of(
                window("5h", null, 7, 10, "requests"))));
        quotas.add(broken);

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(7L, result.get("remain"));
        String warning = String.valueOf(result.get("warning"));
        assertTrue(warning.contains("gemini: token expired"), warning);
        assertFalse(warning.isBlank());
    }

    // ── 夹具 ──

    private static Map<String, Object> quota(String provider, String name, String kind, String balance,
                                             List<Map<String, Object>> windows) {
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("provider", provider);
        quota.put("name", name);
        quota.put("kind", kind);
        if (balance != null) {
            quota.put("balance", balance);
        }
        quota.put("windows", windows);
        return quota;
    }

    private static Map<String, Object> window(String name, String display, Integer remaining, Integer limit,
                                              String unit) {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", name);
        if (display != null) {
            window.put("display", display);
        }
        if (remaining != null) {
            window.put("remaining", remaining);
        }
        if (limit != null) {
            window.put("limit", limit);
        }
        window.put("unit", unit);
        return window;
    }

    private static Map<String, Object> windowWithAmount(String name, int amount, int limit, String unit) {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", name);
        window.put("amount", amount);
        window.put("limit", limit);
        window.put("unit", unit);
        return window;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> packages(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("packages");
    }
}

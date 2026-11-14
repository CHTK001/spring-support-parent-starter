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
    @DisplayName("不同单位分别累加：顶部按 units 分条，明细保留各自 display")
    void differentUnitsAreNotSummedTogether() {
        List<Map<String, Object>> quotas = new ArrayList<>();
        quotas.add(quota("codex", "codex", "subscription", null, List.of(
                window("5h", null, 12, 20, "requests"))));
        quotas.add(quota("claude", "claude", "subscription", null, List.of(
                window("weekly", "1.2k / 3k", null, 3000, "token"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(12L, result.get("remain"));
        assertEquals(20L, result.get("total"));
        assertEquals("requests", result.get("unit"));
        assertEquals(2, packages(result).size());
        // 1.2k / 3k 不能解析成数值，原样保留在 display 里
        assertEquals("1.2k / 3k", packages(result).get(1).get("display"));
        assertNull(packages(result).get(1).get("remain"));
        // 两种单位各一条，前端按单位分条展示，不再报「单位不一致」
        assertEquals(2, units(result).size());
        assertEquals("requests", units(result).get(0).get("unit"));
        assertEquals("token", units(result).get(1).get("unit"));
        assertNull(result.get("warning"));
    }

    @Test
    @DisplayName("没有单位的窗口只进明细，不进合计")
    void windowWithoutUnitStaysOutOfTotals() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                window("5h", null, 7, 10, "requests"),
                window("estimate", null, 200_000_000, 200_000_000, null))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(7L, result.get("remain"));
        assertEquals(10L, result.get("total"));
        assertEquals(2, packages(result).size());
        assertEquals(1, units(result).size());
        assertNull(packages(result).get(1).get("unit"));
    }

    @Test
    @DisplayName("currency 与 credit 归一到同一单位，货币按币种分桶")
    void currencyAndCurrencyCodeAreBucketed() {
        List<Map<String, Object>> quotas = new ArrayList<>();
        quotas.add(quota("openrouter", "openrouter", "balance", "10", List.of()));
        quotas.add(quota("deepseek", "deepseek", "subscription", null, List.of(
                window("wallet", null, 5, 20, "usd"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        // 余额卡没有币种（bucket 键只有 currency），美元窗口带 USD，两者不能相加
        assertEquals(2, units(result).size());
        assertEquals("currency", units(result).get(0).get("unit"));
        assertNull(units(result).get(0).get("currency"));
        assertEquals(10L, units(result).get(0).get("remain"));
        assertEquals("USD", units(result).get(1).get("currency"));
        assertEquals(5L, units(result).get(1).get("remain"));
        // 卡片口径取第一条
        assertEquals(10L, result.get("remain"));
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
    @DisplayName("used 是剩余百分比，为 0 时判超额")
    void exhaustedWindowIsFlagged() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                windowWithUsed("5h", 0, 20, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(0L, result.get("remain"));
        assertEquals(Boolean.TRUE, result.get("exceeded"));
    }

    @Test
    @DisplayName("magpie 的 used 是剩余百分比（线上实测：Qoder 48.3333 → 145/300）")
    void usedFieldIsRemainingPercent() {
        List<Map<String, Object>> quotas = List.of(quota("qoder", "Qoder", "subscription", null,
                List.of(windowWithUsed("Credits", 48, 300, "credits"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(144L, result.get("remain"));
        assertEquals(300L, result.get("total"));
        // credits 归一到 credit
        assertEquals("credit", result.get("unit"));
    }

    @Test
    @DisplayName("amount 是剩余量，优先于 used 百分比（Qoder 实测 145/300）")
    void amountWinsOverUsedPercent() {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", "Credits");
        window.put("used", 48.333333);
        window.put("amount", 145);
        window.put("limit", 300);
        window.put("unit", "credits");

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(
                List.of(quota("qoder", "Qoder", "subscription", null, List.of(window))));

        assertEquals(145L, result.get("remain"));
        assertEquals(300L, result.get("total"));
    }

    @Test
    @DisplayName("剩余量优先取 balance / remaining，再退回 amount")
    void remainFieldPrecedence() {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", "Credits");
        window.put("balance", 9);
        window.put("remaining", 8);
        window.put("amount", 7);
        window.put("limit", 10);
        window.put("unit", "credits");

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(
                List.of(quota("codex", "codex", "subscription", null, List.of(window))));

        assertEquals(9L, result.get("remain"));
    }

    @Test
    @DisplayName("早期版本的 amount 字段按剩余量理解")
    void legacyAmountFieldStillWorks() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                windowWithAmount("5h", 4, 10, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(4L, result.get("remain"));
        assertEquals(10L, result.get("total"));
    }

    @Test
    @DisplayName("「Total」汇总行不计入合计，避免余额翻倍（500 + 3936 + 4436 → 4436）")
    void totalWindowIsNotSummed() {
        List<Map<String, Object>> quotas = List.of(quota("workbuddy-ai", "WorkBuddy AI", "subscription", null,
                List.of(
                        windowWithUsed("Credits", 100, 500, "credits"),
                        windowWithUsed("Extra credits", 100, 3936, "credits"),
                        windowWithUsed("Total credits", 100, 4436, "credits"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        assertEquals(4436L, result.get("remain"));
        assertEquals(4436L, result.get("total"));
        // 三行都保留在明细里，只是汇总行不参与求和
        assertEquals(3, packages(result).size());
    }

    @Test
    @DisplayName("名字不是合计、只是数值碰巧等于其余之和时，按原样相加")
    void ambiguousWindowsAreSummedAsIs() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                window("a", null, 10, 10, "requests"),
                window("b", null, 10, 10, "requests"),
                window("c", null, 20, 20, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        // 「10 + 10 + 20」是合法的三个分桶；没有合计语义的行名就不归并
        assertEquals(40L, result.get("total"));
        assertEquals(40L, result.get("remain"));
    }

    @Test
    @DisplayName("名字像合计但数值对不上时，不当作汇总行")
    void totalLabelWithMismatchedNumbersIsKept() {
        List<Map<String, Object>> quotas = List.of(quota("codex", "codex", "subscription", null, List.of(
                windowWithUsed("Credits", 0, 100, "requests"),
                windowWithUsed("Extra credits", 0, 90, "requests"),
                windowWithUsed("Total credits", 0, 250, "requests"))));

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(quotas);

        // 250 ≠ 100 + 90，说明它不是这两行的合计（可能是另一个真正的桶）
        assertEquals(440L, result.get("total"));
    }

    @Test
    @DisplayName("余额卡只给带符号的原文时保留原文，不折算成整数")
    void balanceCardKeepsRawText() {
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("provider", "openrouter");
        quota.put("name", "openrouter");
        quota.put("kind", "balance");
        quota.put("balance", "$1,234.6");
        quota.put("until", 1_700_000_000L);

        Map<String, Object> result = MagpieChannelCreditProvider.snapshot(List.of(quota));

        // 余额是厂商文案（带币种符号与小数）：早先按整数解析会把 $0.0064 抹成 0，
        // 余额直接显示成「用光」，所以现在保留原文、由前端照抄展示。
        assertNull(result.get("remain"));
        assertEquals("$1,234.6", result.get("text"));
        assertEquals(1_700_000_000_000L, result.get("expiresAt"));
        Map<String, Object> row = packages(result).get(0);
        assertEquals("openrouter", row.get("name"));
        assertEquals("$1,234.6", row.get("display"));
        assertEquals("currency", row.get("unit"));
        assertEquals("USD", row.get("currency"));
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
        // 上游原文（401 / token expired / HTML 片段）不往界面透，只说「这一家读不到」，
        // 细节交给上游日志；否则卡片上会出现一坨看起来像故障的诊断文本。
        assertEquals("「gemini」余额读取失败", String.valueOf(result.get("warning")));
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

    private static Map<String, Object> windowWithUsed(String name, int used, int limit, String unit) {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", name);
        window.put("used", used);
        window.put("limit", limit);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> units(Map<String, Object> result) {
        List<Map<String, Object>> units = (List<Map<String, Object>>) result.get("units");
        return units == null ? List.of() : units;
    }
}

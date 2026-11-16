package com.chua.starter.ai.support.manage.limit;

import com.chua.starter.datasearch.provider.model.PricingSourceConfig;
import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.support.PricingPageSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用价格页读取器验收：同一套代码解析表格、role-grid、卡片和正文命名正则。
 *
 * <p>纯 JUnit 5，不联网、不启动 Spring；样本与四家官方页面结构一致。</p>
 *
 * @author CH
 */
class PricingPageSourceTest {

    @Test
    void tableReadsInputOutputAndCacheColumns() {
        String html = "<table><thead><tr><th>Model</th><th>Input</th><th>Output</th>"
                + "<th>Cached Read</th><th>Cached Write</th></tr></thead><tbody>"
                + "<tr><td>MiniMax M3</td><td>$0.30</td><td>$1.20</td><td>$0.06</td><td>-</td></tr>"
                + "<tr><td>Free Model</td><td>Free</td><td>Free</td><td>Free</td><td>-</td></tr>"
                + "</tbody></table>";
        PricingSourceConfig config = config("html-table");
        config.setColumns(Map.of(
                "Input", "input_cache_miss",
                "Output", "output",
                "Cached Read", "input_cache_hit",
                "Cached Write", "input_cache_write"));
        ProviderPricingRefresh refresh = PricingPageSource.parse(html, config,
                "https://opencode.ai/docs/zen", "2026-11-16T00:00:00Z");
        assertNull(refresh.getError());
        assertTrue(refresh.isLive());
        List<ProviderPricingRule> rules = refresh.getRules();
        assertEquals(6, rules.size(), "两行各 3 条有效价；Cached Write 的横杠跳过");
        ProviderPricingRule input = rule(rules, "MiniMax M3", "input_cache_miss");
        assertEquals(0, input.getStandardPrice().compareTo(new BigDecimal("0.30")));
        assertEquals("USD", input.getCurrency());
        assertEquals(0, input.getQuantity().compareTo(new BigDecimal("1000000")));
        assertEquals(0, rule(rules, "MiniMax M3", "input_cache_hit")
                .getStandardPrice().compareTo(new BigDecimal("0.06")));
        assertEquals(0, rule(rules, "Free Model", "output").getStandardPrice().compareTo(BigDecimal.ZERO));
        assertTrue(input.getVerified());
        assertEquals("2026-11-16T00:00:00Z", input.getFetchedAt());
    }

    @Test
    void gridReadsRoleRowsWithNestedCells() {
        String html = "<div role=\"row\"><div>Model</div><div>Context</div><div>Input/M</div>"
                + "<div>Output/M</div><div>Cache Read</div><div>Cache Write</div><div>Caps</div></div>"
                + "<div role=\"row\"><div><span>GPT-6 Astra</span></div><div>1.1M</div>"
                + "<div>$10.00</div><div>$50.00</div><div>$1.00</div><div>$12.50</div>"
                + "<div><button>+1</button></div></div>"
                + "<div role=\"row\"><div><span>Jev</span></div><div>32K</div>"
                + "<div>$0.042</div><div>$0.00</div><div>-</div><div>-</div>"
                + "<div><button>+1</button></div></div>";
        PricingSourceConfig config = config("html-grid");
        config.setColumns(Map.of(
                "Input", "input_cache_miss",
                "Output", "output",
                "Cache Read", "input_cache_hit",
                "Cache Write", "input_cache_write"));
        ProviderPricingRefresh refresh = PricingPageSource.parse(html, config,
                "https://commandcode.ai/docs/resources/pricing-limits", "2026-11-16T00:00:00Z");
        assertNull(refresh.getError());
        List<ProviderPricingRule> rules = refresh.getRules();
        assertEquals(6, rules.size());
        assertEquals(0, rule(rules, "GPT-6 Astra", "output").getStandardPrice()
                .compareTo(new BigDecimal("50.00")));
        assertEquals(0, rule(rules, "GPT-6 Astra", "input_cache_write").getStandardPrice()
                .compareTo(new BigDecimal("12.50")));
        assertNotNull(rule(rules, "Jev", "input_cache_miss"));
    }

    @Test
    void cardsReadModelTitleAndPriceLabels() {
        String html = "<div class=\"x modelCard\"><h3 class=\"modelName\">Fable 5.2</h3>"
                + "<div class=\"priceRow\"><p class=\"priceLabel\">Prompt caching</p></div>"
                + "<div class=\"priceRow\"><p class=\"priceLabel\">Read</p><p class=\"priceValue\">$0.20 / MTok</p></div>"
                + "<div class=\"priceRow\"><p class=\"priceLabel\">Write</p><p class=\"priceValue\">$10 / MTok</p></div>"
                + "<div class=\"priceRow\"><p class=\"priceLabel\">Input</p><p class=\"priceValue\">$8 / MTok</p></div>"
                + "<div class=\"priceRow\"><p class=\"priceLabel\">Output</p><p class=\"priceValue\">$40 / MTok</p></div>"
                + "</div>";
        PricingSourceConfig config = config("html-cards");
        config.setCardSelector("[class*=modelCard]");
        config.setTitleSelector("h3");
        config.setPriceRowSelector("[class*=priceRow]");
        config.setLabelSelector("[class*=priceLabel]");
        config.setValueSelector("[class*=priceValue]");
        config.setModelPrefix("Claude ");
        ProviderPricingRefresh refresh = PricingPageSource.parse(html, config,
                "https://www.anthropic.com/pricing", "2026-11-16T00:00:00Z");
        assertNull(refresh.getError());
        List<ProviderPricingRule> rules = refresh.getRules();
        assertEquals(4, rules.size());
        assertEquals(0, rule(rules, "Claude Fable 5.2", "input_cache_miss")
                .getStandardPrice().compareTo(new BigDecimal("8")));
        assertEquals(0, rule(rules, "Claude Fable 5.2", "input_cache_write")
                .getStandardPrice().compareTo(new BigDecimal("10")));
    }

    @Test
    void namedRegexUsesTrialPriceAndMapsGroupNames() {
        String html = "<html><body>"
                + "agnes-2.5-flash Cached input $0.005 / M $0 / M Input tokens $0.05 / M $0 / M "
                + "Output tokens $0.15 / M $0 / M "
                + "agnes-2.5-pro Cached input $0.045 / M $0.045 / M Input tokens $0.45 / M $0.45 / M "
                + "Output tokens $0.90 / M $0.90 / M"
                + "</body></html>";
        PricingSourceConfig config = config("named-regex");
        config.setPattern("(?s)(?<model>agnes-[a-z0-9.\\-]+)\\s+Cached input\\s+\\$[0-9.]+\\s*/\\s*M"
                + "\\s+\\$(?<cacheHit>[0-9.]+)\\s*/\\s*M\\s+Input tokens\\s+\\$[0-9.]+\\s*/\\s*M"
                + "\\s+\\$(?<input>[0-9.]+)\\s*/\\s*M\\s+Output tokens\\s+\\$[0-9.]+\\s*/\\s*M"
                + "\\s+\\$(?<output>[0-9.]+)\\s*/\\s*M");
        config.setGroupItems(Map.of(
                "cacheHit", "input_cache_hit",
                "input", "input_cache_miss",
                "output", "output"));
        ProviderPricingRefresh refresh = PricingPageSource.parse(html, config,
                "https://agnes-ai.com/en/docs/pricing", "2026-11-16T00:00:00Z");
        assertNull(refresh.getError());
        List<ProviderPricingRule> rules = refresh.getRules();
        assertEquals(6, rules.size());
        assertEquals(0, rule(rules, "agnes-2.5-flash", "input_cache_miss")
                .getStandardPrice().compareTo(BigDecimal.ZERO),
                "取 Angel User Trial Price 的当前 0 价，而不是 list price 0.05");
        assertEquals(0, rule(rules, "agnes-2.5-pro", "output")
                .getStandardPrice().compareTo(new BigDecimal("0.90")));
    }

    @Test
    void brokenPageReturnsFailure() {
        PricingSourceConfig config = config("html-table");
        config.setColumns(Map.of("Input", "input_cache_miss", "Output", "output"));
        ProviderPricingRefresh refresh = PricingPageSource.parse("<html>changed</html>", config,
                "https://example.invalid/pricing", "2026-11-16T00:00:00Z");
        assertTrue(refresh.isSupported());
        assertFalse(refresh.getError() == null || refresh.getError().isBlank());
        assertTrue(refresh.getRules().isEmpty());
    }

    private static PricingSourceConfig config(String type) {
        PricingSourceConfig config = new PricingSourceConfig();
        config.setType(type);
        config.setCurrency("USD");
        config.setQuantity(new BigDecimal("1000000"));
        config.setUnit("token");
        return config;
    }

    private static ProviderPricingRule rule(List<ProviderPricingRule> rules, String model, String item) {
        return rules.stream()
                .filter(r -> model.equals(r.getModelPattern()) && item.equals(r.getItem()))
                .findFirst().orElseThrow();
    }
}

package com.chua.starter.ai.support.manage.limit;

import com.chua.starter.datasearch.provider.model.PriceWindow;
import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.support.ProviderModelCatalogs;
import com.chua.starter.datasearch.provider.support.ProviderPricings;
import com.chua.starter.datasearch.provider.support.DeepSeekPricingSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定价规则 SPI 验收：峰谷价、按 N 次计价、按模型过滤，以及「未收录返回空」。
 *
 * <p>纯 JUnit 5，不启动 Spring、不连数据库。</p>
 *
 * @author CH
 */
class ProviderPricingSpiTest {

    private static final String DEEPSEEK = "https://api.deepseek.com/v1";

    @Test
    void deepseekHasPeakAndOffPeakPrices() {
        List<ProviderPricingRule> rules = ProviderPricings.of("deepseek", DEEPSEEK);
        assertFalse(rules.isEmpty());
        ProviderPricingRule output = rules.stream()
                .filter(r -> "deepseek-flash".equals(r.getModelPattern()) && "output".equals(r.getItem()))
                .findFirst().orElse(null);
        assertNotNull(output, "deepseek-flash 应有输出价规则");
        assertEquals(0, output.getPeakPrice().compareTo(new BigDecimal("1.2")));
        assertEquals(0, output.getOffPeakPrice().compareTo(new BigDecimal("0.6")));
        assertEquals("USD", output.getCurrency());
        assertEquals(2, output.getPeakWindows().size(), "高峰时段应为两段");
        PriceWindow first = output.getPeakWindows().get(0);
        assertEquals("UTC", first.getTimezone());
        assertEquals(60, first.getStartMinute());
        assertEquals(240, first.getEndMinute());
        assertTrue(output.getVerified());
    }

    @Test
    void perQuantityPricingUsesUnitAndQuantity() {
        List<ProviderPricingRule> rules = ProviderPricings.of("deepseek", DEEPSEEK);
        ProviderPricingRule search = rules.stream()
                .filter(r -> "search".equals(r.getItem()))
                .findFirst().orElse(null);
        assertNotNull(search);
        assertEquals("search", search.getUnit());
        assertEquals(0, search.getQuantity().compareTo(new BigDecimal("1000")));
        assertEquals(0, search.getStandardPrice().compareTo(new BigDecimal("0.75")));
        assertNotNull(search.getNote());
        assertTrue(search.getNote().contains("$0.75"), "备注应写明按千次计价");
    }

    @Test
    void rulesFilterByModelPattern() {
        List<ProviderPricingRule> all = ProviderPricings.of("deepseek", DEEPSEEK);
        List<ProviderPricingRule> flash = ProviderPricings.forModel(all, "deepseek-flash");
        assertTrue(flash.stream().anyMatch(r -> "output".equals(r.getItem())));
        assertTrue(flash.stream().anyMatch(r -> "*".equals(r.getModelPattern())), "通配规则应命中所有模型");
        List<ProviderPricingRule> pro = ProviderPricings.forModel(all, "deepseek-v4-pro");
        assertTrue(pro.stream().anyMatch(r -> "deepseek-v4-pro".equals(r.getModelPattern())));
        assertFalse(pro.stream().anyMatch(r -> "deepseek-flash".equals(r.getModelPattern())));
    }

    @Test
    void catalogCarriesPricingRules() {
        ProviderModelCatalog catalog = ProviderModelCatalogs.of("deepseek", DEEPSEEK);
        assertNotNull(catalog);
        assertFalse(catalog.getPricingRules().isEmpty(), "目录应带上定价规则");
        assertEquals(ProviderPricings.of("deepseek", DEEPSEEK).size(), catalog.getPricingRules().size());
    }

    @Test
    void unknownProviderReturnsEmpty() {
        assertTrue(ProviderPricings.of("no-such-provider", "https://example.invalid/v1").isEmpty());
        assertEquals(0, ProviderPricings.forModel(null, "x").size());
    }

    @Test
    void livePageParsesCurrentPricesNotHardcodedOnes() {
        String html = "<html><body><nav>Models &amp; Pricing</nav>"
                + "<div>MODEL deepseek-flash (1) deepseek-v4-pro BASE URL "
                + "https://api.deepseek.com</div>"
                + "<div>PRICING (2) 1M INPUT TOKENS (CACHE HIT) OFF-PEAK $0.003 $0.022 "
                + "PEAK $0.006 $0.044 1M INPUT TOKENS (CACHE MISS) OFF-PEAK $0.15 $0.66 "
                + "PEAK $0.3 $1.32 1M OUTPUT TOKENS OFF-PEAK $0.6 $1.98 PEAK $1.5 $3.96 "
                + "Concurrency Limit (3) 2500 500</div>"
                + "<div>Off-peak rates are half of the peak rates. Peak hours are "
                + "01:00 - 04:00 and 06:00 - 10:00 UTC, Monday through Friday, excluding "
                + "Chinese public holidays. All other hours are off-peak.</div>"
                + "<div>Web Search &amp; Fetch Pricing Item Unit Price Search (text, image) "
                + "Per 1,000 searches $0.75 Web fetch Per 1,000 fetches $0.75</div>"
                + "<div>File parsing, OCR API Per 1,000 pages $0.225</div>"
                + "<div>Text to Speech (TTS) Per 10,000 characters $0.45 $0.225 "
                + "Speech to Text (ASR) Per hour of audio $0.116 $0.058</div></body></html>";
        var refresh = DeepSeekPricingSource.parse(html, DeepSeekPricingSource.URL, "2026-11-16T00:00:00Z");
        assertNotNull(refresh);
        assertTrue(refresh.isLive());
        assertEquals(null, refresh.getError());
        assertEquals("2026-11-16T00:00:00Z", refresh.getFetchedAt());
        List<ProviderPricingRule> rules = refresh.getRules();
        assertEquals(11, rules.size());
        ProviderPricingRule output = rules.stream()
                .filter(r -> "deepseek-flash".equals(r.getModelPattern()) && "output".equals(r.getItem()))
                .findFirst().orElseThrow();
        // 样本里写的是 1.5，而不是登记表的 1.2：证明同步读的是页面当前值
        assertEquals(0, output.getPeakPrice().compareTo(new BigDecimal("1.5")));
        assertEquals(0, output.getOffPeakPrice().compareTo(new BigDecimal("0.6")));
        assertEquals(2, output.getPeakWindows().size());
        assertEquals("2026-11-16T00:00:00Z", output.getFetchedAt());
        assertTrue(output.getVerified());
        ProviderPricingRule search = rules.stream()
                .filter(r -> "search".equals(r.getItem())).findFirst().orElseThrow();
        assertEquals(0, search.getQuantity().compareTo(new BigDecimal("1000")));
        assertEquals(0, search.getStandardPrice().compareTo(new BigDecimal("0.75")));
        ProviderPricingRule tts = rules.stream()
                .filter(r -> "tts".equals(r.getItem())).findFirst().orElseThrow();
        assertEquals(0, tts.getStandardPrice().compareTo(new BigDecimal("0.225")));
        assertTrue(tts.getNote().contains("0.45"), "备注保留原价");
    }

    @Test
    void brokenPageReturnsFailureInsteadOfStaleRules() {
        var refresh = DeepSeekPricingSource.parse("<html>changed layout</html>",
                DeepSeekPricingSource.URL, "2026-11-16T00:00:00Z");
        assertTrue(refresh.isSupported());
        assertFalse(refresh.getError() == null || refresh.getError().isBlank());
        assertTrue(refresh.getRules().isEmpty());
    }
}

package com.chua.starter.ai.support.manage.limit;

import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.support.AgnesPricingSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agnes 价格页解析回归：文本 / 图片 / 视频三段表格，一行一计费项。
 *
 * <p>纯 JUnit 5，不启动 Spring、不连网络；HTML 样本按官方页面真实结构裁剪。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
class AgnesPricingSourceTest {

    /**
     * 按官方页面真实结构裁剪的样本：6 个文本模型 + 3 个图片模型 + 2 个视频模型。
     */
    private static final String HTML = "<html><body>"
            + "<h1>Model Pricing</h1>"
            + "<p>All prices are API prices; M means one million tokens.</p>"
            + "<table><tbody>"
            + "<tr><td>agnes-2.5-flash</td><td>Cached input</td><td>$0.005 / M</td><td>$0 / M</td>"
            + "<td>Input tokens</td><td>$0.05 / M</td><td>$0 / M</td>"
            + "<td>Output tokens</td><td>$0.15 / M</td><td>$0 / M</td></tr>"
            + "<tr><td>agnes-2.5-pro</td><td>Cached input</td><td>$0.045 / M</td><td>$0.045 / M</td>"
            + "<td>Input tokens</td><td>$0.45 / M</td><td>$0.45 / M</td>"
            + "<td>Output tokens</td><td>$0.90 / M</td><td>$0.90 / M</td></tr>"
            + "<tr><td>agnes-2.5-pro-beta</td><td>Cached input</td><td>$0.01 / M</td><td>$0.01 / M</td>"
            + "<td>Input tokens</td><td>$0.10 / M</td><td>$0.10 / M</td>"
            + "<td>Output tokens</td><td>$0.30 / M</td><td>$0.30 / M</td></tr>"
            + "<tr><td>agnes-2.5-pro-theta</td><td>Cached input</td><td>$0.01 / M</td><td>$0.01 / M</td>"
            + "<td>Input tokens</td><td>$0.10 / M</td><td>$0.10 / M</td>"
            + "<td>Output tokens</td><td>$0.30 / M</td><td>$0.30 / M</td></tr>"
            + "<tr><td>agnes-3.0-pro</td><td>Coming soon</td><td>Cached input</td><td>$0.01 / M</td><td>$0.01 / M</td>"
            + "<td>Input tokens</td><td>$0.10 / M</td><td>$0.10 / M</td>"
            + "<td>Output tokens</td><td>$0.30 / M</td><td>$0.30 / M</td></tr>"
            + "<tr><td>agnes-3.0-flash</td><td>Cached input</td><td>$0.005 / M</td><td>$0 / M</td>"
            + "<td>Input tokens</td><td>$0.05 / M</td><td>$0 / M</td>"
            + "<td>Output tokens</td><td>$0.15 / M</td><td>$0 / M</td></tr>"
            + "</tbody></table>"
            + "<h2>Image models</h2>"
            + "<table><tbody>"
            + "<tr><td>agnes-image-2.0-flash</td><td>1K output image</td><td>$10 / 1,000 images</td><td>$0</td>"
            + "<td>2K output image</td><td>$18 / 1,000 images</td><td>$0</td>"
            + "<td>3K output image</td><td>$21 / 1,000 images</td><td>$0</td>"
            + "<td>4K output image</td><td>$24 / 1,000 images</td><td>$0</td>"
            + "<td>Input reference images from the 4th onward</td><td>$0.003 / image</td><td>$0 / image</td></tr>"
            + "<tr><td>agnes-image-2.1-flash</td><td>1K output image</td><td>$10 / 1,000 images</td><td>$0</td>"
            + "<td>2K output image</td><td>$18 / 1,000 images</td><td>$0</td>"
            + "<td>3K output image</td><td>$21 / 1,000 images</td><td>$0</td>"
            + "<td>4K output image</td><td>$24 / 1,000 images</td><td>$0</td>"
            + "<td>Input reference images from the 4th onward</td><td>$0.003 / image</td><td>$0 / image</td></tr>"
            + "<tr><td>agnes-image-2.5-flash</td><td>1K output image</td><td>$10 / 1,000 images</td><td>$0</td>"
            + "<td>2K output image</td><td>$18 / 1,000 images</td><td>$0</td>"
            + "<td>3K output image</td><td>$21 / 1,000 images</td><td>$0</td>"
            + "<td>4K output image</td><td>$24 / 1,000 images</td><td>$0</td>"
            + "<td>Input reference images from the 4th onward</td><td>$0.003 / image</td><td>$0 / image</td></tr>"
            + "</tbody></table>"
            + "<h2>Video models</h2>"
            + "<table><tbody>"
            + "<tr><td>agnes-video-2.5</td><td>720P output video</td><td>$0.025 / second</td><td>$0.025 / second</td>"
            + "<td>1080P output video</td><td>$0.040 / second</td><td>$0.040 / second</td>"
            + "<td>1K output video</td><td>$0.040 / second</td><td>$0.040 / second</td>"
            + "<td>2K output video</td><td>$0.055 / second</td><td>$0.055 / second</td>"
            + "<td>Input images from the 6th onward</td><td>$0.005 / image</td><td>$0.005 / image</td></tr>"
            + "<tr><td>agnes-video-2.5-flash</td><td>720P video duration</td><td>$0.025 / second</td><td>$0 / second</td></tr>"
            + "</tbody></table>"
            + "</body></html>";

    @Test
    void parsesTextImageAndVideoPrices() {
        ProviderPricingRefresh refresh = AgnesPricingSource.parse(HTML, AgnesPricingSource.URL, "2026-11-18T00:00:00Z");
        assertNotNull(refresh);
        assertTrue(refresh.isLive());
        assertNull(refresh.getError());
        assertEquals("2026-11-18T00:00:00Z", refresh.getFetchedAt());
        List<ProviderPricingRule> rules = refresh.getRules();
        // 6 文本模型 × 3 项 + 3 图片模型 × 5 项 + agnes-video-2.5 的 5 项 + agnes-video-2.5-flash 的 1 项
        assertEquals(39, rules.size());

        // 文本模型：standardPrice = 当前体验价，note 带刊例价
        ProviderPricingRule flashOutput = find(rules, "agnes-2.5-flash", "output");
        // 体验期免费：当前实际计费价（Angel User Trial Price）= 0，刊例价 $0.15 写进 note
        assertEquals(0, flashOutput.getStandardPrice().compareTo(BigDecimal.ZERO));
        assertEquals("token", flashOutput.getUnit());
        assertEquals(0, flashOutput.getQuantity().compareTo(new BigDecimal("1000000")));
        assertTrue(flashOutput.getNote().contains("当前价 $0"), "体验价应写进备注：除 standardPrice 外还应有 note");
        assertTrue(flashOutput.getNote().contains("刊例价 $0.15"));
        ProviderPricingRule proInput = find(rules, "agnes-2.5-pro", "input_cache_miss");
        assertEquals(0, proInput.getStandardPrice().compareTo(new BigDecimal("0.45")));
        ProviderPricingRule proCacheHit = find(rules, "agnes-2.5-pro", "input_cache_hit");
        assertEquals(0, proCacheHit.getStandardPrice().compareTo(new BigDecimal("0.045")));
        assertTrue(find(rules, "agnes-3.0-pro", "output").getNote().contains("Coming soon"),
                "agnes-3.0-pro 应标注 Coming soon");
        assertFalse(find(rules, "agnes-2.5-flash", "output").getNote().contains("Coming soon"));

        // 图片模型：分辨率档按千张，参考图第 4 张起按张
        ProviderPricingRule image1k = find(rules, "agnes-image-2.0-flash", "image");
        assertEquals(0, image1k.getQuantity().compareTo(new BigDecimal("1000")));
        assertEquals("image", image1k.getUnit());
        assertEquals(0, image1k.getStandardPrice().compareTo(BigDecimal.ZERO));
        assertTrue(image1k.getNote().contains("1K"), "备注应能区分分辨率档");
        assertTrue(image1k.getNote().contains("$10"), "刊例价应写进备注");
        long imageRef = rules.stream()
                .filter(r -> "agnes-image-2.1-flash".equals(r.getModelPattern()) && "image".equals(r.getItem()))
                .filter(r -> r.getNote().contains("第 4 张起"))
                .count();
        assertEquals(1, imageRef, "每个图片模型应有一条第 4 张起的参考图规则");

        // 视频模型：按秒计费；flash 体验期 720P 免费
        ProviderPricingRule video720 = find(rules, "agnes-video-2.5", "video");
        assertEquals("second", video720.getUnit());
        assertEquals(0, video720.getStandardPrice().compareTo(new BigDecimal("0.025")));
        ProviderPricingRule video2k = rules.stream()
                .filter(r -> "agnes-video-2.5".equals(r.getModelPattern()) && "video".equals(r.getItem()))
                .filter(r -> r.getNote().contains("2K"))
                .findFirst().orElse(null);
        assertNotNull(video2k, "agnes-video-2.5 应有 2K 档");
        assertEquals(0, video2k.getStandardPrice().compareTo(new BigDecimal("0.055")));
        ProviderPricingRule videoFlash = find(rules, "agnes-video-2.5-flash", "video");
        assertEquals(0, videoFlash.getStandardPrice().compareTo(BigDecimal.ZERO));
        assertTrue(videoFlash.getNote().contains("刊例价 $0.025"));

        // 公共字段
        for (ProviderPricingRule rule : rules) {
            assertEquals("agnes", rule.getProviderId());
            assertEquals("USD", rule.getCurrency());
            assertTrue(rule.getVerified());
            assertEquals("2026-11-18T00:00:00Z", rule.getFetchedAt());
            assertEquals(AgnesPricingSource.URL, rule.getSourceUrl());
        }
    }

    @Test
    void modelPatternsMatchExactly() {
        List<ProviderPricingRule> rules = AgnesPricingSource.parse(HTML, AgnesPricingSource.URL, "2026-11-18T00:00:00Z").getRules();
        ProviderPricingRule video = find(rules, "agnes-video-2.5", "video");
        assertTrue(video.matches("agnes-video-2.5"));
        assertFalse(video.matches("agnes-video-2.5-flash"), "精确规则不应命中 flash 变体");
        ProviderPricingRule flash = find(rules, "agnes-video-2.5-flash", "video");
        assertTrue(flash.matches("agnes-video-2.5-flash"));
        ProviderPricingRule proBeta = find(rules, "agnes-2.5-pro-beta", "output");
        assertTrue(proBeta.matches("agnes-2.5-pro-beta"));
        assertFalse(proBeta.matches("agnes-2.5-pro"));
    }

    @Test
    void malformedPageFailsWithoutRules() {
        ProviderPricingRefresh refresh = AgnesPricingSource.parse(
                "<html><body>hello world</body></html>", AgnesPricingSource.URL, "2026-11-18T00:00:00Z");
        assertNotNull(refresh);
        assertFalse(refresh.isLive());
        assertNotNull(refresh.getError(), "解析失败必须给出原因，不得拿空规则覆盖数据库");
        assertTrue(refresh.getRules() == null || refresh.getRules().isEmpty());
        assertTrue(AgnesPricingSource.parse(null, AgnesPricingSource.URL, "t").getError() != null);
        assertTrue(AgnesPricingSource.parse(" ", AgnesPricingSource.URL, "t").getError() != null);
    }

    /**
     * 按模型 + 计费项取一条规则。
     *
     * @param rules 规则列表
     * @param model 模型名
     * @param item  计费项
     * @return 命中的规则
     */
    private static ProviderPricingRule find(List<ProviderPricingRule> rules, String model, String item) {
        return rules.stream()
                .filter(r -> model.equals(r.getModelPattern()) && item.equals(r.getItem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少规则: " + model + " / " + item));
    }
}

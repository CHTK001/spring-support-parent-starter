package com.chua.starter.ai.support.manage.limit;

import com.chua.starter.datasearch.provider.limit.ProviderLimit;
import com.chua.starter.datasearch.provider.limit.ProviderLimitPresets;
import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.support.ProviderCapabilities;
import com.chua.starter.datasearch.provider.support.ProviderModelCatalogs;
import com.chua.starter.datasearch.provider.support.ProviderProtocolCapabilities;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * datasearch 侧厂商定义 SPI 的接入验收：换到 ai-starter 的类路径上仍然能装载、
 * 能按平台 / 主机名命中，并且「有文档」不会被当成「有限制」。
 *
 * <p>纯 JUnit 5，不启动 Spring、不连数据库。</p>
 *
 * @author CH
 */
class ProviderCatalogSpiTest {

    /**
     * 阿里巴巴兼容地址，用它验证「业务 provider id（qwen）→ 目录厂商（alibaba）」的别名映射
     */
    private static final String DASHSCOPE = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Test
    void registryCoversTemplateProviders() {
        assertTrue(ProviderLimitPresets.size() >= 200,
                "登记表应覆盖模板库 200+ 家，实际 " + ProviderLimitPresets.size());
        assertTrue(ProviderLimitPresets.entries().size() >= 200);
        assertTrue(ProviderLimitPresets.entries().stream().filter(e -> e.doc() != null).count() >= 180,
                "多数条目应带官方文档入口");
    }

    @Test
    void deepseekHasVerifiedModelConcurrency() {
        ProviderLimit limit = ProviderLimitPresets.find("deepseek", "https://api.deepseek.com/v1");
        assertNotNull(limit);
        assertEquals("doc", limit.getKind());
        assertEquals("deepseek", limit.getSource());
        assertTrue(limit.getVerified());
        assertNotNull(limit.getModelConcurrency());
        assertEquals(2500, limit.getModelConcurrency().get("deepseek-flash"));
        assertNotNull(limit.getSourceUrl());
    }

    @Test
    void docOnlyProviderHasUrlButNoNumbers() {
        // qwen 在登记表里只有 models.dev 的文档入口，没有核对过的数值
        assertNull(ProviderLimitPresets.find("qwen", DASHSCOPE));
        String doc = ProviderLimitPresets.docUrl("qwen", DASHSCOPE);
        assertNotNull(doc);
        assertTrue(doc.startsWith("https://"));
        assertEquals("alibaba", ProviderLimitPresets.docSource("qwen", DASHSCOPE));
    }

    @Test
    void unknownProviderIsNull() {
        assertNull(ProviderLimitPresets.find("no-such-provider", "https://example.invalid/v1"));
        assertNull(ProviderLimitPresets.docUrl("no-such-provider", "https://example.invalid/v1"));
    }

    @Test
    void modelCatalogCarriesDescriptionAndPricing() {
        ProviderModelCatalog catalog = ProviderModelCatalogs.of("qwen", DASHSCOPE);
        assertNotNull(catalog);
        assertEquals("alibaba", catalog.getProviderId());
        assertFalse(catalog.getModels().isEmpty());
        List<ProviderModelInfo> models = catalog.getModels();
        assertTrue(models.stream().allMatch(m -> m.getDescription() != null && !m.getDescription().isBlank()),
                "目录内模型都应有说明");
        ProviderModelInfo priced = models.stream()
                .filter(m -> m.getPriceInputPerMusd() != null && m.getPriceOutputPerMusd() != null)
                .findFirst().orElse(null);
        assertNotNull(priced, "应至少有一个带输入 / 输出价格的模型");
        assertTrue(priced.getPriceInputPerMusd() >= 0);
        assertTrue(priced.getContextWindow() != null && priced.getContextWindow() > 0);
    }

    @Test
    void catalogMirrorsCapabilityWhileProtocolRuleFills() {
        ProviderModelCatalog catalog = ProviderModelCatalogs.of("qwen", DASHSCOPE);
        assertNotNull(catalog);
        // 目录本身不带这两个字段（models.dev 没有），由能力位 SPI 统一补齐；
        // 所以断言「与能力位一致」，不是「一律留空」——登记表给出 true 时目录就是 true。
        Boolean streaming = ProviderCapabilities.streaming("qwen", DASHSCOPE);
        Boolean asyncSupported = ProviderCapabilities.asyncSupported("qwen", DASHSCOPE);
        assertTrue(catalog.getModels().stream().allMatch(m -> Objects.equals(m.getStreaming(), streaming)),
                "目录的流式口径应与能力位一致，当前能力位 " + streaming);
        assertTrue(catalog.getModels().stream().allMatch(m -> Objects.equals(m.getAsyncSupported(), asyncSupported)),
                "目录的异步口径应与能力位一致，当前能力位 " + asyncSupported);

        // 协议级口径（A′）：三种协议的流式是协议必备行为，命中即支持，来源 protocol
        for (String protocol : List.of("openai", "anthropic", "gemini")) {
            assertEquals(Boolean.TRUE, ProviderProtocolCapabilities.supportsStreaming(protocol), protocol);
            assertEquals(ProviderProtocolCapabilities.SOURCE_PROTOCOL,
                    ProviderProtocolCapabilities.streamingSource(protocol), protocol);
        }
        // 协议不在名单内不猜
        assertNull(ProviderProtocolCapabilities.supportsStreaming("ollama"));
        assertNull(ProviderProtocolCapabilities.streamingSource(null));

        // 异步只认端点实测过的官方厂商；中转自称 openai 兼容也不算
        assertEquals(Boolean.TRUE, ProviderProtocolCapabilities.supportsAsync("openai", "openai"));
        assertEquals(Boolean.TRUE, ProviderProtocolCapabilities.supportsAsync("anthropic", "anthropic"));
        assertEquals(Boolean.TRUE, ProviderProtocolCapabilities.supportsAsync("google", "gemini"));
        assertEquals(ProviderProtocolCapabilities.SOURCE_PROBE,
                ProviderProtocolCapabilities.asyncSource("anthropic", "anthropic"));
        assertNull(ProviderProtocolCapabilities.supportsAsync("openrouter", "openai"));
        assertNull(ProviderProtocolCapabilities.asyncSource("siliconflow", "openai"));
        // 厂商与协议对不上时同样不下结论
        assertNull(ProviderProtocolCapabilities.supportsAsync("openai", "gemini"));
    }
}

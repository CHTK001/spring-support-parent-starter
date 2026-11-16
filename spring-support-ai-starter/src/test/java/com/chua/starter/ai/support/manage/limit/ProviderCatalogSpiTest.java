package com.chua.starter.ai.support.manage.limit;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.support.ProviderModelCatalogs;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void streamingAndAsyncStayUnknown() {
        ProviderModelCatalog catalog = ProviderModelCatalogs.of("qwen", DASHSCOPE);
        assertNotNull(catalog);
        // 公开目录没有流式 / 异步字段：保持 null（未收录），不按协议默认成 true
        assertTrue(catalog.getModels().stream().allMatch(m -> m.getStreaming() == null));
        assertTrue(catalog.getModels().stream().allMatch(m -> m.getAsyncSupported() == null));
    }
}

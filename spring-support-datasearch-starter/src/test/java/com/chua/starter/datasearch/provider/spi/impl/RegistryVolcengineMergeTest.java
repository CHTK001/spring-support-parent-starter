package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 火山系登记项合并验证：豆包并入火山引擎主条目别名，Coding Plan 变为
 * 主条目变体（独立端点）。不发网络请求，直接读登记表资源。
 *
 * @author CH
 * @since 4.0.0.61
 */
class RegistryVolcengineMergeTest {

    /**
     * 被测登记表。
     */
    private final RegistryProviderRegistry registry = new RegistryProviderRegistry();

    /**
     * doubao 别名命中火山引擎主条目（合并后不再有独立豆包条目）。
     */
    @Test
    void doubaoAliasResolvesToVolcengineMainEntry() {
        ProviderDescriptor hit = registry.find("doubao", null);
        assertNotNull(hit, "doubao 应命中合并后的火山引擎条目");
        assertEquals("volcengine", hit.getId());
        assertEquals("https://ark.cn-beijing.volces.com/api/v3", hit.getBaseUrl());
        assertTrue(hit.getAliases().contains("doubao"), "主条目别名应含 doubao");
    }

    /**
     * coding-plan 变体仍按原 id 命中，且拿到的是编程套餐端点（非主条目地址）。
     */
    @Test
    void codingPlanVariantResolvesWithOwnBaseUrl() {
        ProviderDescriptor hit = registry.find("volcengine-coding-plan", null);
        assertNotNull(hit, "volcengine-coding-plan 应命中变体");
        assertEquals("volcengine-coding-plan", hit.getId());
        assertEquals("https://ark.cn-beijing.volces.com/api/coding/v3", hit.getBaseUrl());
        assertNotEquals(registry.find("volcengine", null).getBaseUrl(), hit.getBaseUrl(),
                "变体端点应与主条目不同（/api/coding/v3 vs /api/v3）");
    }

    /**
     * 变体继承主条目缺省：协议、主机名、能力位、余额口径；文档用变体自己的。
     */
    @Test
    void variantInheritsParentDefaults() {
        ProviderDescriptor main = registry.find("volcengine", null);
        ProviderDescriptor variant = registry.find("volcengine-coding-plan", null);
        assertNotNull(main);
        assertNotNull(variant);
        assertEquals(main.getProtocol(), variant.getProtocol());
        assertEquals(main.getHosts(), variant.getHosts());
        assertEquals(main.getCreditMode(), variant.getCreditMode());
        assertEquals(main.getCreditKind(), variant.getCreditKind());
        assertEquals(main.getWebsite(), variant.getWebsite());
        assertEquals("https://www.volcengine.com/docs/82379/1928261", variant.getDoc());
        assertTrue(variant.getAliases().contains("volcengine-coding-plan"));
    }

    /**
     * 域名匹配落到主条目（变体与主条目同域名时先登记者胜出）。
     */
    @Test
    void hostMatchFallsBackToMainEntry() {
        ProviderDescriptor hit = registry.find(null, "https://ark.cn-beijing.volces.com/api/v3");
        assertNotNull(hit);
        assertEquals("volcengine", hit.getId());
    }
}

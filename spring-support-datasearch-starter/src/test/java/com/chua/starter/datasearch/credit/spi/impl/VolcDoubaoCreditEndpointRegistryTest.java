package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.ProviderCreditEndpoint;
import com.chua.starter.datasearch.credit.support.ProviderCreditEndpoints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 豆包（火山方舟）余额登记验证：doubao / volcengine 等别名命中火山 BSS 预设
 * （AK/SK 签名），模型域名与计费域名不同根（sameOrigin=false，由控制器显式接线，
 * 同阿里云百炼 / 腾讯云 / 华为云口径），不发网络请求。
 *
 * @author CH
 * @since 4.0.0.60
 */
class VolcDoubaoCreditEndpointRegistryTest {

    /**
     * 豆包相关品牌别名都解析到同一条火山 BSS 预设：AK/SK 签名、计费域名端点。
     */
    @Test
    void doubaoAliasesResolveToVolcBillingPreset() {
        for (String brand : new String[]{"doubao", "ark", "volcengine", "volcengine-coding-plan", "volc-billing"}) {
            ProviderCreditEndpoint endpoint = ProviderCreditEndpoints.find(brand);
            assertNotNull(endpoint, brand + " 应命中登记表");
            assertEquals("volc-billing", endpoint.getId(), brand);
            assertEquals("volc", endpoint.getSignType(), brand);
            assertTrue(endpoint.getUrl().startsWith("https://billing.volcengineapi.com"), brand);
        }
        // AK/SK 签名类预设不属于 Bearer 类：API Key 面板不得为豆包生成必然失败的候选，
        // 由控制器在配置 AK/SK 后显式加 host 候选（同阿里云百炼 / 腾讯云 / 华为云口径）
        assertFalse(ProviderCreditEndpoints.supportsHost("doubao"));
        assertFalse(ProviderCreditEndpoints.bearerBrands().contains("doubao"));
        // 无关品牌不得误命中
        assertFalse(ProviderCreditEndpoints.supportsHost("moark"));
        assertFalse(ProviderCreditEndpoints.supportsHost("spark"));
    }

    /**
     * 模型域名（ark.cn-beijing.volces.com）与计费域名（billing.volcengineapi.com）不同根：
     * 自动同源判定必须为 false——豆包渠道由控制器显式加 host 候选，不走同源闸门。
     */
    @Test
    void arkModelDomainIsNotSameOriginAsBillingDomain() {
        assertFalse(ProviderCreditEndpoints.sameOrigin("doubao", "https://ark.cn-beijing.volces.com/api/v3"));
        assertTrue(ProviderCreditEndpoints.sameOrigin("doubao", "https://billing.volcengineapi.com/"));
    }
}

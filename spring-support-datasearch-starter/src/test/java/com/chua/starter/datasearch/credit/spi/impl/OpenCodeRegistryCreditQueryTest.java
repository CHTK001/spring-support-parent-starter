package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OpenCode Go / Zen 余额查询链路验证（host / registry 登记表口径），全程不 mock：
 *
 * <p>同源闸门与未收录快速失败是纯逻辑判定（不发网络）；链路用例持真实 API Key
 * 直接请求 opencode.ai 官方用量端点 {@code /zen/go/v1/usage}，Key 通过系统属性
 * {@code opencode.test.key} 或环境变量 {@code OPENCODE_TEST_KEY} 提供，缺失时按
 * Assumption 跳过——不造假服务器、不编造读数。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class OpenCodeRegistryCreditQueryTest {

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
     * 组装查询：originUrl 用真实渠道上游地址，url 指向官方用量端点（等价于页面里
     * 显式配置余额地址）。
     */
    private static CreditQuery query(String providerId, String originUrl, String key) {
        CreditQuery query = new CreditQuery();
        query.setKind("host");
        query.setProviderId(providerId);
        query.setOriginUrl(originUrl);
        query.setUrl("https://opencode.ai/zen/go/v1/usage");
        query.setToken(key);
        return query;
    }

    /**
     * host 口径真实链路：官方端点如实返回。当前测试 Key 无 Go 订阅时上游回 403，
     * 错误原样透传；换成有 Go 订阅的 Key 则解析出三窗口剩余百分比。
     */
    @Test
    void hostProviderHitsRealOfficialEndpoint() {
        String key = testKey();
        assumeTrue(key != null, "未提供 opencode.test.key / OPENCODE_TEST_KEY，跳过真实链路用例");
        CreditResult result = new HostCreditQueryProvider().query(query("opencode", "https://opencode.ai/zen/v1", key));
        if (result.isOk()) {
            assertEquals("host", result.getSource());
            assertEquals(3, result.getParts().size());
            assertEquals("%", result.getCurrency());
        } else {
            assertTrue(result.getError().startsWith("HTTP "), () -> "上游真实状态码应透传: " + result.getError());
        }
    }

    /**
     * registry 口径真实链路：与 host 读同一登记表，行为一致。
     */
    @Test
    void registryProviderHitsRealOfficialEndpoint() {
        String key = testKey();
        assumeTrue(key != null, "未提供 opencode.test.key / OPENCODE_TEST_KEY，跳过真实链路用例");
        CreditResult result = new RegistryCreditQueryProvider()
                .query(query("opencode-go", "https://opencode.ai/inference/go/openai/v1", key));
        if (result.isOk()) {
            assertEquals("registry", result.getSource());
            assertEquals(3, result.getParts().size());
            assertEquals("usage.monthly.percent", result.getParts().get(2).getLabel());
        } else {
            assertTrue(result.getError().startsWith("HTTP "), () -> "上游真实状态码应透传: " + result.getError());
        }
    }

    /**
     * 同源闸门：第三方站点的 Key 查 OpenCode 官方接口，直接跳过，请求根本不发出。
     */
    @Test
    void foreignOriginIsSkippedBeforeAnyRequest() {
        CreditResult result = new HostCreditQueryProvider()
                .query(query("opencode", "https://api.example.com/v1", "sk-anything"));
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("已跳过"), () -> result.getError());
    }

    /**
     * 未收录厂商快速失败，不发请求。
     */
    @Test
    void unknownProviderFailsFast() {
        CreditResult result = new HostCreditQueryProvider()
                .query(query("opencode-unknown", "https://opencode.ai/zen/v1", "sk-anything"));
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("未收录"), () -> result.getError());
    }
}

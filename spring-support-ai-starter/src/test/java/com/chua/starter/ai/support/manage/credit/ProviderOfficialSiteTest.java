package com.chua.starter.ai.support.manage.credit;

import com.chua.starter.datasearch.credit.support.ProviderCreditEndpoints;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 余额口径分流的判定：官方站点不按中转站探测。
 *
 * <p>纯 JUnit 5，不启动 Spring、不连数据库、不发任何请求。</p>
 *
 * @author CH
 */
class ProviderOfficialSiteTest {

    /**
     * 官方站点：登记表 hosts 与上游主机名一致（或为其子域）
     */
    @Test
    void officialHostIsRecognised() {
        assertTrue(ProviderRegistries.isOfficialSite("mistral", "https://api.mistral.ai/v1"),
                "mistral 的 api.mistral.ai 应在登记表 hosts 里");
        assertTrue(ProviderRegistries.isOfficialSite("groq", "https://api.groq.com/openai/v1"),
                "同一官方主机的子域也算官方站点");
        assertTrue(ProviderRegistries.isOfficialSite("deepseek", "https://api.deepseek.com"),
                "地址不带版本前缀也应命中");
    }

    /**
     * 中转 / 自建网关：主机名不在该厂商的 hosts 里
     */
    @Test
    void relayHostIsNotOfficial() {
        assertFalse(ProviderRegistries.isOfficialSite("mistral", "https://api.agnes.example/v1"),
                "换到别家域名就不是官方站点，应按中转探测");
        assertFalse(ProviderRegistries.isOfficialSite("mistral", "http://127.0.0.1:8080/v1"),
                "本机网关同样不是官方站点");
    }

    /**
     * 只选了厂商、还没填地址时按官方站点处理；主机名命中登记表时，平台标签写错也按官方站点处理
     */
    @Test
    void blankOrUnknownAddress() {
        assertTrue(ProviderRegistries.isOfficialSite("mistral", null),
                "没填上游地址时无从否定同源，按官方站点处理");
        // 平台标签不可信时以主机名为准：api.mistral.ai 就是官方站点，按中转探测只会白发请求
        assertTrue(ProviderRegistries.isOfficialSite("no-such-provider-xyz", "https://api.mistral.ai/v1"),
                "主机名命中登记表即按该厂商官方站点处理");
        assertFalse(ProviderRegistries.isOfficialSite("no-such-provider-xyz", "https://api.agnes.example/v1"),
                "主机名与登记表都不认识时按中转处理");
    }

    /**
     * 前提校验：mistral 这类官方站点在余额登记表里没有接口 —— 正是「官方站点·未收录」的场景
     */
    @Test
    void vendorWithoutRegisteredEndpoint() {
        assertNull(ProviderCreditEndpoints.find("mistral"),
                "mistral 官方文档没写余额接口，不应出现在余额登记表里");
        assertNull(ProviderCreditEndpoints.byHost("https://api.mistral.ai/v1"),
                "按主机名也查不到，说明官方站点上没有可用的余额接口");
        assertNotNull(ProviderCreditEndpoints.find("deepseek"),
                "已登记官方余额接口的厂商仍应命中（这条不能因为改口径而丢）");
    }
}

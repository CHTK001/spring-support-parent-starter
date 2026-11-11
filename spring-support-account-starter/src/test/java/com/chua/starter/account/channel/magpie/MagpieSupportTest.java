// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.MagpieSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MagpieSupport} 的地址 / 凭据归一化测试。
 *
 * <p>这些规则直接决定请求打到哪个地址，错一处就会变成 {@code /v1/v1/chat/completions}
 * 或把用户粘贴的完整 URL 当成根地址。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
class MagpieSupportTest {

    @Test
    @DisplayName("空地址退默认网关，不产生空串")
    void normalizeFallsBackToDefault() {
        assertEquals(MagpieSupport.DEFAULT_BASE_URL, MagpieSupport.normalize(null));
        assertEquals(MagpieSupport.DEFAULT_BASE_URL, MagpieSupport.normalize("   "));
        assertEquals(MagpieSupport.DEFAULT_BASE_URL, MagpieSupport.normalize("/v1"));
    }

    @Test
    @DisplayName("粘贴的完整数据面地址被剥成根地址")
    void normalizeTrimsDataPlaneSuffixes() {
        assertEquals("http://127.0.0.1:3425",
                MagpieSupport.normalize("http://127.0.0.1:3425/v1/chat/completions"));
        assertEquals("http://127.0.0.1:3425",
                MagpieSupport.normalize("http://127.0.0.1:3425/v1/"));
        assertEquals("http://127.0.0.1:3425",
                MagpieSupport.normalize(" http://127.0.0.1:3425/v1/models "));
        assertEquals("https://magpie.example.com",
                MagpieSupport.normalize("https://magpie.example.com/api"));
        // 只有一层尾巴，不误伤路径里的 /v1
        assertEquals("https://magpie.example.com/base",
                MagpieSupport.normalize("https://magpie.example.com/base/"));
    }

    @Test
    @DisplayName("数据面基址只补一次 /v1")
    void v1UrlAppendsOnce() {
        assertEquals("http://127.0.0.1:3425/v1", MagpieSupport.v1Url("http://127.0.0.1:3425"));
        assertEquals("http://127.0.0.1:3425/v1", MagpieSupport.v1Url("http://127.0.0.1:3425/v1"));
        assertEquals("http://127.0.0.1:3425/v1", MagpieSupport.v1Url(null));
    }

    @Test
    @DisplayName("凭据键名兼容下划线与驼峰")
    void credentialsReadBothNamingStyles() {
        Map<String, Object> snake = new LinkedHashMap<>();
        snake.put("base_url", "http://10.0.0.2:3425/v1");
        snake.put("gateway_key", " sk-remote ");
        assertEquals("http://10.0.0.2:3425", MagpieSupport.baseUrl(snake));
        assertEquals("sk-remote", MagpieSupport.gatewayKey(snake));

        Map<String, Object> camel = new LinkedHashMap<>();
        camel.put("baseUrl", "http://10.0.0.3:3425");
        camel.put("apiKey", "sk-camel");
        assertEquals("http://10.0.0.3:3425", MagpieSupport.baseUrl(camel));
        assertEquals("sk-camel", MagpieSupport.gatewayKey(camel));
    }

    @Test
    @DisplayName("空 key 不产生空 Authorization")
    void emptyKeyDegradesToLocalPlaceholder() {
        assertEquals(MagpieSupport.LOCAL_KEY, MagpieSupport.effectiveKey(null));
        assertEquals(MagpieSupport.LOCAL_KEY, MagpieSupport.effectiveKey("  "));
        assertEquals("sk-real", MagpieSupport.effectiveKey(" sk-real "));

        Map<String, String> headers = MagpieSupport.dataHeaders("");
        assertEquals("Bearer " + MagpieSupport.LOCAL_KEY, headers.get("Authorization"));
        assertTrue(headers.containsKey("Content-Type"));
    }

    @Test
    @DisplayName("key 脱敏只留末四位，短 key 全遮")
    void maskKeepsOnlyTail() {
        assertEquals("", MagpieSupport.maskKey(null));
        assertEquals("****", MagpieSupport.maskKey("abcd"));
        assertEquals("****wxyz", MagpieSupport.maskKey("sk-abcdefwxyz"));
        assertFalse(MagpieSupport.maskKey("sk-abcdefwxyz").contains("abcdef"));
    }

    @Test
    @DisplayName("管理面地址：显式优先，缺省由数据面端口 3425 推 3430")
    void pageUrlDerivesFromGatewayPort() {
        assertEquals(MagpieSupport.DEFAULT_ADMIN_URL, MagpieSupport.pageUrl(null));
        assertEquals("http://127.0.0.1:3430", MagpieSupport.pageUrl(Map.of("base_url", "http://127.0.0.1:3425")));
        // 反向代理：非 3425 端口原样保留
        assertEquals("https://magpie.example.com", MagpieSupport.pageUrl(Map.of("base_url", "https://magpie.example.com")));
        assertEquals("http://10.0.0.9:3430",
                MagpieSupport.pageUrl(Map.of("base_url", "http://10.0.0.9:3425", "page_url", "http://10.0.0.9:3430/?k=abc")));
    }

    @Test
    @DisplayName("页面地址归一化会剥掉启动链接的查询串")
    void normalizePageDropsQuery() {
        assertEquals("http://127.0.0.1:3430", MagpieSupport.normalizePage("http://127.0.0.1:3430/?k=abc"));
        assertEquals("http://127.0.0.1:3430", MagpieSupport.normalizePage("http://127.0.0.1:3430#/providers"));
        assertEquals(MagpieSupport.DEFAULT_ADMIN_URL, MagpieSupport.normalizePage(" "));
    }

    @Test
    @DisplayName("启动链接里的 ?k= 被识别为页面 Key")
    void linkKeyReadsQueryKey() {
        assertEquals("abc-123", MagpieSupport.linkKey("http://127.0.0.1:3430/?k=abc-123"));
        assertEquals("a b", MagpieSupport.linkKey("http://127.0.0.1:3430/?x=1&k=a%20b#top"));
        assertEquals(null, MagpieSupport.linkKey("http://127.0.0.1:3430/"));
        assertEquals(null, MagpieSupport.linkKey(null));
    }

    @Test
    @DisplayName("页面地址与数据面地址按端口互转，其它端口原样")
    void portsSwapBetweenFaces() {
        assertEquals("http://127.0.0.1:3430", MagpieSupport.gatewayToPage("http://127.0.0.1:3425"));
        assertEquals("http://127.0.0.1:3425", MagpieSupport.gatewayForPage("http://127.0.0.1:3430"));
        assertEquals("http://127.0.0.1:9123", MagpieSupport.gatewayToPage("http://127.0.0.1:9123"));
        assertTrue(MagpieSupport.isPageUrl("http://127.0.0.1:3430"));
        assertFalse(MagpieSupport.isPageUrl("http://127.0.0.1:3425"));
        assertEquals("magpie_web_3430", MagpieSupport.cookieName("http://127.0.0.1:3430"));
        assertEquals("magpie_web_3430", MagpieSupport.cookieName(null));
    }

    @Test
    @DisplayName("页面 Key 兼容页面地址与驼峰键名")
    void pageKeyReadsBothNamingStyles() {
        assertEquals("pk-snake", MagpieSupport.pageKey(Map.of("page_key", " pk-snake ")));
        assertEquals("pk-camel", MagpieSupport.pageKey(Map.of("pageKey", "pk-camel")));
        assertEquals("", MagpieSupport.pageKey(null));
    }
}

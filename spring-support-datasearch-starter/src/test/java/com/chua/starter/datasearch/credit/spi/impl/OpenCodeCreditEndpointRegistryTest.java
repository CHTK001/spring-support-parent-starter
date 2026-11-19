package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.model.ProviderCreditEndpoint;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.ProviderCreditEndpoints;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenCode Go / Zen 余额登记验证：登记表命中、同源判定与表达式求值，不发网络请求。
 *
 * <p>端点路由已用假令牌探活：{@code GET /zen/go/v1/usage} 未授权返回 401（真路径），
 * {@code /zen/v1/balance} 返回 404（Zen 余额官方尚未提供，upstream #10448 open）。
 * 真实响应样例取自官方仓库 issue #43983 里贴出的 Go 账号实测数据。
 * （原类名 {@code OpenCodeCreditQueryProviderTest} 已让给专属实现
 * {@link OpenCodeCreditQueryProvider} 的测试，这里只测登记表口径。）</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
class OpenCodeCreditEndpointRegistryTest {

    /** 官方用量端点真实响应样例（Go 账号实测，见 anomalyco/opencode#43983） */
    private static final String USAGE_RESPONSE = """
            {"usage":{"rolling":{"status":"ok","percent":1,"resetsAt":"2026-09-30T17:15:45.084Z"},
            "weekly":{"status":"ok","percent":25,"resetsAt":"2026-10-05T00:00:00.000Z"},
            "monthly":{"status":"ok","percent":29,"resetsAt":"2026-10-21T07:45:46.000Z"}}}
            """;

    /**
     * Go 条目命中：地址、表达式、Bearer 类、官方已确证。
     */
    @Test
    void registryResolvesOpenCodeGo() {
        ProviderCreditEndpoint endpoint = ProviderCreditEndpoints.find("opencode-go");
        assertNotNull(endpoint);
        assertEquals("OpenCode Go", endpoint.getName());
        assertEquals("https://opencode.ai/zen/go/v1/usage", endpoint.getUrl());
        assertEquals("!usage.rolling.percent; !usage.weekly.percent; !usage.monthly.percent",
                endpoint.getExpression());
        assertNull(endpoint.getSignType());
        assertTrue(endpoint.isVerified());
    }

    /**
     * Zen 条目命中：复用 Go 官方用量端点，别名 opencode / opencode-zen 都能找到。
     */
    @Test
    void registryResolvesOpenCodeZen() {
        ProviderCreditEndpoint endpoint = ProviderCreditEndpoints.find("opencode");
        assertNotNull(endpoint);
        assertEquals("OpenCode Zen", endpoint.getName());
        assertEquals("https://opencode.ai/zen/go/v1/usage", endpoint.getUrl());
        assertNull(endpoint.getSignType());
        assertTrue(endpoint.getAliases().contains("opencode"));
        assertTrue(endpoint.getAliases().contains("opencode-zen"));
        assertTrue(ProviderCreditEndpoints.bearerBrands().contains("opencode"));
        assertTrue(ProviderCreditEndpoints.bearerBrands().contains("opencode-go"));
    }

    /**
     * 同源判定：Go / Zen 官方上游地址放行，别家站点不放行。
     */
    @Test
    void sameOriginAcceptsOfficialBaseUrlsOnly() {
        assertTrue(ProviderCreditEndpoints.sameOrigin("opencode-go",
                "https://opencode.ai/inference/go/openai/v1"));
        assertTrue(ProviderCreditEndpoints.sameOrigin("opencode", "https://opencode.ai/zen/v1"));
        assertFalse(ProviderCreditEndpoints.sameOrigin("opencode", "https://api.example.com/v1"));
        assertFalse(ProviderCreditEndpoints.sameOrigin("opencode-go", "https://api.example.com/v1"));
    }

    /**
     * 表达式求值：上游「已用百分比」翻成「剩余百分比」，币种为 %。
     */
    @Test
    void expressionTurnsUsedPercentIntoRemaining() {
        CreditResult result = CreditExpressions.parse(
                "!usage.rolling.percent; !usage.weekly.percent; !usage.monthly.percent",
                USAGE_RESPONSE, "host", "%");
        assertTrue(result.isOk(), () -> "解析失败: " + result.getError());
        List<BalancePart> parts = result.getParts();
        assertEquals(3, parts.size());
        assertEquals(0, parts.get(0).getAmount().compareTo(new BigDecimal("99")));
        assertEquals(0, parts.get(1).getAmount().compareTo(new BigDecimal("75")));
        assertEquals(0, parts.get(2).getAmount().compareTo(new BigDecimal("71")));
        assertEquals("usage.rolling.percent", parts.get(0).getLabel());
        assertEquals("usage.weekly.percent", parts.get(1).getLabel());
        assertEquals("usage.monthly.percent", parts.get(2).getLabel());
        assertEquals("%", parts.get(0).getCurrency());
        // 主余额取第一段：rolling 窗口剩余 99%
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("99")));
        assertEquals("%", result.getCurrency());
    }
}

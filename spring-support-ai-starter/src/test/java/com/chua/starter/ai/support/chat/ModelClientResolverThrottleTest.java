package com.chua.starter.ai.support.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 供应商限流口径解析用例：同步限制 → 手动限制 → 表单字段，逐维度回退。
 *
 * @author CH
 * @since 4.0.0.52
 */
class ModelClientResolverThrottleTest {

    /**
     * 同步落库的官方限制优先于手动与表单。
     */
    @Test
    @DisplayName("同步限制优先：declared 压过 limits_manual 与表单")
    void syncedDeclaredWins() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of(
                "concurrency", 8,
                "requests_per_minute", 600)));
        extra.put("limits_manual", Map.of("concurrency", 2, "requests_per_minute", 60));
        extra.put("max_concurrency", 3);
        extra.put("requests_per_minute", 30);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(8, limits.concurrency());
        assertEquals(600, limits.rpm());
    }

    /**
     * 同步只拿到计费口径、没有数值时，回退到手动限制。
     */
    @Test
    @DisplayName("declared 只有计费口径：回退到 limits_manual")
    void fallsBackToManualWhenDeclaredHasNoNumbers() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of("billing_basis", "按 token")));
        extra.put("limits_manual", Map.of("concurrency", 4, "requests_per_minute", 300));
        extra.put("max_concurrency", 3);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(4, limits.concurrency());
        assertEquals(300, limits.rpm());
    }

    /**
     * 两级都没配置时，沿用既有表单字段，行为与改造前一致。
     */
    @Test
    @DisplayName("都没配置：回退到 max_concurrency / requests_per_minute")
    void fallsBackToFormFields() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of("source", "openai")));
        extra.put("max_concurrency", 3);
        extra.put("requests_per_minute", 30);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(3, limits.concurrency());
        assertEquals(30, limits.rpm());
    }

    /**
     * 逐维度回退：同步只给了并发，频率仍取表单值。
     */
    @Test
    @DisplayName("逐维度回退：并发取同步值，频率取表单值")
    void fallsBackPerDimension() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of("concurrency", 5)));
        extra.put("max_concurrency", 3);
        extra.put("requests_per_minute", 30);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(5, limits.concurrency());
        assertEquals(30, limits.rpm());
    }

    /**
     * JSON 落库后数字可能以字符串形式读回，解析要兼容。
     */
    @Test
    @DisplayName("字符串数值兼容：来自 JSON 的 \"7\" / \"550\"")
    void parsesStringValues() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of(
                "concurrency", "7",
                "requests_per_minute", "550")));

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(7, limits.concurrency());
        assertEquals(550, limits.rpm());
    }

    /**
     * 0 表示未配置，继续回退；不能把 0 当成「零并发」把供应商锁死。
     */
    @Test
    @DisplayName("0 视为未配置：继续回退到表单值")
    void zeroFallsThrough() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of("concurrency", 0)));
        extra.put("limits_manual", Map.of("concurrency", 0));
        extra.put("max_concurrency", 3);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(3, limits.concurrency());
    }

    /**
     * 只取账号级 concurrency：按模型的 model_concurrency 不参与供应商级闸门。
     */
    @Test
    @DisplayName("model_concurrency 不参与：只有按模型并发时回退到表单值")
    void ignoresModelConcurrency() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("declared", Map.of(
                "model_concurrency", Map.of("deepseek-flash", 2500))));
        extra.put("max_concurrency", 6);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(6, limits.concurrency());
        assertEquals(0, limits.rpm());
    }

    /**
     * 只认 declared：observed 是上游响应头的瞬时观测，不作固定限制。
     */
    @Test
    @DisplayName("observed 不参与：响应头观测不覆盖表单值")
    void ignoresObserved() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("provider_limits", Map.of("observed", Map.of("requests_per_minute", 1200)));
        extra.put("requests_per_minute", 30);

        ModelClientResolver.ThrottleLimits limits = ModelClientResolver.resolveThrottleLimits(extra);

        assertEquals(30, limits.rpm());
    }
}

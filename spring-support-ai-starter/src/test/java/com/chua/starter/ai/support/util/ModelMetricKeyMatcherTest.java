package com.chua.starter.ai.support.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelMetricKeyMatcher} 的用例。
 *
 * <p>用例分两边：**必须认成同一模型**的（只差版本/日期/别名后缀）与
 * **必须拒绝**的（差的是规格词或推理档位，套上去就是别的模型的数字）。
 * 只测「认得出」是不够的 —— 一个双向 contains 的实现也能通过那半边，
 * 而它正是把 {@code glm-5-3-flash-free} 的指标算到 {@code glm-5-3} 上的原因。</p>
 *
 * <p>另外还锁住「结果确定性」：同一输入在不同插入顺序、不同候选数量下必须命中同一个候选，
 * 否则指标源每同步一次（表扩容重排）卡片上的数字就会翻面。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class ModelMetricKeyMatcherTest {

    @Test
    @DisplayName("只有版本/日期后缀才算同一模型")
    void acceptsVersionAndDateSuffixes() {
        assertTrue(ModelMetricKeyMatcher.isVariantKey("claude-3-5-sonnet", "claude-3-5-sonnet-20241022"));
        assertTrue(ModelMetricKeyMatcher.isVariantKey("deepseek-v4-1-flash", "deepseek-v4-1-flash-1017"));
        assertTrue(ModelMetricKeyMatcher.isVariantKey("glm-5", "glm-5-3"));
        // 版本后缀在本地一侧：候选是基座，请求键更长
        assertTrue(ModelMetricKeyMatcher.isVariantKey("muse-wonder-1-0", "muse-wonder"));
        // 同义别名
        assertTrue(ModelMetricKeyMatcher.isVariantKey("qwen3-8-max", "qwen3-8-max-latest"));
    }

    @Test
    @DisplayName("规格词与推理档位一律拒绝：那是另一个模型/另一档数字")
    void rejectsSpecAndReasoningSuffixes() {
        String base = "gpt-5-4";
        String[] others = {
                "gpt-5-4-mini", "gpt-5-4-nano", "gpt-5-4-pro",
                "gemini-3-5-flash-lite",
                "glm-5-3-flash", "glm-5-3-flash-free",
                "gpt-5-3-codex-spark", "nemotron-3-5-super-vl-free",
                "claude-opus-5-5-medium", "claude-opus-5-5-low", "claude-opus-5-5-xhigh",
                "gpt-5-5-instant-06-26", "deepseek-v4-1-flash-1017-non-reasoning",
                "kimi-k3-1-pro", "qwen3-8-flash-next", "nova-2-0-lite"
        };
        for (String other : others) {
            assertFalse(ModelMetricKeyMatcher.isVariantKey(base, other), "不应把 " + other + " 认成 " + base);
            assertFalse(ModelMetricKeyMatcher.isVariantKey(other, base), "不应把 " + base + " 认成 " + other);
        }
    }

    @Test
    @DisplayName("过短的名字不做变体匹配，避免 o3 命中 o3-mini 之类")
    void rejectsTooShortBaseKey() {
        assertFalse(ModelMetricKeyMatcher.isVariantKey("o3", "o3-mini"));
        assertFalse(ModelMetricKeyMatcher.isVariantKey("o3", "o3-pro-20241022"));
    }

    @Test
    @DisplayName("同键不算变体：精确匹配由调用方先做")
    void sameKeyIsNotVariant() {
        assertFalse(ModelMetricKeyMatcher.isVariantKey("gpt-5-4", "gpt-5-4"));
    }

    @Test
    @DisplayName("多个候选取长度差最小的：不拿推理档位的速度冒充基础模型")
    void picksNearestCandidate() {
        // 真实数据里的形态：基础模型本身缺失，只剩 5 个带档位的记录和一个带版本号的记录
        List<String> keys = Arrays.asList(
                "claude-fable-5-2-medium", "claude-fable-5-2-xhigh", "claude-fable-5-2-low",
                "claude-fable-5-2", "claude-fable-5-2-high");
        assertEquals("claude-fable-5-2",
                ModelMetricKeyMatcher.selectNearest("claude-fable-5", keys, s -> s));
    }

    @Test
    @DisplayName("没有合法候选时返回空，宁可没有数字也不编一个")
    void returnsNullWhenNoLegalCandidate() {
        List<String> keys = Arrays.asList("gpt-5-5-instant-06-26", "gpt-5-3-codex");
        assertNull(ModelMetricKeyMatcher.selectNearest("gpt-5", keys, s -> s));
        assertNull(ModelMetricKeyMatcher.selectNearest("gpt-5", Collections.<String>emptyList(), s -> s));
        assertNull(ModelMetricKeyMatcher.<String>selectNearest("gpt-5", null, s -> s));
    }

    @Test
    @DisplayName("结果与候选顺序无关：指标源扩容重排后不能翻面")
    void resultIsIndependentOfCandidateOrder() {
        List<String> keys = new ArrayList<>(Arrays.asList(
                "claude-opus-5-5-low", "claude-opus-5-5", "claude-opus-5-5-xhigh",
                "claude-opus-5-5-high", "claude-opus-5-5-medium"));
        String forward = ModelMetricKeyMatcher.selectNearest("claude-opus-5", keys, s -> s);
        Collections.reverse(keys);
        String backward = ModelMetricKeyMatcher.selectNearest("claude-opus-5", keys, s -> s);
        assertEquals("claude-opus-5-5", forward);
        assertEquals(forward, backward);
    }

    @Test
    @DisplayName("长度差相同时按字典序定序，结果唯一")
    void tieBreaksByLexicographicOrder() {
        List<String> keys = Arrays.asList("glm-5-9", "glm-5-2");
        assertEquals("glm-5-2", ModelMetricKeyMatcher.selectNearest("glm-5", keys, s -> s));
        Collections.reverse(keys);
        assertEquals("glm-5-2", ModelMetricKeyMatcher.selectNearest("glm-5", keys, s -> s));
    }
}

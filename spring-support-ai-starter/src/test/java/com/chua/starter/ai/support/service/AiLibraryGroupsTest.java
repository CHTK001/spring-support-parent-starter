package com.chua.starter.ai.support.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分组口径 {@link AiLibraryGroups} 测试。
 *
 * <p>验证组 III（多分组检索）的后端语义：</p>
 * <ol>
 *   <li>不传 / 空 = 只查 {@code default}（不是全部）</li>
 *   <li>逗号分隔 = 多组，按首次出现去重，保留顺序</li>
 *   <li>空串 / 空白段被合并为 {@code default}，不是空名</li>
 *   <li>超过 {@link AiLibraryGroups#MAX_GROUPS} 截断</li>
 *   <li>同名 / 大小写 / 空白变体落在同一组上（匹配语义）</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
@DisplayName("AiLibraryGroups 分组口径")
class AiLibraryGroupsTest {

    @Test
    @DisplayName("空参 → 只查 default（不等于查全部）")
    void emptyDefaultsToDefaultGroup() {
        assertEquals(List.of("default"), AiLibraryGroups.parse(null));
        assertEquals(List.of("default"), AiLibraryGroups.parse(""));
        assertEquals(List.of("default"), AiLibraryGroups.parse("   "));
    }

    @Test
    @DisplayName("逗号分隔的多分组去重、保顺序")
    void multiGroupsDedupeKeepsOrder() {
        assertEquals(List.of("default"), AiLibraryGroups.parse("default"));
        assertEquals(List.of("a", "b"), AiLibraryGroups.parse("a,b"));
        assertEquals(List.of("a", "b", "c"), AiLibraryGroups.parse("a,b,c"));
        assertEquals(List.of("a", "b", "c"), AiLibraryGroups.parse("b,a,c,a"));
        assertEquals(List.of("a", "b"), AiLibraryGroups.parse("a,a,b"));
    }

    @Test
    @DisplayName("空白段与同义组被归一化到 default / 同名组")
    void whitespaceSegmentsNormalizeToDefault() {
        assertEquals(List.of("default", "a"), AiLibraryGroups.parse(", a ," + "default"));
        assertEquals(List.of("a"), AiLibraryGroups.parse("a,,b"));
        assertEquals(List.of("a", "b"), AiLibraryGroups.parse("a , b"));
    }

    @Test
    @DisplayName("超过 16 组截断，不无限放大检索次数")
    void testMaxGroups() {
        String[] parts = new String[20];
        for (int i = 0; i < 20; i++) {
            parts[i] = String.valueOf((char) ('a' + i));
        }
        List<String> parsed = AiLibraryGroups.parse(String.join(",", parts));
        assertEquals(AiLibraryGroups.MAX_GROUPS, parsed.size());
        assertTrue(parsed.get(parsed.size() - 1).equals("u"));
    }

    @Test
    @DisplayName("忽略大小写匹配")
    void caseInsensitiveMatches() {
        List<String> list = AiLibraryGroups.parse("default");
        assertTrue(AiLibraryGroups.matches("DEFAULT", list));
        assertTrue(AiLibraryGroups.matches(null, list));
        assertTrue(AiLibraryGroups.matches(" ", list));
    }

    @Test
    @DisplayName("不在预期组里 → 不匹配")
    void noMatchOutsideWanted() {
        List<String> list = AiLibraryGroups.parse("default");
        assertTrue(!AiLibraryGroups.matches("a", list));
        assertTrue(!AiLibraryGroups.matches("b", list));
        assertTrue(AiLibraryGroups.matches(null, list));
    }

    @Test
    @DisplayName("目录名安全")
    void sanitize() {
        assertEquals("default", AiLibraryGroups.sanitize(null));
        assertEquals("abc", AiLibraryGroups.sanitize("abc"));
        assertEquals("a_b", AiLibraryGroups.sanitize("a/b"));
        assertEquals("a_b", AiLibraryGroups.sanitize("a\\b"));
        assertEquals("a_b", AiLibraryGroups.sanitize("a:b"));
        assertEquals("a_b", AiLibraryGroups.sanitize("a..b"));
    }
}

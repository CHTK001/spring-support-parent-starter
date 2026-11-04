// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppKeyModelScope} 逐条覆盖测试 —— 「限定模型」与「分组模型」的九种组合全部钉死。
 *
 * <p>范围语义三态（务必区分，写错一格就是「该拦的放过去」或「该放的拦死」）：</p>
 * <ul>
 *   <li>{@code null}：不限制；</li>
 *   <li>空集合：一个都不许（分组不存在 / 已停用）；</li>
 *   <li>非空集合：只许集合内（小写比较）。</li>
 * </ul>
 *
 * <p>组合矩阵（分组范围 × Key 限定模型）：</p>
 * <pre>
 *   分组 null（不限） × Key null（不限） → null（不限）
 *   分组 null         × Key [a]          → [a]
 *   分组 [a,b]        × Key null         → [a,b]
 *   分组 [a,b]        × Key [b,c]        → [b]（交集）
 *   分组 [a,b]        × Key [c]          → 空集（全拒）
 *   分组 空集         × Key null         → 空集
 *   分组 空集         × Key [a]          → 空集
 *   分组抛异常        × Key 任意         → null（按不限处理，保持历史兜底）
 *   未绑分组(groupId null)               → null（只看 Key 自身）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("AppKeyModelScope 模型范围")
class AppKeyModelScopeTest {

    /**
     * 用固定表构造解析器：groupId → 模型集合。
     *
     * @param table 分组表；不在表里的分组返回 {@code null}（= 该分组不限制）
     * @return 解析器
     */
    private static AppKeyModelScope scopeOf(java.util.Map<Integer, Set<String>> table) {
        return new AppKeyModelScope(groupId -> table.get(groupId));
    }

    /**
     * 构造一个带分组与限定模型的 Key。
     *
     * @param groupId 分组
     * @param models  Key 限定模型（逗号分隔）
     * @return key
     */
    private static SysAiAppKey key(Integer groupId, String models) {
        SysAiAppKey key = new SysAiAppKey();
        key.setAppKey("sk-test");
        key.setStatus(1);
        key.setGroupId(groupId);
        key.setModel(models);
        return key;
    }

    /**
     * 生成小写集合。
     *
     * @param values 值
     * @return 集合
     */
    private static Set<String> setOf(String... values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) {
            out.add(v);
        }
        return out;
    }

    @Nested
    @DisplayName("parseCsv")
    class ParseCsv {

        @Test
        @DisplayName("null / 空白 / 全是逗号 → null（等于不限制）")
        void empty() {
            assertNull(AppKeyModelScope.parseCsv(null));
            assertNull(AppKeyModelScope.parseCsv(""));
            assertNull(AppKeyModelScope.parseCsv("   "));
            assertNull(AppKeyModelScope.parseCsv(",,,"));
            assertNull(AppKeyModelScope.parseCsv(" , , "));
        }

        @Test
        @DisplayName("去空白、转小写、去重、保顺序")
        void normalize() {
            assertEquals(setOf("gpt-4o", "claude-3-5-sonnet"),
                    AppKeyModelScope.parseCsv(" GPT-4o , claude-3-5-sonnet , gpt-4o "));
        }

        @Test
        @DisplayName("空段被丢弃但有效段保留")
        void mixedEmptySegments() {
            assertEquals(setOf("a"), AppKeyModelScope.parseCsv(",a,,"));
        }
    }

    @Nested
    @DisplayName("contains / allows")
    class Match {

        @Test
        @DisplayName("忽略大小写与空白")
        void caseInsensitive() {
            Set<String> scope = setOf("gpt-4o");
            assertTrue(AppKeyModelScope.contains(scope, "GPT-4o"));
            assertTrue(AppKeyModelScope.contains(scope, "  gpt-4o  "));
            assertFalse(AppKeyModelScope.contains(scope, "gpt-4o-mini"));
        }

        @Test
        @DisplayName("模型名为 null → 不命中（不能因 null 而放行）")
        void nullModel() {
            assertFalse(AppKeyModelScope.contains(setOf("gpt-4o"), null));
        }

        @Test
        @DisplayName("scope 为 null → 一律放行")
        void nullScopeAllowsAll() {
            assertTrue(AppKeyModelScope.allows(null, "any-model"));
            assertTrue(AppKeyModelScope.allows(null, null));
        }

        @Test
        @DisplayName("scope 为空集 → 一律拒绝")
        void emptyScopeDeniesAll() {
            assertFalse(AppKeyModelScope.allows(new LinkedHashSet<>(), "any-model"));
        }

        @Test
        @DisplayName("scope 为空集且模型为 null → 拒绝")
        void emptyScopeNullModel() {
            assertFalse(AppKeyModelScope.allows(new LinkedHashSet<>(), null));
        }
    }

    @Nested
    @DisplayName("resolve 九种组合")
    class Resolve {

        @Test
        @DisplayName("分组不限 × Key 不限 → null")
        void bothUnlimited() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of());
            assertNull(scope.resolve(key(1, null)));
        }

        @Test
        @DisplayName("分组不限 × Key [a] → [a]")
        void onlyKeyLimited() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of());
            assertEquals(setOf("a"), scope.resolve(key(1, "A")));
        }

        @Test
        @DisplayName("分组 [a,b] × Key 不限 → [a,b]")
        void onlyGroupLimited() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf("a", "b")));
            assertEquals(setOf("a", "b"), scope.resolve(key(1, null)));
        }

        @Test
        @DisplayName("分组 [a,b] × Key [b,c] → 交集 [b]")
        void intersection() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf("a", "b")));
            assertEquals(setOf("b"), scope.resolve(key(1, "b,c")));
        }

        @Test
        @DisplayName("分组 [a,b] × Key [c] → 空集（全拒，不是不限）")
        void disjointIsEmptyNotUnlimited() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf("a", "b")));
            Set<String> resolved = scope.resolve(key(1, "c"));
            assertNotNull(resolved);
            assertTrue(resolved.isEmpty());
            assertFalse(AppKeyModelScope.allows(resolved, "a"));
            assertFalse(AppKeyModelScope.allows(resolved, "c"));
        }

        @Test
        @DisplayName("分组空集（分组不存在/停用）× Key 不限 → 空集")
        void emptyGroupDeniesAll() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, new LinkedHashSet<>()));
            Set<String> resolved = scope.resolve(key(1, null));
            assertTrue(resolved.isEmpty());
        }

        @Test
        @DisplayName("分组空集 × Key [a] → 空集")
        void emptyGroupWithKeyScope() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, new LinkedHashSet<>()));
            assertTrue(scope.resolve(key(1, "a")).isEmpty());
        }

        @Test
        @DisplayName("读取分组范围抛异常 → 按不限处理（保持历史兜底，不因 DB 抖动把所有人挡死）")
        void providerFailureFallsBackToUnlimited() {
            AppKeyModelScope scope = new AppKeyModelScope(groupId -> {
                throw new IllegalStateException("db down");
            });
            assertNull(scope.resolve(key(1, null)));
            assertEquals(setOf("a"), scope.resolve(key(1, "a")));
        }

        @Test
        @DisplayName("未绑分组（groupId null）→ 只看 Key 自身限定模型")
        void noGroup() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf("a")));
            assertNull(scope.resolve(key(null, null)));
            assertEquals(setOf("b"), scope.resolve(key(null, "b")));
        }

        @Test
        @DisplayName("Key 为 null → 不限")
        void nullKey() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf("a")));
            assertNull(scope.resolve((SysAiAppKey) null));
        }

        @Test
        @DisplayName("分组清单里的空白项被丢弃，大小写归一")
        void groupScopeNormalized() {
            AppKeyModelScope scope = scopeOf(java.util.Map.of(1, setOf(" GPT-4O ", "", "Claude-3")));
            assertEquals(setOf("gpt-4o", "claude-3"), scope.groupScope(1));
            assertTrue(AppKeyModelScope.allows(scope.resolve(key(1, null)), "GPT-4o"));
        }
    }

    @Nested
    @DisplayName("filter（模型获取端点的统一过滤）")
    class Filter {

        @Test
        @DisplayName("scope 为 null → 原样返回（新列表，不改入参）")
        void nullScopeKeepsAll() {
            List<String> models = new ArrayList<>(List.of("a", "b"));
            List<String> out = AppKeyModelScope.filter(null, models);
            assertEquals(models, out);
            out.add("c");
            assertEquals(2, models.size());
        }

        @Test
        @DisplayName("scope 非空 → 只留集合内，顺序保持")
        void filters() {
            List<String> models = List.of("gpt-4o", "gpt-4o-mini", "claude-3");
            assertEquals(List.of("gpt-4o", "claude-3"), AppKeyModelScope.filter(setOf("gpt-4o", "claude-3"), models));
        }

        @Test
        @DisplayName("scope 为空集 → 空列表（模型下拉为空，与「一个都不许」一致）")
        void emptyScope() {
            assertTrue(AppKeyModelScope.filter(new LinkedHashSet<>(), List.of("a", "b")).isEmpty());
        }

        @Test
        @DisplayName("入参 null / 空 → 空列表，不抛 NPE")
        void nullOrEmptyItems() {
            assertTrue(AppKeyModelScope.filter(setOf("a"), (List<String>) null).isEmpty());
            assertTrue(AppKeyModelScope.filter(setOf("a"), List.of()).isEmpty());
            assertTrue(AppKeyModelScope.filter(null, (List<String>) null).isEmpty());
        }

        @Test
        @DisplayName("自定义取值函数：对象清单也能过滤（/v1/models 走 ModelDefinition::getId）")
        void customCodeOf() {
            List<String> items = List.of("gpt-4o::GPT-4o", "claude-3::Claude");
            List<String> out = AppKeyModelScope.filter(setOf("gpt-4o"), items,
                    s -> s.substring(0, s.indexOf("::")));
            assertEquals(List.of("gpt-4o::GPT-4o"), out);
        }

        @Test
        @DisplayName("元素取值为 null → 不命中，不会误放行")
        void nullCodeItem() {
            List<String> items = new ArrayList<>();
            items.add(null);
            items.add("a");
            assertEquals(1, AppKeyModelScope.filter(setOf("a"), items).size());
        }

        @Test
        @DisplayName("codeOf 为 null → 抛 NPE（调用约定：必须传取值函数）")
        void nullFunction() {
            assertThrows(NullPointerException.class,
                    () -> AppKeyModelScope.filter(setOf("a"), List.of("a"), null));
        }
    }
}

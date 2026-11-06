package com.chua.starter.collect.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 采集表达式求值测试。
 *
 * <p>覆盖统一变量模型的全部口径，全部用纯内存变量表驱动，不碰数据库与网络：</p>
 * <ul>
 *   <li>{@code ${data.field}} / {@code ${data}} 两种取数写法；</li>
 *   <li>{@code ${list.field}} 行列表投影 —— List&lt;Map&gt; 拍平成该字段的值列表，供
 *       {@code IN (${data.id})} 拼万级 IN 子句；</li>
 *   <li>{@code ${url[N]} / ${url.size()} / ${url.length}} —— start 节点种子 URL 的定位与计数；</li>
 *   <li>{@code ${env.xxx}} —— 流程变量命名空间；</li>
 *   <li>{@code ${data.toJson}} —— 当前数据转 JSON 串（用户明确要求的写法）；</li>
 *   <li>下标越界、未知成员返回 null 而不是抛异常打断流程。</li>
 * </ul>
 *
 * @author chua
 */
class CollectExpressionTest {

    /**
     * 造一份 List&lt;Map&gt; 形态的上游数据。
     *
     * @param count 行数
     * @return 每行含 {@code id} 与 {@code word} 两个字段
     */
    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("word", "w" + i);
            list.add(row);
        }
        return list;
    }

    @Test
    @DisplayName("${data.field} 走行列表投影、${data[0].field} 取首行标量")
    void readsFieldAndWholeTable() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("data", Arrays.asList(row(1, "alpha"), row(2, "beta")));

        // 多行表上的 ${data.word} 是「行列表投影」：拍平成该字段的值列表，
        // 正是 ${data.id} 能直接喂给 IN (${data.id}) 的原因。
        assertEquals(Arrays.asList("alpha", "beta"),
                new ArrayList<>((List<?>) CollectExpression.eval("${data.word}", vars)));
        // 要拿单行标量，用下标定位到行再取字段。
        assertEquals("alpha", CollectExpression.evalText("${data[0].word}", vars));
        assertEquals("beta", CollectExpression.evalText("${data[1].word}", vars));
        assertEquals(2, ((List<?>) CollectExpression.eval("${data}", vars)).size());
    }

    /**
     * 造一行。
     *
     * @param id   id
     * @param word 词
     * @return 单行
     */
    private static Map<String, Object> row(int id, String word) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("word", word);
        return map;
    }

    @Test
    @DisplayName("${list.field} 行列表投影成字段值列表")
    void projectsFieldFromRowList() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("data", rows(3));

        Object projected = CollectExpression.eval("${data.id}", vars);
        assertNotNull(projected);
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>((List<?>) projected));

        assertEquals(Arrays.asList("w1", "w2", "w3"),
                new ArrayList<>((List<?>) CollectExpression.eval("${data.word}", vars)));
    }

    @Test
    @DisplayName("${url[N]} 定位种子 URL、${url.size()} 计条数")
    void indexesSeedUrls() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("url", Arrays.asList("https://a.test/", "https://b.test/"));

        assertEquals("https://a.test/", CollectExpression.evalText("${url[0]}", vars));
        assertEquals("https://b.test/", CollectExpression.evalText("${url[1]}", vars));
        assertEquals(2, CollectExpression.evalExpr("${url.size()}", vars));
        assertEquals(2, CollectExpression.evalExpr("${url.length}", vars));
    }

    @Test
    @DisplayName("下标越界返回 null 而不是抛异常")
    void outOfRangeIndexIsNull() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("url", Arrays.asList("https://a.test/"));

        assertNull(CollectExpression.eval("${url[9]}", vars));
    }

    @Test
    @DisplayName("${env.xxx} 走流程变量命名空间")
    void readsEnvNamespace() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("env", Map.of("myToken", "secret123"));

        assertEquals("secret123", CollectExpression.evalText("${env.myToken}", vars));
        assertTrue(CollectExpression.evalCondition("${env.myToken == 'secret123'}", vars));
    }

    @Test
    @DisplayName("${data.toJson} 把当前数据转成 JSON 串")
    void convertsDataToJson() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("data", rows(2));

        String json = CollectExpression.evalText("${data.toJson}", vars);
        assertNotNull(json);
        assertTrue(json.contains("\"id\""), "JSON 应含字段名 id，实际: " + json);
        assertTrue(json.contains("\"word\""), "JSON 应含字段名 word，实际: " + json);
        assertTrue(json.startsWith("["), "行表转出的应是 JSON 数组，实际: " + json);
    }

    @Test
    @DisplayName("未知成员返回 null")
    void unknownMemberIsNull() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("data", Map.of("id", 1));

        assertNull(CollectExpression.eval("${data.notExist}", vars));
    }

    /**
     * 古诗示例的正文表达式回归：换行必须是真正的换行符。
     *
     * <p>线上曾经把分隔符写成多重转义，求值器按 {@code \n} 的语义把反斜杠保留下来，
     * 结果 54,422 行诗词正文里是可见的 {@code \\\\n} 而不是换行。这里的断言把
     * 「{@code '\n'} 两个字符 = 换行」这个契约钉死：改动 readString 的转义规则会立刻红。</p>
     */
    @Test
    @DisplayName("${join(paragraphs, '\\n')} 用真正的换行连接段落")
    void joinsParagraphsWithRealNewline() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("paragraphs", Arrays.asList("第一句", "第二句"));

        // 反斜杠 + n 两个字符
        String expression = "${join(paragraphs, '" + "\\" + "n')}";
        String joined = CollectExpression.evalText(expression, vars);

        assertEquals("第一句\n第二句", joined);
        assertTrue(joined.contains("\n"), "正文里应当是换行符");
        assertFalse(joined.contains("\\n"), "不应留下字面量反斜杠 n，实际: " + joined);
    }

    /**
     * 反面例子：写成两个反斜杠时，求值结果就是字面量反斜杠 n。
     *
     * <p>这条断言说明「多一层转义」并不会被自动纠正，因此示例 SQL 里的写法必须与
     * 上面那条测试一致，否则错误会安静地写进库。</p>
     */
    @Test
    @DisplayName("多转义一层会留下字面量反斜杠 n")
    void doubleEscapedSeparatorKeepsBackslash() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("paragraphs", Arrays.asList("第一句", "第二句"));

        String expression = "${join(paragraphs, '" + "\\\\" + "n')}";
        String joined = CollectExpression.evalText(expression, vars);

        assertEquals("第一句\\n第二句", joined);
        assertFalse(joined.contains("\n"), "多转义一层不会产生换行");
    }
}

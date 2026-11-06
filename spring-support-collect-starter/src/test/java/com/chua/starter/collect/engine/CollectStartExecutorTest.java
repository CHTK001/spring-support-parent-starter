// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.collect.engine.executor.CollectStartExecutor;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.dto.CollectNodeDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.properties.CollectProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 开始节点种子 URL 解析测试。
 *
 * <p>回归背景：古诗示例的种子在库里曾被写成「反斜杠 + n」两个字面量，而不是真正的换行。
 * 开始节点按 {@code \r?\n} 切分，切不开就把 58 个分片当成 1 条 URL，抓取节点直接以
 * {@code Illegal character in path} 失败。这里把两种输入的差别固化成测试。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class CollectStartExecutorTest {

    /**
     * 构造一个只有 params 的开始节点。
     *
     * @param urls 种子文本
     * @return 可执行节点
     */
    private static CollectNode startNode(String urls) {
        return new CollectNode(new CollectNodeDTO("start", "start", "开始", 40, 160,
                List.of(new CollectParamDTO(CollectStartExecutor.URLS, urls, null)), null));
    }

    /**
     * 构造最小可用的执行上下文（不落库、不打日志）。
     *
     * @return 根上下文
     */
    private static CollectContext context() {
        return new CollectContext(1, 1L, "T1", new CollectProperties(), new CollectSink() {
            @Override
            public void nodeLog(CollectNode node, int iteration, String status, long costMs,
                                String message, String preview) {
                // 测试不需要节点日志
            }

            @Override
            public void record(CollectRecordEntry entry) {
                // 测试不需要落库
            }
        });
    }

    @Test
    @DisplayName("真实换行的多行种子拆成多条 URL，写入 url 列表")
    void splitsRealNewlines() {
        String seed = "https://a.example/poet.tang.0.json\n"
                + "https://a.example/poet.tang.1000.json\n"
                + "https://a.example/poet.tang.2000.json";
        Map<String, Object> variables = new LinkedHashMap<>();

        new CollectStartExecutor().execute(startNode(seed), context(), variables);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) variables.get("url");
        assertEquals(3, urls.size());
        assertEquals("https://a.example/poet.tang.0.json", urls.get(0));
        assertEquals("https://a.example/poet.tang.2000.json", urls.get(2));
        // 输出名默认 data 与 url 指向同一份列表
        assertEquals(urls, variables.get(CollectStartExecutor.DEFAULT_OUTPUT));
    }

    @Test
    @DisplayName("空行与纯空白行被跳过，不产生空 URL")
    void skipsBlankLines() {
        String seed = "https://a.example/1.json\n\n   \nhttps://a.example/2.json\n";
        Map<String, Object> variables = new LinkedHashMap<>();

        new CollectStartExecutor().execute(startNode(seed), context(), variables);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) variables.get("url");
        assertEquals(2, urls.size());
    }

    @Test
    @DisplayName("字面量 \\n 不会切分——库里必须存真实换行，否则整表被当成一条 URL")
    void literalBackslashNIsNotASeparator() {
        String seed = "https://a.example/1.json\\nhttps://a.example/2.json";
        Map<String, Object> variables = new LinkedHashMap<>();

        new CollectStartExecutor().execute(startNode(seed), context(), variables);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) variables.get("url");
        assertEquals(1, urls.size(), "字面量 \\n 不是换行，切不开时只会有 1 条");
        assertTrue(urls.get(0).contains("\\n"), "这条 URL 会带着反斜杠进请求，正是线上失败的原因");
        assertFalse(urls.get(0).startsWith("https://a.example/2.json"));
    }

    @Test
    @DisplayName("种子里的 ${} 表达式先求值，再逐行收集")
    void evaluatesExpressionsBeforeSplitting() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("host", "https://cdn.example");
        String seed = "${host}/1.json\n${host}/2.json";

        new CollectStartExecutor().execute(startNode(seed), context(), variables);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) variables.get("url");
        assertEquals(2, urls.size());
        assertEquals("https://cdn.example/2.json", urls.get(1));
    }
}
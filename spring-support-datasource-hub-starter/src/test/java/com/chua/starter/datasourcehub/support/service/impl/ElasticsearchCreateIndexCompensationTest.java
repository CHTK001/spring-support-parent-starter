package com.chua.starter.datasourcehub.support.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.middleware.ElasticsearchClient;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareEndpoint;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexDetailVO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchFieldDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchIndexDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Elasticsearch 建索引「超时后复核」补偿的行为测试（子类打桩，不连真实实例）。
 *
 * <p><b>为什么必须有这一条</b>：冷态、单节点、小内存实例上建索引会超过客户端超时
 * （896MB 单节点 ES 8.17 冷态建索引 curl 实测 20.6s），但请求其实已在 ES 侧生效。
 * 若不复核就直接报错，用户会以为没建成，却在索引列表/关系图里看到一个「凭空出现」的索引。
 * 该分支依赖「超时」这一真实但难稳定复现的条件，用桩把两种走向钉死。</p>
 *
 * <p>本类沿用仓库「禁止 mockito 等 mock 框架」的约定，用 {@link ElasticsearchClient}
 * 的真实子类覆写请求方法作为桩。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class ElasticsearchCreateIndexCompensationTest {

    /**
     * 请求桩：{@code createIndex} 恒抛「超时」，索引是否存在由 {@link #existsAfter} 决定。
     */
    private static final class StubClient extends ElasticsearchClient {

        /** 桩记录的建索引请求体，用于断言补偿路径没有把入参丢掉 */
        private String lastBody;

        /** 复核时 {@code _cat/indices} 是否已能看到目标索引 */
        private boolean existsAfter = true;

        private StubClient() {
            super(null);
        }

        @Override
        public Target target(Long datasourceId) {
            return new Target(new MiddlewareEndpoint("http://127.0.0.1:9200", "", "", ""));
        }

        @Override
        public JsonObject createIndex(Target target, String index, String body) {
            lastBody = body;
            // 模拟「请求已到达并生效，但响应超过客户端超时」：调用方看到的是超时异常
            throw new IllegalStateException("Elasticsearch 请求失败 /" + index + ": request timed out");
        }

        @Override
        public JsonArray indices(Target target) {
            if (!existsAfter) {
                return Json.getJsonArray("[]");
            }
            return Json.getJsonArray("[{\"index\":\"acc-es-design\",\"health\":\"green\",\"status\":\"open\","
                    + "\"uuid\":\"u1\",\"docs.count\":\"0\",\"store.size\":\"227b\"}]");
        }

        @Override
        public JsonObject index(Target target, String index) {
            return Json.getJsonObject("{\"" + index + "\":{"
                    + "\"settings\":{\"index.number_of_shards\":\"1\",\"index.number_of_replicas\":\"0\"},"
                    + "\"mappings\":{\"properties\":{\"title\":{\"type\":\"text\"}}}}}");
        }
    }

    /**
     * 建索引请求构造器。
     *
     * @param name 索引名
     * @return 请求
     */
    private static ElasticsearchIndexDTO request(String name) {
        return new ElasticsearchIndexDTO(name, 1, 0,
                List.of(new ElasticsearchFieldDTO("title", "text", null, null, null)), 0L);
    }

    @Test
    @DisplayName("建索引超时但索引已存在：按成功处理，返回详情而非抛错")
    void timeoutButIndexExistsIsTreatedAsSuccess() {
        StubClient client = new StubClient();
        client.existsAfter = true;

        ElasticsearchIndexDetailVO detail =
                new ElasticsearchManageServiceImpl(client).createIndex(request("acc-es-design"));

        assertNotNull(detail, "索引已建成时必须返回详情，而不是把超时当失败抛出去");
        assertEquals("acc-es-design", detail.name());
        assertEquals(Integer.valueOf(1), detail.shards(), "复核路径返回的仍是真实索引设置");
        assertEquals(Integer.valueOf(0), detail.replicas());
        assertNotNull(client.lastBody, "建索引请求确实发出去了（只是响应超时）");
    }

    @Test
    @DisplayName("建索引超时且索引确实没建成：原样抛出，不吞错")
    void timeoutAndIndexAbsentStillFails() {
        StubClient client = new StubClient();
        client.existsAfter = false;

        IllegalStateException error = null;
        try {
            new ElasticsearchManageServiceImpl(client).createIndex(request("acc-es-design"));
        } catch (IllegalStateException e) {
            error = e;
        }

        assertNotNull(error, "复核不到索引时必须抛错，否则用户会以为建成了");
        assertTrue(String.valueOf(error.getMessage()).contains("timed out"),
                "错误信息要保留原始超时原因，实际: " + error.getMessage());
    }

    @Test
    @DisplayName("temporary guard: index name 不能为空，先于任何请求被拦下")
    void blankNameIsRejectedBeforeRequest() {
        StubClient client = new StubClient();

        IllegalStateException error = null;
        try {
            new ElasticsearchManageServiceImpl(client).createIndex(request("  "));
        } catch (IllegalStateException e) {
            error = e;
        }

        assertNotNull(error, "空索引名必须在发请求前拦住");
        assertNull(client.lastBody, "被拦下时不应该已经发出建索引请求");
    }
}

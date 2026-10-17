package com.chua.starter.datasourcehub.support.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.middleware.ElasticsearchClient;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareEndpoint;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchFieldDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchIndexDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 重建索引的 mapping 保真测试：只动用户改过的字段，其余字段逐字保留。
 *
 * <p><b>为什么必须有这一条</b>：索引详情里的字段是 {@link ElasticsearchManageServiceImpl} 里
 * {@code flatten} 展开出来的<b>扁平叶子</b>——只有 name / type / analyzer / format / index 五项，
 * 看不见 {@code nested} 类型，也看不见 {@code text} 的 {@code fields}（最常见的 keyword 子字段）。
 * 若重建时照这份扁平入参重新推导整份 mapping，用户「只删一个字段」会把<b>没碰过的字段静默改掉</b>：
 * keyword 子字段丢失、nested 退化成普通 object。这属于破坏性操作的静默数据形状损失，用桩钉死。</p>
 *
 * <p>本类沿用仓库「禁止 mockito 等 mock 框架」的约定，用 {@link ElasticsearchClient} 的真实子类
 * 覆写请求方法作为桩。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class ElasticsearchRebuildMappingTest {

    /**
     * 索引名：与真实样例索引同名，便于对照
     */
    private static final String INDEX = "acc-es-idx";

    /**
     * 现有索引的原始 mapping：刻意包含 text 的 keyword 子字段、nested 类型与 date 格式，
     * 这三样正是扁平化会丢掉的东西
     */
    private static final String RAW_MAPPING = "{"
            + "\"" + INDEX + "\":{"
            + "\"settings\":{\"index.number_of_shards\":\"1\",\"index.number_of_replicas\":\"0\"},"
            + "\"mappings\":{\"properties\":{"
            + "\"title\":{\"type\":\"text\",\"analyzer\":\"standard\","
            + "\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
            + "\"tags\":{\"type\":\"nested\",\"properties\":{"
            + "\"k\":{\"type\":\"keyword\"},\"v\":{\"type\":\"text\"}}},"
            + "\"created\":{\"type\":\"date\",\"format\":\"yyyy-MM-dd\"},"
            + "\"legacy\":{\"type\":\"keyword\"}"
            + "}}}}";

    /**
     * 请求桩：记录建索引请求体与调用次序，不连真实实例
     */
    private static final class StubClient extends ElasticsearchClient {

        /**
         * 按顺序记录的建索引请求体（第 1 次建临时索引，第 2 次建回同名索引）
         */
        private final List<String> bodies = new ArrayList<>();

        /**
         * 调用次序：{@code create:索引} / {@code reindex:源->目标} / {@code delete:索引} / {@code count:索引} / {@code refresh:索引}
         */
        private final List<String> flow = new ArrayList<>();

        /**
         * 每次 {@code _reindex} 携带的 {@code _source.excludes}（被剔数据的字段）
         */
        private final List<List<String>> reindexExcludes = new ArrayList<>();

        private StubClient() {
            super(null);
        }

        @Override
        public Target target(Long datasourceId) {
            return new Target(new MiddlewareEndpoint("http://127.0.0.1:9200", "", "", ""));
        }

        @Override
        public JsonObject index(Target target, String index) {
            return Json.getJsonObject(RAW_MAPPING);
        }

        @Override
        public JsonArray indices(Target target) {
            return Json.getJsonArray("[{\"index\":\"" + INDEX + "\",\"health\":\"green\",\"status\":\"open\","
                    + "\"uuid\":\"u1\",\"docs.count\":\"3\",\"store.size\":\"1kb\"}]");
        }

        @Override
        public JsonObject count(Target target, String index) {
            flow.add("count:" + index);
            return Json.getJsonObject("{\"count\":3}");
        }

        /**
         * 刷新桩。真机上 {@code _reindex} 不刷新目标索引，未刷新文档对 {@code _count} 不可见；
         * 这里把它记进调用次序，好断言「刷新发生在数文档之前」。
         */
        @Override
        public JsonObject refresh(Target target, String index) {
            flow.add("refresh:" + index);
            return Json.getJsonObject("{\"_shards\":{\"successful\":1}}");
        }

        @Override
        public JsonObject createIndex(Target target, String index, String body) {
            bodies.add(body);
            flow.add("create:" + index);
            return Json.getJsonObject("{\"acknowledged\":true}");
        }

        @Override
        public JsonObject deleteIndex(Target target, String index) {
            flow.add("delete:" + index);
            return Json.getJsonObject("{\"acknowledged\":true}");
        }

        @Override
        public JsonObject reindex(Target target, String source, String dest) {
            return reindex(target, source, dest, List.of());
        }

        @Override
        public JsonObject reindex(Target target, String source, String dest, List<String> sourceExcludes) {
            flow.add("reindex:" + source + "->" + dest);
            reindexExcludes.add(new ArrayList<>(sourceExcludes == null ? List.of() : sourceExcludes));
            return Json.getJsonObject("{\"created\":3,\"total\":3,\"failures\":[]}");
        }

        /**
         * 取重建后的新 mapping.properties（同时校验两次建索引用的是同一份 body）
         *
         * @return 新 mapping 的 properties
         */
        JsonObject newProperties() {
            assertEquals(2, bodies.size(), "重建要建两次索引：临时索引 + 建回同名索引");
            assertEquals(bodies.get(0), bodies.get(1), "两次建索引必须用同一份新 mapping");
            return Json.getJsonObject(bodies.get(0)).getJsonObject("mappings").getJsonObject("properties");
        }
    }

    /**
     * 造字段
     *
     * @param name     字段名
     * @param type     类型
     * @param analyzer 分词器
     * @param format   日期格式
     * @return 字段
     */
    private static ElasticsearchFieldDTO field(String name, String type, String analyzer, String format) {
        return new ElasticsearchFieldDTO(name, type, analyzer, format, null);
    }

    /**
     * 索引详情里展开出来的「现状字段」（与 backend flatten 的口径一致）
     *
     * @return 现状字段
     */
    private static List<ElasticsearchFieldDTO> currentFields() {
        List<ElasticsearchFieldDTO> fields = new ArrayList<>();
        fields.add(field("title", "text", "standard", null));
        fields.add(field("tags.k", "keyword", null, null));
        fields.add(field("tags.v", "text", null, null));
        fields.add(field("created", "date", null, "yyyy-MM-dd"));
        fields.add(field("legacy", "keyword", null, null));
        return fields;
    }

    /**
     * 构造重建请求
     *
     * @param fields 用户提交的完整字段列表
     * @return 请求
     */
    private static ElasticsearchIndexDTO request(List<ElasticsearchFieldDTO> fields) {
        return new ElasticsearchIndexDTO(INDEX, 1, 0, fields, 0L);
    }

    @Test
    @DisplayName("搬运次序固定：建临时 -> 搬入 -> 刷新 -> 数文档 -> 删原 -> 建回同名 -> 搬回 -> 刷新 -> 删临时")
    void rebuildKeepsTwoPassOrder() {
        StubClient client = new StubClient();

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(currentFields()));

        List<String> flow = client.flow;
        String tmp = flow.stream().filter(s -> s.startsWith("create:acc-es-idx__rebuild__"))
                .map(s -> s.substring("create:".length())).findFirst().orElse("");
        assertTrue(tmp.startsWith(INDEX + "__rebuild__"), "临时索引要带可识别前缀，便于失败后人工恢复：" + flow);
        assertEquals(List.of(
                "count:" + INDEX,
                "create:" + tmp,
                "reindex:" + INDEX + "->" + tmp,
                "refresh:" + tmp,
                "count:" + tmp,
                "delete:" + INDEX,
                "create:" + INDEX,
                "reindex:" + tmp + "->" + INDEX,
                "refresh:" + INDEX,
                "delete:" + tmp), flow);
    }

    @Test
    @DisplayName("删掉的字段连 _source 数据一起剔：只剔被删的，且只在搬入临时索引时剔")
    void droppedFieldDataIsExcludedFromReindex() {
        StubClient client = new StubClient();
        List<ElasticsearchFieldDTO> fields = currentFields();
        fields.removeIf(f -> "legacy".equals(f.name()));

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(fields));

        assertEquals(2, client.reindexExcludes.size(), "两次搬运都要记录");
        assertEquals(List.of("legacy"), client.reindexExcludes.get(0),
                "搬入临时索引时必须把被删字段作为 _source.excludes 传下去，"
                        + "否则目标索引的动态映射会把字段又加回来（用户看到「删了又回来」）");
        assertEquals(List.of(), client.reindexExcludes.get(1),
                "搬回时临时索引里已经没有该字段的数据，无需再剔");
    }

    @Test
    @DisplayName("没有删字段时不给 _reindex 传 excludes（不做无谓的 _source 过滤）")
    void noExcludesWhenNothingDropped() {
        StubClient client = new StubClient();

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(currentFields()));

        assertEquals(List.of(), client.reindexExcludes.get(0));
        assertEquals(List.of(), client.reindexExcludes.get(1));
    }

    @Test
    @DisplayName("刷新必须在「数文档」之前：_reindex 不刷新目标，漏了会把正常重建误判成文档数不一致")
    void refreshHappensBeforeCounting() {
        StubClient client = new StubClient();

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(currentFields()));

        List<String> flow = client.flow;
        String tmp = flow.stream().filter(s -> s.startsWith("create:acc-es-idx__rebuild__"))
                .map(s -> s.substring("create:".length())).findFirst().orElse("");
        int refreshed = flow.indexOf("refresh:" + tmp);
        int counted = flow.indexOf("count:" + tmp);
        assertTrue(refreshed >= 0, "临时索引搬完必须显式刷新：" + flow);
        assertTrue(counted >= 0, "搬完要数文档做一致性校验：" + flow);
        assertTrue(refreshed < counted,
                "刷新必须在数文档之前，否则未刷新的文档数会被读成 0 而中止重建：" + flow);
    }

    @Test
    @DisplayName("用户什么都没改：nested 类型与 text 的 keyword 子字段逐字保留")
    void untouchedFieldsKeepRawDefinition() {
        StubClient client = new StubClient();

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(currentFields()));
        JsonObject properties = client.newProperties();

        JsonObject title = properties.getJsonObject("title");
        assertNotNull(title, "未触碰的 title 必须还在");
        assertEquals("standard", String.valueOf(title.get("analyzer")));
        JsonObject keyword = title.getJsonObject("fields") == null
                ? null : title.getJsonObject("fields").getJsonObject("keyword");
        assertNotNull(keyword, "text 的 keyword 子字段不能被扁平化丢掉");
        assertEquals("256", String.valueOf(keyword.get("ignore_above")), "子字段细节也要逐字保留");

        JsonObject tags = properties.getJsonObject("tags");
        assertNotNull(tags, "未触碰的 nested 字段必须还在");
        assertEquals("nested", String.valueOf(tags.get("type")), "nested 不能被退化成 object");
        assertEquals("keyword",
                String.valueOf(tags.getJsonObject("properties").getJsonObject("k").get("type")));

        assertEquals("yyyy-MM-dd", String.valueOf(properties.getJsonObject("created").get("format")),
                "date 的 format 要保留");
        assertEquals("keyword", String.valueOf(properties.getJsonObject("legacy").get("type")));
    }

    @Test
    @DisplayName("删掉一个字段：只有它消失，不带累没碰过的字段，空掉的父层也清掉")
    void removedFieldDisappearsAndEmptyParentIsPruned() {
        StubClient client = new StubClient();
        List<ElasticsearchFieldDTO> fields = new ArrayList<>();
        fields.add(field("title", "text", "standard", null));
        fields.add(field("created", "date", null, "yyyy-MM-dd"));

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(fields));
        JsonObject properties = client.newProperties();

        String json = Json.toJson(properties);
        assertFalse(json.contains("\"tags\""), "tags 的两个叶子都删了，nested 父层不能留空壳：" + json);
        assertFalse(json.contains("\"legacy\""), "legacy 应被删除：" + json);
        assertNotNull(properties.getJsonObject("title"), "未触碰的 title 不能被顺手删掉");
        assertNotNull(properties.getJsonObject("title").getJsonObject("fields"),
                "删别的字段不该动到 title 的 keyword 子字段");
        assertEquals("yyyy-MM-dd", String.valueOf(properties.getJsonObject("created").get("format")));
    }

    @Test
    @DisplayName("改一个字段的类型：只有它换成新定义，别的字段仍逐字保留")
    void changedFieldIsReplacedOthersKept() {
        StubClient client = new StubClient();
        List<ElasticsearchFieldDTO> fields = currentFields();
        fields.set(4, field("legacy", "long", null, null));

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(fields));
        JsonObject properties = client.newProperties();

        assertEquals("long", String.valueOf(properties.getJsonObject("legacy").get("type")), "改类型要真的生效");
        assertNotNull(properties.getJsonObject("title").getJsonObject("fields"),
                "改 legacy 不该把 title 的 keyword 子字段一起冲掉");
        assertEquals("nested", String.valueOf(properties.getJsonObject("tags").get("type")));
    }

    @Test
    @DisplayName("新增一个字段：写进去，且不影响现有字段的原始定义")
    void addedFieldIsWritten() {
        StubClient client = new StubClient();
        List<ElasticsearchFieldDTO> fields = currentFields();
        fields.add(field("extra", "keyword", null, null));

        new ElasticsearchManageServiceImpl(client).rebuildIndex(request(fields));
        JsonObject properties = client.newProperties();

        assertNotNull(properties.getJsonObject("extra"), "新增字段必须写进新 mapping");
        assertEquals("keyword", String.valueOf(properties.getJsonObject("extra").get("type")));
        assertNotNull(properties.getJsonObject("title").getJsonObject("fields"),
                "新增字段不该动到 title 的 keyword 子字段");
        assertEquals("nested", String.valueOf(properties.getJsonObject("tags").get("type")));
    }
}

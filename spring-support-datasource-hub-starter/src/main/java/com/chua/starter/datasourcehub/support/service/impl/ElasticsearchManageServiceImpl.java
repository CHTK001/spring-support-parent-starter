package com.chua.starter.datasourcehub.support.service.impl;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.middleware.ElasticsearchClient;
import com.chua.starter.datasourcehub.support.model.ElasticsearchClusterVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchFieldVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchGraphVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexDetailVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchShardVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchClusterSettingsDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchFieldDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchIndexDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.service.ElasticsearchManageService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 管理实现
 * <p>
 * 索引在 ES 里同时承担「表」与「文档容器」两个角色：建索引 = 建表，mapping.property = 列定义，
 * 因此「设计表」直接落成「新建索引 + 写 properties」，字段类型即 mapping type。
 * </p>
 * <p>
 * 关系图的边只画<b>别名</b>：ES 没有外键，别名是索引之间唯一真实的关联（读写分离、时间切片）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchManageServiceImpl implements ElasticsearchManageService {

    /**
     * 关系图里单个索引最多展开的字段数：字段可能是成百上千，全画出来反而看不清关系
     */
    private static final int GRAPH_FIELD_LIMIT = 60;

    /**
     * Elasticsearch 客户端
     */
    private final ElasticsearchClient client;

    @Override
    public MiddlewareTestVO test(MiddlewareTestDTO request) {
        Long datasourceId = request == null ? null : request.datasourceId();
        try {
            ElasticsearchClient.Target target = client.target(datasourceId);
            JsonObject root = client.root(target);
            JsonObject version = root.getJsonObject("version");
            return new MiddlewareTestVO(true, version == null ? null : str(version.get("number")), "连接成功");
        } catch (IllegalStateException e) {
            return new MiddlewareTestVO(false, null, e.getMessage());
        }
    }

    @Override
    public List<ElasticsearchIndexVO> indices(Long datasourceId) {
        JsonArray rows = client.indices(client.target(datasourceId));
        List<ElasticsearchIndexVO> result = new ArrayList<>();
        for (Object row : rows) {
            JsonObject item = asObject(row);
            if (item == null) {
                continue;
            }
            result.add(new ElasticsearchIndexVO(str(item.get("index")), str(item.get("health")),
                    str(item.get("status")), str(item.get("uuid")), str(item.get("docs.count")),
                    str(item.get("store.size"))));
        }
        return result;
    }

    @Override
    public ElasticsearchIndexDetailVO index(String index, Long datasourceId) {
        String name = requireIndex(index);
        ElasticsearchClient.Target target = client.target(datasourceId);
        JsonObject all = client.index(target, name);
        JsonObject one = all.getJsonObject(name);
        if (one == null) {
            for (Object value : all.values()) {
                one = asObject(value);
                break;
            }
        }
        if (one == null) {
            throw new IllegalStateException("索引不存在: " + name);
        }
        Map<String, Object> settings = flatSettings(one.getJsonObject("settings"));
        JsonObject mapping = one.getJsonObject("mappings");
        List<ElasticsearchFieldVO> fields = new ArrayList<>();
        flatten(mapping == null ? null : mapping.getJsonObject("properties"), "", fields);

        ElasticsearchIndexVO row = findRow(target, name);
        return new ElasticsearchIndexDetailVO(name,
                row == null ? null : row.health(),
                row == null ? null : row.status(),
                intOrNull(settings.get("index.number_of_shards")),
                intOrNull(settings.get("index.number_of_replicas")),
                longOrNull(row == null ? null : row.docs()),
                row == null ? null : row.storeSize(),
                fields,
                settings);
    }

    @Override
    public ElasticsearchIndexDetailVO createIndex(ElasticsearchIndexDTO request) {
        if (request == null) {
            throw new IllegalStateException("新建索引请求不能为空");
        }
        String name = requireIndex(request.name());
        ElasticsearchClient.Target target = client.target(request.datasourceId());

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> settings = new LinkedHashMap<>();
        if (request.shards() != null) {
            settings.put("number_of_shards", request.shards());
        }
        if (request.replicas() != null) {
            settings.put("number_of_replicas", request.replicas());
        }
        if (!settings.isEmpty()) {
            body.put("settings", settings);
        }
        Map<String, Object> properties = properties(request.fields());
        if (!properties.isEmpty()) {
            body.put("mappings", Map.of("properties", properties));
        }
        try {
            client.createIndex(target, name, Json.toJson(body));
        } catch (IllegalStateException e) {
            // 建索引在冷态小实例上可能超过客户端超时（实测 20.6s），但请求往往已在 ES 侧生效；
            // 直接报错会让用户以为没建成，进而在关系图/索引列表里看到一个「凭空出现」的索引。
            // 故超时后按 _cat/indices 复核：索引已存在即视为成功，否则原样抛出。
            if (!exists(target, name)) {
                throw e;
            }
            log.warn("[ES] 建索引 {} 请求超时但索引已存在，按成功处理", name);
        }
        return index(name, request.datasourceId());
    }

    @Override
    public Boolean deleteIndex(String index, Long datasourceId) {
        String name = requireIndex(index);
        client.deleteIndex(client.target(datasourceId), name);
        return true;
    }

    @Override
    public ElasticsearchIndexDetailVO addMapping(ElasticsearchMappingDTO request) {
        if (request == null) {
            throw new IllegalStateException("追加字段请求不能为空");
        }
        String name = requireIndex(request.index());
        Map<String, Object> properties = properties(request.fields());
        if (properties.isEmpty()) {
            throw new IllegalStateException("至少要给出一个字段");
        }
        client.putMapping(client.target(request.datasourceId()), name,
                Json.toJson(Map.of("properties", properties)));
        return index(name, request.datasourceId());
    }

    @Override
    public ElasticsearchClusterVO cluster(Long datasourceId) {
        ElasticsearchClient.Target target = client.target(datasourceId);
        JsonObject root = client.root(target);
        JsonObject health = client.clusterHealth(target);
        JsonArray nodeRows = client.nodes(target);
        JsonObject version = root.getJsonObject("version");

        List<ElasticsearchClusterVO.Node> nodes = new ArrayList<>();
        for (Object row : nodeRows) {
            JsonObject item = asObject(row);
            if (item == null) {
                continue;
            }
            nodes.add(new ElasticsearchClusterVO.Node(str(item.get("name")), str(item.get("ip")),
                    str(item.get("node.role")), str(item.get("master")), str(item.get("heap.percent")),
                    str(item.get("cpu")), str(item.get("load_1m")), str(item.get("version"))));
        }
        return new ElasticsearchClusterVO(str(root.get("cluster_name")), str(health.get("status")),
                intOrNull(health.get("number_of_nodes")), intOrNull(health.get("number_of_data_nodes")),
                intOrNull(health.get("active_shards")), intOrNull(health.get("unassigned_shards")),
                intOrNull(health.get("initializing_shards")), intOrNull(health.get("relocating_shards")),
                version == null ? null : str(version.get("number")), nodes, effectiveSettings(target));
    }

    @Override
    public List<ElasticsearchShardVO> shards(Long datasourceId) {
        JsonArray rows = client.shards(client.target(datasourceId));
        List<ElasticsearchShardVO> result = new ArrayList<>();
        for (Object row : rows) {
            JsonObject item = asObject(row);
            if (item == null) {
                continue;
            }
            result.add(new ElasticsearchShardVO(str(item.get("index")), str(item.get("shard")),
                    str(item.get("prirep")), str(item.get("state")), str(item.get("docs")),
                    str(item.get("store")), str(item.get("node"))));
        }
        return result;
    }

    @Override
    public ElasticsearchClusterVO updateClusterSettings(ElasticsearchClusterSettingsDTO request) {
        if (request == null || (isEmpty(request.persistent()) && isEmpty(request.transientSettings()))) {
            throw new IllegalStateException("persistent 与 transient 至少要给一项");
        }
        ElasticsearchClient.Target target = client.target(request.datasourceId());
        Map<String, Object> body = new LinkedHashMap<>();
        if (!isEmpty(request.persistent())) {
            body.put("persistent", request.persistent());
        }
        if (!isEmpty(request.transientSettings())) {
            body.put("transient", request.transientSettings());
        }
        client.updateClusterSettings(target, Json.toJson(body));
        return cluster(request.datasourceId());
    }

    @Override
    public ElasticsearchGraphVO graph(Long datasourceId) {
        ElasticsearchClient.Target target = client.target(datasourceId);
        JsonObject root = client.root(target);
        JsonObject mappings = client.mappings(target);
        JsonObject aliases = client.aliases(target);

        List<ElasticsearchGraphVO.Node> nodes = new ArrayList<>();
        List<ElasticsearchGraphVO.Edge> edges = new ArrayList<>();
        nodes.add(new ElasticsearchGraphVO.Node("cluster", str(root.get("cluster_name")), "cluster", null));

        for (Object rowObj : client.indices(target)) {
            JsonObject row = asObject(rowObj);
            if (row == null) {
                continue;
            }
            String name = str(row.get("index"));
            if (name == null) {
                continue;
            }
            String indexId = "idx:" + name;
            nodes.add(new ElasticsearchGraphVO.Node(indexId,
                    name + " (" + str(row.get("docs.count")) + " docs)", "index", "cluster"));

            JsonObject mapping = mappings.getJsonObject(name);
            JsonObject properties = mapping == null ? null : mapping.getJsonObject("mappings");
            List<ElasticsearchFieldVO> fields = new ArrayList<>();
            flatten(properties == null ? null : properties.getJsonObject("properties"), "", fields);
            int limit = Math.min(fields.size(), GRAPH_FIELD_LIMIT);
            for (int i = 0; i < limit; i++) {
                ElasticsearchFieldVO field = fields.get(i);
                nodes.add(new ElasticsearchGraphVO.Node("fld:" + name + "." + field.name(),
                        field.name() + ": " + field.type(), "field", indexId));
            }

            JsonObject aliasObj = aliases.getJsonObject(name);
            JsonObject aliasMap = aliasObj == null ? null : aliasObj.getJsonObject("aliases");
            if (aliasMap != null) {
                for (String alias : aliasMap.keySet()) {
                    nodes.add(new ElasticsearchGraphVO.Node("als:" + alias, alias, "alias", "cluster"));
                    edges.add(new ElasticsearchGraphVO.Edge("als:" + alias, indexId, "alias"));
                }
            }
        }
        return new ElasticsearchGraphVO(nodes, edges);
    }

    /**
     * 把字段定义转成 ES 的 properties 结构
     */
    private Map<String, Object> properties(List<ElasticsearchFieldDTO> fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (fields == null) {
            return properties;
        }
        for (ElasticsearchFieldDTO field : fields) {
            if (field == null || field.name() == null || field.name().isBlank()) {
                continue;
            }
            String name = field.name().trim();
            String type = field.type() == null || field.type().isBlank() ? "keyword" : field.type().trim();
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", type);
            if ("text".equals(type) && field.analyzer() != null && !field.analyzer().isBlank()) {
                def.put("analyzer", field.analyzer().trim());
            }
            if ("date".equals(type) && field.format() != null && !field.format().isBlank()) {
                def.put("format", field.format().trim());
            }
            if (Boolean.FALSE.equals(field.index())) {
                def.put("index", false);
            }
            properties.put(name, def);
        }
        return properties;
    }

    /**
     * 递归展开 mapping.properties：对象/嵌套字段继续下钻，叶子字段落成一行
     */
    private void flatten(JsonObject properties, String prefix, List<ElasticsearchFieldVO> out) {
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            JsonObject def = asObject(entry.getValue());
            if (def == null) {
                continue;
            }
            String name = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonObject sub = def.getJsonObject("properties");
            if (sub != null && !sub.isEmpty()) {
                flatten(sub, name, out);
                continue;
            }
            out.add(new ElasticsearchFieldVO(name, str(def.get("type")), str(def.get("analyzer")),
                    str(def.get("format")), boolOrNull(def.get("index"))));
        }
    }

    /**
     * 集群「有效设置」：defaults 被 persistent 覆盖后的结果
     */
    private Map<String, Object> effectiveSettings(ElasticsearchClient.Target target) {
        JsonObject raw = client.clusterSettings(target);
        Map<String, Object> effective = new LinkedHashMap<>();
        JsonObject defaults = raw.getJsonObject("defaults");
        if (defaults != null) {
            effective.putAll(defaults);
        }
        JsonObject persistent = raw.getJsonObject("persistent");
        if (persistent != null) {
            effective.putAll(persistent);
        }
        JsonObject transientSettings = raw.getJsonObject("transient");
        if (transientSettings != null) {
            effective.putAll(transientSettings);
        }
        return effective;
    }

    /**
     * 取索引列表里的对应行（拿健康度与文档数）
     */
    private ElasticsearchIndexVO findRow(ElasticsearchClient.Target target, String name) {
        for (ElasticsearchIndexVO row : indices(target, name)) {
            return row;
        }
        return null;
    }

    /**
     * 索引是否已存在（建索引超时后复核用）
     *
     * @param target 访问目标
     * @param name   索引名
     * @return true 表示 {@code _cat/indices} 里已能看到该索引
     */
    private boolean exists(ElasticsearchClient.Target target, String name) {
        try {
            return findRow(target, name) != null;
        } catch (IllegalStateException e) {
            // 复核请求本身失败：无法证明索引已建，交回调用方按失败处理
            return false;
        }
    }

    /**
     * 按名字取索引行
     */
    private List<ElasticsearchIndexVO> indices(ElasticsearchClient.Target target, String name) {
        List<ElasticsearchIndexVO> result = new ArrayList<>();
        for (Object rowObj : client.indices(target)) {
            JsonObject row = asObject(rowObj);
            if (row != null && name.equals(str(row.get("index")))) {
                result.add(new ElasticsearchIndexVO(str(row.get("index")), str(row.get("health")),
                        str(row.get("status")), str(row.get("uuid")), str(row.get("docs.count")),
                        str(row.get("store.size"))));
            }
        }
        return result;
    }

    /**
     * 索引设置转成扁平 Map（请求时带了 flat_settings=true）
     */
    private Map<String, Object> flatSettings(JsonObject settings) {
        return settings == null ? Map.of() : new LinkedHashMap<>(settings);
    }

    /**
     * 取字符串
     */
    private String requireIndex(String index) {
        if (index == null || index.isBlank()) {
            throw new IllegalStateException("索引名不能为空");
        }
        return index.trim();
    }

    /**
     * 空 Map 判定
     */
    private boolean isEmpty(Map<String, Object> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 把任意 JSON 值规整成对象
     */
    private JsonObject asObject(Object value) {
        if (value instanceof JsonObject object) {
            return object;
        }
        if (value instanceof Map<?, ?> map) {
            return Json.createJsonObject((Map) map);
        }
        if (value instanceof String text && !text.isBlank()) {
            return Json.getJsonObject(text);
        }
        return null;
    }

    /**
     * 空值安全的字符串
     */
    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 空值安全的布尔
     */
    private static Boolean boolOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    /**
     * 空值安全的整数
     */
    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 空值安全的长整数
     */
    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

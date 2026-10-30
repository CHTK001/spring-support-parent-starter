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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        String body = Json.toJson(indexBody(request.shards(), request.replicas(), properties(request.fields())));
        try {
            client.createIndex(target, name, body);
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
    public ElasticsearchIndexDetailVO rebuildIndex(ElasticsearchIndexDTO request) {
        if (request == null) {
            throw new IllegalStateException("重建索引请求不能为空");
        }
        String name = requireIndex(request.name());
        ElasticsearchClient.Target target = client.target(request.datasourceId());
        if (!exists(target, name)) {
            throw new IllegalStateException("索引不存在: " + name);
        }
        // 以索引现有 mapping 为底做增量补丁，而不是按扁平的入参重新推导整份 mapping，
        // 否则「只删一个字段」也会把 nested 类型与 text 的 keyword 子字段静默改掉。
        RebuildPlan plan = planRebuild(target, name, request.fields());
        Map<String, Object> properties = plan.properties();
        if (properties.isEmpty()) {
            throw new IllegalStateException("重建索引至少要保留一个字段；要整个删掉请用删除索引");
        }
        String body = Json.toJson(indexBody(request.shards(), request.replicas(), properties));

        // ES 不能删字段 / 不能改已存在字段的类型，也没有「改索引名」。所以「重建同名索引」只能是：
        //   建临时索引(新 mapping) → 原索引数据搬进临时索引 → 删原索引 → 用新 mapping 建回同名索引 →
        //   临时索引数据搬回同名索引 → 删临时索引。
        // 第 3 步之后原索引短暂不存在；若后续失败，数据仍在临时索引里（日志会打出名字，可人工恢复）。
        String tmp = name + "__rebuild__" + System.currentTimeMillis();
        long sourceCount = docCount(target, name);
        boolean originalDeleted = false;
        try {
            client.createIndex(target, tmp, body);
            // 被删字段连同 _source 数据一起剔除，否则目标索引的动态映射会把字段又加回来
            client.reindex(target, name, tmp, plan.dropped());
            // 必须显式刷新：_reindex 不刷新目标，未刷新的文档对 _count 不可见，
            // 少了这一步下面的文档数校验会稳定读成 0，把正常重建误判成不一致而中止。
            client.refresh(target, tmp);
            long copied = docCount(target, tmp);
            if (sourceCount >= 0 && copied >= 0 && copied != sourceCount) {
                throw new IllegalStateException(
                        "重建中止：临时索引文档数 " + copied + " 与源索引 " + sourceCount + " 不一致");
            }
            client.deleteIndex(target, name);
            originalDeleted = true;
            client.createIndex(target, name, body);
            // 搬回时临时索引里已经没有那些字段的数据，无需再剔
            client.reindex(target, tmp, name);
            client.refresh(target, name);
            client.deleteIndex(target, tmp);
            return index(name, request.datasourceId());
        } catch (RuntimeException e) {
            // 关键保护：如果原索引已在第 3 步被删，但后续失败，用临时索引数据原地恢复原索引
            if (originalDeleted && !exists(target, name) && exists(target, tmp)) {
                log.warn("[ES] 重建索引 {} 在第 3 步（原索引已删）后失败，尝试从临时索引 {} 恢复原索引 ...", name, tmp);
                try {
                    client.reindex(target, tmp, name);
                    client.refresh(target, name);
                    log.info("[ES] 已从临时索引 {} 恢复原索引 {}（mapping 保持原样）", tmp, name);
                } catch (RuntimeException ex) {
                    log.error("[ES] 自动恢复失败，请人工处理：临时索引 {} 完整保留，可手动执行 reindex API 恢复。原因: {}",
                            tmp, ex.getMessage());
                }
            } else if (exists(target, tmp)) {
                log.error("[ES] 重建索引 {} 失败：数据仍保留在临时索引 {}，请按需人工恢复。原因: {}",
                        name, tmp, e.getMessage());
            }
            throw e;
        }
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
     * @param fields 参数
     * @return 返回值
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
     * 拼建索引请求体（settings + mappings），建索引与重建索引共用
     *
     * @param shards     主分片数，可为空
     * @param replicas   副本数，可为空
     * @param properties 字段映射，可为空
     * @return 请求体
     */
    private Map<String, Object> indexBody(Integer shards, Integer replicas, Map<String, Object> properties) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> settings = new LinkedHashMap<>();
        if (shards != null) {
            settings.put("number_of_shards", shards);
        }
        if (replicas != null) {
            settings.put("number_of_replicas", replicas);
        }
        if (!settings.isEmpty()) {
            body.put("settings", settings);
        }
        if (properties != null && !properties.isEmpty()) {
            body.put("mappings", Map.of("properties", properties));
        }
        return body;
    }

    /**
     * 重建索引的字段决策结果
     *
     * @param properties 新 mapping 的 properties
     * @param dropped    被删除、需要连同 {@code _source} 数据一起剔除的字段（扁平点路径）
     */
    private record RebuildPlan(Map<String, Object> properties, List<String> dropped) {
    }

    /**
     * 重建索引的字段规划：以索引现有 mapping 为底，只对本次「新增 / 明改 / 删除」的字段动手。
     *
     * <p><b>为什么不直接用 {@link #properties(List)} 从入参重新推导整份 mapping</b>：索引详情里的
     * 字段是 {@link #flatten} 展开出来的扁平叶子，看不见 {@code nested} 类型，也看不见
     * {@code text} 的 {@code fields}（最常见的 keyword 子字段）。若照扁平入参重建，用户「只删一个
     * 字段」也会把没碰过的字段静默改掉：keyword 子字段丢失、nested 退化成普通 object。</p>
     *
     * <p>故这里逐字段比对：没动的<b>逐字保留</b>原定义；新增或明改的写入新定义；
     * 删掉的字段连同因此变空的中间对象层一起清掉，并记进 {@link RebuildPlan#dropped()}
     * —— 后者要交给 {@code _reindex} 做 {@code _source} 剔除，否则搬过去的数据会被
     * 动态映射把字段又加回来。</p>
     *
     * @param target 访问目标
     * @param name   索引名
     * @param fields 用户提交的完整字段列表（含未改动字段）
     * @return 新 mapping 与要剔除数据的字段
     */
    private RebuildPlan planRebuild(ElasticsearchClient.Target target, String name,
                                    List<ElasticsearchFieldDTO> fields) {
        JsonObject raw = currentProperties(target, name);
        Map<String, Object> next = toMap(raw);

        List<ElasticsearchFieldVO> flat = new ArrayList<>();
        flatten(raw, "", flat);
        Map<String, ElasticsearchFieldVO> current = new LinkedHashMap<>();
        for (ElasticsearchFieldVO field : flat) {
            current.put(field.name(), field);
        }

        Set<String> keep = new LinkedHashSet<>();
        for (ElasticsearchFieldDTO field : fields == null ? List.<ElasticsearchFieldDTO>of() : fields) {
            if (field == null || field.name() == null || field.name().isBlank()) {
                continue;
            }
            String path = field.name().trim();
            keep.add(path);
            ElasticsearchFieldVO original = current.get(path);
            if (original != null && sameExposed(original, field)) {
                // 用户没动这个字段：原样保留（fields 子字段、nested 类型等一并保留）
                continue;
            }
            Map<String, Object> def = definitionOf(field);
            if (def != null) {
                putProperty(next, path, def);
            }
        }
        List<String> dropped = new ArrayList<>();
        for (String path : current.keySet()) {
            if (!keep.contains(path)) {
                removeProperty(next, path);
                dropped.add(path);
            }
        }
        return new RebuildPlan(next, dropped);
    }

    /**
     * 单个字段的 mapping 定义：沿用 {@link #properties(List)} 的口径，
     * 保证「重建时新增/改动字段」与「建索引」走同一套默认规则
     *
     * @param field 字段
     * @return 字段定义；字段名为空时返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> definitionOf(ElasticsearchFieldDTO field) {
        Object def = properties(List.of(field)).get(field.name().trim());
        return def instanceof Map ? (Map<String, Object>) def : null;
    }

    /**
     * 取索引当前的 {@code mappings.properties}
     * @param name 字符串
     * @param target 参数
     * @return 返回值
    */
    private JsonObject currentProperties(ElasticsearchClient.Target target, String name) {
        JsonObject all = client.index(target, name);
        JsonObject one = all.getJsonObject(name);
        if (one == null) {
            for (Object value : all.values()) {
                one = asObject(value);
                break;
            }
        }
        JsonObject mapping = one == null ? null : one.getJsonObject("mappings");
        return mapping == null ? null : mapping.getJsonObject("properties");
    }

    /**
     * JSON 对象转成可变 Map（重建时要在这上面增删字段）
     * @param object 参数
     * @return 返回值
    */
    private Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (object == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof JsonObject child) {
                result.put(entry.getKey(), toMap(child));
            } else if (value instanceof Map<?, ?> map) {
                result.put(entry.getKey(), toMap(asObject(map)));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 用户提交的字段与索引现有字段，在「界面上可改的四项」（类型 / 分词器 / 日期格式 / 是否建索引）上是否一致
     * @param desired 参数
     * @param original 参数
     * @return 返回值
    */
    private boolean sameExposed(ElasticsearchFieldVO original, ElasticsearchFieldDTO desired) {
        String type = desired.type() == null || desired.type().isBlank() ? "keyword" : desired.type().trim();
        String analyzer = "text".equals(type) && desired.analyzer() != null ? desired.analyzer().trim() : "";
        String format = "date".equals(type) && desired.format() != null ? desired.format().trim() : "";
        boolean indexed = !Boolean.FALSE.equals(desired.index());
        return type.equals(defaultKeyword(original.type()))
                && analyzer.equals(nullToEmpty(original.analyzer()))
                && format.equals(nullToEmpty(original.format()))
                && indexed == !Boolean.FALSE.equals(original.index());
    }

    /**
     * 把字段定义写到 dotted path 对应的位置，缺失的中间对象层按需创建
     * @param Map<String 参数
     * @param def 参数
     * @param path 字符串
     * @param root 参数
     * @return 返回值
    */
    private void putProperty(Map<String, Object> root, String path, Map<String, Object> def) {
        String[] parts = path.split("\\.");
        Map<String, Object> parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = childProperties(parent, parts[i], true);
        }
        parent.put(parts[parts.length - 1], def);
    }

    /**
     * 删除 dotted path 对应的字段定义，并清掉因此变空的中间对象层
     * @param Map<String 参数
     * @param path 字符串
     * @param root 参数
     * @return 返回值
    */
    private void removeProperty(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> parent = root;
        for (int i = 0; i < parts.length - 1 && parent != null; i++) {
            parent = childProperties(parent, parts[i], false);
        }
        if (parent != null) {
            parent.remove(parts[parts.length - 1]);
        }
        pruneEmpty(root);
    }

    /**
    /**
     * 取（{@code create=true} 时按需创建）某个对象字段的 {@code properties} 容器
     * @param Map<String 参数
     * @param create 创建
     * @param key 字符串
     * @param parent 参数
     * @return 返回值
    */
    private Map<String, Object> childProperties(Map<String, Object> parent, String key, boolean create) {
        Object node = parent.get(key);
        Map<String, Object> holder;
        if (node instanceof Map<?, ?> map) {
            holder = (Map<String, Object>) map;
        } else if (create) {
            holder = new LinkedHashMap<>();
            parent.put(key, holder);
        } else {
            return null;
        }
        Object sub = holder.get("properties");
        if (sub instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (!create) {
            return null;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        holder.put("properties", created);
        return created;
    }

    /**
    /**
     * 清掉没有子字段的空对象层：ES 不接受空的 {@code properties}
     * @param Map<String 参数
     * @param properties 参数
     * @return 返回值
    */
    private void pruneEmpty(Map<String, Object> properties) {
        for (Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            if (!(entry.getValue() instanceof Map<?, ?> node)) {
                continue;
            }
            Map<String, Object> holder = (Map<String, Object>) node;
            Object sub = holder.get("properties");
            if (sub instanceof Map<?, ?> subMap) {
                Map<String, Object> children = (Map<String, Object>) subMap;
                pruneEmpty(children);
                if (children.isEmpty()) {
                    holder.remove("properties");
                }
            }
            Object type = holder.get("type");
            boolean container = "object".equals(type) || "nested".equals(type);
            if (!holder.containsKey("properties") && (container || holder.isEmpty())) {
                it.remove();
            }
        }
    }

    /**
     * 空值安全的默认类型（无显式类型的字段按 ES 的 keyword 处理）
     * @param value 字符串
     * @return 返回值
    */
    private static String defaultKeyword(String value) {
        return value == null || value.isBlank() ? "keyword" : value;
    }

    /**
     * 空值安全的空串
     * @param value 字符串
     * @return 返回值
    */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 递归展开 mapping.properties：对象/嵌套字段继续下钻，叶子字段落成一行
     * @param out 参数
     * @param prefix 字符串
     * @param properties 参数
     * @return 返回值
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
     * @param target 参数
     * @return 返回值
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
     * @param name 字符串
     * @param target 参数
     * @return 返回值
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
     * 文档数（重建索引前后做一致性校验用）
     *
     * @param target 访问目标
     * @param index  索引名
     * @return 文档数，取不到返回 -1
     */
    private long docCount(ElasticsearchClient.Target target, String index) {
        try {
            Object value = client.count(target, index).get("count");
            return value == null ? -1L : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /**
     * 按名字取索引行
     * @param name 字符串
     * @param target 参数
     * @return 返回值
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
     * @param settings 参数
     * @return 返回值
    */
    private Map<String, Object> flatSettings(JsonObject settings) {
        return settings == null ? Map.of() : new LinkedHashMap<>(settings);
    }

    /**
     * 取字符串
     * @param index 字符串
     * @return 返回值
    */
    private String requireIndex(String index) {
        if (index == null || index.isBlank()) {
            throw new IllegalStateException("索引名不能为空");
        }
        return index.trim();
    }

    /**
     * 空 Map 判定
     * @param Map<String 参数
     * @param map 映射
     * @return 返回值
    */
    private boolean isEmpty(Map<String, Object> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 把任意 JSON 值规整成对象
     * @param value 对象
     * @return 返回值
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
     * @param value 对象
     * @return 返回值
    */
    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 空值安全的布尔
     * @param value 对象
     * @return 返回值
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
     * @param value 对象
     * @return 返回值
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
     * Dev Tools 原始查询：透传任意 ES REST 调用
     *
     * <p>Dev Tools（如 Kibana Console）需要能发任意 REST 请求，本方法将 method + path + body 透传到 ES，
     * 原样返回 ES 的响应正文（不做 JSON 解析，直接返回原始字符串）。</p>
     */
    @Override
    public String raw(String method, String path, String body, Long datasourceId) {
        ElasticsearchClient.Target target = client.target(datasourceId);
        try {
            return client.raw(target, method, path, body);
        } catch (IOException e) {
            throw new RuntimeException(
                    "ES Dev Tools 请求失败 [" + method + " " + path + "]: " + e.getMessage(), e);
        }
    }

    /**
     * 空值安全的长整数
     * @param value 对象
     * @return 返回值
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

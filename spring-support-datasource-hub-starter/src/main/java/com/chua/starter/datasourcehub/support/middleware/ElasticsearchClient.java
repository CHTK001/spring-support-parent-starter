package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch REST 客户端
 * <p>
 * 只做「按数据源拼地址、带凭据、发请求、把响应读成 JSON」这一层；索引/字段/集群的解析与
 * 组装在 {@code ElasticsearchManageServiceImpl} 里完成，与 {@code InfluxdbClient} 的分工一致。
 * </p>
 * <p>
 * 走 Elasticsearch 自身的 HTTP API 而非 ES 官方 Java Client：官方 client 与服务端主版本强绑定
 * （9.x client 拒绝连 8.x 服务端），而这里要连的是任意已登记的 ES 实例；REST 接口则从 5.x 到 9.x
 * 保持稳定。
 * </p>
 * <p>
 * 凭据落在数据源行的 {@code username}/{@code password} 上，开鉴权时走 HTTP Basic；
 * 未开鉴权（{@code xpack.security.enabled=false}）时留空即可。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchClient {

    /**
     * 集群状态变更类操作的超时（建索引、删索引）。
     *
     * <p>共享通道 {@link MiddlewareHttp} 的默认超时为 10s，适用于查询类请求；但建/删索引会
     * 触发集群状态更新，在冷态、单节点、小内存实例上实测可达 20s 以上（896MB、单节点 ES
     * 8.17 冷态建索引 curl 实测 20.6s），10s 会误判为失败——而请求其实已在 ES 侧生效。
     * 故此处单独放宽到 30s；建索引另有「超时后复核」兜底，见
     * {@code ElasticsearchManageServiceImpl#createIndex}。</p>
     */
    private static final Duration MUTATION_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 读取集群「有效设置」的超时。
     *
     * <p>{@code _cluster/settings?include_defaults=true} 会返回上千项设置（单节点 ES 8.17 实测
     * 781 项），正常约 0.2~1.1s，但实例繁忙/GC 时偶发冲高，超过共享通道的 10s 默认超时——
     * 实测日志在 10-16、10-17 共出现 6 次 {@code /_cluster/settings: request timed out}，
     * 每次都会让整页「集群信息」失败。故这条重读单独放宽到 30s。</p>
     */
    private static final Duration CLUSTER_SETTINGS_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * Elasticsearch 访问目标
     *
     * @param endpoint 数据源解析出的端点
     * @author CH
     * @since 4.0.0.42
     */
    public record Target(MiddlewareEndpoint endpoint) {

        /**
         * 拼请求地址
         *
         * @param path API 路径，以 {@code /} 开头
         * @return 完整地址
         */
        public String url(String path) {
            return endpoint.url(path);
        }
    }

    /**
     * 解析数据源对应的访问目标
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 访问目标
     */
    public Target target(Long datasourceId) {
        return target(requireDatasource(datasourceId));
    }

    /**
     * 用数据源行解析访问目标
     *
     * @param ds 数据源配置
     * @return 访问目标
     */
    public Target target(DataSourceVO ds) {
        return new Target(MiddlewareEndpoint.of(ds, ""));
    }

    /**
     * 取指定（或第一个 elasticsearch）数据源
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 数据源配置
     */
    private DataSourceVO requireDatasource(Long datasourceId) {
        return MiddlewareDatasources.require(datasourceService, datasourceId, "elasticsearch", "es");
    }

    /**
     * 请求头：开鉴权时补 Basic 凭据
     *
     * @param target 访问目标
     * @return 请求头
     */
    private Map<String, String> headers(Target target) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (target.endpoint().authed()) {
            String raw = target.endpoint().username() + ":" + target.endpoint().password();
            headers.put("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return headers;
    }

    /**
     * GET 并读成 JSON 对象
     *
     * @param target 访问目标
     * @param path   API 路径
     * @param query  查询参数
     * @return JSON 对象
     */
    public JsonObject get(Target target, String path, Map<String, String> query) {
        return read(target, path, query, () -> MiddlewareHttp.get(target.url(path), query, headers(target)));
    }

    /**
     * GET 并读成 JSON 对象，可指定请求超时（用于大响应/慢读取）
     *
     * @param target  访问目标
     * @param path    API 路径
     * @param query   查询参数
     * @param timeout 请求超时
     * @return JSON 对象
     */
    public JsonObject get(Target target, String path, Map<String, String> query, Duration timeout) {
        return read(target, path, query, () -> MiddlewareHttp.get(target.url(path), query, headers(target), timeout));
    }

    /**
     * GET 并读成 JSON 数组（{@code _cat} 系列返回的就是裸数组）
     *
     * @param target 访问目标
     * @param path   API 路径
     * @param query  查询参数
     * @return JSON 数组
     */
    public JsonArray getArray(Target target, String path, Map<String, String> query) {
        try {
            return Json.getJsonArray(MiddlewareHttp.get(target.url(path), query, headers(target)));
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch 请求失败 " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * POST JSON
     *
     * @param target 访问目标
     * @param path   API 路径
     * @param body   请求体
     * @return JSON 对象
     */
    public JsonObject post(Target target, String path, String body) {
        return read(target, path, Map.of(), () -> MiddlewareHttp.postJson(target.url(path), Map.of(), body, headers(target)));
    }

    /**
     * PUT JSON
     *
     * @param target 访问目标
     * @param path   API 路径
     * @param body   请求体
     * @return JSON 对象
     */
    public JsonObject put(Target target, String path, String body) {
        return read(target, path, Map.of(), () -> MiddlewareHttp.putJson(target.url(path), Map.of(), body, headers(target)));
    }

    /**
     * DELETE
     *
     * @param target 访问目标
     * @param path   API 路径
     * @return JSON 对象
     */
    public JsonObject delete(Target target, String path) {
        return read(target, path, Map.of(), () -> MiddlewareHttp.delete(target.url(path), Map.of(), headers(target)));
    }

    /**
     * 读取根节点：拿到集群名与版本号
     *
     * @param target 访问目标
     * @return 根节点 JSON
     */
    public JsonObject root(Target target) {
        return get(target, "/", Map.of());
    }

    /**
     * 读取集群健康度
     *
     * @param target 访问目标
     * @return 集群健康度
     */
    public JsonObject clusterHealth(Target target) {
        return get(target, "/_cluster/health", Map.of());
    }

    /**
     * 读取集群设置（含默认值，便于页面展示「改了什么」）
     *
     * <p>返回上千项、偶发冲高，走 {@link #CLUSTER_SETTINGS_TIMEOUT} 而非默认 10s。</p>
     *
     * @param target 访问目标
     * @return 集群设置
     */
    public JsonObject clusterSettings(Target target) {
        return get(target, "/_cluster/settings",
                Map.of("flat_settings", "true", "include_defaults", "true"), CLUSTER_SETTINGS_TIMEOUT);
    }

    /**
     * 更新集群设置
     *
     * @param target 访问目标
     * @param body   请求体，形如 {@code {"persistent":{...}}}
     * @return 生效结果
     */
    public JsonObject updateClusterSettings(Target target, String body) {
        return put(target, "/_cluster/settings", body);
    }

    /**
     * 索引列表（带文档数、存储大小与健康度）
     *
     * @param target 访问目标
     * @return {@code _cat/indices} 行
     */
    public JsonArray indices(Target target) {
        return getArray(target, "/_cat/indices",
                Map.of("format", "json", "h", "index,health,status,uuid,pri,rep,docs.count,docs.deleted,store.size"));
    }

    /**
     * 节点列表
     *
     * @param target 访问目标
     * @return {@code _cat/nodes} 行
     */
    public JsonArray nodes(Target target) {
        return getArray(target, "/_cat/nodes",
                Map.of("format", "json", "h", "name,ip,node.role,master,heap.percent,cpu,load_1m,version"));
    }

    /**
     * 分片分配
     *
     * @param target 访问目标
     * @return {@code _cat/shards} 行
     */
    public JsonArray shards(Target target) {
        return getArray(target, "/_cat/shards", Map.of("format", "json", "h", "index,shard,prirep,state,docs,store,node"));
    }

    /**
     * 索引详情：设置 + 字段映射
     *
     * @param target 访问目标
     * @param index  索引名
     * @return 索引详情
     */
    public JsonObject index(Target target, String index) {
        return get(target, "/" + encode(index), Map.of("flat_settings", "true"));
    }

    /**
     * 全量映射：一次拿到所有索引的 mapping（关系图用，避免逐索引请求）
     *
     * @param target 访问目标
     * @return 形如 {@code {"索引名":{"mappings":{...}}}}
     */
    public JsonObject mappings(Target target) {
        return get(target, "/_mapping", Map.of());
    }

    /**
     * 新建索引
     *
     * <p>用 {@link #MUTATION_TIMEOUT} 而非默认 10s：建索引在冷态小实例上可能超过 10s。
     * 即便如此仍可能超时（请求已生效但响应未回），故调用方需按
     * {@code _cat/indices} 复核，见 {@code ElasticsearchManageServiceImpl#createIndex}。</p>
     *
     * @param target 访问目标
     * @param index  索引名
     * @param body   请求体（settings + mappings）
     * @return 新建结果
     */
    public JsonObject createIndex(Target target, String index, String body) {
        return read(target, "/" + encode(index), Map.of(),
                () -> MiddlewareHttp.putJson(target.url("/" + encode(index)), Map.of(), body, headers(target),
                        MUTATION_TIMEOUT));
    }

    /**
     * 删除索引
     *
     * <p>用 {@link #MUTATION_TIMEOUT} 而非默认 10s，理由同
     * {@link #createIndex(Target, String, String)}。</p>
     *
     * @param target 访问目标
     * @param index  索引名
     * @return 删除结果
     */
    public JsonObject deleteIndex(Target target, String index) {
        return read(target, "/" + encode(index), Map.of(),
                () -> MiddlewareHttp.delete(target.url("/" + encode(index)), Map.of(), headers(target), MUTATION_TIMEOUT));
    }

    /**
     * 给已有索引追加字段映射
     *
     * @param target 访问目标
     * @param index  索引名
     * @param body   请求体（properties）
     * @return 生效结果
     */
    public JsonObject putMapping(Target target, String index, String body) {
        return put(target, "/" + encode(index) + "/_mapping", body);
    }

    /**
     * 别名清单：ES 里唯一真实的「索引间关系」
     *
     * @param target 访问目标
     * @return 别名 JSON
     */
    public JsonObject aliases(Target target) {
        return get(target, "/_alias", Map.of());
    }

    /**
     * 文档数
     *
     * @param target 访问目标
     * @param index  索引名
     * @return 文档数
     */
    public JsonObject count(Target target, String index) {
        return get(target, "/" + encode(index) + "/_count", Map.of());
    }

    /**
     * 搜索
     *
     * @param target 访问目标
     * @param index  索引名
     * @param body   查询体
     * @return 搜索结果
     */
    public JsonObject search(Target target, String index, String body) {
        return post(target, "/" + encode(index) + "/_search", body);
    }

    /**
     * 发请求并把响应读成 JSON 对象
     */
    private JsonObject read(Target target, String path, Map<String, String> query, IoCall call) {
        return Json.getJsonObject(callText(path, call));
    }

    /**
     * 发请求并返回原始正文
     */
    private String callText(String path, IoCall call) {
        try {
            return call.get();
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch 请求失败 " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * 路径片段编码：索引名允许中文与逗号，通配符不能被编码掉
     */
    private String encode(String index) {
        return index;
    }

    /**
     * 可抛 IO 异常的请求动作
     */
    @FunctionalInterface
    private interface IoCall {
        /**
         * 执行请求
         *
         * @return 响应正文
         * @throws IOException 连接失败或服务端返回非 2xx
         */
        String get() throws IOException;
    }
}
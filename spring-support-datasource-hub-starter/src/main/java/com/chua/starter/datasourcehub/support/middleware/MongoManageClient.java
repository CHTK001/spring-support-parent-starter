package com.chua.starter.datasourcehub.support.middleware;

import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

/**
 * MongoDB 管理侧客户端：官方同步驱动（{@code mongodb-driver-sync}）。
 *
 * <p>与 Neo4j 走 HTTP 不同，MongoDB 没有稳定的只读 HTTP 查询面，管理面必须引官方驱动。
 * 驱动客户端是线程安全、自带连接池的重量级对象，按连接串缓存复用，不随请求创建销毁
 * （数据源配置变更会换连接串自然换新客户端，旧客户端留待进程退出释放）。</p>
 *
 * <p>数据源行字段口径（见 {@link DatasourceHubConstants#DS_TYPE_MONGODB}）：
 * {@code host}/{@code port} 为服务地址，{@code database} 为默认逻辑库兼认证库，
 * {@code username}/{@code password} 为凭据，留空用户名表示无认证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoManageClient {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "mongodb";

    /**
     * 查询默认返回行数（语句未显式 .limit 时）
     */
    public static final int DEFAULT_ROW_LIMIT = 50;

    /**
     * 单次查询最大返回行数
     */
    public static final int MAX_ROW_LIMIT = 500;

    /**
     * 字符串值截断长度：超长字段只截显示，避免响应体被单个文档撑爆
     */
    public static final int VALUE_TEXT_LIMIT = 4000;

    /**
     * 服务选主与建连超时：探测失败要在秒级返回，而不是驱动默认的 30 秒
     */
    private static final long CONNECT_TIMEOUT_MS = 5000L;

    private final DatasourceService datasourceService;

    /**
     * 按连接串缓存的 MongoClient，键为完整连接串（含凭据），配置变更自然换新键
     */
    private final ConcurrentHashMap<String, MongoClient> clients = new ConcurrentHashMap<>();

    /**
     * 一次调用所需的「客户端 + 逻辑库名」。
     *
     * @param client   复用的驱动客户端
     * @param database 默认逻辑库名
     * @author CH
     * @since 4.0.0.42
     */
    public record Target(MongoClient client, String database) {
    }

    /**
     * 服务端版本探测结果。
     *
     * @param version buildInfo 版本号，取不到时为空串
     * @author CH
     * @since 4.0.0.42
     */
    public record Server(String version) {
    }

    /**
     * 一句查询的执行结果。
     *
     * @param rows      行数据，键 → 归一化后的值
     * @param limited   结果是否被行数上限截断
     * @param totalRows 截断前口径的总行数（拿不到时等于下发行数）
     * @author CH
     * @since 4.0.0.42
     */
    public record Result(List<Map<String, Object>> rows, boolean limited, Integer totalRows) {
    }

    /**
     * 解析数据源对应的访问目标。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 访问目标
     * @throws IllegalStateException 数据源不存在、类型不符或未登记
     */
    public Target target(Long datasourceId) {
        return target(MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE));
    }

    /**
     * 用数据源行（可为未落库的草稿）解析访问目标。
     *
     * @param ds 数据源配置
     * @return 访问目标
     */
    public Target target(DataSourceVO ds) {
        String database = trim(ds.database());
        if (database.isEmpty()) {
            database = DatasourceHubConstants.MONGODB_DEFAULT_DATABASE;
        }
        String host = trim(ds.host());
        int port = ds.port() == null ? DatasourceHubConstants.MONGODB_DEFAULT_PORT : ds.port();
        String user = trim(ds.username());
        String password = ds.password() == null ? "" : ds.password();

        StringBuilder cs = new StringBuilder("mongodb://");
        if (!user.isEmpty()) {
            cs.append(urlEncode(user)).append(':').append(urlEncode(password)).append('@');
        }
        cs.append(host).append(':').append(port).append('/').append(database);
        if (!user.isEmpty()) {
            // 认证库与默认逻辑库一致：数据源行的 database 即认证库
            cs.append("?authSource=").append(database);
        }
        return new Target(clients.computeIfAbsent(cs.toString(), this::createClient), database);
    }

    /**
     * 探测连通性并取版本号。
     *
     * <p>先跑一句不碰业务数据的 {@code {ping: 1}}：地址不通、凭据不对都会在这一步暴露；
     * buildInfo 作为版本号的尽力来源，拿不到不影响探测结论。</p>
     *
     * @param target 访问目标
     * @return 版本信息
     * @throws MongoException 连接失败、凭据不符或命令被拒
     */
    public Server probeServer(Target target) {
        target.client().getDatabase(target.database()).runCommand(new Document("ping", 1));
        String version = "";
        try {
            Document buildInfo = target.client().getDatabase(target.database())
                    .runCommand(new Document("buildInfo", 1));
            Object value = buildInfo.get("version");
            version = value == null ? "" : String.valueOf(value);
        } catch (MongoException e) {
            log.debug("[mongodb] buildInfo 不可用，版本号留空: {}", e.getMessage());
        }
        return new Server(version);
    }

    /**
     * 列出全部逻辑库。
     *
     * @param target 访问目标
     * @return 逻辑库列表（name/sizeOnDisk/empty）
     * @throws MongoException 连接失败或权限不足
     */
    public List<Document> databases(Target target) {
        ListDatabasesIterable<Document> iterable = target.client().listDatabases();
        List<Document> result = new ArrayList<>();
        try (MongoCursor<Document> cursor = iterable.cursor()) {
            while (cursor.hasNext()) {
                result.add(cursor.next());
            }
        }
        return result;
    }

    /**
     * 列出指定逻辑库的集合及其类型与估算文档数。
     *
     * @param target   访问目标
     * @param database 逻辑库名
     * @return 集合列表（name/type/estimatedCount）
     * @throws MongoException 连接失败或权限不足
     */
    public List<Document> collections(Target target, String database) {
        MongoDatabase db = target.client().getDatabase(database);
        List<Document> result = new ArrayList<>();
        try (MongoCursor<Document> cursor = db.listCollections().cursor()) {
            while (cursor.hasNext()) {
                Document info = cursor.next();
                String name = String.valueOf(info.get("name"));
                Document row = new Document("name", name)
                        .append("type", info.get("type") == null ? "collection" : String.valueOf(info.get("type")));
                try {
                    row.append("estimatedCount", db.getCollection(name).estimatedDocumentCount());
                } catch (MongoException e) {
                    // 估算数拿不到不推翻集合存在这一事实
                    log.debug("[mongodb] 集合 {} 文档数估算失败: {}", name, e.getMessage());
                }
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 执行一句只读 shell 查询。
     *
     * <p>命中默认行数上限且未显式 limit 时，补一次 countDocuments 给出截断前总行数
     * （与 Neo4j 控制台的「已截断，服务端共 N 行」口径一致）；聚合管道走驱动
     * {@code aggregate}，统一按 {@value #MAX_ROW_LIMIT} 封顶。</p>
     *
     * @param target     访问目标
     * @param database   当前逻辑库名（shell 里的 {@code db}）
     * @param expression shell 表达式
     * @return 查询结果
     * @throws MongoException 连接失败、权限不足或查询被服务端拒绝
     */
    public Result run(Target target, String database, String expression) {
        MongoShell.Query query = MongoShell.parse(expression);
        String dbName = database == null || database.isBlank() ? target.database() : database.trim();
        MongoCollection<Document> collection = target.client().getDatabase(dbName).getCollection(query.collection());
        Document filter = Document.parse(MongoShell.normalizeJson(query.filter()));

        switch (query.method()) {
            case "find", "findone" -> {
                boolean explicit = query.limit() != null;
                int limit = explicit ? Math.min(query.limit(), MAX_ROW_LIMIT) : DEFAULT_ROW_LIMIT;
                if ("findone".equals(query.method())) {
                    limit = 1;
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                try (MongoCursor<Document> cursor = collection
                        .find(filter)
                        .projection(Document.parse(MongoShell.normalizeJson(query.projection())))
                        .sort(query.sort().isEmpty()
                                ? new Document()
                                : Document.parse(MongoShell.normalizeJson(query.sort())))
                        .skip(query.skip())
                        .limit(limit)
                        .cursor()) {
                    while (cursor.hasNext()) {
                        rows.add(toRow(cursor.next()));
                    }
                }
                if ("findone".equals(query.method())) {
                    return new Result(rows, false, rows.size());
                }
                if (explicit) {
                    return new Result(rows, query.limit() > MAX_ROW_LIMIT, rows.size());
                }
                // 未显式 limit：命中默认上限说明可能还有更多，补计数给出总行数
                if (rows.size() >= DEFAULT_ROW_LIMIT) {
                    long total = countQuietly(collection, filter);
                    return new Result(rows, true, (int) Math.min(total, Integer.MAX_VALUE));
                }
                return new Result(rows, false, rows.size());
            }
            case "count", "countdocuments" -> {
                long count = collection.countDocuments(filter);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("count", count);
                return new Result(List.of(row), false, 1);
            }
            default -> {
                List<Document> pipeline = parsePipeline(query.pipeline());
                List<Map<String, Object>> rows = new ArrayList<>();
                try (MongoCursor<Document> cursor = collection.aggregate(pipeline)
                        .allowDiskUse(true)
                        .cursor()) {
                    while (cursor.hasNext()) {
                        rows.add(toRow(cursor.next()));
                    }
                }
                // 聚合无法预知总数，截断时总行数口径即为下发行数
                return new Result(rows, rows.size() >= MAX_ROW_LIMIT, rows.size());
            }
        }
    }

    /**
     * 按 {@code _id} 取单个文档。
     *
     * <p>先按 ObjectId 解析，失败时按原始字符串兜底（字符串主键集合）。</p>
     *
     * @param target     访问目标
     * @param database   逻辑库名
     * @param collection 集合名
     * @param id         文档 ID（ObjectId 十六进制串或原始主键）
     * @return 文档内容；不存在时返回 null
     * @throws MongoException 连接失败、权限不足或查询被服务端拒绝
     */
    public Map<String, Object> document(Target target, String database, String collection, String id) {
        MongoDatabase db = target.client().getDatabase(database == null || database.isBlank()
                ? target.database() : database.trim());
        Document filter;
        try {
            filter = new Document("_id", new ObjectId(id));
        } catch (IllegalArgumentException e) {
            filter = new Document("_id", id);
        }
        Document doc = db.getCollection(collection).find(filter).first();
        return doc == null ? null : toRow(doc);
    }

    /**
     * 创建带超时的驱动客户端。
     *
     * @param connectionString 连接串
     * @return 客户端（懒连接，不在此刻真正建连）
     */
    private MongoClient createClient(String connectionString) {
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToClusterSettings(builder -> builder
                        .serverSelectionTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(15_000L, TimeUnit.MILLISECONDS))
                .build());
    }

    /**
     * 解析聚合管道数组。
     *
     * <p>{@code Document.parse} 只认对象，顶层数组需包一层壳再取回。
     * {@code $out}/{@code $merge} 会把聚合结果写进其它集合，与「只读管理面」的承诺冲突，
     * 在此直接拒绝；末尾统一追加 {@code $limit} 封顶（AggregateIterable 没有 limit 方法）。</p>
     *
     * @param pipeline 管道 JSON 文本
     * @return 阶段文档列表
     */
    private List<Document> parsePipeline(String pipeline) {
        Document wrapper = Document.parse("{\"pipeline\":" + MongoShell.normalizeJson(pipeline) + "}");
        Object raw = wrapper.get("pipeline");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("aggregate 参数需为阶段数组，如 [{$match:{...}}]");
        }
        List<Document> stages = new ArrayList<>();
        for (Object stage : list) {
            if (!(stage instanceof Document doc)) {
                throw new IllegalArgumentException("aggregate 阶段需为对象: " + stage);
            }
            for (String key : doc.keySet()) {
                if ("$out".equals(key) || "$merge".equals(key)) {
                    throw new IllegalArgumentException("aggregate 不支持写阶段 " + key + "：管理面只读");
                }
            }
            stages.add(doc);
        }
        stages.add(new Document("$limit", MAX_ROW_LIMIT));
        return stages;
    }

    /**
     * 计数兜底：拿不到时回落下发行数，不把整次查询判失败。
     *
     * @param collection 集合
     * @param filter     条件
     * @return 总行数
     */
    private long countQuietly(MongoCollection<Document> collection, Document filter) {
        try {
            return collection.countDocuments(filter);
        } catch (MongoException e) {
            log.debug("[mongodb] 截断计数失败，回落下发行数: {}", e.getMessage());
            return DEFAULT_ROW_LIMIT;
        }
    }

    /**
     * 一行文档归一化为「键 → JSON 友好值」。
     *
     * @param doc 原始文档
     * @return 归一化行
     */
    private Map<String, Object> toRow(Document doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            row.put(entry.getKey(), toJsonValue(entry.getValue()));
        }
        return row;
    }

    /**
     * BSON 值归一化：ObjectId 走十六进制、日期走 ISO 串、二进制走 Base64、
     * 嵌套文档/数组递归，超长字符串截断，其余原样透传。
     *
     * @param value 原始值
     * @return 归一化值
     */
    private Object toJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId oid) {
            return oid.toHexString();
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Decimal128 decimal) {
            return decimal.toString();
        }
        if (value instanceof Binary binary) {
            return Base64.getEncoder().encodeToString(binary.getData());
        }
        if (value instanceof Document doc) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                nested.put(entry.getKey(), toJsonValue(entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>();
            for (Object item : list) {
                nested.add(toJsonValue(item));
            }
            return nested;
        }
        if (value instanceof String text && text.length() > VALUE_TEXT_LIMIT) {
            return text.substring(0, VALUE_TEXT_LIMIT) + "…(已截断)";
        }
        return value;
    }

    /**
     * 连接串用户信息段 URL 编码（空格走 %20 而非 +）。
     *
     * @param text 原文
     * @return 编码结果
     */
    private String urlEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 去空白，null 安全。
     *
     * @param text 原文
     * @return 去空白结果
     */
    private String trim(String text) {
        return text == null ? "" : text.trim();
    }
}

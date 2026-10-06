package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * etcd 客户端（走 etcd 的 gRPC-gateway JSON API，不引 gRPC 依赖）。
 *
 * <p>用到的端点都在 {@code /v3} 下：{@code /kv/range} 列举与读取、{@code /kv/put} 写入、
 * {@code /kv/deleterange} 删除、{@code /lease/*} 租约、{@code /cluster/member/list} 成员、
 * {@code /maintenance/status} 服务端状态。</p>
 *
 * <p>三个实测要点：</p>
 * <ul>
 *   <li>字节字段（key / value / range_end）在 JSON 里一律 **base64**；</li>
 *   <li>数值字段一律以**字符串**返回（{@code "dbSize":"24576"}），必须显式解析；</li>
 *   <li>前缀列举靠 {@code range_end}：把前缀最后一个字节 +1；前缀为空时用 etcd 的约定
 *       {@code key=range_end="\0"}（单个 NUL 字节表示「所有键」）。直接拿前缀当 key 只能取到
 *       恰好等于它的那一个键。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtcdClient {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "etcd";

    /**
     * etcd 默认客户端端口
     */
    public static final int DEFAULT_PORT = 2379;

    /**
     * 列举键的默认上限（页面树一次不拉太多）
     */
    private static final int DEFAULT_LIMIT = 200;

    /**
     * 列举键的硬上限
     */
    private static final int MAX_LIMIT = 5000;

    /**
     * 远端错误文案的截断长度
     */
    private static final int ERROR_TEXT_LIMIT = 300;

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 端点地址 → 鉴权 token。
     *
     * <p>etcd 开启鉴权时，除 {@code /auth/authenticate} 外的请求都要带
     * {@code Authorization: <token>}。token 有效期有限，这里只做「一次连接内复用」，
     * 401 时清掉重取（见 {@link #postJson}）。</p>
     */
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    /**
     * etcd 访问目标：端点 + 鉴权凭据。
     *
     * @param endpoint 端点
     * @param username 用户名，空表示 etcd 未开启鉴权
     * @param password 密码
     */
    public record Target(MiddlewareEndpoint endpoint, String username, String password) {
    }

    /**
     * 服务端状态。
     *
     * @param version     etcd 版本
     * @param leader      leader 成员 ID
     * @param dbSize      数据文件大小（字节）
     * @param dbSizeInUse 实际使用大小（字节）
     * @param raftIndex   raft 已提交日志索引
     * @param raftTerm    raft 任期
     * @param revision    当前全局 revision
     */
    public record Server(String version, String leader, Long dbSize, Long dbSizeInUse,
                         Long raftIndex, Long raftTerm, Long revision) {
    }

    /**
     * 解析要操作的 etcd 端点。
     *
     * @param datasourceId 数据源 ID，为空时取第一个 etcd 数据源
     * @return 访问目标
     * @throws IllegalStateException 数据源不存在或类型不匹配
     */
    public Target target(Long datasourceId) {
        return target(MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE));
    }

    /**
     * 由数据源行构造目标（也可用于即席填写的地址）。
     *
     * <p>etcd 的 {@code database} 字段只用来表达 http/https（与 Neo4j/InfluxDB 一致），
     * 不是库名；填完整 URL 时按 URL 解析并把路径当上下文。</p>
     *
     * @param ds 数据源配置
     * @return 访问目标
     */
    public Target target(DataSourceVO ds) {
        String database = trim(ds.database());
        String user = trim(ds.username());
        String pass = blankToEmpty(ds.password());
        if (isAbsoluteUrl(database)) {
            var uri = URI.create(database);
            return new Target(new MiddlewareEndpoint(uri.getScheme() + "://" + uri.getAuthority(),
                    trimSlash(uri.getPath()), user, pass), user, pass);
        }
        String scheme = "https".equalsIgnoreCase(database) ? "https" : "http";
        int port = ds.port() == null ? DEFAULT_PORT : ds.port();
        return new Target(new MiddlewareEndpoint(scheme + "://" + trim(ds.host()) + (port > 0 ? ":" + port : ""),
                "", user, pass), user, pass);
    }

    /**
     * 读取服务端状态（同时充当连通性探测）。
     *
     * @param target 访问目标
     * @return 服务端状态
     * @throws IllegalStateException 请求失败
     */
    public Server status(Target target) {
        JsonObject json = postJson(target, "/v3/maintenance/status", Map.of());
        return new Server(str(json.get("version")), str(json.get("leader")),
                longOrNull(json.get("dbSize")), longOrNull(json.get("dbSizeInUse")),
                longOrNull(json.get("raftIndex")), longOrNull(json.get("raftTerm")),
                longOrNull(json.getJsonObject("header") == null ? null : json.getJsonObject("header").get("revision")));
    }

    /**
     * 完整探测：状态 + **真正鉴权一次**。
     *
     * <p>必须两步：{@code /v3/maintenance/status} 在 etcd 开启鉴权后**仍然免鉴权**
     * （实测匿名与坏 token 都回 200 正常体），只调它的话**错密码也会报"连接成功"** ——
     * 正是最该避免的假绿。这里补一个必须鉴权的 KV 查询，把凭据真正验一遍
     * （查一个不存在的键即可：鉴权通过则返回空 kvs，不通过则 400/401）。</p>
     *
     * @param target 访问目标
     * @return 服务端状态
     * @throws IllegalStateException 不可达或凭据无效
     */
    public Server probe(Target target) {
        Server server = status(target);
        verifyCredentials(target);
        return server;
    }

    /**
     * 用一个必须鉴权的最小查询验证凭据。
     *
     * @param target 访问目标
     * @throws IllegalStateException 凭据无效或请求失败
     */
    public void verifyCredentials(Target target) {
        range(target, "", 1, true);
    }

    /**
     * 列举集群成员（原始 JSON）。
     *
     * @param target 访问目标
     * @return 成员对象列表
     * @throws IllegalStateException 请求失败
     */
    public List<JsonObject> members(Target target) {
        JsonObject json = postJson(target, "/v3/cluster/member/list", Map.of());
        JsonArray array = json.getJsonArray("members");
        List<JsonObject> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject item = array.getJsonObject(i);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /**
     * 按前缀列举键。
     *
     * @param target   访问目标
     * @param prefix   前缀，空表示所有键
     * @param limit    条数上限，非正时用默认值
     * @param keysOnly 只取键名不取值
     * @return 原始响应（含 kvs / count / more）
     * @throws IllegalStateException 请求失败
     */
    public JsonObject range(Target target, String prefix, int limit, boolean keysOnly) {
        Map<String, Object> body = new LinkedHashMap<>();
        byte[] prefixBytes = (prefix == null ? "" : prefix).getBytes(StandardCharsets.UTF_8);
        body.put("key", base64(prefixBytes.length == 0 ? new byte[]{0} : prefixBytes));
        body.put("range_end", base64(rangeEnd(prefixBytes)));
        body.put("limit", clampLimit(limit));
        body.put("keys_only", keysOnly);
        body.put("sort_order", "ASCEND");
        body.put("sort_target", "KEY");
        return postJson(target, "/v3/kv/range", body);
    }

    /**
     * 读取单个键（不存在时返回空响应）。
     *
     * @param target 访问目标
     * @param key    键名
     * @return 原始响应，kvs 为空表示键不存在
     * @throws IllegalStateException 请求失败
     */
    public JsonObject getKey(Target target, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", base64(key.getBytes(StandardCharsets.UTF_8)));
        return postJson(target, "/v3/kv/range", body);
    }

    /**
     * 写入键（etcd 的 put 即覆盖）。
     *
     * @param target 访问目标
     * @param key    键名
     * @param value  值（UTF-8 文本）
     * @param lease  租约 ID，非正表示不绑定
     * @throws IllegalStateException 请求失败
     */
    public void put(Target target, String key, String value, long lease) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", base64(key.getBytes(StandardCharsets.UTF_8)));
        body.put("value", base64((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        if (lease > 0L) {
            body.put("lease", lease);
        }
        postJson(target, "/v3/kv/put", body);
    }

    /**
     * 删除键。
     *
     * @param target 访问目标
     * @param key    键名
     * @param prefix 为真时按前缀删除（删掉所有以 key 为前缀的键）
     * @return 实际删除的键数量
     * @throws IllegalStateException 请求失败
     */
    public long delete(Target target, String key, boolean prefix) {
        Map<String, Object> body = new LinkedHashMap<>();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        body.put("key", base64(keyBytes));
        if (prefix) {
            body.put("range_end", base64(rangeEnd(keyBytes)));
        }
        JsonObject json = postJson(target, "/v3/kv/deleterange", body);
        Long deleted = longOrNull(json.get("deleted"));
        return deleted == null ? 0L : deleted;
    }

    /**
     * 授予租约。
     *
     * @param target 访问目标
     * @param ttl    TTL 秒数
     * @return 租约 ID
     * @throws IllegalStateException 请求失败或服务端未返回 ID
     */
    public long grantLease(Target target, long ttl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("TTL", ttl);
        JsonObject json = postJson(target, "/v3/lease/grant", body);
        Long id = longOrNull(json.get("ID"));
        if (id == null || id <= 0L) {
            throw new IllegalStateException("etcd 未返回租约 ID: " + abbreviate(json.toJSONString()));
        }
        return id;
    }

    /**
     * 列举租约。
     *
     * @param target 访问目标
     * @return 租约对象列表
     * @throws IllegalStateException 请求失败
     */
    public List<JsonObject> leases(Target target) {
        JsonObject json = postJson(target, "/v3/lease/leases", Map.of());
        JsonArray array = json.getJsonArray("leases");
        List<JsonObject> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject item = array.getJsonObject(i);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /**
     * 查询租约剩余 TTL 与绑定的键。
     *
     * @param target  访问目标
     * @param leaseId 租约 ID
     * @return 原始响应，租约不存在时字段为空
     * @throws IllegalStateException 请求失败
     */
    public JsonObject timeToLive(Target target, long leaseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ID", leaseId);
        body.put("keys", true);
        return postJson(target, "/v3/lease/timetolive", body);
    }

    /**
     * 撤销租约（绑定的键会一并删除）。
     *
     * @param target  访问目标
     * @param leaseId 租约 ID
     * @throws IllegalStateException 请求失败
     */
    public void revokeLease(Target target, long leaseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ID", leaseId);
        postJson(target, "/v3/lease/revoke", body);
    }

    /**
     * 读取健康端点（{@code /health}），用于把「进程在但成员不健康」区分出来。
     *
     * @param target 访问目标
     * @return 健康状态原文，失败时为空串
     */
    public String health(Target target) {
        try {
            return MiddlewareHttp.get(target.endpoint().url("/health"), Map.of());
        } catch (Exception e) {
            log.debug("[etcd] 读取 /health 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * POST 一个 JSON 请求并解析响应。
     *
     * <p>etcd 开启鉴权后除 authenticate 外的请求都要带 token；token 过期会回 401，
     * 这里**清掉缓存重试一次**——否则用户改完 etcd 密码/重启集群后，页面会一直报未授权直到服务重启。</p>
     *
     * @param target 访问目标
     * @param path   接口路径
     * @param body   请求体
     * @return 响应对象
     * @throws IllegalStateException 请求失败
     */
    private JsonObject postJson(Target target, String path, Map<String, Object> body) {
        String url = target.endpoint().url(path);
        // 先把请求头（含鉴权）拿到手：鉴权失败要**原样透出**，
        // 否则会被下面的 catch 再套一层「调用 <本接口> 失败」，页面看到
        // 「调用 etcd .../maintenance/status 失败: 调用 etcd .../auth/authenticate 失败: ...」这种双层壳。
        Map<String, String> heads;
        try {
            heads = headers(target);
        } catch (IllegalStateException e) {
            throw e;
        }
        try {
            return Json.getJsonObject(MiddlewareHttp.postJson(url, Map.of(), Json.toJson(body), heads));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception first) {
            if (looksUnauthorized(first) && !target.username().isEmpty()) {
                tokens.remove(cacheKey(target));
                try {
                    return Json.getJsonObject(
                            MiddlewareHttp.postJson(url, Map.of(), Json.toJson(body), headers(target)));
                } catch (IllegalStateException e) {
                    throw e;
                } catch (Exception second) {
                    throw failure(target.endpoint(), path, second);
                }
            }
            throw failure(target.endpoint(), path, first);
        }
    }

    /**
     * 构造请求头：etcd 未开启鉴权时为空；开启时带 token。
     *
     * @param target 访问目标
     * @return 请求头
     */
    private Map<String, String> headers(Target target) {
        if (target.username().isEmpty()) {
            return Map.of();
        }
        return Map.of("Authorization", token(target));
    }

    /**
     * 取鉴权 token（带缓存）。
     *
     * @param target 访问目标
     * @return token
     * @throws IllegalStateException 鉴权失败
     */
    private String token(Target target) {
        return tokens.computeIfAbsent(cacheKey(target), key -> authenticate(target));
    }

    /**
     * 调 {@code /v3/auth/authenticate} 换 token。
     *
     * @param target 访问目标
     * @return token
     * @throws IllegalStateException 用户名密码不匹配或响应异常
     */
    private String authenticate(Target target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", target.username());
        body.put("password", target.password());
        String url = target.endpoint().url("/v3/auth/authenticate");
        try {
            JsonObject json = Json.getJsonObject(
                    MiddlewareHttp.postJson(url, Map.of(), Json.toJson(body), Map.of()));
            String token = str(json.get("token"));
            if (token.isEmpty()) {
                throw new IllegalStateException("etcd 未返回 token: " + abbreviate(json.toJSONString()));
            }
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw failure(target.endpoint(), "/v3/auth/authenticate", e);
        }
    }

    /**
     * 判断异常是否为「未授权」。
     *
     * @param e 异常
     * @return 文案里出现 401 或 unauthorized 时为真
     */
    private static boolean looksUnauthorized(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid auth token");
    }

    /**
     * token 缓存的键：端点 + 用户名 + **密码**。
     *
     * <p>密码必须进缓存键：否则同一端点、同一用户名、**不同密码**的两个数据源会共用同一个 token
     * —— 表现为「密码填错的数据源也报连接成功」，把凭据校验变成摆设（本轮实测踩到）。</p>
     *
     * @param target 访问目标
     * @return 缓存键
     */
    private static String cacheKey(Target target) {
        return target.endpoint().base() + target.endpoint().context() + "|" + target.username()
                + "|" + target.password().length() + ":" + target.password().hashCode();
    }

    /**
     * 计算前缀列举的 range_end。
     *
     * <p>把最后一个字节 +1；若最后一个字节已是 {@code 0xFF}，追加一个 {@code 0x00}
     * （与 etcd 官方客户端的算法一致）。前缀为空时返回单个 {@code 0x00}，
     * 这是 etcd 表示「所有键」的约定。</p>
     *
     * @param prefix 前缀字节
     * @return range_end 字节
     */
    private static byte[] rangeEnd(byte[] prefix) {
        if (prefix.length == 0) {
            return new byte[]{0};
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(prefix.length + 1);
        for (int i = 0; i < prefix.length - 1; i++) {
            out.write(prefix[i]);
        }
        int last = prefix[prefix.length - 1] & 0xFF;
        if (last == 0xFF) {
            out.write(0xFF);
            out.write(0x00);
        } else {
            out.write(last + 1);
        }
        return out.toByteArray();
    }

    /**
     * 规整列举上限。
     *
     * @param limit 传入上限
     * @return 可用上限
     */
    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 字节转 base64。
     *
     * @param bytes 字节
     * @return base64 文本
     */
    public static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * base64 转字节，非法输入返回空数组。
     *
     * @param text base64 文本
     * @return 字节
     */
    public static byte[] decode(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            log.debug("[etcd] base64 解码失败: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 构造可读的失败异常。
     *
     * @param endpoint 端点
     * @param path     接口路径
     * @param e        原始异常
     * @return 异常
     */
    private static IllegalStateException failure(MiddlewareEndpoint endpoint, String path, Exception e) {
        return new IllegalStateException("调用 etcd " + endpoint.url(path) + " 失败: " + abbreviate(e.getMessage()));
    }

    /**
     * 截断过长的错误文案。
     *
     * @param text 原文
     * @return 截断结果
     */
    private static String abbreviate(String text) {
        String value = text == null ? "" : text;
        return value.length() <= ERROR_TEXT_LIMIT ? value : value.substring(0, ERROR_TEXT_LIMIT) + "...";
    }

    /**
     * 对象转字符串，null 转空串。
     *
     * @param value 原值
     * @return 字符串
     */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 解析 etcd 以字符串返回的数值。
     *
     * @param value 原值
     * @return 数值，空或无意义时为 null
     */
    private static Long longOrNull(Object value) {
        String text = str(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否完整 URL。
     *
     * @param value 原值
     * @return 以 http(s):// 开头为真
     */
    private static boolean isAbsoluteUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    /**
     * 去尾部斜杠。
     *
     * @param path 路径
     * @return 结果
     */
    private static String trimSlash(String path) {
        String value = trim(path);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 去空白结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 结果
     */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}

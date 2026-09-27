package com.chua.starter.monitor.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos v1 OpenAPI 客户端：负责端点解析、鉴权令牌与收发，不含任何展示语义。
 *
 * <p>数据源表里的 {@code nacos} 行给出地址与账号；未显式指定数据源时取第一个 {@code nacos}
 * 类型行，因为配置详情/发布/删除这三个接口在页面上只带 dataId、group、namespace。</p>
 *
 * <p>令牌按「地址+账号」缓存，提前一分钟续期；Nacos 未开启鉴权时不发登录请求。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosClient {

    /**
     * 数据源类型标识
     */
    private static final String DS_TYPE = "nacos";

    /**
     * 未填写库名字段时的默认上下文路径
     */
    private static final String DEFAULT_CONTEXT = "/nacos";

    /**
     * 服务端未回传有效期时使用的令牌寿命（毫秒）
     */
    private static final long TOKEN_DEFAULT_TTL_MS = 300_000L;

    /**
     * 令牌提前续期的余量（毫秒）
     */
    private static final long TOKEN_RENEW_AHEAD_MS = 60_000L;

    /**
     * 监控数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 令牌缓存，键为「地址+账号」
     */
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    /**
     * 解析要操作的 Nacos 端点。
     *
     * @param datasourceId 数据源 ID，为空时取第一个 nacos 数据源
     * @return 端点
     * @throws IllegalStateException 数据源不存在或类型不匹配
     */
    public MiddlewareEndpoint endpoint(Long datasourceId) {
        return MiddlewareEndpoint.of(requireDatasource(datasourceId), DEFAULT_CONTEXT);
    }

    /**
     * 用即席填写的地址构造端点（测试连接用）。
     *
     * @param datasource    临时数据源配置
     * @param defaultContext 默认上下文路径
     * @return 端点
     */
    public MiddlewareEndpoint endpoint(DataSourceVO datasource, String defaultContext) {
        return MiddlewareEndpoint.of(datasource, defaultContext);
    }

    /**
     * GET 请求并解析成 JSON 对象。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param query    查询参数
     * @return 解析后的对象，响应不是 JSON 对象时为空对象
     */
    public JsonObject getObject(MiddlewareEndpoint endpoint, String path, Map<String, String> query) {
        return Json.getJsonObject(get(endpoint, path, query));
    }

    /**
     * GET 请求并解析成 JSON 数组（Nacos 1.x 的节点接口直接回数组）。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param query    查询参数
     * @return 解析后的数组，响应不是 JSON 数组时为空数组
     */
    public List<Object> getArray(MiddlewareEndpoint endpoint, String path, Map<String, String> query) {
        String body = get(endpoint, path, query).trim();
        return body.startsWith("[") ? Json.getJsonArray(body) : List.of();
    }

    /**
     * GET 请求并保留响应头。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param query    查询参数
     * @return 响应
     */
    public MiddlewareHttp.Response getRaw(MiddlewareEndpoint endpoint, String path, Map<String, String> query) {
        try {
            return MiddlewareHttp.getResponse(endpoint.url(path), withToken(endpoint, query));
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * GET 请求取原文。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param query    查询参数
     * @return 响应体文本
     */
    public String get(MiddlewareEndpoint endpoint, String path, Map<String, String> query) {
        try {
            return MiddlewareHttp.get(endpoint.url(path), withToken(endpoint, query));
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 表单方式 POST。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param form     表单字段
     * @return 响应体文本
     */
    public String postForm(MiddlewareEndpoint endpoint, String path, Map<String, String> form) {
        try {
            Map<String, String> body = new LinkedHashMap<>(form == null ? Map.of() : form);
            body.remove("accessToken");
            String token = accessToken(endpoint);
            return MiddlewareHttp.postForm(endpoint.url(path),
                    token.isEmpty() ? Map.of() : Map.of("accessToken", token), body);
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 表单方式 PUT（Nacos 实例上下线/改权重）。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param form     表单字段
     * @return 响应体文本
     */
    public String putForm(MiddlewareEndpoint endpoint, String path, Map<String, String> form) {
        try {
            Map<String, String> body = new LinkedHashMap<>(form == null ? Map.of() : form);
            body.remove("accessToken");
            String token = accessToken(endpoint);
            return MiddlewareHttp.putForm(endpoint.url(path),
                    token.isEmpty() ? Map.of() : Map.of("accessToken", token), body);
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }
    /**
     * DELETE 请求。
     *
     * @param endpoint 端点
     * @param path     OpenAPI 路径
     * @param query    查询参数
     * @return 响应体文本
     */
    public String delete(MiddlewareEndpoint endpoint, String path, Map<String, String> query) {
        try {
            return MiddlewareHttp.delete(endpoint.url(path), withToken(endpoint, query));
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 取数据源配置并校验类型。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 数据源配置
     * @throws IllegalStateException 未登记或类型不符
     */
    private DataSourceVO requireDatasource(Long datasourceId) {
        return MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE);
    }

    /**
     * 给请求参数补上访问令牌。
     *
     * @param endpoint 端点
     * @param params   原始参数
     * @return 携带令牌的参数副本
     */
    private Map<String, String> withToken(MiddlewareEndpoint endpoint, Map<String, String> params) {
        Map<String, String> copy = new LinkedHashMap<>(params == null ? Map.of() : params);
        String token = accessToken(endpoint);
        if (!token.isEmpty()) {
            copy.put("accessToken", token);
        }
        return copy;
    }

    /**
     * 取得访问令牌，未开启鉴权时返回空串。
     *
     * @param endpoint 端点
     * @return 令牌
     */
    private String accessToken(MiddlewareEndpoint endpoint) {
        if (!endpoint.authed()) {
            return "";
        }
        String key = endpoint.base() + endpoint.context() + "@" + endpoint.username() + "/" + endpoint.password().hashCode();
        long now = System.currentTimeMillis();
        Token cached = tokens.get(key);
        if (cached != null && now < cached.renewAt()) {
            return cached.value();
        }
        String body = postForm0(endpoint, "/v1/auth/login", Map.of(
                "username", endpoint.username(),
                "password", endpoint.password()));
        JsonObject json = Json.getJsonObject(body);
        String token = json.getObject("accessToken") == null ? "" : String.valueOf(json.getObject("accessToken"));
        if (token.isEmpty()) {
            throw new IllegalStateException("Nacos 登录未返回 accessToken，请检查用户名密码");
        }
        long ttlSeconds = toLong(json.getObject("tokenTtl"), 0L);
        long ttlMs = ttlSeconds > 0L ? ttlSeconds * 1000L : TOKEN_DEFAULT_TTL_MS;
        // 提前量不得超过有效期的一半，否则短 TTL（如压测环境的秒级配置）会退化成「每次请求都登录」
        long renewAhead = Math.min(TOKEN_RENEW_AHEAD_MS, ttlMs / 2);
        tokens.put(key, new Token(token, now + Math.max(0L, ttlMs - renewAhead)));
        return token;
    }

    /**
     * 登录请求不带令牌，避免递归取令牌。
     *
     * @param endpoint 端点
     * @param path     路径
     * @param form     表单
     * @return 响应体文本
     */
    private String postForm0(MiddlewareEndpoint endpoint, String path, Map<String, String> form) {
        try {
            return MiddlewareHttp.postForm(endpoint.url(path), form);
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 把传输层异常换成可读原因，带上目标地址以便区分多套 Nacos。
     *
     * @param endpoint 端点
     * @param path     请求路径
     * @param e        原始异常
     * @return 统一异常
     */
    private static IllegalStateException failure(MiddlewareEndpoint endpoint, String path, Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String url = endpoint == null ? path : endpoint.url(path);
        log.warn("[nacos] 调用 {} 失败: {}", url, reason);
        return new IllegalStateException("调用 Nacos " + url + " 失败: " + reason, e);
    }

    /**
     * 宽松取整数值。
     *
     * @param value      原始值
     * @param defaultVal 缺省值
     * @return 长整数
     */
    private static long toLong(Object value, long defaultVal) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultVal;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 缓存的访问令牌。
     *
     * @param value   令牌
     * @param renewAt 需要重新登录的时间戳（毫秒），已扣除提前刷新量
     */
    private record Token(String value, long renewAt) {
    }
}

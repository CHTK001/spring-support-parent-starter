package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.PlatformApiVersion;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 大华乐橙（Imou / LeChange）SDK 实现 —— 按官方文档 OpenAPI（官方无 Java SDK）。
 *
 * <h3>官方文档核对结果（open.imou.com / openapi.lechange.cn）</h3>
 * <ul>
 *   <li>统一网关：{@code POST https://openapi.lechange.cn/openapi/{method}}</li>
 *   <li>请求体固定三层：
 *     <pre>{@code
 * {
 *   "system": { "ver":"1.0", "appId":"...", "sign":"...", "time":秒, "nonce":"uuid" },
 *   "id": "uuid",
 *   "params": { ... }
 * }
 *     }</pre></li>
 *   <li>签名：{@code sign = md5("time:{time},nonce:{nonce},appSecret:{appSecret}")}（32 位小写十六进制）</li>
 *   <li>响应：{@code {result:{code:"0", msg, data}} }，{@code code} 为 {@code "0"} 表示成功</li>
 *   <li>取 token：{@code accessToken}（无业务参数），返回 {@code result.data.accessToken}</li>
 *   <li>设备列表：官方现行接口为 {@code listDeviceDetailsByPage}（旧的 deviceList 已不推荐）</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class LeChangeSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "lechange";

    /** 默认网关（官方文档域名） */
    private static final String DEFAULT_BASE_URL = "https://openapi.lechange.cn";

    /** 取 token 方法名 */
    private static final String METHOD_TOKEN = "accessToken";

    /** 设备分页方法名（现行） */
    private static final String METHOD_DEVICE_PAGE = "listDeviceDetailsByPage";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 缓存的 accessToken */
    private volatile String accessToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造乐橙 SDK 实现。
     *
     * @param info 连接信息
     */
    public LeChangeSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("openapi-v1",
                "https://open.imou.com/document/pages/887e07");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return LeChangeApis.all();
    }

    /**
     * 网关地址：官方固定地址，不允许凭据覆盖（平台 URL 固定，改动会打断调用）。
     *
     * @return base url，无尾斜杠
     */
    private String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    /**
     * 计算乐橙签名：md5("time:{time},nonce:{nonce},appSecret:{secret}")。
     *
     * @param time   秒级时间戳
     * @param nonce  随机串
     * @param secret appSecret
     * @return 32 位小写十六进制
     */
    private static String sign(long time, String nonce, String secret) {
        String raw = "time:" + time + ",nonce:" + nonce + ",appSecret:" + secret;
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("乐橙签名失败", ex);
        }
    }

    /**
     * 构造乐橙签名请求壳。
     *
     * @param params 业务参数
     * @return 完整请求体
     */
    private Map<String, Object> envelope(Map<String, Object> params) {
        long time = System.currentTimeMillis() / 1000L;
        String nonce = UUID.randomUUID().toString();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("ver", "1.0");
        system.put("appId", info.appKey());
        system.put("sign", sign(time, nonce, info.appSecret()));
        system.put("time", time);
        system.put("nonce", nonce);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system", system);
        body.put("id", UUID.randomUUID().toString());
        body.put("params", params == null ? Map.of() : params);
        return body;
    }

    /**
     * 取管理员 accessToken（3 天有效，提前 60 秒刷新）。
     */
    private synchronized void login() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        JsonNode root = callEnvelope(METHOD_TOKEN, Map.of());
        // 乐橙信封：{ result: { code, msg, data: { accessToken, expireTime } } }
        JsonNode result = root == null ? null : root.get("result");
        String code = result == null ? null : str(result, "code");
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException("乐橙换取 accessToken 失败: code=" + code
                    + ", msg=" + str(result, "msg", "message"));
        }
        JsonNode data = result == null ? null : result.get("data");
        String token = data == null ? null : str(data, "accessToken");
        if (token == null) {
            throw new IllegalStateException("乐橙换取 accessToken 失败: 响应缺少 result.data.accessToken");
        }
        long expireTime = 259_200L;
        JsonNode expireNode = data.get("expireTime");
        if (expireNode != null && !expireNode.isNull()) {
            expireTime = expireNode.asLong(259_200L);
        }
        this.accessToken = token;
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, expireTime - 60L) * 1000L;
        log.info("[LeChange] 登录成功: appId={}", info.appKey());
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[LeChange] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : page(METHOD_DEVICE_PAGE)) {
            result.add(new VendorDevice(
                    str(node, "deviceId", "deviceSerial", "id"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "model"),
                    "camera",
                    str(node, "deviceSn", "deviceSerial", "serial"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "deviceAddress", "address"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "deviceStatus", "online", "status")));
        }
        return result;
    }

    @Override
    public PlatformApiResult invoke(String code, Map<String, Object> params) {
        PlatformApiSpec spec = apis().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElse(null);
        if (spec == null) {
            return PlatformApiResult.fail(code, "未登记的接口编码: " + code, null, 0L);
        }
        long start = System.currentTimeMillis();
        try {
            String method = extra("apiMethod." + code);
            if (method == null || method.isBlank()) {
                method = spec.path().replaceFirst("^/openapi/", "");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            if (params != null) {
                body.putAll(params);
            }
            if (!METHOD_TOKEN.equals(method)) {
                body.putIfAbsent("token", token());
            }
            JsonNode data = call(method, body);
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized,
                    data == null || data.isNull() ? "{}" : data.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 分页拉取设备。
     *
     * @param method 方法名
     * @return 全量条目
     */
    private List<JsonNode> page(String method) {
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= 200; page++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", token());
            body.put("page", page);
            body.put("pageSize", 50);
            JsonNode root = call(method, body);
            List<JsonNode> list = toList(root);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < 50) {
                break;
            }
        }
        return all;
    }

    /**
     * 取当前 token（过期自动续）。
     *
     * @return accessToken
     */
    private String token() {
        login();
        return accessToken;
    }

    /**
     * 调一个乐橙 OpenAPI 方法，返回 {@code result.data}。
     *
     * @param method 方法名
     * @param params 业务参数
     * @return data 节点
     */
    private JsonNode call(String method, Map<String, Object> params) {
        return unwrap(callEnvelope(method, params));
    }

    /**
     * 调 v2 接口（官方文档：{@code POST /openapi/v2/{点号方法}}），返回 {@code result.data}。
     *
     * @param method 点号方法名，如 {@code device.detail.list}
     * @param params 业务参数
     * @return data 节点
     */
    private JsonNode callV2(String method, Map<String, Object> params) {
        return unwrap(callEnvelopeV2(method, params));
    }

    /**
     * 调 v1 接口并取 {@code result.data}。
     *
     * <p>官方部分接口（如 {@code openDoorRemote}）明确「无 data 数据返回」，
     * 此时返回空对象而不是 null，调用方无需判空；失败靠 {@code code != "0"} 抛出。</p>
     *
     * @param method 方法名，如 {@code setDeviceSnap}
     * @param params 业务参数（token 自动补齐）
     * @return data 节点，无 data 时为空对象
     */
    public JsonNode callData(String method, Map<String, Object> params) {
        JsonNode data = call(method, withToken(params));
        return data == null || data.isNull() ? MAPPER.createObjectNode() : data;
    }

    /**
     * 调 v2 接口并取 {@code result.data}，无 data 时返回空对象。
     *
     * @param method 点号方法名
     * @param params 业务参数（token 自动补齐）
     * @return data 节点
     */
    public JsonNode callDataV2(String method, Map<String, Object> params) {
        JsonNode data = callV2(method, withToken(params));
        return data == null || data.isNull() ? MAPPER.createObjectNode() : data;
    }

    /**
     * 补齐管理员 accessToken（调用方已显式传 token 时不覆盖）。
     *
     * @param params 业务参数
     * @return 带 token 的参数副本
     */
    private Map<String, Object> withToken(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (params != null) {
            body.putAll(params);
        }
        body.putIfAbsent("token", token());
        return body;
    }

    /**
     * 拆乐橙响应信封：{@code {result:{code,msg,data}}}。
     *
     * @param root 响应根节点
     * @return data 节点（可能为 null）
     */
    private JsonNode unwrap(JsonNode root) {
        JsonNode result = root == null ? null : root.get("result");
        String code = result == null ? null : str(result, "code");
        if (code != null && !"0".equals(code)) {
            // TK1002 = token 失效，下次调用重换
            if ("TK1002".equals(code)) {
                this.accessToken = null;
                this.tokenExpiry = 0L;
            }
            throw new IllegalStateException("乐橙返回失败: code=" + code
                    + ", msg=" + str(result, "msg", "message"));
        }
        return result == null ? root : result.get("data");
    }

    /**
     * 调 v2 接口并返回完整响应根节点。
     *
     * @param method 点号方法名
     * @param params 业务参数
     * @return 响应根节点
     */
    private JsonNode callEnvelopeV2(String method, Map<String, Object> params) {
        return callEnvelopeAt(baseUrl() + "/openapi/v2/" + method, method, params);
    }

    /**
     * 调乐橙 OpenAPI 并返回完整响应根节点（含 result 信封）。
     *
     * @param method 方法名
     * @param params 业务参数
     * @return 响应根节点
     */
    private JsonNode callEnvelope(String method, Map<String, Object> params) {
        return callEnvelopeAt(baseUrl() + "/openapi/" + method, method, params);
    }

    /**
     * 按完整 URL 发起乐橙 OpenAPI 调用。
     *
     * @param url    完整地址
     * @param method 方法名（仅用于错误信息）
     * @param params 业务参数
     * @return 响应根节点
     */
    private JsonNode callEnvelopeAt(String url, String method, Map<String, Object> params) {
        try {
            String json = MAPPER.writeValueAsString(envelope(params));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = parse(response.body());
            if (root == null) {
                throw new IllegalStateException("乐橙响应非 JSON: HTTP " + response.statusCode()
                        + " " + response.body().substring(0, Math.min(200, response.body().length())));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("乐橙调用失败: " + method + " -> " + ex.getMessage(), ex);
        }
    }
}

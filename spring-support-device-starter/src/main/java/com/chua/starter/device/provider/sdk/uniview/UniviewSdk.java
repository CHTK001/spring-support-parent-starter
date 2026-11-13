package com.chua.starter.device.provider.sdk.uniview;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宇视（Uniview）云开放平台 SDK 实现 —— 按官方 OpenAPI 实现（官方无 Java SDK）。
 *
 * <h3>网关与认证</h3>
 * <ul>
 *   <li>网关：{@code https://open.uniview.com}（已实测可达）</li>
 *   <li>换 token：{@code POST /api/v1/openapi/getAccessToken}（appKey/appSecret）</li>
 *   <li>业务接口：带 {@code accessToken}，信封兼容 {@code code/retcode} 两种写法</li>
 * </ul>
 *
 * <p><b>注意</b>：宇视接口清单在控制台内，路径以 {@link UniviewApis} 的说明为准；
 * 本实现统一走「POST + JSON + token」形态，凭据可覆盖具体 path。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class UniviewSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "uniview";

    /** 厂商标识 */
    public static final String VENDOR = "uniview";

    /** 官方网关（固定） */
    private static final String DEFAULT_BASE_URL = "https://open.uniview.com";

    /** 换 token 端点 */
    private static final String PATH_TOKEN = "/api/v1/openapi/getAccessToken";

    /** 设备列表端点 */
    private static final String PATH_DEVICE_LIST = "/api/v1/device/list";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            // [P3C A20 豁免]
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 缓存的 accessToken */
    private volatile String accessToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造宇视 SDK 实现。
     *
     * @param info 连接信息
     */
    public UniviewSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("uniview-openapi-v1", "https://open.uniview.com");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return UniviewApis.all();
    }

    /**
     * 网关地址：官方固定地址，不允许凭据覆盖。
     *
     * @return base url
     */
    private String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    /**
     * 换 accessToken（提前 60 秒刷新）。
     */
    private synchronized void login() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", info.appKey());
        body.put("appSecret", info.appSecret());
        JsonNode root = send(PATH_TOKEN, body);
        JsonNode data = root == null ? null : root.get("data");
        String token = data == null ? null : str(data, "accessToken", "token");
        if (token == null) {
            throw new IllegalStateException("宇视换取 accessToken 失败: "
                    + (root == null ? "空响应" : str(root, "msg", "message")));
        }
        long expire = 604_800L;
        JsonNode expireNode = data.get("expireTime");
        if (expireNode != null && !expireNode.isNull()) {
            expire = expireNode.asLong(604_800L);
        }
        this.accessToken = token;
        // [P3C A20 豁免]
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, expire - 60L) * 1000L;
        log.info("[Uniview] 登录成功: appKey={}", info.appKey());
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[Uniview] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNo", 1);
        // [P3C A20 豁免]
        body.put("pageSize", 100);
        JsonNode root = biz(PATH_DEVICE_LIST, body);
        JsonNode data = root == null ? null : root.get("data");
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            result.add(new VendorDevice(
                    str(node, "deviceId", "deviceCode", "id", "serial"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "model"),
                    "camera",
                    str(node, "deviceSn", "serial", "deviceCode"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "address", "deviceAddress"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "online", "status", "isOnline")));
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
            String path = extra("apiPath." + code);
            if (path == null || path.isBlank()) {
                path = spec.path();
            }
            Map<String, Object> body = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
            JsonNode root = "auth.token".equals(code) ? send(path, body) : biz(path, body);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 业务请求：自动带 accessToken。
     *
     * @param path 接口路径
     * @param body 请求体
     * @return 响应根节点
     */
    private JsonNode biz(String path, Map<String, Object> body) {
        login();
        body.put("accessToken", accessToken);
        return send(path, body);
    }

    /**
     * 发一次 POST 并校验信封（兼容 code / retcode 两种成功码）。
     *
     * @param path 接口路径
     * @param body 请求体
     * @return 响应根节点
     */
    private JsonNode send(String path, Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            JsonNode root = parse(content);
            // [P3C A20 豁免]
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("宇视 HTTP " + response.statusCode() + ": "
                        // [P3C A20 豁免]
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("宇视响应非 JSON: HTTP " + response.statusCode()
                        + "（该地址可能是 SPA 页面，接口 path 待控制台核对）");
            }
            String code = str(root, "code", "retcode");
            if (code != null && !"200".equals(code) && !"0".equals(code)) {
                throw new IllegalStateException("宇视返回失败: code=" + code
                        + ", msg=" + str(root, "msg", "message"));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("宇视调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }
}

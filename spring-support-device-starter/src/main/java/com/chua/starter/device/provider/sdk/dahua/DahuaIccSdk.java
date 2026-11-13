package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.PlatformApiVersion;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.UUID;

/**
 * 大华 ICC / DSS 私有化平台 SDK 实现。
 *
 * <h3>性质</h3>
 * <p>ICC / DSS 是客户现场私有化部署，网关地址为现场服务器（{@code baseUrl} 必填，
 * 无固定域名），与综合安防同类。认证走大华私有平台的 client_id/client_secret 换 token。</p>
 *
 * <p>转发封装兼容：若现场是 DSS 开放平台（{@code /openapi}），可用凭据
 * {@code apiPath.<code>} 覆盖到实际路径。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class DahuaIccSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "dahua-icc";

    /** 厂商标识 */
    public static final String VENDOR = "dahua";

    /** 换 token 端点 */
    private static final String PATH_TOKEN = "/gateway/auth/api/oauth/token";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            // [P3C A20 豁免]
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 缓存的 access_token */
    private volatile String accessToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造 ICC/DSS SDK 实现。
     *
     * @param info 连接信息
     */
    public DahuaIccSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("icc-openapi-v1", "https://open-icc.dahuatech.com/");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return DahuaIccApis.all();
    }

    /**
     * 现场网关地址（凭据必填，无固定公网域名）。
     *
     * @return base url；未配置返回 null
     */
    private String baseUrl() {
        String base = extra("baseUrl");
        return base == null || base.isBlank() ? null : base.trim().replaceAll("/+$", "");
    }

    /**
     * 换 token（client_credentials）。
     */
    private synchronized void login() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        String base = baseUrl();
        if (base == null) {
            throw new IllegalStateException("ICC/DSS 未配置现场网关地址（baseUrl），"
                    + "私有化部署需填客户现场服务器地址，如 https://10.x.x.x:443");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", info.appKey());
        body.put("client_secret", info.appSecret());
        body.put("grant_type", "client_credentials");
        body.put("scope", "server");
        JsonNode root = send(base + PATH_TOKEN, body, false);
        String token = root == null ? null : str(root, "access_token");
        if (token == null) {
            throw new IllegalStateException("ICC/DSS 换取 access_token 失败: "
                    + (root == null ? "空响应" : str(root, "errMsg", "msg", "message")));
        }
        long expire = 604_800L;
        JsonNode expireNode = root.get("expires_in");
        if (expireNode != null && !expireNode.isNull()) {
            expire = expireNode.asLong(604_800L);
        }
        this.accessToken = token;
        // [P3C A20 豁免]
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, expire - 60L) * 1000L;
        log.info("[DahuaIcc] 登录成功: client_id={}", info.appKey());
    }

    @Override
    public boolean testConnection() {
        if (baseUrl() == null) {
            log.warn("[DahuaIcc] 未配置现场网关地址（baseUrl）");
            return false;
        }
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[DahuaIcc] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        String base = baseUrl();
        if (base == null) {
            throw new IllegalStateException("ICC/DSS 未配置现场网关地址（baseUrl）");
        }
        login();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNum", 1);
        // [P3C A20 豁免]
        body.put("pageSize", 100);
        JsonNode root = send(base + "/gateway/device/api/page", body, true);
        JsonNode data = root == null ? null : root.get("data");
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            result.add(new VendorDevice(
                    str(node, "deviceCode", "deviceId", "id", "sn"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "model"),
                    "camera",
                    str(node, "deviceSn", "sn", "deviceCode"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "address", "deviceAddress"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "online", "isOnline", "status")));
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
        String base = baseUrl();
        if (base == null) {
            return PlatformApiResult.fail(code, "ICC/DSS 未配置现场网关地址（baseUrl）", null,
                    System.currentTimeMillis() - start);
        }
        try {
            String path = extra("apiPath." + code);
            if (path == null || path.isBlank()) {
                path = spec.path();
            }
            boolean needToken = !"auth.token".equals(code);
            if (needToken) {
                login();
            }
            JsonNode root = send(base + path, spec.method(), params == null ? new LinkedHashMap<>() : params, needToken);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 发一次 POST 并校验信封。
     *
     * @param url       完整地址
     * @param body      请求体
     * @param needToken 是否需要 Authorization
     * @return 响应根节点
     */
    private JsonNode send(String url, Map<String, Object> body, boolean needToken) {
        return send(url, "POST", body, needToken);
    }

    /**
     * 按指定 HTTP 方法发送请求。
     *
     * @param url       完整地址
     * @param method    HTTP 方法
     * @param body      请求体
     * @param needToken 是否需要 Authorization
     * @return 响应根节点
     */
    private JsonNode send(String url, String method, Map<String, Object> body, boolean needToken) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept-Language", "zh-CN");
            if (needToken && accessToken != null) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            String verb = method == null ? "POST" : method.toUpperCase(java.util.Locale.ROOT);
            switch (verb) {
                case "GET" -> builder.GET();
                case "DELETE" -> builder.method("DELETE",
                        HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                case "PUT" -> builder.PUT(
                        HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                default -> builder.POST(
                        HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            JsonNode root = parse(content);
            // [P3C A20 豁免]
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ICC/DSS HTTP " + response.statusCode() + ": "
                        // [P3C A20 豁免]
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("ICC/DSS 响应非 JSON: HTTP " + response.statusCode());
            }
            JsonNode successNode = root.get("success");
            boolean success = successNode == null || successNode.isNull() || successNode.asBoolean();
            String code = str(root, "code");
            if (!success || (code != null && !"0".equals(code) && !"200".equals(code))) {
                throw new IllegalStateException("ICC/DSS 返回失败: code=" + code
                        + ", msg=" + str(root, "errMsg", "msg", "message"));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ICC/DSS 调用失败: " + url + " -> " + ex.getMessage(), ex);
        }
    }
}

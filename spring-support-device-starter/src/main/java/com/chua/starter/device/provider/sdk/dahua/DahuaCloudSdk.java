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
 * 大华云开发者平台（open.cloud-dahua.com，IoT 开放平台）SDK 实现 —— 官方无 Java SDK，按官方文档 API 实现。
 *
 * <h3>平台归属</h3>
 * <p>用户实际接入的是<b>大华云开发者平台 IoT 开放平台</b>（控制台 {@code console/develop/iotList}，
 * 产品 ID 86118185），与「大华云睿 SaaS」({@code www.cloud-dahua.com/gateway}) 不是同一套。
 * 早期误接 SaaS 网关导致 {@code EC101002 账户异常}，本实现改为本平台真实的 open-api。</p>
 *
 * <h3>认证（官方「大华云开放平台 IoT 基础通用套件」）</h3>
 * <ul>
 *   <li>换 token：{@code POST https://open.cloud-dahua.com/open-api/api-base/auth/getAppAccessToken}
 *       <br>签名 {@code Sign = HMAC-SHA512(SecretKey, AccessKey + timestamp + nonce)}（十六进制大写）
 *       <br>请求头：{@code AccessKey / Timestamp / Nonce / Sign / ProductId / Version=V1 / Sign-Type=simple}
 *       <br>返回 {@code data.appAccessToken}（{@code validitySeconds} 约 7 天）</li>
 *   <li>业务接口：{@code https://open.cloud-dahua.com/open-api/...}
 *       <br>同签名头 + {@code AppAccessToken}，签名因子
 *       {@code AccessKey + appAccessToken + timestamp + nonce}</li>
 *   <li>统一响应：{@code {code:"200", success:true, data, msg}}</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class DahuaCloudSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "dahua-cloud";

    /** 默认网关（大华云开发者平台） */
    private static final String DEFAULT_BASE_URL = "https://open.cloud-dahua.com";

    /** 换 token 端点 */
    private static final String PATH_TOKEN = "/open-api/api-base/auth/getAppAccessToken";

    /** 设备分页端点 */
    private static final String PATH_DEVICE_LIST = "/open-api/api-iot/device/getDeviceList";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 缓存的 AppAccessToken */
    private volatile String appToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造云睿 SDK 实现。
     *
     * @param info 连接信息
     */
    public DahuaCloudSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("iot-open-api-v1",
                "https://open.cloud-dahua.com/platform/develop/doccenter/doc");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return DahuaCloudApis.all();
    }

    /**
     * 网关地址：官方固定地址（大华云开发者平台），不允许凭据覆盖。
     *
     * @return base url，无尾斜杠
     */
    private String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    /**
     * 产品 ID：优先取凭据 productCode / productId / appId。
     *
     * @return 产品 ID
     */
    private String productId() {
        String id = extra("productCode");
        if (id == null || id.isBlank()) {
            id = extra("productId");
        }
        if (id == null || id.isBlank()) {
            id = extra("appId");
        }
        return id == null ? "" : id.trim();
    }

    /**
     * 计算海康式 HMAC-SHA512 签名（十六进制大写）。
     *
     * @param data 签名因子
     * @return 签名串
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(info.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02X", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("大华云签名失败", ex);
        }
    }

    /**
     * 取当前 AppAccessToken（过期自动续）。
     */
    private synchronized void login() {
        if (appToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> headers = baseHeaders(ts, nonce, sign(info.appKey() + ts + nonce), false);
        JsonNode root = send(PATH_TOKEN, "{}", headers);
        JsonNode data = root == null ? null : root.get("data");
        String token = data == null ? null : str(data, "appAccessToken");
        if (token == null) {
            throw new IllegalStateException("大华云换取 appAccessToken 失败: "
                    + (root == null ? "空响应" : str(root, "msg", "message")));
        }
        long validity = 604_800L;
        JsonNode validityNode = data.get("validitySeconds");
        if (validityNode != null && !validityNode.isNull()) {
            validity = validityNode.asLong(604_800L);
        }
        this.appToken = token;
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, validity - 60L) * 1000L;
        log.info("[DahuaCloud] 登录成功: productId={}", productId());
    }

    /**
     * 构造公共签名头。
     *
     * @param ts      时间戳（毫秒）
     * @param nonce   随机串
     * @param sign    签名
     * @param withAuth 是否业务请求（带 AppAccessToken）
     * @return 请求头
     */
    private Map<String, String> baseHeaders(String ts, String nonce, String sign, boolean withAuth) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("AccessKey", info.appKey());
        headers.put("Timestamp", ts);
        headers.put("Nonce", nonce);
        headers.put("Sign", sign);
        headers.put("ProductId", productId());
        headers.put("Version", "V1");
        headers.put("Sign-Type", "simple");
        if (withAuth) {
            headers.put("AppAccessToken", appToken);
        }
        return headers;
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[DahuaCloud] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNum", 1);
        body.put("pageSize", 100);
        JsonNode root = biz(PATH_DEVICE_LIST, body);
        JsonNode data = root == null ? null : root.get("data");
        List<JsonNode> nodes = new ArrayList<>();
        // 官方返回 data.pageData[].deviceList[]，兼容 data.deviceList / data.list
        JsonNode pageData = data == null ? null : data.get("pageData");
        if (pageData != null && pageData.isArray()) {
            for (JsonNode page : pageData) {
                nodes.addAll(toList(page.get("deviceList")));
            }
        } else {
            nodes.addAll(toList(data == null ? null : data.get("deviceList")));
            nodes.addAll(toList(data));
        }
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            result.add(new VendorDevice(
                    str(node, "deviceId", "deviceCode", "deviceSn", "id", "sn"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "model", "categoryCode"),
                    "camera",
                    str(node, "deviceId", "deviceSn", "sn", "deviceCode"),
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
        try {
            String path = extra("apiPath." + code);
            if (path == null || path.isBlank()) {
                path = spec.path();
            }
            Map<String, Object> body = params == null ? new LinkedHashMap<>() : params;
            JsonNode root = "auth.token".equals(code)
                    ? tokenRoot(body)
                    : biz(path, body);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 触发一次 token 换取，返回响应根节点（用于 auth.token 接口）。
     *
     * @param body 忽略
     * @return 响应根节点
     */
    private JsonNode tokenRoot(Map<String, Object> body) {
        this.appToken = null;
        this.tokenExpiry = 0L;
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        return send(PATH_TOKEN, "{}", baseHeaders(ts, nonce,
                sign(info.appKey() + ts + nonce), false));
    }

    /**
     * 业务请求：自动带 AppAccessToken 与业务签名。
     *
     * @param path 接口路径
     * @param body 请求体
     * @return 响应根节点
     */
    private JsonNode biz(String path, Map<String, Object> body) {
        login();
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = sign(info.appKey() + appToken + ts + nonce);
        return send(path, body == null ? "{}" : writeJson(body),
                baseHeaders(ts, nonce, signature, true));
    }

    /**
     * 发一次 POST 并校验信封。
     *
     * @param path    接口路径
     * @param json    请求体 JSON
     * @param headers 请求头
     * @return 响应根节点
     */
    private JsonNode send(String path, String json, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            JsonNode root = parse(content);
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("大华云 HTTP " + response.statusCode() + ": "
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("大华云响应非 JSON: HTTP " + response.statusCode()
                        + " " + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            String code = str(root, "code");
            JsonNode successNode = root.get("success");
            boolean success = successNode == null || successNode.isNull() || successNode.asBoolean();
            if (!success || (code != null && !"200".equals(code) && !"0".equals(code))) {
                throw new IllegalStateException("大华云返回失败: code=" + code
                        + ", msg=" + str(root, "msg", "message", "errMsg"));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("大华云调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 序列化请求体，失败抛异常。
     *
     * @param body 请求体
     * @return JSON
     */
    private String writeJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("请求体序列化失败", ex);
        }
    }
}

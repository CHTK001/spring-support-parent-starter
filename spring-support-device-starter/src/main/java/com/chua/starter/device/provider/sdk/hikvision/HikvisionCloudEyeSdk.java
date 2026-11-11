package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.PlatformApiVersion;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
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
 * 海康云眸（Hik-Cloud Eye）SDK 实现 —— 按官方文档 OpenAPI（官方无 Java SDK）。
 *
 * <h3>官方文档核对结果（pic.hik-cloud.com 授权凭证获取 / 接口调用说明）</h3>
 * <ul>
 *   <li>换 token：{@code POST https://api2.hik-cloud.com/oauth/token}
 *       <br>格式：{@code application/x-www-form-urlencoded}
 *       <br>参数：{@code client_id}（=AK）、{@code client_secret}（=SK）、
 *       {@code grant_type=client_credentials}、{@code scope=app}（可选）
 *       <br>返回：{@code access_token} / {@code token_type} / {@code expires_in}（约 7 天）</li>
 *   <li>业务接口：{@code https://api2.hik-cloud.com/api/v1/...}
 *       <br>请求头：{@code Authorization: Bearer <access_token>}</li>
 *   <li>统一响应：{@code {code, message, data}}，{@code code} 为 0 或 200 表示成功</li>
 *   <li>凭据失效：HTTP 401 表示 token 失效，需重新换 token</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class HikvisionCloudEyeSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "cloud-eye";

    /** 云眸网关默认地址（官方文档为 api2，旧 api 域名会 301） */
    private static final String DEFAULT_BASE_URL = "https://api2.hik-cloud.com";

    /** 换 token */
    private static final String PATH_TOKEN = "/oauth/token";

    /** 设备列表（官方：GET /api/v1/open/basic/devices/list，groupNo 必填） */
    private static final String PATH_DEVICE_LIST = "/api/v1/open/basic/devices/list";

    /** 分组/区域列表（官方：GET /api/v1/open/basic/groups/actions/listAll） */
    private static final String PATH_GROUP_LIST = "/api/v1/open/basic/groups/actions/listAll";

    /** 通道列表（官方：GET /api/v1/open/basic/channels/list，deviceSerial 必填） */
    private static final String PATH_CHANNEL_LIST = "/api/v1/open/basic/channels/list";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 缓存的 access_token */
    private volatile String accessToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造云眸 SDK 实现。
     *
     * @param info 连接信息
     */
    public HikvisionCloudEyeSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("cloud-eye-v1",
                "https://pic.hik-cloud.com/opencustom/apidoc/pb/open/4de4ae9fd2924b35988cb73ed5bb66af.html");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return HikvisionCloudEyeApis.all();
    }

    /**
     * 网关地址：官方固定地址 api2（旧 api 域名会 301），不允许凭据覆盖。
     *
     * @return base url，无尾斜杠
     */
    private String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    /**
     * 换 access_token（7 天有效，提前 60 秒刷新）。
     */
    private synchronized void login() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", info.appKey());
        form.put("client_secret", info.appSecret());
        form.put("grant_type", "client_credentials");
        form.put("scope", "app");
        JsonNode root = sendForm(PATH_TOKEN, form);
        String token = root == null ? null : str(root, "access_token");
        if (token == null) {
            throw new IllegalStateException("云眸换取 access_token 失败: "
                    + (root == null ? "空响应" : str(root, "message", "msg", "error_description")));
        }
        long expiresIn = 604_800L;
        JsonNode expiresNode = root.get("expires_in");
        if (expiresNode != null && !expiresNode.isNull()) {
            expiresIn = expiresNode.asLong(604_800L);
        }
        this.accessToken = token;
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, expiresIn - 60L) * 1000L;
        log.info("[HikvisionCloudEye] 登录成功: client_id={}", info.appKey());
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[HikvisionCloudEye] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (String groupNo : groupNumbers()) {
            for (JsonNode node : page(PATH_DEVICE_LIST, Map.of("groupNo", groupNo))) {
                String serial = str(node, "deviceSerial");
                if (serial == null) {
                    continue;
                }
                result.add(new VendorDevice(
                        str(node, "deviceId", "deviceSerial"),
                        str(node, "deviceName"),
                        str(node, "deviceModel"),
                        "camera",
                        serial,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "1".equals(str(node, "deviceStatus"))));
            }
        }
        return result;
    }

    @Override
    public List<VendorDevice> listMonitors() {
        List<VendorDevice> result = new ArrayList<>();
        for (VendorDevice device : listDevices()) {
            String serial = device.serial();
            if (serial == null) {
                continue;
            }
            for (JsonNode node : page(PATH_CHANNEL_LIST, Map.of("deviceSerial", serial))) {
                String channelId = str(node, "channelId");
                if (channelId == null) {
                    continue;
                }
                String channelStatus = str(node, "channelStatus");
                result.add(new VendorDevice(
                        channelId,
                        str(node, "channelName"),
                        str(node, "childDeviceModel"),
                        "camera",
                        str(node, "ipcSerial", "deviceSerial"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        1,
                        "1".equals(channelStatus)));
            }
        }
        return result;
    }

    /**
     * 全部组编号（官方 {@code groups/actions/listAll}；设备列表接口要求 {@code groupNo} 必填）。
     *
     * @return 组编号
     */
    private List<String> groupNumbers() {
        List<String> result = new ArrayList<>();
        for (JsonNode node : toList(dataOf(sendGet(PATH_GROUP_LIST, Map.of())))) {
            String groupNo = str(node, "groupNo");
            if (groupNo != null) {
                result.add(groupNo);
            }
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
            Map<String, Object> body = params == null ? Map.of() : params;
            JsonNode root = "GET".equalsIgnoreCase(spec.method())
                    ? sendGet(path, body)
                    : sendJson(path, body);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 分页拉取全部条目（云眸是 GET + query）。
     *
     * @param path 接口路径
     * @param base 固定查询参数（如 {@code groupNo} / {@code deviceSerial}）
     * @return 全量条目
     */
    private List<JsonNode> page(String path, Map<String, Object> base) {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= 200; pageNo++) {
            Map<String, Object> query = new LinkedHashMap<>(base);
            query.put("pageNo", pageNo);
            query.put("pageSize", 100);
            JsonNode root = sendGet(path, query);
            List<JsonNode> list = toList(dataOf(root));
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < 100) {
                break;
            }
        }
        return all;
    }

    /**
     * 取响应 {@code data} 节点（云眸统一信封 {@code {code,message,data}}）。
     *
     * @param root 响应根节点
     * @return data 节点，缺失返回 null
     */
    private static JsonNode dataOf(JsonNode root) {
        return root == null ? null : root.get("data");
    }

    /**
     * 发 GET（业务接口），带 Bearer token。
     *
     * @param path  接口路径
     * @param query 查询参数
     * @return 响应根节点
     */
    private JsonNode sendGet(String path, Map<String, Object> query) {
        login();
        try {
            StringBuilder sb = new StringBuilder();
            if (query != null) {
                query.forEach((k, v) -> {
                    if (v == null) {
                        return;
                    }
                    sb.append(sb.length() == 0 ? '?' : '&');
                    sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
                    sb.append('=');
                    sb.append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
                });
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path + sb))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            return execute(request);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("云眸调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 发 JSON POST（业务接口），带 Bearer token。
     *
     * @param path 接口路径
     * @param body 请求体
     * @return 响应根节点
     */
    private JsonNode sendJson(String path, Map<String, Object> body) {
        login();
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return execute(request);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("云眸调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 发表单 POST（换 token）。
     *
     * @param path 接口路径
     * @param form 表单
     * @return 响应根节点
     */
    private JsonNode sendForm(String path, Map<String, String> form) {
        try {
            StringBuilder sb = new StringBuilder();
            form.forEach((k, v) -> {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
            });
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                    .build();
            return execute(request);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("云眸换 token 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 执行请求并校验信封。
     *
     * @param request 请求
     * @return 响应根节点
     */
    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401) {
                // token 失效：清缓存，下次调用自动重换
                this.accessToken = null;
                this.tokenExpiry = 0L;
                throw new IllegalStateException("云眸 token 已失效（HTTP 401），请重试");
            }
            JsonNode root = parse(response.body());
            if (root == null) {
                throw new IllegalStateException("云眸响应非 JSON: HTTP " + response.statusCode()
                        + " " + response.body().substring(0, Math.min(200, response.body().length())));
            }
            String code = str(root, "code");
            if (code != null && !"0".equals(code) && !"200".equals(code)) {
                throw new IllegalStateException("云眸返回失败: code=" + code
                        + ", message=" + str(root, "message", "msg"));
            }
            return root;
        } catch (java.io.IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("云眸请求异常: " + ex.getMessage(), ex);
        }
    }
}

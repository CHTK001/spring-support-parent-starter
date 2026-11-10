package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
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
 * 海康云曜 SDK 实现（官方未提供 Java SDK，按官方 OpenAPI 实现）。
 *
 * <p><b>官方文档核对结果</b>（open.hikyun.com 文档树 API 取得）：</p>
 * <ul>
 *   <li>换 token：{@code POST /artemis/oauth/token/v2}，字段 accessKey / secretKey / productCode / projectId</li>
 *   <li>令牌请求头：{@code access_token: <token>}（<b>不是</b> Authorization: Bearer）</li>
 *   <li>设备分页：{@code POST /api/eits/v1/global/device/page}（全局_ 接口，走 AK/SK 全局 token）</li>
 *   <li>监控点分页：{@code POST /api/eits/v1/global/monitor/page}</li>
 *   <li>组织分页：{@code POST /api/eits/v1/global/cascade/org/page}</li>
 *   <li>人员分页：{@code POST /api/eits/v1/global/user/page}</li>
 * </ul>
 *
 * <p>实测注意：{@code /api/eits/v2/global/device/page} 返回 400，只有 v1 可用；
 * 非 global 前缀属「需用户 Authorization」，用全局 token 会得到 401。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class HikvisionCloudYaoSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "cloud-yao";

    /** 默认网关 */
    private static final String DEFAULT_BASE_URL = "https://open.hikyun.com";

    /** 换 token */
    private static final String PATH_TOKEN = "/artemis/oauth/token/v2";

    /** 设备分页 */
    private static final String PATH_DEVICE_PAGE = "/artemis/api/eits/v1/global/device/page";

    /** 监控点分页 */
    private static final String PATH_MONITOR_PAGE = "/artemis/api/eits/v1/global/monitor/page";

    /** 组织分页 */
    private static final String PATH_ORG_PAGE = "/artemis/api/eits/v1/global/cascade/org/page";

    /** 人员分页 */
    private static final String PATH_USER_PAGE = "/artemis/api/eits/v1/global/user/page";

    /** 分页大小 */
    private static final int PAGE_SIZE = 100;

    /** HTTP 客户端（复用连接） */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 产品编码 */
    private final String productCode;

    /** 项目 ID */
    private final String projectId;

    /** 缓存的 access_token */
    private volatile String accessToken;

    /** token 过期时刻（毫秒） */
    private volatile long tokenExpiry;

    /**
     * 构造云曜 SDK 实现。
     *
     * @param info 连接信息
     */
    public HikvisionCloudYaoSdk(DeviceConnectInfo info) {
        super(info);
        this.productCode = extra("productCode");
        this.projectId = extra("projectId");
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public com.chua.starter.device.provider.sdk.PlatformApiVersion apiVersion() {
        // 实测：v1 的 global 分页接口可用；v2 的 device/page 返回 400，故锁定 v1
        return com.chua.starter.device.provider.sdk.PlatformApiVersion.verified(
                "eits-v1", "https://open.hikyun.com/");
    }

    @Override
    public java.util.List<com.chua.starter.device.provider.sdk.PlatformApiSpec> apis() {
        return HikvisionCloudYaoApis.all();
    }

    @Override
    public com.chua.starter.device.provider.sdk.PlatformApiResult invoke(
            String code, java.util.Map<String, Object> params) {
        com.chua.starter.device.provider.sdk.PlatformApiSpec spec = HikvisionCloudYaoApis.all().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElse(null);
        if (spec == null) {
            return com.chua.starter.device.provider.sdk.PlatformApiResult.fail(
                    code, "未登记的接口编码: " + code, null, 0L);
        }
        long start = System.currentTimeMillis();
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            if (params != null) {
                body.putAll(params);
            }
            if (productCode != null) {
                body.putIfAbsent("productCode", productCode);
            }
            if (projectId != null) {
                body.putIfAbsent("projectId", projectId);
            }
            String token = spec.auth() == com.chua.starter.device.provider.sdk.PlatformApiSpec.Auth.NONE
                    ? null : token();
            JsonNode root = send(spec.path(), body, token);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return com.chua.starter.device.provider.sdk.PlatformApiResult.ok(
                    code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return com.chua.starter.device.provider.sdk.PlatformApiResult.fail(
                    code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
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
     * 换取全局 token（12 小时有效，提前 60 秒刷新）。
     */
    private synchronized void login() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessKey", info.appKey());
        body.put("secretKey", info.appSecret());
        if (productCode != null) {
            body.put("productCode", productCode);
        }
        if (projectId != null) {
            body.put("projectId", projectId);
        }
        JsonNode root = send(PATH_TOKEN, body, null);
        JsonNode data = root == null ? null : root.get("data");
        String token = data == null ? null : str(data, "access_token", "accessToken");
        if (token == null) {
            throw new IllegalStateException("海康云曜换取 access_token 失败: " + (root == null ? "空响应" : str(root, "msg", "message")));
        }
        long expiresIn = 43_200L;
        JsonNode expiresNode = data.get("expires_in");
        if (expiresNode != null && !expiresNode.isNull()) {
            expiresIn = expiresNode.asLong(43_200L);
        }
        this.accessToken = token;
        this.tokenExpiry = System.currentTimeMillis() + Math.max(60L, expiresIn - 60L) * 1000L;
        log.info("[HikvisionCloudYao] 登录成功: productCode={}, projectId={}", productCode, projectId);
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[HikvisionCloudYao] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : page(PATH_DEVICE_PAGE)) {
            result.add(new VendorDevice(
                    str(node, "deviceSerial", "deviceId", "id", "serial"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "productModel", "model"),
                    "camera",
                    str(node, "deviceSerial", "serial"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "address", "deviceAddress"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "onlineStatus", "online", "status")));
        }
        return result;
    }

    @Override
    public List<VendorDevice> listMonitors() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : page(PATH_MONITOR_PAGE)) {
            result.add(new VendorDevice(
                    str(node, "monitorCode", "monitorUniqueCode", "id"),
                    str(node, "monitorName", "name", "deviceName"),
                    str(node, "deviceModel", "model"),
                    "camera",
                    str(node, "deviceSerial", "deviceCode", "serial"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "address", "installAddress"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    1,
                    bool(node, "onlineStatus", "online", "status")));
        }
        return result;
    }

    @Override
    public List<OrganizationNode> listOrganizations() {
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : page(PATH_ORG_PAGE)) {
            result.add(new OrganizationNode(
                    str(node, "id", "orgId", "orgCode", "departmentId"),
                    str(node, "name", "orgName", "departmentName", "title"),
                    str(node, "parentId", "parentOrgId", "parentDepartmentId")));
        }
        return result;
    }

    @Override
    public List<DevicePerson> listPersons() {
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : page(PATH_USER_PAGE)) {
            result.add(new DevicePerson(
                    str(node, "userDepartmentId", "departmentId", "orgId"),
                    str(node, "id", "userId", "userName"),
                    str(node, "nickName", "userName", "name"),
                    str(node, "departmentName", "title"),
                    str(node, "phoneNo", "phone", "mobile"),
                    str(node, "email", "mail")));
        }
        return result;
    }

    /**
     * 分页拉取全部条目。
     *
     * @param path 接口路径
     * @return 全量条目
     */
    private List<JsonNode> page(String path) {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= 200; pageNo++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            if (productCode != null) {
                body.put("productCode", productCode);
            }
            if (projectId != null) {
                body.put("projectId", projectId);
            }
            JsonNode root = send(path, body, token());
            JsonNode data = root == null ? null : root.get("data");
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /**
     * 取当前 token（过期自动续）。
     *
     * @return access_token
     */
    private String token() {
        login();
        return accessToken;
    }

    /**
     * 发一次 POST。
     *
     * @param path  接口路径
     * @param body  请求体
     * @param token access_token，null 表示换 token 请求
     * @return 解析后的 JSON 根节点
     */
    private JsonNode send(String path, Map<String, Object> body, String token) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (token != null) {
                // 云曜网关要求 access_token 头；Authorization: Bearer 会被当成未传
                builder.header("access_token", token);
            }
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = parse(response.body());
            if (root == null) {
                throw new IllegalStateException("海康云曜响应非 JSON: HTTP " + response.statusCode()
                        + " " + response.body().substring(0, Math.min(200, response.body().length())));
            }
            String code = str(root, "code");
            if (code != null && !"0".equals(code) && !"200".equals(code)) {
                throw new IllegalStateException("海康云曜返回失败: code=" + code + ", msg=" + str(root, "msg", "message"));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("海康云曜调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }
}

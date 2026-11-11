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
 * <p><b>官方文档核对结果</b>（help.hikyun.com 文档快照）：</p>
 * <ul>
 *   <li>换 token：{@code POST /artemis/oauth/token/v2}，字段 accessKey / secretKey / productCode / projectId</li>
 *   <li>令牌请求头：{@code access_token: <token>}（<b>不是</b> Authorization: Bearer）</li>
 *   <li>设备分页：{@code POST /artemis/api/eits/v2/global/device/page}，
 *       包体是<b>条件数组</b> {@code [{key,option:"eq",value}]}（pageNo / pageSize / projectId）</li>
 *   <li>监控点分页：{@code POST /artemis/api/eits/v2/global/monitor/page}，
 *       包体 {@code {projectId,pageNo,pageSize}}；该接口的官方入参写的是
 *       {@code Authorization} 用户凭证，AK/SK 的 access_token 不满足，未配 userToken 时会被网关拒绝</li>
 *   <li>组织分页：{@code POST /artemis/api/eits/fdcs/v1/personOrg/page}</li>
 *   <li>人员分页：{@code POST /artemis/api/eits/fdcs/v1/person/page}</li>
 * </ul>
 *
 * <p>文档里的「请求路径」均不含 {@code /artemis} 前缀，真实调用必须补上。</p>
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

    /** 设备分页（官方 v2 全局接口，条件数组包体） */
    private static final String PATH_DEVICE_PAGE = "/artemis/api/eits/v2/global/device/page";

    /** 监控点分页（官方 v2 全局接口，对象包体） */
    private static final String PATH_MONITOR_PAGE = "/artemis/api/eits/v2/global/monitor/page";

    /** 组织分页（官方人员组织域接口） */
    private static final String PATH_ORG_PAGE = "/artemis/api/eits/fdcs/v1/personOrg/page";

    /** 人员分页（官方人员组织域接口） */
    private static final String PATH_USER_PAGE = "/artemis/api/eits/fdcs/v1/person/page";

    /** 分页大小 */
    private static final int PAGE_SIZE = 100;

    /** 分页翻页上限，防御平台不返回 total 时死循环 */
    private static final int MAX_PAGES = 200;

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
        // 官方文档口径：设备/监控点分页在 v2，人员组织域在 fdcs/v1
        return com.chua.starter.device.provider.sdk.PlatformApiVersion.verified(
                "eits-v2", "https://open.hikyun.com/artemis/");
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
        for (JsonNode node : devicePage()) {
            result.add(new VendorDevice(
                    str(node, "deviceSerial", "id"),
                    str(node, "deviceName"),
                    str(node, "model"),
                    categoryOf(str(node, "modelType", "modelTypeStr")),
                    str(node, "deviceSerial"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    integer(node, "channumCount", "channelNum"),
                    bool(node, "onlineStatus")));
        }
        return result;
    }

    @Override
    public List<VendorDevice> listMonitors() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : monitorPage()) {
            result.add(new VendorDevice(
                    str(node, "monitorCode", "monitorSerial", "id"),
                    str(node, "monitorName", "deviceName"),
                    str(node, "model"),
                    categoryOf(str(node, "modelType", "moduleType")),
                    str(node, "monitorSerial"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    1,
                    bool(node, "onlineStatus")));
        }
        return result;
    }

    @Override
    public List<OrganizationNode> listOrganizations() {
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : fdcsPage(PATH_ORG_PAGE)) {
            result.add(new OrganizationNode(
                    str(node, "orgId", "orgCode"),
                    str(node, "orgName"),
                    str(node, "parentOrgId")));
        }
        return result;
    }

    @Override
    public List<DevicePerson> listPersons() {
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : fdcsPage(PATH_USER_PAGE)) {
            result.add(new DevicePerson(
                    str(node, "orgId"),
                    str(node, "personId"),
                    str(node, "personName"),
                    str(node, "jobPost", "personNum"),
                    str(node, "phone", "phoneNo"),
                    str(node, "email")));
        }
        return result;
    }

    /**
     * 设备分页逐页拉取（官方 v2 全局接口，包体是条件数组）。
     *
     * @return 全量设备条目
     */
    private List<JsonNode> devicePage() {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            List<Map<String, Object>> conditions = new ArrayList<>();
            conditions.add(condition("pageNo", pageNo));
            conditions.add(condition("pageSize", PAGE_SIZE));
            if (projectId != null) {
                conditions.add(condition("projectId", projectId));
            }
            JsonNode root = send(PATH_DEVICE_PAGE, conditions, token());
            List<JsonNode> list = toList(root == null ? null : root.get("data"));
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || lastPage(root == null ? null : root.get("data"), pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 监控点分页逐页拉取（官方 v2 全局接口，包体是对象）。
     *
     * @return 全量监控点条目
     */
    private List<JsonNode> monitorPage() {
        if (projectId == null) {
            log.info("[HikvisionCloudYao] 未配置 projectId，监控点分页不可用（官方 projectId 必填）");
            return List.of();
        }
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectId", projectIdNumber());
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode root = send(PATH_MONITOR_PAGE, body, token());
            List<JsonNode> list = toList(root == null ? null : root.get("data"));
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || lastPage(root == null ? null : root.get("data"), pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 人员/组织分页逐页拉取（官方 fdcs 接口，projectId + productCode 必填）。
     *
     * @param path 接口路径
     * @return 全量条目
     */
    private List<JsonNode> fdcsPage(String path) {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> body = new LinkedHashMap<>();
            if (projectId != null) {
                body.put("projectId", projectIdNumber());
            }
            if (productCode != null) {
                body.put("productCode", productCode);
            }
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode root = send(path, body, token());
            List<JsonNode> list = toList(root == null ? null : root.get("data"));
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || lastPage(root == null ? null : root.get("data"), pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 构造设备分页条件项 {@code {key,option:"eq",value}}。
     *
     * @param key   字段
     * @param value 值
     * @return 条件项
     */
    private static Map<String, Object> condition(String key, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("option", "eq");
        item.put("value", value);
        return item;
    }

    /**
     * 项目 ID 的包体取值：官方示例是数字，非数字时原样下发。
     *
     * @return projectId 或 {@code null}
     */
    private Object projectIdNumber() {
        if (projectId == null) {
            return null;
        }
        try {
            return Long.parseLong(projectId);
        } catch (NumberFormatException ex) {
            return projectId;
        }
    }

    /**
     * 分页是否已到末页（响应带 totalPage / total 时据此提前结束）。
     *
     * @param data   响应 data 节点
     * @param pageNo 当前页
     * @return true 已到末页
     */
    private static boolean lastPage(JsonNode data, int pageNo) {
        if (data == null || !data.isObject()) {
            return false;
        }
        int totalPage = data.path("totalPage").asInt(0);
        if (totalPage > 0) {
            return pageNo >= totalPage;
        }
        long total = data.path("total").asLong(0L);
        return total > 0 && (long) pageNo * PAGE_SIZE >= total;
    }

    /**
     * 设备类型映射：CAMERA 视频 / ALARM 报警 / ACCESS 门禁。
     *
     * @param code 平台类型码
     * @return 设备分类
     */
    private static String categoryOf(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.contains("ACCESS") || value.contains("门禁")) {
            return "accessControl";
        }
        if (value.contains("ALARM") || value.contains("报警")) {
            return "alarm";
        }
        return "camera";
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
    private JsonNode send(String path, Object body, String token) {
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

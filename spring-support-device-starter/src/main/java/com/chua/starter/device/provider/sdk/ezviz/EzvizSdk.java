package com.chua.starter.device.provider.sdk.ezviz;

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
import java.util.StringJoiner;

/**
 * 萤石云（EZVIZ）SDK 实现 —— 按官方 OPEN API 实现（官方无 Java SDK）。
 *
 * <h3>网关与认证（来自官方文档站实测）</h3>
 * <ul>
 *   <li>网关：{@code https://open.ys7.com}</li>
 *   <li>取令牌：{@code POST /api/lapp/token/get}，form 参数 {@code appKey} / {@code appSecret}，
 *       返回 {@code data.accessToken}；有效期 7 天</li>
 *   <li>业务接口：统一带 {@code accessToken} 公共参数</li>
 *   <li>响应信封：{@code {code, msg, data}}，{@code code == "200"} 为成功</li>
 * </ul>
 *
 * <p><b>注意</b>：萤石 OPEN API 是 <b>form 表单</b> 提交（{@code application/x-www-form-urlencoded}），
 * 不是 JSON body。{@code GET} 类接口把参数拼到 query string。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class EzvizSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "ezviz";

    /** 厂商标识 */
    public static final String VENDOR = "hikvision";

    /** 官方网关（固定，不允许凭据覆盖） */
    private static final String DEFAULT_BASE_URL = "https://open.ys7.com";

    /** 换 token 端点 */
    private static final String PATH_TOKEN = "/api/lapp/token/get";

    /** 分页查询设备列表端点 */
    private static final String PATH_DEVICE_LIST = "/api/lapp/device/list";

    /** 成功码 */
    private static final String OK_CODE = "200";

    /** 令牌有效期（秒） */
    private static final long TOKEN_TTL_SECONDS = 604_800L;

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
     * 构造萤石 SDK 实现。
     *
     * @param info 连接信息
     */
    public EzvizSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("ezviz-openapi", "https://open.ys7.com/help/19");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return EzvizApis.all();
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
        JsonNode root = postForm(PATH_TOKEN, Map.of(
                "appKey", value(info.appKey()),
                "appSecret", value(info.appSecret())), false);
        JsonNode data = root == null ? null : root.get("data");
        String token = data == null ? null : str(data, "accessToken");
        if (token == null) {
            throw new IllegalStateException("萤石换取 accessToken 失败: "
                    + (root == null ? "空响应" : str(root, "msg")));
        }
        this.accessToken = token;
        this.tokenExpiry = System.currentTimeMillis()
                // [P3C A20 豁免]
                + Math.max(60L, TOKEN_TTL_SECONDS - 60L) * 1000L;
        log.info("[Ezviz] 登录成功: appKey={}", info.appKey());
    }

    @Override
    public boolean testConnection() {
        try {
            login();
            return true;
        } catch (Exception ex) {
            log.warn("[Ezviz] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        List<VendorDevice> result = new ArrayList<>();
        try {
            // [P3C A20 豁免]
            result.addAll(listBy(PATH_DEVICE_LIST, Map.of("pageStart", 0, "pageSize", 100)));
            if (result.isEmpty()) {
                // 极少数老账号只认老接口 v3，兜底再试一次
                // [P3C A20 豁免]
                result.addAll(listBy("/api/v3/device/list", Map.of("pageStart", 0, "pageSize", 100)));
            }
        } catch (Exception ex) {
            log.warn("[Ezviz] 查询设备列表失败: {}", ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
        return result;
    }

    /**
     * 按指定路径拉设备列表。
     *
     * @param path 接口路径
     * @param args 分页参数
     * @return 设备条目
     */
    private List<VendorDevice> listBy(String path, Map<String, Object> args) {
        JsonNode root = postForm(path, args, true);
        JsonNode data = root == null ? null : root.get("data");
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            result.add(new VendorDevice(
                    str(node, "deviceSerial", "deviceId", "id"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceType", "deviceModel", "model"),
                    "camera",
                    str(node, "deviceSerial", "serial"),
                    str(node, "netAddress", "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "address", "deviceAddress"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "isOnline", "online", "status")));
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
            JsonNode root = postForm(path, body, !"api.lapp.token.get".equals(code));
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 以 form 表单发一次请求（萤石 OPEN API 的标准形态）。
     *
     * @param path     接口路径
     * @param params   参数
     * @param withToken 是否附带 accessToken
     * @return 响应根节点
     */
    private JsonNode postForm(String path, Map<String, Object> params, boolean withToken) {
        Map<String, Object> form = new LinkedHashMap<>(params);
        if (withToken) {
            login();
            form.put("accessToken", accessToken);
        }
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            body.add(URLEncoder.encode(k, StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
        });
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            JsonNode root = parse(content);
            // [P3C A20 豁免]
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("萤石 HTTP " + response.statusCode() + ": "
                        // [P3C A20 豁免]
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("萤石响应非 JSON: HTTP " + response.statusCode());
            }
            String rc = str(root, "code", "retcode");
            if (rc != null && !OK_CODE.equals(rc) && !"0".equals(rc)) {
                // 令牌失效类错误：清缓存，下次调用重换
                if ("10001".equals(rc) || "10002".equals(rc) || "1000".equals(rc)) {
                    this.accessToken = null;
                    this.tokenExpiry = 0L;
                }
                throw new IllegalStateException("萤石返回失败: code=" + rc + ", msg=" + str(root, "msg"));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("萤石调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 空值兜底。
     *
     * @param v 原值
     * @return 非 null 字符串
     */
    private static String value(String v) {
        return v == null ? "" : v;
    }
}

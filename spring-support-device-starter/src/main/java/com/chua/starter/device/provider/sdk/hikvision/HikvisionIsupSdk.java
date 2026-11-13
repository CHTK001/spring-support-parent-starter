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
 * 海康 ISUP / EHome SDK 实现。
 *
 * <h3>协议说明（重要）</h3>
 * <p>ISUP（原 EHome）是海康的<b>设备主动注册协议</b>，不是 HTTP OpenAPI。
 * 标准做法是：设备向 ISUP 服务端注册（默认 7660），服务端用 ISUP SDK 接收注册、
 * 透传预览/回放/云台/告警。因此本实现有两种落地形态：</p>
 * <ol>
 *   <li><b>转发模式（本类默认）</b>：凭据里配置 {@code baseUrl} 指向「ISUP 服务端的
 *       REST 封装」（企业自建或第三方 ISUP 网关），本类以 HTTP 调用其查询/控制接口。
 *       这是纯 Java 可落地的形态，无需厂商 C 库。</li>
 *   <li><b>SDK 模式</b>：进程内加载海康 ISUP SDK（C/C++ 动态库，需 JNA 封装），
 *       由上层另行实现 {@link com.chua.starter.device.provider.sdk.DevicePlatformSdk}。</li>
 * </ol>
 *
 * <p>凭据未配置 {@code baseUrl} 时，查询类接口会明确报错（不会伪造数据），
 * 提示先部署 ISUP 服务端或改用 SDK 模式。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class HikvisionIsupSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "isup";

    /** 厂商标识 */
    public static final String VENDOR = "hikvision";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            // [P3C A20 豁免]
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 构造 ISUP SDK 实现。
     *
     * @param info 连接信息
     */
    public HikvisionIsupSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("isup-v1.0", "https://open.hikvision.com/");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return HikvisionIsupApis.all();
    }

    /**
     * ISUP 服务端 REST 封装地址（凭据必填，无固定公网网关）。
     *
     * @return base url；未配置返回 null
     */
    private String baseUrl() {
        String base = extra("baseUrl");
        return base == null || base.isBlank() ? null : base.trim().replaceAll("/+$", "");
    }

    @Override
    public boolean testConnection() {
        String base = baseUrl();
        if (base == null) {
            log.warn("[HikvisionIsup] 未配置 ISUP 服务端地址（baseUrl），无法探测");
            return false;
        }
        try {
            JsonNode root = send(base + "/isup/device/list", Map.of("pageNo", 1, "pageSize", 1));
            return root != null;
        } catch (Exception ex) {
            log.warn("[HikvisionIsup] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        String base = baseUrl();
        if (base == null) {
            throw new IllegalStateException(
                    "ISUP 未配置服务端地址：请在凭据里填 baseUrl（企业自建 ISUP 服务端 REST 封装地址），"
                            + "或改用 ISUP SDK 模式");
        }
        // [P3C A20 豁免]
        JsonNode root = send(base + "/isup/device/list", Map.of("pageNo", 1, "pageSize", 200));
        JsonNode data = root == null ? null : root.get("data");
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            result.add(new VendorDevice(
                    str(node, "deviceId", "ehomeId", "serial", "id"),
                    str(node, "deviceName", "name"),
                    str(node, "deviceModel", "model"),
                    "camera",
                    str(node, "ehomeId", "serial", "deviceId"),
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
            return PlatformApiResult.fail(code, "未登记的能力编码: " + code, null, 0L);
        }
        long start = System.currentTimeMillis();
        String base = baseUrl();
        if (base == null) {
            return PlatformApiResult.fail(code,
                    "ISUP 未配置服务端地址（baseUrl）；ISUP 是设备注册协议，"
                            + "需部署 ISUP 服务端后以其 REST 封装地址接入",
                    null, System.currentTimeMillis() - start);
        }
        try {
            String path = extra("apiPath." + code);
            if (path == null || path.isBlank()) {
                path = "/isup/" + spec.code().substring("isup.".length());
            }
            JsonNode root = send(base + path, params == null ? new LinkedHashMap<>() : params);
            JsonNode data = root == null ? null : root.get("data");
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 发一次 POST 到 ISUP 服务端封装。
     *
     * @param url  完整地址
     * @param body 请求体
     * @return 响应根节点
     */
    private JsonNode send(String url, Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (info.appKey() != null) {
                builder.header("X-App-Key", info.appKey());
            }
            if (info.appSecret() != null) {
                builder.header("X-App-Secret", info.appSecret());
            }
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            JsonNode root = parse(content);
            // [P3C A20 豁免]
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ISUP 服务端 HTTP " + response.statusCode() + ": "
                        // [P3C A20 豁免]
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("ISUP 服务端响应非 JSON: HTTP " + response.statusCode());
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ISUP 调用失败: " + url + " -> " + ex.getMessage(), ex);
        }
    }
}

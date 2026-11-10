package com.chua.starter.device.provider.sdk.protocol;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.PlatformApiVersion;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.VendorDevice;
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
 * GB/T 28181 国标接入 SDK 实现。
 *
 * <h3>协议说明</h3>
 * <p>GB28181 是 SIP 信令 + RTP 媒体的国标协议，不是 HTTP API。实际部署需要一个
 * SIP 服务端（WVP / ZLMediaKit / SRS / 自研 SIP 栈）接收设备注册与目录查询。</p>
 *
 * <p>本实现采用<b>网关转发模式</b>：凭据 {@code baseUrl} 指向国标 SIP 服务端提供的
 * REST 封装（如 WVP 的 {@code /api/v1/device/list}），本类以 HTTP 调用。
 * 未配置 {@code baseUrl} 时明确报错，不伪造数据。</p>
 *
 * <p>若需要进程内 SIP 栈，请另行实现 {@link com.chua.starter.device.provider.sdk.DevicePlatformSdk}
 * 并通过 SPI 注册（{@link com.chua.starter.device.provider.sdk.DevicePlatformSdks#register}）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class Gb28181Sdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "gb28181";

    /** 厂商标识（标准协议，不绑定厂商） */
    public static final String VENDOR = "standard";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 构造 GB28181 SDK 实现。
     *
     * @param info 连接信息
     */
    public Gb28181Sdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("gb28181-2016",
                "https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=3F4A2A5D4E3E1E3A0F4A5D4E3E1E3A0F");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return Gb28181Apis.gb();
    }

    /**
     * 国标 SIP 服务端的 REST 封装地址（凭据必填）。
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
            log.warn("[GB28181] 未配置国标 SIP 服务端地址（baseUrl），无法探测");
            return false;
        }
        try {
            return send(base + "/api/v1/device/list", Map.of("page", 1, "count", 1)) != null;
        } catch (Exception ex) {
            log.warn("[GB28181] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        String base = baseUrl();
        if (base == null) {
            throw new IllegalStateException(
                    "GB28181 未配置 SIP 服务端地址：请在凭据里填 baseUrl（WVP/ZLMediaKit 等服务的 REST 地址）");
        }
        JsonNodeHolder holder = new JsonNodeHolder(send(base + "/api/v1/device/list",
                Map.of("page", 1, "count", 500)));
        List<VendorDevice> result = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode node : toList(holder.root)) {
            result.add(new VendorDevice(
                    str(node, "deviceId", "gbId", "id"),
                    str(node, "name", "deviceName"),
                    str(node, "manufacturer", "model"),
                    "camera",
                    str(node, "deviceId", "gbId"),
                    str(node, "ip", "hostAddress"),
                    integer(node, "port"),
                    str(node, "civilCode", "address"),
                    decimal(node, "longitude", "lng"),
                    decimal(node, "latitude", "lat"),
                    integer(node, "channelCount"),
                    bool(node, "online", "status")));
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
                    "GB28181 未配置 SIP 服务端地址（baseUrl）；国标是 SIP 协议，需部署 WVP 等服务端后以其 REST 地址接入",
                    null, System.currentTimeMillis() - start);
        }
        try {
            String path = extra("apiPath." + code);
            if (path == null || path.isBlank()) {
                path = "/api/v1/" + spec.code().substring("gb.".length());
            }
            com.fasterxml.jackson.databind.JsonNode root = send(base + path,
                    params == null ? new LinkedHashMap<>() : params);
            Object normalized = root == null ? null : MAPPER.convertValue(root, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 发一次 GET/POST 到国标服务端封装。
     *
     * @param url  完整地址
     * @param body 请求体
     * @return 响应根节点
     */
    private com.fasterxml.jackson.databind.JsonNode send(String url, Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String content = response.body();
            com.fasterxml.jackson.databind.JsonNode root = parse(content);
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("GB28181 服务端 HTTP " + response.statusCode() + ": "
                        + (content == null ? "" : content.substring(0, Math.min(200, content.length()))));
            }
            if (root == null) {
                throw new IllegalStateException("GB28181 服务端响应非 JSON: HTTP " + response.statusCode());
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("GB28181 调用失败: " + url + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 承载解析结果，避免嵌套过深。
     */
    private static final class JsonNodeHolder {
        /** 响应根节点 */
        private final com.fasterxml.jackson.databind.JsonNode root;

        /**
         * 构造持有者。
         *
         * @param root 根节点
         */
        private JsonNodeHolder(com.fasterxml.jackson.databind.JsonNode root) {
            this.root = root;
        }
    }
}

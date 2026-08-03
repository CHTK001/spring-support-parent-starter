package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海康安防平台（iSecure Center / 综合安防管理平台）OpenAPI 会话
 * <p>对接海康综合安防平台 OpenAPI（artemis 协议），纯 Java HTTP 全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>endpoint、AppKey/AppSecret、签名算法需按安防平台官方 OpenAPI 文档配置</li>
 *   <li>常见签名：x-ca-key + x-ca-signature-headers + HMAC-SHA256（artemis 网关）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HikvisionSecurePlatformSession extends AbstractOpenApiSession {

    /**
     * 海康安防平台默认 baseUrl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://artemis.hikvision.com";

    /**
     * 视频取流接口
     */
    private static final String PATH_VIDEO_STREAM = "/api/video/v1/cameras/previewURLs";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/api/video/v1/ptz/channels/controllable";

    /**
     * 门禁控制接口
     */
    private static final String PATH_DOOR_CONTROL = "/api/access/v1/door/control";

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;

    /**
     * 平台 baseUrl
     */
    private final String baseUrl;

    public HikvisionSecurePlatformSession(DeviceConnectInfo info) {
        super(info);
        Object extra = info.getExtra() == null ? null : info.getExtra().get("baseUrl");
        this.baseUrl = extra instanceof String s ? s : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT))
                .build();
        // 安防平台通常无独立登录，AppKey/AppSecret 通过签名头传递
        this.accessToken = "artemis:" + info.getAppKey();
        this.tokenExpiry = java.time.Instant.MAX;
    }

    /**
     * artemis 网关签名：x-ca-signature = Base64(HMAC-SHA256(appSecret, StringToSign))
     *
     * @param method    HTTP 方法
     * @param path      请求路径（含 query）
     * @param headers   参与签名的请求头
     * @return 签名字符串
     */
    private String artemisSign(String method, String path, Map<String, String> headers) {
        String stringToSign = method + "\n"
                + headers.getOrDefault("x-ca-key", "") + "\n"
                + path + "\n"
                + headers.getOrDefault("x-ca-timestamp", "") + "\n"
                + headers.getOrDefault("x-ca-nonce", "");
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(utf8(info.getAppSecret()), "HmacSHA256"));
            byte[] digest = mac.doFinal(utf8(stringToSign));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("artemis 签名失败", e);
        }
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = java.util.UUID.randomUUID().toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-ca-key", info.getAppKey());
        headers.put("x-ca-timestamp", timestamp);
        headers.put("x-ca-nonce", nonce);
        headers.put("Accept", "application/json");

        String query = "";
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            params.forEach((k, v) -> {
                if (v != null) {
                    if (sb.length() > 0) sb.append("&");
                    sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8));
                    sb.append("=");
                    sb.append(java.net.URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
                }
            });
            query = sb.toString();
        }
        String fullPath = query.isEmpty() ? path : path + "?" + query;
        headers.put("x-ca-signature-headers", "x-ca-key,x-ca-timestamp,x-ca-nonce");
        headers.put("x-ca-signature", artemisSign(method, fullPath, headers));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + fullPath))
                    .timeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .headers(headers.entrySet().stream()
                            .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                            .toArray(String[]::new))
                    .method(method.toUpperCase(),
                            "GET".equalsIgnoreCase(method) ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return MAPPER.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("海康安防平台 HTTP 调用失败: " + method + " " + path + " -> " + e.getMessage(), e);
        }
    }

    /**
     * 获取摄像头预览 URL（HLS/RTSP 等）
     */
    public JsonNode getPreviewUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.getDeviceSerial());
        params.put("streamType", 0);
        params.put("protocol", "rtsp");
        params.put("transmode", 1);
        return assertOk(post(PATH_VIDEO_STREAM, params));
    }

    @Override
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.getDeviceSerial());
        params.put("action", mapArtemisDirection(cmd));
        params.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ_CONTROL, params);
    }

    private int mapArtemisDirection(com.chua.starter.device.spi.model.PtzCommand cmd) {
        // artemis 云台方向码（参考 iSecure Center OpenAPI 文档）
        switch (cmd) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            case ZOOM_IN: return 10;
            case ZOOM_OUT: return 11;
            case FOCUS_IN: return 12;
            case FOCUS_OUT: return 13;
            case IRIS_IN: return 14;
            case IRIS_OUT: return 15;
            default: return -1;
        }
    }

    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("doorIndexCode", info.getDeviceSerial() + ":" + doorId);
        params.put("controlType", 1);
        post(PATH_DOOR_CONTROL, params);
    }
}

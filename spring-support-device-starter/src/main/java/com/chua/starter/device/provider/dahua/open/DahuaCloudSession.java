package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 大华云睿（DH Cloud / DoLynk Cloud / ICC）平台 OpenAPI 会话
 * <p>对接大华云睿平台 OpenAPI，纯 Java HTTP 全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>endpoint、AppKey/AppSecret、签名算法需按大华云睿 OpenAPI 文档配置</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DahuaCloudSession extends AbstractOpenApiSession {

    /**
     * 大华云睿默认 baseUrl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://api.dahua-cloud.com";

    /**
     * 登录接口
     */
    private static final String PATH_LOGIN = "/openapi/auth/login";

    /**
     * 实时预览 URL 接口
     */
    private static final String PATH_LIVE_URL = "/openapi/video/live/url";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/openapi/ptz/control";

    /**
     * 门禁控制接口
     */
    private static final String PATH_DOOR_CONTROL = "/openapi/access/control";

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;

    /**
     * 平台 baseUrl
     */
    private final String baseUrl;

    public DahuaCloudSession(DeviceConnectInfo info) {
        super(info);
        Object extra = info.getExtra() == null ? null : info.getExtra().get("baseUrl");
        this.baseUrl = extra instanceof String s ? s : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT))
                .build();
        login();
    }

    /**
     * 大华云睿签名：sign = MD5(appSecret + time + nonce + appKey)。
     */
    private String sign(String time, String nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String src = info.getAppSecret() + time + nonce + info.getAppKey();
            byte[] digest = md.digest(utf8(src));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("大华云睿签名失败", e);
        }
    }

    private void login() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("appKey", info.getAppKey());
            params.put("appSecret", info.getAppSecret());
            JsonNode resp = doRequest("POST", PATH_LOGIN, params);
            JsonNode data = assertOk(resp);
            String token = data.path("accessToken").asText(null);
            long expires = data.path("expiresIn").asLong(7200L);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("大华云睿 accessToken 为空");
            }
            cacheToken(token, expires);
            log.info("大华云睿登录成功: deviceSerial={}", info.getDeviceSerial());
        } catch (Exception e) {
            throw new IllegalStateException("大华云睿登录失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!tokenValid() && !PATH_LOGIN.equals(path)) {
            login();
        }
        String time = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (accessToken != null && !PATH_LOGIN.equals(path)) {
            realParams.put("accessToken", accessToken);
        }
        realParams.put("appKey", info.getAppKey());
        realParams.put("time", time);
        realParams.put("nonce", nonce);
        realParams.put("sign", sign(time, nonce));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT))
                    .header("Content-Type", "application/json;charset=UTF-8");
            String body = MAPPER.writeValueAsString(realParams);
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return MAPPER.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("大华云睿 HTTP 调用失败: " + method + " " + path + " -> " + e.getMessage(), e);
        }
    }

    public JsonNode getLiveUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("stream", 0);
        return assertOk(post(PATH_LIVE_URL, params));
    }

    @Override
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", 1);
        params.put("action", mapAction(cmd));
        params.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ_CONTROL, params);
    }

    private String mapAction(com.chua.starter.device.spi.model.PtzCommand cmd) {
        switch (cmd) {
            case UP: return "up";
            case DOWN: return "down";
            case LEFT: return "left";
            case RIGHT: return "right";
            case ZOOM_IN: return "zoomIn";
            case ZOOM_OUT: return "zoomOut";
            case FOCUS_IN: return "focusNear";
            case FOCUS_OUT: return "focusFar";
            case IRIS_IN: return "irisOpen";
            case IRIS_OUT: return "irisClose";
            default: return "stop";
        }
    }

    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("doorId", doorId);
        params.put("action", "open");
        post(PATH_DOOR_CONTROL, params);
    }
}

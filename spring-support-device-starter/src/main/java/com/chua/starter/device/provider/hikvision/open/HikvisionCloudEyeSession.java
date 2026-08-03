package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海康云眸（Cloud Eye / iSecure Center 公有云版）OpenAPI 会话
 * <p>对接海康企业级视频云平台 OpenAPI，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>海康云眸 endpoint、AppKey/AppSecret、签名算法需按平台官方 OpenAPI 文档配置</li>
 *   <li>本类提供签名 + HTTP 调用框架，具体 endpoint 与字段以官方文档为准</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HikvisionCloudEyeSession extends AbstractOpenApiSession {

    /**
     * 海康云眸默认 baseUrl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://api.hik-cloud-eye.com";

    /**
     * 登录换取 token 接口
     */
    private static final String PATH_LOGIN = "/api/v1/auth/login";

    /**
     * 视频取流地址接口
     */
    private static final String PATH_VIDEO_URL = "/api/v1/video/url";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/api/v1/ptz/control";

    /**
     * 门禁控制接口
     */
    private static final String PATH_DOOR_CONTROL = "/api/v1/door/control";

    public HikvisionCloudEyeSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        login();
    }

    /**
     * 登录换取 sessionId / token
     */
    private void login() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appKey", info.getAppKey());
        params.put("appSecret", info.getAppSecret());
        try {
            JsonNode resp = execute("POST", PATH_LOGIN, params, null);
            JsonNode data = assertOk(resp);
            String token = data.path("token").asText(null);
            long expires = data.path("expiresIn").asLong(7200L);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("海康云眸 token 为空");
            }
            cacheToken(token, expires);
            log.info("海康云眸登录成功: deviceSerial={}", info.getDeviceSerial());
        } catch (Exception e) {
            throw new IllegalStateException("海康云眸登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 海康云眸签名：参数按字典序排序后拼接 + appSecret，SHA-256。
     */
    private String sign(Map<String, Object> params) {
        String sorted = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b).orElse("");
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(utf8(sorted + info.getAppSecret()));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("海康云眸签名失败", e);
        }
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!tokenValid() && !PATH_LOGIN.equals(path)) {
            login();
        }
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (accessToken != null) {
            realParams.put("token", accessToken);
        }
        if (!PATH_LOGIN.equals(path)) {
            realParams.put("sign", sign(realParams));
        }
        return execute(method, path, realParams, null);
    }

    /**
     * 获取视频取流 URL
     */
    public JsonNode getVideoUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("streamType", "main");
        return assertOk(post(PATH_VIDEO_URL, params));
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
        params.put("speed", Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ_CONTROL, params);
    }

    private String mapAction(com.chua.starter.device.spi.model.PtzCommand cmd) {
        switch (cmd) {
            case UP: return "UP";
            case DOWN: return "DOWN";
            case LEFT: return "LEFT";
            case RIGHT: return "RIGHT";
            case ZOOM_IN: return "ZOOM_IN";
            case ZOOM_OUT: return "ZOOM_OUT";
            case FOCUS_IN: return "FOCUS_IN";
            case FOCUS_OUT: return "FOCUS_OUT";
            case IRIS_IN: return "IRIS_OPEN";
            case IRIS_OUT: return "IRIS_CLOSE";
            default: return "STOP";
        }
    }

    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("doorId", doorId);
        params.put("action", "OPEN");
        post(PATH_DOOR_CONTROL, params);
    }
}

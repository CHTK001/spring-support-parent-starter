package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.support.util.JsonMappers;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 大华云睿（DH Cloud / DoLynk Cloud / ICC）平台 OpenAPI 会话
 * <p>对接大华云睿平台 OpenAPI，基于 common-starter HttpClient，全平台可用。</p>
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

    public DahuaCloudSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
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
            JsonNode resp = execute("POST", PATH_LOGIN, params, null);
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
        String sig = sign(time, nonce);
        realParams.put("sign", sig);
        if (debugEnabled()) {
            log.info("[OpenAPI-DEBUG] 大华云睿 签名: time={}, nonce={}, sign={}",
                    time, nonce, sig.length() > 12 ? sig.substring(0, 12) + "..." : sig);
        }
        return execute(method, path, realParams, null);
    }

    public JsonNode getLiveUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("stream", 0);
        return assertOk(post(PATH_LIVE_URL, params));
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getLiveUrl(channel);
        return data.path("url").asText(data.path("hls").asText(data.path("flv").asText("")));
    }

    @Override
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.getStart().toString());
        params.put("endTime", range.getEnd().toString());
        JsonNode data = assertOk(post("/openapi/video/playback/url", params));
        return data.path("url").asText(data.path("hls").asText(""));
    }

    @Override
    public java.util.List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(
            com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("startTime", range.getStart().toString());
        params.put("endTime", range.getEnd().toString());
        JsonNode data = assertOk(post("/openapi/access/records", params));
        return JsonMappers.parseAccessRecords(data);
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

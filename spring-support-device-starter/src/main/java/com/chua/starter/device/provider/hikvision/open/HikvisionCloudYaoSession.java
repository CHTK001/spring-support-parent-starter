package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 海康云耀（Hik-Connect / HiDDNS 外网云）OpenAPI 会话
 * <p>对接海康消费级/中小项目云平台，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>海康开放平台地址、AppKey/AppSecret、签名算法需按海康云耀 OpenAPI 文档配置</li>
 *   <li>本类提供签名 + HTTP 调用的统一实现，具体 endpoint 与字段以官方文档为准</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionCloudYaoSession extends AbstractOpenApiSession {

    /**
     * 海康云耀默认 baseUrl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://api.hik-cloud.com";

    /**
     * accessToken 接口路径
     */
    private static final String PATH_TOKEN = "/api/v1/oauth/token";

    /**
     * 获取视频直播地址接口
     */
    private static final String PATH_LIVE_ADDRESS = "/api/v1/video/live/address";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/api/v1/ptz/control";

    /**
     * 远程开关门接口
     */
    private static final String PATH_DOOR_CONTROL = "/api/v1/access/control";

    /**
     * 构造云耀会话
     *
     * @param info 设备连接信息
     */
    public HikvisionCloudYaoSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        login();
    }

    /**
     * 登录换取 accessToken（按海康云耀文档约定的 OAuth 流程）
     */
    private void login() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appKey", info.getAppKey());
        params.put("appSecret", info.getAppSecret());
        try {
            JsonNode resp = execute("POST", PATH_TOKEN, params, null);
            JsonNode data = assertOk(resp);
            String token = data.path("accessToken").asText(null);
            long expires = data.path("expiresIn").asLong(7200L);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("海康云耀 accessToken 为空");
            }
            cacheToken(token, expires);
            log.info("海康云耀登录成功: deviceSerial={}", info.getDeviceSerial());
        } catch (Exception e) {
            throw new IllegalStateException("海康云耀登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 海康云耀通用签名：按参数键排序后拼接 + appSecret，HMAC-SHA256。
     *
     * @param params 参数
     * @return 签名值
     */
    private String sign(Map<String, Object> params) {
        String sorted = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(utf8(info.getAppSecret()), "HmacSHA256"));
            byte[] digest = mac.doFinal(utf8(sorted));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("海康云耀签名失败", e);
        }
    }

    @Override
    /** DoRequest */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!tokenValid() && !PATH_TOKEN.equals(path)) {
            login();
        }
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (accessToken != null) {
            realParams.put("accessToken", accessToken);
        }
        if (!PATH_TOKEN.equals(path)) {
            String sign = sign(realParams);
            realParams.put("sign", sign);
            if (debugEnabled()) {
                log.info("[OpenAPI-DEBUG] 海康云耀 签名: {}", sign.length() > 12 ? sign.substring(0, 12) + "..." : sign);
            }
        }
        return execute(method, path, realParams, null);
    }

    /**
     * 获取视频直播地址（HLS）
     *
     * @param channel  通道号
     * @param protocol 协议类型
     * @return 视频地址数据
     */
    public JsonNode getLiveAddress(int channel, String protocol) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("protocol", protocol);
        return assertOk(post(PATH_LIVE_ADDRESS, params));
    }

    @Override
    /** 获取LiveUrl */
    public String getLiveUrl(int channel, String protocol) {
        String p = protocol == null || protocol.isEmpty() ? "hls" : protocol;
        JsonNode data = getLiveAddress(channel, p);
        // 海康云耀返回字段示例：data.url / data.hls / data.hlsAddress
        return data.path("url").asText(data.path("hls").asText(data.path("hlsAddress").asText("")));
    }

    /**
     * 获取录像回放 URL（海康云耀）
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 回放 HLS URL
     */
    @Override
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.getStart().toString());
        params.put("endTime", range.getEnd().toString());
        params.put("protocol", "hls");
        JsonNode data = assertOk(post("/api/v1/video/playback/url", params));
        return data.path("url").asText(data.path("hls").asText(""));
    }

    @Override
    /** PtzControl */
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", 1);
        params.put("direction", mapDirection(cmd));
        params.put("speed", Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ_CONTROL, params);
    }

    /**
     * 海康云耀云台方向码（按官方文档约定）
     */
    private int mapDirection(com.chua.starter.device.spi.model.PtzCommand cmd) {
        switch (cmd) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            case ZOOM_IN: return 4;
            case ZOOM_OUT: return 5;
            case FOCUS_IN: return 6;
            case FOCUS_OUT: return 7;
            case IRIS_IN: return 8;
            case IRIS_OUT: return 9;
            default: return -1;
        }
    }

    @Override
    /** Remote解锁 */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("doorId", doorId);
        params.put("command", 1);
        post(PATH_DOOR_CONTROL, params);
    }
}

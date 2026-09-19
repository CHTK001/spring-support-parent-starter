package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海康云眸（Cloud Eye / isecure Center 公有云版）打开api 会话
 * <p>对接海康企业级视频云平台 OpenAPI，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>海康云眸 endpoint、AppKey/AppSecret、签名算法需按平台官方 OpenAPI 文档配置</li>
 *   <li>本类提供签名 + HTTP 调用框架，具体 endpoint 与字段以官方文档为准</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionCloudEyeSession extends AbstractOpenApiSession {

    /**
     * 海康云眸默认 baseurl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://api.hik-cloud-eye.com";

    /**
     * 登录换取 令牌 接口
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

    /**
     * 创建 hikvisioncloudeye会话 实例
     * @param info 信息
     */
    public HikvisionCloudEyeSession(DeviceConnectInfo info) {
        /**
         * 父。
         * @param info 信息
         * @param DEFAULT_BASE_URL 默认baseurl
         */
        super(info, DEFAULT_BASE_URL);
        /**
         * login。
         */
        login();
    }

    /**
     * 登录换取 会话标识 / 令牌
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
                /**
                 * illegal状态异常。
                 * @param 为空" 为空"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("海康云眸 token 为空");
            }
            /**
             * 缓存令牌。
             * @param token 令牌
             * @param expires expires
             */
            cacheToken(token, expires);
            log.info("海康云眸登录成功: deviceSerial={}", info.getDeviceSerial());
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("海康云眸登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 海康云眸签名：参数按字典序排序后拼接 + appsecret，SHA-256。
     * @param params 参数
     * @return 标志的结果
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
            /**
             * illegal状态异常。
             * @param "海康云眸签名失败" "海康云眸签名失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("海康云眸签名失败", e);
        }
    }

    @Override
    /**
     * 执行请求
    */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!tokenValid() && !PATH_LOGIN.equals(path)) {
            /**
             * login。
             */
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
            String sign = sign(realParams);
            realParams.put("sign", sign);
            if (debugEnabled()) {
                log.info("[OpenAPI-DEBUG] 海康云眸 签名: {}", sign.length() > 12 ? sign.substring(0, 12) + "..." : sign);
            }
        }
        return execute(method, path, realParams, null);
    }

    /**
     * 获取视频取流 URL
     * @param channel 通道
     * @return 获取视频url的结果
     */
    public JsonNode getVideoUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("streamType", "main");
        return assertOk(post(PATH_VIDEO_URL, params));
    }

    @Override
    /**
     * 获取liveurl
    */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getVideoUrl(channel);
        return data.path("url").asText(data.path("hls").asText(data.path("flv").asText("")));
    }

    @Override
    /**
     * 获取playbackurl
    */
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.getStart().toString());
        params.put("endTime", range.getEnd().toString());
        JsonNode data = assertOk(post("/api/v1/video/playback", params));
        return data.path("url").asText(data.path("hls").asText(""));
    }

    @Override
    /**
     * ptzcontrol
    */
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("channel", 1);
        params.put("action", mapAction(cmd));
        params.put("speed", Math.max(1, Math.min(255, Math.round(speed * 255))));
        /**
         * post。
         * @param PATH_PTZ_CONTROL 路径ptzcontrol
         * @param params 参数
         */
        post(PATH_PTZ_CONTROL, params);
    }

    /**
     * 映射动作
     *
     * @param cmd CMD
     * @return 映射动作的结果
     */
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
    /**
     * 远程解锁
    */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.getDeviceSerial());
        params.put("doorId", doorId);
        params.put("action", "OPEN");
        /**
         * post。
         * @param PATH_DOOR_CONTROL 路径门control
         * @param params 参数
         */
        post(PATH_DOOR_CONTROL, params);
    }
}

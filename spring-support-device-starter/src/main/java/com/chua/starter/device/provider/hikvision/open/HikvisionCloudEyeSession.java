// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海康云眸（Cloud Eye / isecure Center 公有云版）打开api 会话
 * <p>对接海康企业级视频云平台 OpenAPI，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>官方接入</h3>
 * <ul>
 *   <li>固定网关 {@code https://api2.hik-cloud.com}，不允许凭据覆盖</li>
 *   <li>OAuth 使用 {@code POST /oauth/token} 表单换取 Bearer access token</li>
 *   <li>业务接口使用 {@code Authorization: Bearer <access_token>}，响应码 0/200 表示成功</li>
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
    private static final String DEFAULT_BASE_URL = "https://api2.hik-cloud.com";

    /**
     * 登录换取令牌接口（官方 OAuth client_credentials）。
     */
    private static final String PATH_LOGIN = "/oauth/token";

    /**
     * 视频取流地址接口
     */
    private static final String PATH_VIDEO_URL = "/api/v1/open/video/live/address";

    /**
     * 录像回放地址接口
     */
    private static final String PATH_VIDEO_PLAYBACK = "/api/v1/open/video/playback/address";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/api/v1/open/video/ptz/control";

    /**
     * 门禁控制接口
     */
    private static final String PATH_DOOR_CONTROL = "/api/v1/open/access/doors/open";

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
    private synchronized void login() {
        if (tokenValid()) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", info.appKey());
        form.put("client_secret", info.appSecret());
        form.put("grant_type", "client_credentials");
        form.put("scope", "app");
        try {
            StringBuilder body = new StringBuilder();
            form.forEach((key, value) -> {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                body.append('=');
                body.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
            });
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_BASE_URL + PATH_LOGIN))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = MAPPER.readTree(response.body() == null ? "{}" : response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + root);
            }
            String code = root.path("code").asText("");
            if (!code.isEmpty() && !"0".equals(code) && !"200".equals(code)) {
                throw new IllegalStateException("code=" + code + ", message="
                        + root.path("message").asText(root.path("msg").asText("")));
            }
            String token = root.path("access_token").asText(null);
            long expires = root.path("expires_in").asLong(604_800L);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("OAuth 响应未返回 access_token");
            }
            cacheToken(token, expires);
            log.info("海康云眸登录成功: deviceSerial={}", info.deviceSerial());
        } catch (Exception e) {
            throw new IllegalStateException("海康云眸登录失败: " + e.getMessage(), e);
        }
    }

    @Override
    /**
     * 执行请求
     * @param Map<String 参数
     * @param method 字符串
     * @param params 参数
     * @param path 字符串
     * @return 返回值
    */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!PATH_LOGIN.equals(path)) {
            login();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (!PATH_LOGIN.equals(path) && accessToken != null) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return execute(method, path, params, headers);
    }

    /**
     * 校验云眸响应信封（官方成功码为 0 或 200）。
     *
     * @param response 响应根节点
     * @return data 节点；响应没有 data 时返回根节点
     */
    private JsonNode assertCloudEyeOk(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("云眸响应为空");
        }
        String code = response.path("code").asText("");
        if (!code.isEmpty() && !"0".equals(code) && !"200".equals(code)) {
            throw new IllegalStateException("云眸调用失败: code=" + code + ", message="
                    + response.path("message").asText(response.path("msg").asText("")));
        }
        JsonNode data = response.get("data");
        return data == null || data.isNull() ? response : data;
    }

    /**
     * 获取视频取流 URL
     * @param channel 通道
     * @return 获取视频url的结果
     */
    public JsonNode getVideoUrl(int channel) {
        return getVideoUrl(channel, "main");
    }

    /**
     * 获取视频地址（指定码流）
     *
     * @param channel    通道
     * @param streamType 码流：main / sub
     * @return 视频地址数据
     */
    public JsonNode getVideoUrl(int channel, String streamType) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        params.put("streamType", "sub".equalsIgnoreCase(streamType) ? "sub" : "main");
        return assertCloudEyeOk(post(PATH_VIDEO_URL, params));
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getVideoUrl(channel);
        return data.path("url").asText(data.path("hls").asText(data.path("flv").asText("")));
    }

    @Override
    /**
     * 获取指定码流的liveurl
     *
     * @param channel    通道
     * @param protocol   协议
     * @param streamType 码流
     * @return 拉流地址
     */
    public String getLiveUrl(int channel, String protocol, String streamType) {
        JsonNode data = getVideoUrl(channel, streamType);
        return data.path("url").asText(data.path("hls").asText(data.path("flv").asText("")));
    }

    @Override
    /**
     * 获取playbackurl
     * @param channel 整数
     * @param range 参数
     * @return 返回值
    */
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = assertCloudEyeOk(post(PATH_VIDEO_PLAYBACK, params));
        return data.path("url").asText(data.path("hls").asText(""));
    }

    @Override
    /**
     * ptzcontrol
     * @param cmd 参数
     * @param speed 双精度数
     * @return 返回值
    */
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", 1);
        params.put("action", mapAction(cmd));
        params.put("speed", Math.max(1, Math.min(255, Math.round(speed * 255))));
        /**
         * post。
         * @param PATH_PTZ_CONTROL 路径ptzcontrol
         * @param params 参数
         */
        assertCloudEyeOk(post(PATH_PTZ_CONTROL, params));
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
     * @param doorId 整数
     * @return 返回值
    */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("doorId", doorId);
        params.put("action", "OPEN");
        /**
         * post。
         * @param PATH_DOOR_CONTROL 路径门control
         * @param params 参数
         */
        assertCloudEyeOk(post(PATH_DOOR_CONTROL, params));
    }
}
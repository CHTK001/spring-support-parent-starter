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
 * 大华云睿（DH Cloud / 执行lynk Cloud / ICC）平台 打开api 会话
 * <p>对接大华云睿平台 OpenAPI，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>endpoint、AppKey/AppSecret、签名算法需按大华云睿 OpenAPI 文档配置</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DahuaCloudSession extends AbstractOpenApiSession {

    /**
     * 大华云睿默认 baseurl（按官方文档配置）
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
     * 创建 dahuacloud会话 实例
     * @param info 信息
     */
    public DahuaCloudSession(DeviceConnectInfo info) {
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
     * 大华云睿签名：标志 = MD5(appsecret + 时间 + nonce + appKey)。
     * @param time 时间
     * @param nonce nonce
     * @return 标志的结果
     */
    private String sign(String time, String nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String src = info.appSecret() + time + nonce + info.appKey();
            byte[] digest = md.digest(utf8(src));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "大华云睿签名失败" "大华云睿签名失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("大华云睿签名失败", e);
        }
    }

    /**
     * Login
    */
    private void login() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("appKey", info.appKey());
            params.put("appSecret", info.appSecret());
            JsonNode resp = execute("POST", PATH_LOGIN, params, null);
            JsonNode data = assertOk(resp);
            String token = data.path("accessToken").asText(null);
            long expires = data.path("expiresIn").asLong(7200L);
            if (token == null || token.isEmpty()) {
                /**
                 * illegal状态异常。
                 * @param 为空" 为空"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("大华云睿 accessToken 为空");
            }
            /**
             * 缓存令牌。
             * @param token 令牌
             * @param expires expires
             */
            cacheToken(token, expires);
            log.info("大华云睿登录成功: deviceSerial={}", info.deviceSerial());
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("大华云睿登录失败: " + e.getMessage(), e);
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
        String time = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (accessToken != null && !PATH_LOGIN.equals(path)) {
            realParams.put("accessToken", accessToken);
        }
        realParams.put("appKey", info.appKey());
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

    /**
     * 获取liveurl
     *
     * @param channel 通道
     * @return 获取liveurl的结果
     */
    public JsonNode getLiveUrl(int channel) {
        return getLiveUrlData(channel, "main");
    }

    /**
     * 获取视频地址（指定码流）
     *
     * @param channel    通道
     * @param streamType 码流：main / sub
     * @return 视频地址数据
     */
    public JsonNode getLiveUrlData(int channel, String streamType) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        // 大华：0 主码流、1 子码流
        params.put("stream", "sub".equalsIgnoreCase(streamType) ? 1 : 0);
        return assertOk(post(PATH_LIVE_URL, params));
    }

    @Override
    /**
     * 获取liveurl
    */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getLiveUrl(channel);
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
        JsonNode data = getLiveUrlData(channel, streamType);
        return data.path("url").asText(data.path("hls").asText(data.path("flv").asText("")));
    }

    @Override
    /**
     * 获取playbackurl
    */
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = assertOk(post("/openapi/video/playback/url", params));
        return data.path("url").asText(data.path("hls").asText(""));
    }

    @Override
    /**
     * 查询accessrecords
     * @param range 范围
     * @param params 参数
     * @param cmd CMD
     * @param speed 速度
     * @param 1 1
     * @param params 参数
     * @param cmd CMD
     * @param doorId 门标识
     * @param doorId 门标识
     * @param params 参数
     */
    public java.util.List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(
            com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("startTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = assertOk(post("/openapi/access/records", params));
        return JsonMappers.parseAccessRecords(data);
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
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", 1);
        params.put("action", mapAction(cmd));
        params.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
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
    /**
     * 远程解锁
    */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("doorId", doorId);
        params.put("action", "open");
        /**
         * post。
         * @param PATH_DOOR_CONTROL 路径门control
         * @param params 参数
         */
        post(PATH_DOOR_CONTROL, params);
    }
}

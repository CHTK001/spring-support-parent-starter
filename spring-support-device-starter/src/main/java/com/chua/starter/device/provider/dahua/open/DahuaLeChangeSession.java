package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 大华乐橙（IMOU / lecheng）开放平台 打开api 会话
 * <p>对接大华乐橙开放平台，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>endpoint、AppKey/AppSecret、签名算法需按乐橙开放平台 OpenAPI 文档配置</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DahuaLeChangeSession extends AbstractOpenApiSession {

    /**
     * 乐橙开放平台默认 baseurl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://openapi.lechange.com/openapi";

    /**
     * 获取 access令牌 接口
     */
    private static final String PATH_TOKEN = "/accessToken";

    /**
     * 实时预览 URL 接口
     */
    private static final String PATH_LIVE_URL = "/live";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/ptz";

    /**
     * 创建 dahuale改变会话 实例
     * @param info 信息
     */
    public DahuaLeChangeSession(DeviceConnectInfo info) {
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
     * 乐橙签名：MD5(appsecret + 系统 + 参数 + 时间 + nonce)。
     * @param params 参数
     * @param system 系统
     * @param time 时间
     * @param nonce nonce
     * @return 标志的结果
     */
    private String sign(Map<String, Object> params, String system, String time, String nonce) {
        TreeMap<String, Object> sorted = new TreeMap<>(params);
        StringBuilder paramStr = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (v != null) {
                paramStr.append(k).append(v);
            }
        });
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String src = info.getAppSecret() + system + paramStr + time + nonce;
            byte[] digest = md.digest(utf8(src));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "乐橙签名失败" "乐橙签名失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("乐橙签名失败", e);
        }
    }

    /** Login */
    private void login() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("appKey", info.getAppKey());
            params.put("appSecret", info.getAppSecret());
            params.put("system", "openAPI");
            JsonNode resp = execute("POST", PATH_TOKEN, params, null);
            JsonNode data = assertOk(resp);
            JsonNode result = data.path("result");
            String token = result.path("accessToken").asText(null);
            long expires = result.path("expiresIn").asLong(7200L);
            if (token == null || token.isEmpty()) {
                /**
                 * illegal状态异常。
                 * @param 为空" 为空"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("乐橙 accessToken 为空");
            }
            /**
             * 缓存令牌。
             * @param token 令牌
             * @param expires expires
             */
            cacheToken(token, expires);
            log.info("乐橙登录成功: deviceSerial={}", info.getDeviceSerial());
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("乐橙登录失败: " + e.getMessage(), e);
        }
    }

    @Override
    /** 执行请求 */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!tokenValid() && !PATH_TOKEN.equals(path)) {
            /**
             * login。
             */
            login();
        }
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (accessToken != null && !PATH_TOKEN.equals(path)) {
            realParams.put("token", accessToken);
        }
        Map<String, Object> signParams = new LinkedHashMap<>(realParams);
        signParams.put("time", time);
        signParams.put("nonce", nonce);
        String sig = sign(signParams, "openAPI", time, nonce);
        signParams.put("sign", sig);
        if (debugEnabled()) {
            log.info("[OpenAPI-DEBUG] 乐橙 签名: time={}, nonce={}, sign={}",
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
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", info.getDeviceSerial());
        params.put("channelId", channel);
        params.put("streamId", 0);
        return assertOk(post(PATH_LIVE_URL, params));
    }

    @Override
    /** 获取liveurl */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getLiveUrl(channel);
 // 乐橙返回 数据.结果.liveurl / hls / flv
        JsonNode result = data.path("result");
        return result.path("liveURL").asText(result.path("hls").asText(result.path("flv").asText("")));
    }

    @Override
    /** 获取playbackurl */
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", info.getDeviceSerial());
        params.put("channelId", channel);
        params.put("beginTime", range.getStart().toString());
        params.put("endTime", range.getEnd().toString());
        params.put("streamId", 0);
        JsonNode data = assertOk(post("/playback", params));
        JsonNode result = data.path("result");
        return result.path("url").asText(result.path("hls").asText(""));
    }

    @Override
    /** ptzcontrol */
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        if (cmd == com.chua.starter.device.spi.model.PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", info.getDeviceSerial());
        params.put("channelId", 1);
        params.put("operation", mapOperation(cmd));
        params.put("duration", (int) Math.max(100, Math.min(2000, Math.round(2000 * speed))));
        /**
         * post。
         * @param PATH_PTZ_CONTROL 路径ptzcontrol
         * @param params 参数
         */
        post(PATH_PTZ_CONTROL, params);
    }

    /**
     * 映射operation
     *
     * @param cmd CMD
     * @return 映射operation的结果
     */
    private int mapOperation(com.chua.starter.device.spi.model.PtzCommand cmd) {
        switch (cmd) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            case ZOOM_IN: return 8;
            case ZOOM_OUT: return 9;
            case FOCUS_IN: return 12;
            case FOCUS_OUT: return 13;
            case IRIS_IN: return 16;
            case IRIS_OUT: return 17;
            default: return -1;
        }
    }
}

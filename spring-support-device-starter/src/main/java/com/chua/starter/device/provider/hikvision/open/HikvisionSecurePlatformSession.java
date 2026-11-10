// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisApis;
import com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisSdk;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 海康综合安防平台（iSecure Center / Artemis）会话。
 * <p>业务请求委托给官方 Artemis SDK，由每次请求独立的 {@code Request} 携带 host/key/secret，
 * 避免 Artemis 静态配置在多凭据并发时相互覆盖。私有化部署可由凭据提供 baseUrl。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionSecurePlatformSession extends AbstractOpenApiSession {

    /**
     * 海康安防平台默认 baseurl（按官方文档配置）
     */
    private static final String DEFAULT_BASE_URL = "https://artemis.hikvision.com";

    /**
     * 视频取流接口
     */
    private static final String PATH_VIDEO_STREAM = "/artemis/api/video/v2/cameras/previewURLs";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/artemis/api/video/v1/ptz/controlling";

    /**
     * 门禁控制接口
     */
    private static final String PATH_DOOR_CONTROL = "/artemis/api/access/v1/door/control";

    /** 官方 Artemis SDK 适配器，负责线程安全签名与请求执行。 */
    private final HikvisionArtemisSdk delegate;

    /**
     * 创建 hikvisionsecureplatform会话 实例
     * @param info 信息
     */
    public HikvisionSecurePlatformSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        this.delegate = new HikvisionArtemisSdk(info);
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        String code = HikvisionArtemisApis.all().stream()
                .filter(spec -> spec.path().equals(path))
                .map(com.chua.starter.device.provider.sdk.PlatformApiSpec::code)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Artemis 接口未登记: " + path));
        PlatformApiResult result = delegate.invoke(code, params);
        if (result == null || !result.ok()) {
            throw new IllegalStateException("Artemis 调用失败: "
                    + (result == null ? "无调用结果" : result.message()));
        }
        return MAPPER.valueToTree(result.data());
    }

    @Override
    public boolean testConnection() {
        return delegate.testConnection();
    }

    /**
     * 获取摄像头预览 URL（HLS/RTSP 等）
     * @param channel 通道
     * @return 获取previewurl的结果
     */
    public JsonNode getPreviewUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.deviceSerial());
        params.put("streamType", 0);
        params.put("protocol", "rtsp");
        params.put("transmode", 1);
        return assertOk(post(PATH_VIDEO_STREAM, params));
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getPreviewUrl(channel);
        return data.path("url").asText(data.path("hls").asText(data.path("rtsp").asText("")));
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
        params.put("cameraIndexCode", info.deviceSerial());
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("protocol", "hls");
        JsonNode data = assertOk(post("/artemis/api/video/v2/cameras/playbackURLs", params));
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
        params.put("cameraIndexCode", info.deviceSerial());
        params.put("action", mapArtemisDirection(cmd));
        params.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ_CONTROL, params);
    }

    /**
     * 映射Artemisdirection
     *
     * @param cmd CMD
     * @return 映射Artemisdirection的结果
     */
    private int mapArtemisDirection(com.chua.starter.device.spi.model.PtzCommand cmd) {
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
    /**
     * 远程解锁
     * @param doorId 整数
     * @return 返回值
    */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("doorIndexCode", info.deviceSerial() + ":" + doorId);
        params.put("controlType", 1);
        post(PATH_DOOR_CONTROL, params);
    }
}
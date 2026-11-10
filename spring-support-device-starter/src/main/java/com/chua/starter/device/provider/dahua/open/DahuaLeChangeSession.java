// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.dahua.LeChangeApis;
import com.chua.starter.device.provider.sdk.dahua.LeChangeSdk;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 大华乐橙（IMOU / lecheng）开放平台 打开api 会话
 * <p>对接大华乐橙开放平台，基于 common-starter HttpClient，全平台可用。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>调用复用 {@link LeChangeSdk}：统一网关、官方三层 JSON 请求壳与签名均由 SDK 负责</li>
 *   <li>DeviceSession 适配只映射官方接口目录；缺少请求/响应字段文档的操作明确抛出异常</li>
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
    private static final String DEFAULT_BASE_URL = "https://openapi.lechange.cn/openapi";

    /**
     * 实时预览 URL 接口
     */
    private static final String PATH_LIVE_URL = "/liveList";

    /**
     * 回放 URL 接口
     */
    private static final String PATH_PLAYBACK_URL = "/playbackByDevice";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/controlMovePTZ";

    private static final String PATH_CHANNELS = "/listDeviceChannelByPage";
    private static final String PATH_CAPTURE = "/setDeviceSnap";
    private static final String PATH_ALARM_LIST = "/alarmMessageList";
    private static final String PATH_ACCESS_RECORDS = "/accessRecordList";
    private static final String PATH_OPEN_DOOR = "/openDoor";
    private static final String PATH_LOCAL_RECORDS = "/queryLocalRecords";

    /** 官方协议实现复用 SPI SDK，确保统一信封与签名规则。 */
    private final LeChangeSdk delegate;

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
        this.delegate = new LeChangeSdk(info);
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
        String code = LeChangeApis.all().stream()
                .filter(spec -> ("/openapi" + path).equals(spec.path()))
                .map(com.chua.starter.device.provider.sdk.PlatformApiSpec::code)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("乐橙接口未登记: " + path));
        PlatformApiResult result = delegate.invoke(code, params);
        if (result == null || !result.ok()) {
            throw new IllegalStateException("乐橙调用失败: "
                    + (result == null ? "无调用结果" : result.message()));
        }
        try {
            return MAPPER.readTree(result.raw() == null ? "{}" : result.raw());
        } catch (Exception ex) {
            throw new IllegalStateException("乐橙响应解析失败: " + code, ex);
        }
    }

    @Override
    public boolean testConnection() {
        return delegate.testConnection();
    }

    /**
     * 获取liveurl
     *
     * @param channel 通道
     * @return 获取liveurl的结果
     */
    public JsonNode getLiveUrl(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", info.deviceSerial());
        params.put("channelId", channel);
        params.put("streamId", 0);
        return assertOk(post(PATH_LIVE_URL, params));
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        JsonNode data = getLiveUrl(channel);
 // 乐橙返回 数据.结果.liveurl / hls / flv
        JsonNode result = data.path("result");
        return result.path("liveURL").asText(result.path("hls").asText(result.path("flv").asText("")));
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
        params.put("deviceId", info.deviceSerial());
        params.put("channelId", channel);
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("streamId", 0);
        JsonNode data = assertOk(post(PATH_PLAYBACK_URL, params));
        JsonNode result = data.path("result");
        return result.path("url").asText(result.path("hls").asText(""));
    }

    @Override
    public java.util.List<com.chua.starter.device.spi.model.DeviceChannel> listChannels() {
        throw undocumented(PATH_CHANNELS, "分页请求字段以及通道响应字段/结构");
    }

    @Override
    public java.io.InputStream capture(int channel) {
        throw undocumented(PATH_CAPTURE, "设备/通道请求字段以及抓图响应字段（图片字节或图片地址）");
    }

    @Override
    public java.util.List<com.chua.starter.device.spi.model.DeviceEvent> pollEvents(
            com.chua.starter.device.spi.model.TimeRange range) {
        throw undocumented(PATH_ALARM_LIST, "设备、时间范围、分页请求字段以及告警响应字段/结构");
    }

    @Override
    public java.util.List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(
            com.chua.starter.device.spi.model.TimeRange range) {
        throw undocumented(PATH_ACCESS_RECORDS, "设备、时间范围、分页请求字段以及通行记录响应字段/结构");
    }

    @Override
    public void remoteUnlock(int doorId) {
        throw undocumented(PATH_OPEN_DOOR, "设备/门标识请求字段、开门动作请求字段以及成功响应字段");
    }

    @Override
    public java.util.List<com.chua.starter.device.spi.model.RecordFile> queryRecords(
            int channel, com.chua.starter.device.spi.model.TimeRange range) {
        throw undocumented(PATH_LOCAL_RECORDS, "设备/通道、时间范围、分页请求字段以及录像记录响应字段/结构");
    }

    /**
     * API 目录仅确认接口方法名和路径；缺少对应请求/响应字段文档时不猜测调用协议。
     * @param path 官方目录路径
     * @param missing 缺少的官方字段说明
     * @return 显式未支持异常
     */
    private UnsupportedOperationException undocumented(String path, String missing) {
        return new UnsupportedOperationException("乐橙接口 /openapi" + path + " 字段文档不完整：" + missing);
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
        params.put("deviceId", info.deviceSerial());
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
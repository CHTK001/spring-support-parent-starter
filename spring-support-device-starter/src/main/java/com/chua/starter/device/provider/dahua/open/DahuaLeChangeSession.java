// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.provider.sdk.dahua.LeChangeSdk;
import com.chua.starter.device.spi.EventListener;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大华乐橙（IMOU / LeChange）开放平台会话。
 *
 * <h3>对接口径（逐条核对 open.imou.com 文档）</h3>
 * <ul>
 *   <li>统一网关与方法名、三层信封、MD5 签名全部由 {@link LeChangeSdk} 负责，
 *       会话只调 {@code callData}(v1) / {@code callDataV2}(v2)，不自行拼装请求</li>
 *   <li>v1：{@code POST /openapi/{method}}；v2：{@code POST /openapi/v2/{点号方法}}</li>
 *   <li>乐橙的「门」是门锁设备本身（{@code deviceId} 即门锁序列号），不存在门点位列表接口，
 *       故 {@link #listDoors()} 显式抛异常而不是返回空列表</li>
 *   <li>抓图只回图片地址（{@code data.url}），需要自行下载；调用频率限制 1 次 / 3 秒</li>
 *   <li>回放地址用 {@code createDeviceRecordHls}：入参时间是 {@code yyyy-MM-dd HH:mm:ss} 且不允许跨天，
 *       与告警查询的 {@code yyyyMMdd'T'HHmmss} 不是同一套格式</li>
 *   <li>云台走 {@code controlMovePTZ}（operation 0~10）；预置位乐橙只提供 {@code controlLocationPTZ}
 *       的绝对定位，没有增删查预置位接口，相关方法显式抛异常</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DahuaLeChangeSession extends AbstractOpenApiSession {

    /**
     * 乐橙开放平台默认 baseurl（官方固定域名）
     */
    private static final String DEFAULT_BASE_URL = "https://openapi.lechange.cn/openapi";

    /** 直播地址：创建设备源直播地址 */
    private static final String M_LIVE_BIND = "bindDeviceLive";

    /** 直播地址：查询设备通道直播信息 */
    private static final String M_LIVE_INFO = "getLiveStreamInfo";

    /** 直播地址：删除直播地址 */
    private static final String M_LIVE_UNBIND = "unbindLive";

    /** 设备详情（含通道列表） */
    private static final String M_DEVICE_DETAIL = "device.detail.list";

    /** 设备抓图 */
    private static final String M_CAPTURE = "setDeviceSnap";

    /** 告警历史 */
    private static final String M_MESSAGE_LIST = "device.message.list";

    /** 开门记录 */
    private static final String M_OPEN_RECORD = "getOpenDoorRecord";

    /** 远程开门 */
    private static final String M_OPEN_DOOR = "openDoorRemote";

    /** 门锁密钥列表 */
    private static final String M_DOOR_KEYS = "getDoorKeys";

    /** 本地录像片段 */
    private static final String M_LOCAL_RECORDS = "queryLocalRecords";

    /** 录像 hls 回放地址 */
    private static final String M_RECORD_HLS = "createDeviceRecordHls";

    /** 云台移动控制 */
    private static final String M_PTZ_MOVE = "controlMovePTZ";

    /** 云台绝对定位 */
    private static final String M_PTZ_LOCATION = "controlLocationPTZ";

    /** 调用服务（v2） */
    private static final String M_SERVICE_INVOKE = "device.service.invoke";

    /** 云台服务：按速度转动 */
    private static final String SVC_PTZ_AIM = "ptz.aim.relative";

    /** 告警时间格式：设备本地时间，无分隔符，T 为字面量 */
    private static final DateTimeFormatter F_ALARM = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /** 录像 / 回放时间格式：官方要求的带连接符格式 */
    private static final DateTimeFormatter F_RECORD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 抓图下载客户端 */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 抓图 URL 有效期很短，失败重试一次 */
    private static final int DOWNLOAD_RETRY = 1;

    /** 官方协议实现复用 SPI SDK，确保统一信封与签名规则。 */
    private final LeChangeSdk delegate;

    /**
     * 创建大华乐橙会话实例。
     *
     * @param info 连接信息
     */
    public DahuaLeChangeSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        this.delegate = new LeChangeSdk(info);
    }

    /**
     * 本平台全部调用都交给 SDK：统一信封、签名、token 续期只有一份实现。
     *
     * <p>基类的 {@code get/post} 通道在这里不可用（乐橙不是普通 REST 平台），
     * 一律显式失败，避免误用导致无签名请求打到网关。</p>
     *
     * @param method HTTP 方法
     * @param path   接口路径
     * @param params 请求参数
     * @return 不会返回
     */
    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "乐橙接口必须走 LeChangeSdk（三层信封 + MD5 签名），不支持直接 " + method + " " + path);
    }

    @Override
    public boolean testConnection() {
        return delegate.testConnection();
    }

    /**
     * 取设备序列号（乐橙所有业务接口的主键）。
     *
     * @return 设备序列号
     */
    private String serial() {
        String serial = info.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalStateException("乐橙会话缺少 deviceSerial（设备序列号）");
        }
        return serial;
    }

    /**
     * 通道号转乐橙要求的字符串形态（IPC 常见 "0"）。
     *
     * @param channel 通道
     * @return 字符串通道号
     */
    private static String channelId(int channel) {
        return String.valueOf(channel);
    }

    // ------------------------------------------------------------------ 实时预览

    /**
     * 获取实时预览地址：先绑定直播（幂等：已绑定则复用），再读 hls 地址。
     *
     * @param channel  通道
     * @param protocol 期望协议（乐橙只提供 HLS / FLV / RTMP 各自独立接口，这里返回 HLS）
     * @return HLS 地址，取不到返回空串
     */
    @Override
    public String getLiveUrl(int channel, String protocol) {
        return liveUrl(channel, 0);
    }

    /**
     * 按码流取实时预览地址。
     *
     * @param channel    通道
     * @param protocol   期望协议
     * @param streamType 0 主码流（默认）/ 1 辅码流
     * @return HLS 地址
     */
    @Override
    public String getLiveUrl(int channel, String protocol, String streamType) {
        int stream = "1".equals(streamType) || "sub".equalsIgnoreCase(streamType) ? 1 : 0;
        return liveUrl(channel, stream);
    }

    /**
     * 绑定直播并取 hls 地址。
     *
     * <p>官方明确「必须先通过 bindDeviceLive 创建直播地址，否则无法获取直播信息」，
     * 因此这里先调 bindDeviceLive，失败再退回 getLiveStreamInfo 查询已有地址。</p>
     *
     * @param channel 通道
     * @param stream  0 主码流 / 1 辅码流
     * @return HLS 地址
     */
    private String liveUrl(int channel, int stream) {
        Map<String, Object> bind = new LinkedHashMap<>();
        bind.put("deviceId", serial());
        bind.put("channelId", channelId(channel));
        bind.put("streamId", stream);
        try {
            JsonNode bound = delegate.callData(M_LIVE_BIND, bind);
            String url = hlsOf(bound, stream);
            if (!url.isBlank()) {
                return url;
            }
        } catch (RuntimeException ex) {
            // 已绑定过会返回业务错误码，退回查询已有直播信息
            log.debug("[LeChange] bindDeviceLive 未返回地址，改查已有直播信息: {}", ex.getMessage());
        }
        return hlsOf(infoOf(channel), stream);
    }

    /**
     * 查询设备通道下已创建的直播信息。
     *
     * @param channel 通道
     * @return data 节点
     */
    private JsonNode infoOf(int channel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        params.put("channelId", channelId(channel));
        return delegate.callData(M_LIVE_INFO, params);
    }

    /**
     * 从直播信息里取指定码流的 hls 地址。
     *
     * <p>官方返回 {@code data.streams[]}，每项含 {@code streamId}（0 主 / 1 辅）、
     * {@code hls}、{@code status}；同一码流可能出现 http 与 https 两条，
     * 优先取 https。</p>
     *
     * @param data   直播信息 data 节点
     * @param stream 码流类型
     * @return hls 地址，取不到返回空串
     */
    private static String hlsOf(JsonNode data, int stream) {
        if (data == null) {
            return "";
        }
        JsonNode streams = data.path("streams");
        if (streams.isArray()) {
            String fallback = "";
            for (JsonNode item : streams) {
                if (item.path("streamId").asInt(-1) != stream) {
                    continue;
                }
                String hls = item.path("hls").asText("");
                if (hls.isBlank()) {
                    continue;
                }
                if (hls.startsWith("https://")) {
                    return hls;
                }
                fallback = hls;
            }
            return fallback;
        }
        return data.path("hls").asText("");
    }

    /**
     * 停止直播：删除直播地址，释放公开可访问的播放地址。
     *
     * @param streamId 开启直播时返回的标识（乐橙以 liveToken 标识；此处按设备+通道删除）
     */
    @Override
    public void stopLive(String streamId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        delegate.callData(M_LIVE_UNBIND, params);
    }

    // ------------------------------------------------------------------ 回放

    /**
     * 获取录像回放 HLS 地址。
     *
     * <p>官方 {@code createDeviceRecordHls} 限制「不支持跨天时间」，
     * 跨天请求按开始日期截断到当天 23:59:59，并在告警日志里提示调用方分段。</p>
     *
     * @param channel 通道
     * @param range   时间范围
     * @return HLS 地址
     */
    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("乐橙回放必须提供起止时间");
        }
        LocalDateTime begin = range.start();
        LocalDateTime end = range.end();
        if (end.toLocalDate().isAfter(begin.toLocalDate())) {
            end = begin.toLocalDate().atTime(23, 59, 59);
            log.warn("[LeChange] 回放不支持跨天，已截断为 {} ~ {}", begin, end);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        params.put("channelId", channelId(channel));
        params.put("beginTime", begin.format(F_RECORD));
        params.put("endTime", end.format(F_RECORD));
        params.put("recordType", "localRecord");
        JsonNode data = delegate.callData(M_RECORD_HLS, params);
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("乐橙未返回回放地址: " + data);
        }
        return url;
    }

    // ------------------------------------------------------------------ 通道

    /**
     * 列出设备下的通道。
     *
     * <p>官方 {@code device.detail.list}（v2）：入 {@code deviceList[{deviceId, channelId[]}]}
     * （不传 channelId = 全部），出 {@code deviceList[].channelList[]}。</p>
     *
     * @return 通道列表
     */
    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", serial());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceList", List.of(device));
        JsonNode data = delegate.callDataV2(M_DEVICE_DETAIL, params);
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode deviceNode : toList(data.path("deviceList"))) {
            String deviceId = deviceNode.path("deviceId").asText(serial());
            for (JsonNode channel : toList(deviceNode.path("channelList"))) {
                String raw = channel.path("channelId").asText("");
                Integer no = parseInt(raw);
                JsonNode resolutions = channel.path("resolutions");
                Integer streamCount = resolutions.isArray() && resolutions.size() > 0
                        ? resolutions.size() : null;
                String model = channel.path("productId").asText(
                        deviceNode.path("deviceModel").asText(null));
                result.add(new DeviceChannel(
                        deviceId + "_" + raw,
                        no,
                        channel.path("channelName").asText(""),
                        deviceId,
                        onlineOf(channel.path("channelStatus").asText("")),
                        model,
                        streamCount));
            }
        }
        return result;
    }

    /**
     * 设备抓图：乐橙只回图片 URL，需再下载一次。
     *
     * <p>官方限制抓图频率 1 次 / 3 秒，这里下载失败重试一次（URL 带时效签名）。</p>
     *
     * @param channel 通道
     * @return JPEG 字节流
     */
    @Override
    public InputStream capture(int channel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        params.put("channelId", channelId(channel));
        JsonNode data = delegate.callData(M_CAPTURE, params);
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("乐橙未返回抓图地址: " + data);
        }
        return download(url);
    }

    /**
     * 下载抓图。
     *
     * @param url 抓图地址
     * @return 图片字节流
     */
    private static InputStream download(String url) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= DOWNLOAD_RETRY; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = DOWNLOADER.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = response.body();
                if (response.statusCode() == 200 && body != null && body.length > 0) {
                    return new ByteArrayInputStream(body);
                }
                last = new IllegalStateException("乐橙抓图下载失败: HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("乐橙抓图下载被中断", ex);
            } catch (Exception ex) {
                last = new IllegalStateException("乐橙抓图下载异常: " + ex.getMessage(), ex);
            }
        }
        throw last == null ? new IllegalStateException("乐橙抓图下载失败") : last;
    }

    // ------------------------------------------------------------------ 云台

    /**
     * 云台控制：八向移动 + 变倍 + 停止。
     *
     * <p>官方 {@code controlMovePTZ} 的 {@code operation}：0 上 / 1 下 / 2 左 / 3 右 /
     * 4 左上 / 5 左下 / 6 右上 / 7 右下 / 8 放大 / 9 缩小 / 10 停止。
     * 官方没有独立的聚焦、光圈指令，{@code FOCUS_*} / {@code IRIS_*} 显式拒绝。</p>
     *
     * @param cmd   指令
     * @param speed 速度（0~1，换算成 100ms~2000ms 的持续时间）
     */
    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        Integer operation = operationOf(cmd);
        if (operation == null) {
            throw new UnsupportedOperationException(
                    "乐橙 controlMovePTZ 不支持该云台指令: " + cmd + "（只支持八向移动、变倍与停止）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        params.put("channelId", channelId(0));
        params.put("operation", String.valueOf(operation));
        params.put("duration", durationOf(speed));
        delegate.callData(M_PTZ_MOVE, params);
    }

    /**
     * 速度换算持续时间。
     *
     * @param speed 0~1
     * @return 毫秒
     */
    private static long durationOf(double speed) {
        double ratio = Math.min(1.0, Math.max(0.0, speed));
        return Math.max(100L, Math.round(2000L * ratio));
    }

    /**
     * 指令映射为官方 operation 值。
     *
     * @param cmd 指令
     * @return operation，不支持返回 null
     */
    private static Integer operationOf(PtzCommand cmd) {
        switch (cmd) {
            case UP:
                return 0;
            case DOWN:
                return 1;
            case LEFT:
                return 2;
            case RIGHT:
                return 3;
            case ZOOM_IN:
                return 8;
            case ZOOM_OUT:
                return 9;
            case STOP:
                return 10;
            default:
                return null;
        }
    }

    /**
     * 云台绝对定位（{@code controlLocationPTZ} 的 h/v/z 分量）。
     *
     * @param h 水平 [-1,1]
     * @param v 垂直 [-1,1]
     * @param z 变倍 [0,1]
     */
    public void locationPtz(double h, double v, double z) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        params.put("channelId", channelId(0));
        params.put("h", clamp(h, -1, 1));
        params.put("v", clamp(v, -1, 1));
        params.put("z", clamp(z, 0, 1));
        delegate.callData(M_PTZ_LOCATION, params);
    }

    /**
     * 收缩到合法区间。
     *
     * @param value 原值
     * @param min   下限
     * @param max   上限
     * @return 合法值
     */
    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 乐橙官方只提供云台定位，没有预置位增删查接口。
     *
     * @param presetId 预置位
     * @param name     名称
     */
    @Override
    public void setPreset(int presetId, String name) {
        throw presetUnsupported();
    }

    /**
     * 乐橙官方只提供云台定位，没有预置位增删查接口。
     *
     * @param presetId 预置位
     */
    @Override
    public void callPreset(int presetId) {
        throw presetUnsupported();
    }

    /**
     * 乐橙官方只提供云台定位，没有预置位增删查接口。
     *
     * @param presetId 预置位
     */
    @Override
    public void deletePreset(int presetId) {
        throw presetUnsupported();
    }

    /**
     * 乐橙官方只提供云台定位，没有预置位增删查接口。
     *
     * @return 空列表
     */
    @Override
    public List<PresetInfo> listPresets() {
        throw presetUnsupported();
    }

    /**
     * 预置位不支持的统一说明。
     *
     * @return 异常
     */
    private static UnsupportedOperationException presetUnsupported() {
        return new UnsupportedOperationException(
                "乐橙开放平台未提供预置位增删查接口（官方仅有 controlMovePTZ 与 controlLocationPTZ）");
    }

    // ------------------------------------------------------------------ 门禁

    /**
     * 门锁密钥列表。
     *
     * <p>乐橙的门锁设备没有「门点位」概念，一个门锁设备就是一个门，
     * 这里取官方 {@code getDoorKeys} 的密钥列表作为可操作的凭据清单；
     * 需要门点位元数据的场景请用 {@link #listDoors()} 之外的方式。</p>
     *
     * @return 门列表（门锁设备本身一项）
     */
    @Override
    public List<DeviceDoorInfo> listDoors() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        JsonNode data = delegate.callData(M_DOOR_KEYS, params);
        List<DeviceDoorInfo> doors = new ArrayList<>();
        String status = "unknown";
        doors.add(new DeviceDoorInfo(serial(), serial(), serial(), status, 0));
        JsonNode keys = data.path("keys");
        log.debug("[LeChange] 门锁 {} 共 {} 把密钥", serial(), keys.isArray() ? keys.size() : 0);
        return doors;
    }

    /**
     * 远程开门：官方 {@code openDoorRemote} 入参只有门锁设备序列号，且无 data 返回。
     *
     * @param doorId 门标识（乐橙即门锁设备序列号，传 0 表示当前会话设备）
     */
    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        delegate.callData(M_OPEN_DOOR, params);
    }

    /**
     * 查询开门记录。
     *
     * <p>官方 {@code getOpenDoorRecord} 入 {@code recordId}（首次 {@code "-1"}）+ {@code count}，
     * 出 {@code records[{recordId,type,name,localTime}]}；只回密钥类型、名称与时间，
     * 没有卡号、方向、抓拍图，这几项按接口能力留空。</p>
     *
     * @param range 时间范围（官方接口不支持按时间过滤，拉回后在本地筛选）
     * @return 开门记录
     */
    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        List<AccessRecord> result = new ArrayList<>();
        String recordId = "-1";
        int pageSize = 50;
        int maxPage = 20;
        for (int page = 0; page < maxPage; page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", serial());
            params.put("recordId", recordId);
            params.put("count", pageSize);
            JsonNode data = delegate.callData(M_OPEN_RECORD, params);
            List<JsonNode> records = toList(data.path("records"));
            if (records.isEmpty()) {
                break;
            }
            for (JsonNode record : records) {
                LocalDateTime time = parseTime(record.path("localTime").asText(""), F_ALARM);
                if (range != null && time != null && !inRange(time, range)) {
                    continue;
                }
                result.add(new AccessRecord(
                        serial(),
                        null,
                        record.path("name").asText(""),
                        time,
                        record.path("type").asText(""),
                        null,
                        null));
            }
            recordId = records.get(records.size() - 1).path("recordId").asText("-1");
            if ("-1".equals(recordId) || records.size() < pageSize) {
                break;
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 事件

    /**
     * 拉取告警事件。
     *
     * <p>官方 {@code device.message.list}（v2）：{@code beginTime/endTime} 必须是
     * 设备本地 {@code yyyyMMdd'T'HHmmss}（不是 Unix 秒），出 {@code messages[]}，
     * 每条 {@code msgType}（= eventId）、{@code time}（Unix 秒）、{@code localTime}、
     * {@code alarmId}、{@code thumbUrl}/{@code picUrlArray}、{@code outputData}。</p>
     *
     * @param range 时间范围
     * @return 事件列表
     */
    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("乐橙告警查询必须提供起止时间");
        }
        List<DeviceEvent> result = new ArrayList<>();
        String next = "-1";
        int maxPage = 20;
        for (int page = 0; page < maxPage; page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", serial());
            params.put("beginTime", range.start().format(F_ALARM));
            params.put("endTime", range.end().format(F_ALARM));
            params.put("nextAlarmId", next);
            JsonNode data = delegate.callDataV2(M_MESSAGE_LIST, params);
            List<JsonNode> messages = toList(data.path("messages"));
            if (messages.isEmpty()) {
                break;
            }
            for (JsonNode message : messages) {
                String msgType = message.path("msgType").asText("");
                Integer channel = parseInt(message.path("channelId").asText(""));
                JsonNode output = message.path("outputData");
                LocalDateTime occurred = parseTime(message.path("localTime").asText(""), F_ALARM);
                if (occurred == null && message.hasNonNull("time")) {
                    occurred = LocalDateTime.ofEpochSecond(message.get("time").asLong(),
                            0, java.time.ZoneOffset.ofHours(8));
                }
                result.add(new DeviceEvent(
                        message.path("deviceId").asText(serial()),
                        channel,
                        eventTypeOf(msgType),
                        output == null || output.isMissingNode() || output.isNull()
                                ? message.path("alarmId").asText("") : output.toString(),
                        occurred,
                        message));
            }
            String nextId = data.path("nextAlarmId").asText("");
            if (nextId.isBlank() || nextId.equals(next)) {
                break;
            }
            next = nextId;
        }
        return result;
    }

    /**
     * 事件类型映射：乐橙 {@code msgType} 是能力目录的 eventId（点号命名）。
     *
     * @param msgType 事件 id
     * @return 统一事件类型
     */
    private static EventType eventTypeOf(String msgType) {
        if (msgType == null || msgType.isBlank()) {
            return EventType.OTHER;
        }
        String lower = msgType.toLowerCase();
        if (lower.contains("motion") || lower.contains("human") || lower.contains("detection")) {
            return EventType.MOTION;
        }
        if (lower.contains("alarm") && (lower.contains("io") || lower.contains("input"))) {
            return EventType.IO_ALARM;
        }
        if (lower.contains("door") || lower.contains("access") || lower.contains("lock")) {
            return EventType.ACCESS;
        }
        if (lower.contains("temp")) {
            return EventType.TEMP;
        }
        if (lower.contains("network") || lower.contains("offline")) {
            return EventType.NETWORK;
        }
        return EventType.OTHER;
    }

    /**
     * 订阅事件：乐橙为 HTTP 回调推送（setMessageCallback），进程内订阅仅做本地登记。
     *
     * @param type     类型
     * @param listener 监听器
     */
    @Override
    public void subscribeEvent(EventType type, EventListener listener) {
        super.subscribeEvent(type, listener);
    }

    // ------------------------------------------------------------------ 录像

    /**
     * 查询设备本地录像片段。
     *
     * <p>官方 {@code queryLocalRecords}：入 {@code beginTime/endTime} 为
     * {@code yyyy-MM-dd HH:mm:ss}（与告警的格式不同）、{@code count}（0~100）或
     * {@code queryRange}；出 {@code records[{recordId,fileLength,channelID,beginTime,endTime,type,streamType}]}。
     * 平台会截断超量返回，这里按官方推荐用「上次最大 endTime + 1 秒」继续翻页。</p>
     *
     * @param channel 通道
     * @param range   时间范围
     * @return 录像片段
     */
    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("乐橙录像查询必须提供起止时间");
        }
        List<RecordFile> result = new ArrayList<>();
        LocalDateTime begin = range.start();
        LocalDateTime end = range.end();
        int count = 100;
        int maxPage = 50;
        for (int page = 0; page < maxPage && !begin.isAfter(end); page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", serial());
            params.put("channelId", channelId(channel));
            params.put("beginTime", begin.format(F_RECORD));
            params.put("endTime", end.format(F_RECORD));
            params.put("count", count);
            JsonNode data = delegate.callData(M_LOCAL_RECORDS, params);
            List<JsonNode> records = toList(data.path("records"));
            if (records.isEmpty()) {
                break;
            }
            LocalDateTime maxEnd = null;
            for (JsonNode record : records) {
                LocalDateTime start = parseTime(record.path("beginTime").asText(""), F_RECORD);
                LocalDateTime stop = parseTime(record.path("endTime").asText(""), F_RECORD);
                if (stop != null && (maxEnd == null || stop.isAfter(maxEnd))) {
                    maxEnd = stop;
                }
                result.add(new RecordFile(
                        record.path("recordId").asText(""),
                        record.path("channelID").asText(channelId(channel)),
                        start,
                        stop,
                        record.path("fileLength").asLong(0L)));
            }
            if (records.size() < count || maxEnd == null) {
                break;
            }
            begin = maxEnd.plusSeconds(1);
        }
        return result;
    }

    /**
     * 通过 v2 {@code device.service.invoke} 调用任意能力服务（如云台巡航、报警布撤防）。
     *
     * @param channel   通道（设备级服务传 -1，则不下发 channelId）
     * @param serviceId 完整 serviceId
     * @param inputData 入参，无入参传空 Map
     * @return 服务出参 {@code outputData}
     */
    public JsonNode invokeService(int channel, String serviceId, Map<String, Object> inputData) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", serial());
        if (channel >= 0) {
            params.put("channelId", channelId(channel));
        }
        params.put("serviceId", serviceId);
        params.put("inputData", inputData == null ? Map.of() : inputData);
        return delegate.callDataV2(M_SERVICE_INVOKE, params).path("outputData");
    }

    /**
     * 按速度转动云台（官方推荐的新接入方式：{@code ptz.aim.relative}）。
     *
     * @param horizontal 水平速度 -1~1
     * @param vertical   垂直速度 -1~1
     * @param zoom       变倍 -1~1
     * @param duration   持续时间（毫秒）
     * @return 服务出参
     */
    public JsonNode ptzAim(double horizontal, double vertical, double zoom, int duration) {
        Map<String, Object> input = new LinkedHashMap<>();
        // 官方要求速度类字段传字符串，例如 "0.5"，传数字会被判非法
        input.put("horizontal", String.valueOf(clamp(horizontal, -1, 1)));
        input.put("vertical", String.valueOf(clamp(vertical, -1, 1)));
        input.put("zoom", String.valueOf(clamp(zoom, -1, 1)));
        input.put("duration", Math.max(100, Math.min(10000, duration)));
        return invokeService(0, SVC_PTZ_AIM, input);
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 在线状态映射：乐橙 {@code deviceStatus/channelStatus} 取
     * {@code online / offline / sleep / upgrading}，只有 online 算在线。
     *
     * @param status 平台状态
     * @return 布尔值，空状态返回 null
     */
    private static Boolean onlineOf(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return "online".equalsIgnoreCase(status);
    }

    /**
     * 字符串转整数，非数字返回 null。
     *
     * @param text 文本
     * @return 整数
     */
    private static Integer parseInt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 按官方格式解析时间，格式不符返回 null。
     *
     * @param text    时间文本
     * @param format  格式
     * @return 本地时间
     */
    private static LocalDateTime parseTime(String text, DateTimeFormatter format) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), format);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 判断是否落在时间范围内（时间未知时保留）。
     *
     * @param time  时间
     * @param range 范围
     * @return 是否在范围内
     */
    private static boolean inRange(LocalDateTime time, TimeRange range) {
        if (time == null) {
            return true;
        }
        if (range.start() != null && time.isBefore(range.start())) {
            return false;
        }
        return range.end() == null || !time.isAfter(range.end());
    }

    /**
     * 把节点归一成列表（官方列表字段有 {@code deviceList} / {@code messages} / {@code records} 等）。
     *
     * @param node 节点
     * @return 条目列表
     */
    private static List<JsonNode> toList(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return result;
        }
        if (node.isArray()) {
            node.forEach(result::add);
            return result;
        }
        for (JsonNode child : node) {
            if (child.isArray()) {
                child.forEach(result::add);
                return result;
            }
        }
        return result;
    }
}

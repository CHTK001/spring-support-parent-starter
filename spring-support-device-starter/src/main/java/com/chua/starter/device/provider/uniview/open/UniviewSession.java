package com.chua.starter.device.provider.uniview.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宇视云开放平台会话。
 *
 * <p>网关 {@code https://ezcloud.uniview.com}，认证走
 * {@code POST /openapi/user/app/token/get}（appId + secretKey）换 accessToken，
 * 后续请求统一带 {@code Authorization: <accessToken>}。</p>
 *
 * <p>接口路径与字段取自官方文档：device/list、device/get、device/channel/list、
 * live/video/device/url/get、device/record/time/query、device/capture/get、
 * device/ptz/start|stop、device/ptz/preset/*、event/alarm/query。
 * 官方 openapi 目录未提供组织、人员、门禁与回放地址接口，相关方法显式抛
 * {@link UnsupportedOperationException}，不返回空值。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class UniviewSession extends AbstractOpenApiSession {

    /** 宇视云网关 */
    private static final String BASE_URL = "https://ezcloud.uniview.com";

    /** 取 token */
    private static final String PATH_TOKEN = "/openapi/user/app/token/get";

    /** 设备详情 */
    private static final String PATH_DEVICE_GET = "/openapi/device/get";

    /** 设备新增 */
    private static final String PATH_DEVICE_ADD = "/openapi/device/add";

    /** 设备删除 */
    private static final String PATH_DEVICE_DELETE = "/openapi/device/delete";

    /** 设备改名 */
    private static final String PATH_DEVICE_RENAME = "/openapi/device/name/update";

    /** 摄像头列表 */
    private static final String PATH_CHANNEL_LIST = "/openapi/device/channel/list";

    /** 固定拉流地址 */
    private static final String PATH_LIVE_URL = "/openapi/live/video/device/url/get";

    /** 录像文件查询 */
    private static final String PATH_RECORD_QUERY = "/openapi/device/record/time/query";

    /** 设备抓拍 */
    private static final String PATH_CAPTURE = "/openapi/device/capture/get";

    /** 云台开始 */
    private static final String PATH_PTZ_START = "/openapi/device/ptz/start";

    /** 云台停止 */
    private static final String PATH_PTZ_STOP = "/openapi/device/ptz/stop";

    /** 预置位新增 */
    private static final String PATH_PRESET_ADD = "/openapi/device/ptz/preset/add";

    /** 预置位列表 */
    private static final String PATH_PRESET_LIST = "/openapi/device/ptz/preset/list";

    /** 预置位调用 */
    private static final String PATH_PRESET_GOTO = "/openapi/device/ptz/preset/goto";

    /** 预置位删除 */
    private static final String PATH_PRESET_DELETE = "/openapi/device/ptz/preset/delete";

    /** 告警列表 */
    private static final String PATH_ALARM_QUERY = "/openapi/event/alarm/query";

    /** 上一次云台方向（停止时回传） */
    private volatile int lastPtzCommand = -1;

    /**
     * 构造宇视会话。
     *
     * @param info 连接信息（platformType=uniview，appKey=appId，appSecret=secretKey，deviceSerial=设备序列号）
     */
    public UniviewSession(DeviceConnectInfo info) {
        super(info, BASE_URL);
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        ensureToken();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", accessToken);
        headers.put("Content-Type", "application/json");
        return execute(method, path, params, headers);
    }

    /**
     * 获取并缓存 accessToken，有效期以平台返回的 expireTime 为准。
     */
    private synchronized void ensureToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", info.appKey());
        body.put("secretKey", info.appSecret());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        JsonNode resp = executeJson("POST", PATH_TOKEN, body, headers);
        int code = resp.path("code").asInt(-1);
        if (code != 200) {
            throw new IllegalStateException("宇视取 accessToken 失败: code=" + code
                    + ", message=" + resp.path("message").asText(""));
        }
        JsonNode data = resp.path("data");
        String token = data.path("accessToken").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("宇视取 accessToken 失败: 响应缺少 accessToken");
        }
        long expire = data.path("expireTime").asLong(0L);
        long ttl = expire > 0 ? Math.max(60L, expire - Instant.now().getEpochSecond()) : 6L * 24 * 3600;
        cacheToken(token, ttl);
    }

    /**
     * 校验响应码，非 200 直接失败。
     *
     * @param resp 响应
     * @return 原响应
     */
    private JsonNode ok(JsonNode resp) {
        int code = resp.path("code").asInt(-1);
        if (code != 200) {
            throw new IllegalStateException("宇视接口返回错误: code=" + code
                    + ", message=" + resp.path("message").asText(""));
        }
        return resp;
    }

    /**
     * 设备序列号（平台模式必填）。
     *
     * @return 序列号
     */
    private String requireSerial() {
        String serial = info.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalStateException("宇视平台接入需要设备序列号（deviceSerial）");
        }
        return serial;
    }

    /**
     * 默认通道号，可由 extra.channelNo 覆盖，IPC 固定为 0。
     *
     * @return 通道号
     */
    private int defaultChannel() {
        Object value = info.extra() == null ? null : info.extra().get("channelNo");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignore) {
                log.warn("宇视 extra.channelNo 非数字: {}", text);
            }
        }
        return 0;
    }

    @Override
    public boolean testConnection() {
        try {
            ensureToken();
            if (info.deviceSerial() != null && !info.deviceSerial().isBlank()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("deviceSerial", info.deviceSerial());
                ok(post(PATH_DEVICE_GET, body));
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("宇视连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 添加设备。
     *
     * <p>字段取官方 device/add 路径的最小集（deviceSerial + name），
     * 尚未真机校准；平台要求更多字段时以返回码为准。</p>
     *
     * @param deviceSerial 设备序列号
     * @param name         设备名称
     */
    public void addDevice(String deviceSerial, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", deviceSerial);
        body.put("name", name);
        ok(post(PATH_DEVICE_ADD, body));
    }

    /**
     * 删除设备。
     *
     * @param deviceSerial 设备序列号
     */
    public void deleteDevice(String deviceSerial) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", deviceSerial);
        ok(post(PATH_DEVICE_DELETE, body));
    }

    /**
     * 修改设备名称。
     *
     * @param deviceSerial 设备序列号
     * @param name         新名称
     */
    public void renameDevice(String deviceSerial, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", deviceSerial);
        body.put("name", name);
        ok(post(PATH_DEVICE_RENAME, body));
    }

    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (info.deviceSerial() != null && !info.deviceSerial().isBlank()) {
            body.put("deviceSerial", info.deviceSerial());
        }
        body.put("pageStart", 0);
        body.put("pageSize", 100);
        JsonNode data = ok(post(PATH_CHANNEL_LIST, body)).path("data");
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : data.path("channelList")) {
            int channelNo = node.path("channelNo").asInt(0);
            String serial = node.path("deviceSerial").asText(info.deviceSerial());
            result.add(new DeviceChannel(serial + "_" + channelNo, channelNo,
                    node.path("channelName").asText(""), serial,
                    node.path("status").asInt(0) == 1, null, 2));
        }
        return result;
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        return getLiveUrl(channel, protocol, "main");
    }

    @Override
    public String getLiveUrl(int channel, String protocol, String streamType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel);
        JsonNode data = ok(post(PATH_LIVE_URL, body)).path("data");
        int quality = "sub".equalsIgnoreCase(streamType) ? 1 : 0;
        String fallback = null;
        for (JsonNode node : data.path("liveUrlList")) {
            String url = node.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            if (node.path("quality").asInt(0) == quality) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("宇视未返回直播地址: channel=" + channel + ", streamType=" + streamType);
    }

    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        throw new UnsupportedOperationException(
                "宇视开放平台未提供回放地址接口（仅有录像文件检索 device/record/time/query）");
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("宇视录像查询要求起止时间");
        }
        long start = range.start().atZone(ZoneId.systemDefault()).toEpochSecond();
        long end = range.end().atZone(ZoneId.systemDefault()).toEpochSecond();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel);
        body.put("startTime", start);
        body.put("endTime", end);
        body.put("quality", 0);
        body.put("srcType", 0);
        JsonNode data = ok(post(PATH_RECORD_QUERY, body)).path("data");
        String serial = requireSerial();
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : data.path("recordList")) {
            long from = node.path("startTime").asLong(0L);
            long to = node.path("endTime").asLong(0L);
            result.add(new RecordFile(serial + "_" + channel + "_" + from,
                    serial + "_" + channel,
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(from), ZoneId.systemDefault()),
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(to), ZoneId.systemDefault()),
                    0L));
        }
        return result;
    }

    @Override
    public InputStream capture(int channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel);
        JsonNode data = ok(post(PATH_CAPTURE, body)).path("data");
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("宇视抓图未返回图片地址: channel=" + channel);
        }
        return download(url);
    }

    /**
     * 下载抓拍图片。
     *
     * @param url 图片地址（云存储签名 URL）
     * @return 图片字节流
     */
    private InputStream download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout((int) DEFAULT_CONNECT_TIMEOUT);
            connection.setReadTimeout((int) DEFAULT_READ_TIMEOUT);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IllegalStateException("宇视抓图下载失败: HTTP " + status);
            }
            return new ByteArrayInputStream(connection.getInputStream().readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException("宇视抓图下载失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        if (cmd == null) {
            throw new IllegalArgumentException("云台指令不能为空");
        }
        int command = toCommand(cmd);
        int value = (int) Math.max(1, Math.min(9, Math.round(speed)));
        if (cmd == PtzCommand.STOP) {
            if (lastPtzCommand >= 0) {
                Map<String, Object> stop = new LinkedHashMap<>();
                stop.put("deviceSerial", requireSerial());
                stop.put("channelNo", defaultChannel());
                stop.put("command", lastPtzCommand);
                ok(post(PATH_PTZ_STOP, stop));
            }
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel());
        body.put("command", command);
        body.put("speed", value);
        ok(post(PATH_PTZ_START, body));
        lastPtzCommand = command;
    }

    /**
     * SPI 指令映射为宇视 command：0-11。
     *
     * @param cmd 指令
     * @return 宇视命令码
     */
    private int toCommand(PtzCommand cmd) {
        return switch (cmd) {
            case UP -> 0;
            case DOWN -> 1;
            case LEFT -> 2;
            case RIGHT -> 3;
            case ZOOM_IN -> 8;
            case ZOOM_OUT -> 9;
            case FOCUS_IN -> 10;
            case FOCUS_OUT -> 11;
            case STOP -> -1;
            case IRIS_IN, IRIS_OUT -> throw new UnsupportedOperationException(
                    "宇视云台没有光圈指令（command 仅 0-11）: " + cmd);
        };
    }

    @Override
    public void setPreset(int presetId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel());
        body.put("presetIndex", presetId);
        body.put("name", name == null || name.isBlank() ? "preset" + presetId : name);
        ok(post(PATH_PRESET_ADD, body));
    }

    @Override
    public void callPreset(int presetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel());
        body.put("presetIndex", presetId);
        ok(post(PATH_PRESET_GOTO, body));
    }

    @Override
    public void deletePreset(int presetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel());
        body.put("presetIndex", presetId);
        ok(post(PATH_PRESET_DELETE, body));
    }

    @Override
    public List<PresetInfo> listPresets() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel());
        body.put("pageStart", 0);
        body.put("pageSize", 100);
        JsonNode data = ok(post(PATH_PRESET_LIST, body)).path("data");
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode node : data.path("presetList")) {
            result.add(new PresetInfo(node.path("presetIndex").asInt(0), node.path("name").asText("")));
        }
        return result;
    }

    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("宇视告警查询要求起止时间");
        }
        long start = range.start().atZone(ZoneId.systemDefault()).toEpochSecond();
        long end = range.end().atZone(ZoneId.systemDefault()).toEpochSecond();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startTime", start);
        body.put("endTime", end);
        if (info.deviceSerial() != null && !info.deviceSerial().isBlank()) {
            body.put("deviceSerial", info.deviceSerial());
        }
        body.put("pageStart", 0);
        body.put("pageSize", 100);
        JsonNode data = ok(post(PATH_ALARM_QUERY, body)).path("data");
        List<DeviceEvent> result = new ArrayList<>();
        for (JsonNode node : data.path("alarmList")) {
            long time = node.path("alarmTime").asLong(0L);
            String typeText = node.path("alarmTypeString").asText("");
            result.add(new DeviceEvent(node.path("deviceSerial").asText(requireSerial()),
                    node.path("channelNo").asInt(0),
                    mapEventType(typeText),
                    typeText + "#" + node.path("alarmId").asText(""),
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.systemDefault()),
                    node.toString()));
        }
        return result;
    }

    /**
     * 告警类型字符串映射到统一事件类型。
     *
     * @param type 平台类型
     * @return 统一类型
     */
    private EventType mapEventType(String type) {
        String lower = type == null ? "" : type.toLowerCase();
        if (lower.contains("motion") || lower.contains("move")) {
            return EventType.MOTION;
        }
        if (lower.contains("access") || lower.contains("door")) {
            return EventType.ACCESS;
        }
        if (lower.contains("io") || lower.contains("alarmin")) {
            return EventType.IO_ALARM;
        }
        if (lower.contains("network") || lower.contains("offline")) {
            return EventType.NETWORK;
        }
        if (lower.contains("temp")) {
            return EventType.TEMP;
        }
        return EventType.OTHER;
    }

    @Override
    public List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(TimeRange range) {
        throw new UnsupportedOperationException("宇视开放平台 openapi 目录未提供门禁记录接口");
    }

    @Override
    public void remoteUnlock(int doorId) {
        throw new UnsupportedOperationException("宇视开放平台 openapi 目录未提供远程开门接口");
    }

    @Override
    public List<com.chua.starter.device.spi.model.DeviceDoorInfo> listDoors() {
        throw new UnsupportedOperationException("宇视开放平台 openapi 目录未提供门禁点列表接口");
    }
}

package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.LiveHandle;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海康 ISUP / EHome 会话（企业自建 ISUP 服务端 REST 网关模式）。
 *
 * <p>ISUP 是设备主动注册协议，不是 HTTP OpenAPI。本会话把凭据里的
 * {@code baseUrl} 指向 ISUP 服务端的 REST 封装，按既有的
 * {@code HikvisionIsupSdk} 契约调用 {@code /isup/...}：</p>
 * <ul>
 *   <li>{@code POST /isup/device/list}：已注册设备</li>
 *   <li>{@code POST /isup/device/channels}：设备通道</li>
 *   <li>{@code POST /isup/live} / {@code /isup/playback}：取流与回放</li>
 *   <li>{@code POST /isup/record/query}：录像检索</li>
 *   <li>{@code POST /isup/ptz}：云台控制</li>
 * </ul>
 *
 * <p>网关不是海康官方 HTTP 服务，路径与字段是「企业自建 ISUP 网关」的适配契约；
 * 未配置 {@code baseUrl} 时全部调用明确失败，不伪造数据。部署方可用
 * {@code extra("apiPath.xxx")} 覆盖单条路径。</p>
 *
 * <p><b>未真机验证</b>：本机没有 ISUP 服务端，接口契约按现有 SDK 目录实现。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class HikvisionIsupSession extends AbstractOpenApiSession {

    /** 网关时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 通道序号到设备通道号的映射 */
    private final Map<Integer, String> channelIds = new ConcurrentHashMap<>();

    /** 直播句柄到通道号的映射 */
    private final Map<String, String> liveChannels = new ConcurrentHashMap<>();

    /**
     * 构造 ISUP 会话。
     *
     * @param info 连接信息（{@code extra.baseUrl} 必填）
     */
    public HikvisionIsupSession(DeviceConnectInfo info) {
        super(info, "");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "ISUP 未配置服务端地址：请在凭据 extra 里填 baseUrl（企业自建 ISUP 服务端 REST 封装地址）");
        }
    }

    @Override
    public String getDeviceId() {
        String ehomeId = extraText("ehomeId");
        return ehomeId != null ? ehomeId : super.getDeviceId();
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (info.appKey() != null && !info.appKey().isBlank()) {
            headers.put("X-App-Key", info.appKey());
        }
        if (info.appSecret() != null && !info.appSecret().isBlank()) {
            headers.put("X-App-Secret", info.appSecret());
        }
        String authorization = extraText("authorization");
        if (authorization != null) {
            headers.put("Authorization", authorization);
        }
        return execute(method, path, params == null ? Collections.emptyMap() : params, headers);
    }

    @Override
    public boolean testConnection() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pageNo", 1);
            params.put("pageSize", 1);
            return data(post("/isup/device/list", params)) != null;
        } catch (Exception ex) {
            log.warn("[ISUP] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        JsonNode data = data(post("/isup/device/channels", params));
        List<JsonNode> nodes = listOf(data);
        List<DeviceChannel> result = new ArrayList<>(nodes.size());
        channelIds.clear();
        int index = 1;
        for (JsonNode node : nodes) {
            String channelId = text(node, "channelId", "channelNo", "id");
            if (channelId == null || channelId.isBlank()) {
                continue;
            }
            Integer channelNo = integer(node, "channelNo", "channel");
            if (channelNo == null) {
                channelNo = index;
            }
            channelIds.put(index, channelId);
            channelIds.put(channelNo, channelId);
            result.add(new DeviceChannel(
                    channelId,
                    channelNo,
                    text(node, "name", "channelName"),
                    getDeviceId(),
                    bool(node, "online", "status", "isOnline"),
                    text(node, "model", "deviceModel"),
                    integer(node, "streamCount", "streamNum")));
            index++;
        }
        return result;
    }

    @Override
    public InputStream preview(int channel) {
        return emptyStream();
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        return getLiveUrl(channel, protocol, "main");
    }

    @Override
    public String getLiveUrl(int channel, String protocol, String streamType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("protocol", protocol == null || protocol.isBlank() ? "flv" : protocol);
        params.put("streamType", streamType == null || streamType.isBlank() ? "main" : streamType);
        String url = resolveUrl(data(post(apiPath("live", "/isup/live"), params)),
                protocol == null || protocol.isBlank() ? "flv" : protocol);
        if (url == null) {
            throw new IllegalStateException("ISUP 取流响应中没有播放地址或 streamId");
        }
        return url;
    }

    @Override
    public LiveHandle startLive(int channel, String protocol, String streamType) {
        String url = getLiveUrl(channel, protocol, streamType);
        String streamId = getDeviceId() + ":live:" + channel + ":" + streamType;
        liveChannels.put(streamId, channelIdOf(channel));
        return new LiveHandle(streamId, url, protocol, streamType, null);
    }

    @Override
    public void stopLive(String streamId) {
        String channelId = liveChannels.remove(streamId);
        if (channelId == null) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelId);
        data(post(apiPath("live.stop", "/isup/live/stop"), params));
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("ISUP 录像查询必须提供起止时间");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("startTime", TIME_FORMAT.format(range.start()));
        params.put("endTime", TIME_FORMAT.format(range.end()));
        JsonNode data = data(post(apiPath("record.query", "/isup/record/query"), params));
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            LocalDateTime start = time(node, "startTime", "beginTime");
            LocalDateTime end = time(node, "endTime", "stopTime");
            if (start == null || end == null) {
                continue;
            }
            result.add(new RecordFile(
                    text(node, "fileId", "id", "recordId"),
                    channelIdOf(channel),
                    start,
                    end,
                    longValue(node, "size", "fileSize")));
        }
        return result;
    }

    @Override
    public InputStream playback(int channel, TimeRange range) {
        return emptyStream();
    }

    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("ISUP 回放必须提供起止时间");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("startTime", TIME_FORMAT.format(range.start()));
        params.put("endTime", TIME_FORMAT.format(range.end()));
        params.put("protocol", "hls");
        String url = resolveUrl(data(post(apiPath("playback", "/isup/playback"), params)), "hls");
        if (url == null) {
            throw new IllegalStateException("ISUP 回放响应中没有播放地址或 streamId");
        }
        return url;
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        ptzControl(1, cmd, speed);
    }

    /**
     * 按通道执行云台控制。
     *
     * @param channel 通道序号
     * @param cmd     指令
     * @param speed   速度（0~1 或 0~100）
     */
    public void ptzControl(int channel, PtzCommand cmd, double speed) {
        if (cmd == null) {
            throw new IllegalArgumentException("云台指令不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("command", ptzCommand(cmd));
        params.put("speed", speedValue(speed, cmd == PtzCommand.STOP));
        data(post(apiPath("ptz", "/isup/ptz"), params));
    }

    @Override
    public void setPreset(int presetId, String name) {
        setPreset(1, presetId, name);
    }

    /**
     * 设置预置位。
     *
     * @param channel  通道序号
     * @param presetId 预置位号
     * @param name     名称
     */
    public void setPreset(int channel, int presetId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("presetId", presetId);
        params.put("presetName", name);
        data(post(apiPath("ptz.preset.set", "/isup/ptz/preset/set"), params));
    }

    @Override
    public void callPreset(int presetId) {
        callPreset(1, presetId);
    }

    /**
     * 调用预置位。
     *
     * @param channel  通道序号
     * @param presetId 预置位号
     */
    public void callPreset(int channel, int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("presetId", presetId);
        data(post(apiPath("ptz.preset.call", "/isup/ptz/preset/call"), params));
    }

    @Override
    public void deletePreset(int presetId) {
        deletePreset(1, presetId);
    }

    /**
     * 删除预置位。
     *
     * @param channel  通道序号
     * @param presetId 预置位号
     */
    public void deletePreset(int channel, int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(channel));
        params.put("presetId", presetId);
        data(post(apiPath("ptz.preset.delete", "/isup/ptz/preset/delete"), params));
    }

    @Override
    public List<PresetInfo> listPresets() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channel", channelIdOf(1));
        JsonNode data = data(post(apiPath("ptz.preset.list", "/isup/ptz/preset/list"), params));
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            Integer id = integer(node, "presetId", "id");
            if (id != null) {
                result.add(new PresetInfo(id, text(node, "name", "presetName")));
            }
        }
        return result;
    }

    /**
     * 解析通道序号对应的网关通道号。
     *
     * @param channel 通道序号
     * @return 通道号
     */
    private String channelIdOf(int channel) {
        return channelIds.getOrDefault(channel, String.valueOf(channel));
    }

    /**
     * 校验响应并取出 data。
     *
     * @param root 响应根节点
     * @return data 节点
     */
    private JsonNode data(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException("ISUP 服务端响应为空");
        }
        JsonNode code = root.get("code");
        if (code != null) {
            String value = code.asText();
            if (!"0".equals(value) && !"200".equals(value)) {
                throw new IllegalStateException("ISUP 调用失败: code=" + value
                        + ", msg=" + root.path("msg").asText(""));
            }
        }
        JsonNode data = root.get("data");
        return data == null || data.isNull() ? root : data;
    }

    /**
     * 取列表节点，兼容 {@code data.list} 与纯数组。
     *
     * @param data data 节点
     * @return 列表
     */
    private static List<JsonNode> listOf(JsonNode data) {
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        if (data.isArray()) {
            List<JsonNode> result = new ArrayList<>(data.size());
            data.forEach(result::add);
            return result;
        }
        for (String name : new String[]{"list", "records", "rows", "items", "channels"}) {
            JsonNode node = data.get(name);
            if (node != null && node.isArray()) {
                List<JsonNode> result = new ArrayList<>(node.size());
                node.forEach(result::add);
                return result;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 解析播放地址。
     *
     * @param data     data 节点
     * @param protocol 协议
     * @return 地址；无法解析返回 null
     */
    private String resolveUrl(JsonNode data, String protocol) {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isTextual()) {
            return data.asText().isBlank() ? null : data.asText();
        }
        String normalized = protocol == null || protocol.isBlank() ? "flv" : protocol.toLowerCase();
        String url = text(data, normalized, "httpFlv", "wsFlv", "hls", "rtmp", "rtsp", "url");
        if (url != null && !url.isBlank()) {
            return url;
        }
        String streamId = text(data, "streamId", "stream", "ssrc");
        if (streamId == null || streamId.isBlank()) {
            return null;
        }
        String media = extraText("mediaBaseUrl");
        if (media == null) {
            media = baseUrl;
        }
        if (media == null || media.isBlank()) {
            return null;
        }
        media = media.replaceAll("/+$", "");
        switch (normalized) {
            case "hls":
                return media + "/hls/" + streamId + "/hls.m3u8";
            case "ws-flv":
            case "wsflv":
                return media.replaceFirst("^http", "ws") + "/live/" + streamId + ".live.flv";
            case "rtmp":
                return media.replaceFirst("^https?", "rtmp") + "/live/" + streamId;
            case "rtsp":
                return media.replaceFirst("^https?", "rtsp") + "/rtp/" + streamId;
            default:
                return media + "/live/" + streamId + ".live.flv";
        }
    }

    /**
     * 路径覆盖。
     *
     * @param key         键
     * @param defaultPath 默认路径
     * @return 路径
     */
    private String apiPath(String key, String defaultPath) {
        String path = extraText("apiPath." + key);
        return path == null || path.isBlank() ? defaultPath : path;
    }

    /**
     * 读取扩展文本。
     *
     * @param key 键
     * @return 文本
     */
    private String extraText(String key) {
        Object value = info.extra() == null ? null : info.extra().get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    /**
     * 指令转网关命令。
     *
     * @param cmd 指令
     * @return 命令
     */
    private static String ptzCommand(PtzCommand cmd) {
        switch (cmd) {
            case UP:
                return "up";
            case DOWN:
                return "down";
            case LEFT:
                return "left";
            case RIGHT:
                return "right";
            case ZOOM_IN:
                return "zoomIn";
            case ZOOM_OUT:
                return "zoomOut";
            case FOCUS_IN:
                return "focusIn";
            case FOCUS_OUT:
                return "focusOut";
            case IRIS_IN:
                return "irisIn";
            case IRIS_OUT:
                return "irisOut";
            case STOP:
            default:
                return "stop";
        }
    }

    /**
     * 速度归一化到 0~255。
     *
     * @param speed 速度
     * @param stop  是否停止
     * @return 0~255
     */
    private static int speedValue(double speed, boolean stop) {
        if (stop) {
            return 0;
        }
        double normalized = speed <= 1.0d ? speed * 255.0d : speed;
        return (int) Math.max(1, Math.min(255, Math.round(normalized)));
    }

    /**
     * 取文本字段。
     *
     * @param node   节点
     * @param fields 字段
     * @return 文本
     */
    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 取整数字段。
     *
     * @param node   节点
     * @param fields 字段
     * @return 整数
     */
    private static Integer integer(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asInt();
                }
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                    // 继续下一个字段
                }
            }
        }
        return null;
    }

    /**
     * 取长整数字段。
     *
     * @param node   节点
     * @param fields 字段
     * @return 长整数
     */
    private static long longValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asLong();
                }
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                    // 继续下一个字段
                }
            }
        }
        return 0L;
    }

    /**
     * 取布尔字段。
     *
     * @param node   节点
     * @param fields 字段
     * @return 布尔值
     */
    private static Boolean bool(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isBoolean()) {
                    return value.asBoolean();
                }
                String text = value.asText();
                return "1".equals(text) || "true".equalsIgnoreCase(text)
                        || "online".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
            }
        }
        return null;
    }

    /**
     * 解析时间字段。
     *
     * @param node   节点
     * @param fields 字段
     * @return 时间
     */
    private static LocalDateTime time(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                long epoch = value.asLong();
                if (epoch < 100000000000L) {
                    epoch *= 1000L;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
            }
            String text = value.asText().trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                if (text.length() >= 19) {
                    return LocalDateTime.parse(text.substring(0, 19).replace('T', ' '), TIME_FORMAT);
                }
                return LocalDateTime.parse(text, TIME_FORMAT);
            } catch (Exception ignored) {
                // 继续下一个字段
            }
        }
        return null;
    }
}

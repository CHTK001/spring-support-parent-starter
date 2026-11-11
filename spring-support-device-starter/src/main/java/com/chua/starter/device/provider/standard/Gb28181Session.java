package com.chua.starter.device.provider.standard;

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
 * GB/T 28181 国标会话（WVP / ZLMediaKit REST 网关模式）。
 *
 * <p>国标本身是 SIP 信令 + RTP 媒体，不是 HTTP API；本会话不实现 SIP 栈，
 * 而是把凭据里的 {@code baseUrl} 指向国标服务端的 REST 封装（默认按
 * <a href="https://github.com/648540858/wvp-GB28181-pro">WVP</a> 的
 * {@code /api/v1/...} 路径），由服务端完成 INVITE、收流与转封装。</p>
 *
 * <p>通道号在国标里是 20 位字符串，而 SPI 的 {@code channel} 是 {@code int}。
 * 本会话在 {@link #listChannels()} 时按返回顺序建立「序号 → 国标通道 ID」映射，
 * 因此业务侧必须先拉通道列表再取流/回放/云台；未拉列表时把 {@code channel}
 * 直接当作国标通道 ID 字符串使用。</p>
 *
 * <p><b>未真机验证</b>：本机没有可用的 WVP/ZLMediaKit 环境，路径按 WVP 公开
 * REST 约定实现，字段解析兼容 {@code data.list} / {@code data} 两种信封。
 * 部署时可用 {@code extra("apiPath.xxx")} 覆盖单条路径，用
 * {@code extra("mediaBaseUrl")} 指定 ZLMediaKit 的 HTTP/WS 地址。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class Gb28181Session extends AbstractOpenApiSession {

    /** WVP 时间格式 */
    private static final DateTimeFormatter WVP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 通道序号到国标通道 ID 的映射 */
    private final Map<Integer, String> channelIds = new ConcurrentHashMap<>();

    /** 当前会话建立的直播句柄到通道 ID 的映射 */
    private final Map<String, String> liveChannels = new ConcurrentHashMap<>();

    /**
     * 构造国标会话。
     *
     * @param info 连接信息（{@code extra.baseUrl} 必填）
     */
    public Gb28181Session(DeviceConnectInfo info) {
        super(info, "");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "GB28181 未配置 SIP 服务端地址：请在凭据 extra 里填 baseUrl（WVP/ZLMediaKit REST 地址）");
        }
    }

    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        Map<String, String> headers = new LinkedHashMap<>();
        String authorization = extraText("authorization");
        if (authorization == null) {
            String token = extraText("token");
            authorization = token == null ? null : "Bearer " + token;
        }
        if (authorization != null) {
            headers.put("Authorization", authorization);
        }
        String accessToken = extraText("accessToken");
        if (accessToken != null) {
            headers.put("access-token", accessToken);
        }
        return execute(method, path, params == null ? Collections.emptyMap() : params, headers);
    }

    @Override
    public boolean testConnection() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("page", 1);
            params.put("count", 1);
            JsonNode data = data(get("/api/v1/device/list", params));
            return data != null;
        } catch (Exception ex) {
            log.warn("[GB28181] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        JsonNode data = data(get("/api/v1/device/channel/list", params));
        List<JsonNode> nodes = listOf(data);
        List<DeviceChannel> result = new ArrayList<>(nodes.size());
        channelIds.clear();
        int index = 1;
        for (JsonNode node : nodes) {
            String channelId = text(node, "channelId", "gbId", "deviceId", "id");
            if (channelId == null || channelId.isBlank()) {
                continue;
            }
            Integer channelNo = integer(node, "channelNo", "channel");
            if (channelNo == null) {
                channelNo = index;
            }
            channelIds.put(channelNo, channelId);
            result.add(new DeviceChannel(
                    channelId,
                    channelNo,
                    text(node, "name", "channelName"),
                    getDeviceId(),
                    bool(node, "online", "status"),
                    text(node, "model", "deviceModel"),
                    integer(node, "streamCount", "streamNum")));
            index++;
        }
        return result;
    }

    @Override
    public InputStream preview(int channel) {
        // 国标由网关转封装，业务侧通过 getLiveUrl 播放，不提供进程内字节流
        return emptyStream();
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        return getLiveUrl(channel, protocol, "main");
    }

    @Override
    public String getLiveUrl(int channel, String protocol, String streamType) {
        String channelId = channelIdOf(channel);
        String path = apiPath("live", "/api/v1/play/start/" + encode(getDeviceId()) + "/" + encode(channelId));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("streamType", streamType == null || streamType.isBlank() ? "main" : streamType);
        JsonNode data = data(get(path, params));
        String url = resolveUrl(data, protocol);
        if (url == null) {
            throw new IllegalStateException("GB28181 取流响应中没有播放地址或 streamId: " + data);
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
        if (channelId == null && streamId != null) {
            String[] parts = streamId.split(":");
            if (parts.length >= 3) {
                try {
                    channelId = channelIdOf(Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // 句柄不是本会话创建的，忽略
                }
            }
        }
        if (channelId == null) {
            return;
        }
        String path = apiPath("live.stop",
                "/api/v1/play/stop/" + encode(getDeviceId()) + "/" + encode(channelId));
        data(get(path, Collections.emptyMap()));
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("GB28181 录像查询必须提供起止时间");
        }
        String channelId = channelIdOf(channel);
        String path = apiPath("record.query",
                "/api/v1/record/query/" + encode(getDeviceId()) + "/" + encode(channelId));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startTime", WVP_TIME.format(range.start()));
        params.put("endTime", WVP_TIME.format(range.end()));
        JsonNode data = data(get(path, params));
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            LocalDateTime start = time(node, "startTime", "beginTime");
            LocalDateTime end = time(node, "endTime", "stopTime");
            if (start == null || end == null) {
                continue;
            }
            result.add(new RecordFile(
                    text(node, "fileId", "id", "recordId"),
                    channelId,
                    start,
                    end,
                    longValue(node, "size", "fileSize")));
        }
        return result;
    }

    @Override
    public InputStream playback(int channel, TimeRange range) {
        // 回放同样由网关转封装，业务侧使用 getPlaybackUrl
        return emptyStream();
    }

    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("GB28181 回放必须提供起止时间");
        }
        String channelId = channelIdOf(channel);
        String path = apiPath("record.play",
                "/api/v1/record/play/" + encode(getDeviceId()) + "/" + encode(channelId));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startTime", WVP_TIME.format(range.start()));
        params.put("endTime", WVP_TIME.format(range.end()));
        JsonNode data = data(get(path, params));
        String url = resolveUrl(data, "hls");
        if (url == null) {
            throw new IllegalStateException("GB28181 回放响应中没有播放地址或 streamId: " + data);
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
        String channelId = channelIdOf(channel);
        String path = apiPath("ptz.control",
                "/api/v1/ptz/control/" + encode(getDeviceId()) + "/" + encode(channelId));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", ptzCommand(cmd));
        int value = speedValue(speed, cmd == PtzCommand.STOP);
        params.put("horizonSpeed", value);
        params.put("verticalSpeed", value);
        params.put("zoomSpeed", value);
        data(post(path, params));
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
        String path = apiPath("ptz.preset.add",
                "/api/v1/ptz/preset/add/" + encode(getDeviceId()) + "/" + encode(channelIdOf(channel)));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("presetId", presetId);
        params.put("presetName", name);
        data(get(path, params));
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
        String path = apiPath("ptz.preset.call",
                "/api/v1/ptz/preset/call/" + encode(getDeviceId()) + "/" + encode(channelIdOf(channel)));
        data(get(path, Map.of("presetId", presetId)));
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
        String path = apiPath("ptz.preset.delete",
                "/api/v1/ptz/preset/delete/" + encode(getDeviceId()) + "/" + encode(channelIdOf(channel)));
        data(get(path, Map.of("presetId", presetId)));
    }

    @Override
    public List<PresetInfo> listPresets() {
        String path = apiPath("ptz.preset.query",
                "/api/v1/ptz/preset/query/" + encode(getDeviceId()) + "/" + encode(channelIdOf(1)));
        JsonNode data = data(get(path, Collections.emptyMap()));
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
     * 解析通道序号对应的国标通道 ID。
     *
     * @param channel 通道序号
     * @return 国标通道 ID
     */
    private String channelIdOf(int channel) {
        return channelIds.getOrDefault(channel, String.valueOf(channel));
    }

    /**
     * 从响应中提取 {@code data} 并校验 WVP 返回码。
     *
     * @param root 响应根节点
     * @return data 节点
     */
    private JsonNode data(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException("GB28181 服务端响应为空");
        }
        JsonNode code = root.get("code");
        if (code != null) {
            String value = code.asText();
            if (!"0".equals(value) && !"200".equals(value)) {
                throw new IllegalStateException("GB28181 调用失败: code=" + value
                        + ", msg=" + root.path("msg").asText(""));
            }
        }
        JsonNode data = root.get("data");
        return data == null || data.isNull() ? root : data;
    }

    /**
     * 取响应中的列表节点，兼容 {@code data.list}、{@code data.records} 与纯数组。
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
        for (String name : new String[]{"list", "records", "rows", "items"}) {
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
     * 解析播放地址：优先取响应里的 URL，其次用 streamId 拼 ZLMediaKit 地址。
     *
     * @param data     data 节点
     * @param protocol 协议
     * @return 播放地址；无法解析返回 null
     */
    private String resolveUrl(JsonNode data, String protocol) {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isTextual()) {
            String text = data.asText();
            return text.isBlank() ? null : text;
        }
        String normalized = protocol == null || protocol.isBlank() ? "flv" : protocol.toLowerCase();
        String url = text(data, normalized, "httpFlv", "wsFlv", "hls", "rtmp", "rtsp", "webrtc", "url");
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
            case "webrtc":
                return media + "/index/api/webrtc?app=rtp&stream=" + streamId + "&type=play";
            default:
                return media + "/live/" + streamId + ".live.flv";
        }
    }

    /**
     * 路径覆盖：优先 {@code extra("apiPath.<key>")}。
     *
     * @param key         路径键
     * @param defaultPath 默认路径
     * @return 实际路径
     */
    private String apiPath(String key, String defaultPath) {
        String path = extraText("apiPath." + key);
        return path == null || path.isBlank() ? defaultPath : path;
    }

    /**
     * 读取扩展字段文本。
     *
     * @param key 键
     * @return 文本；不存在返回 null
     */
    private String extraText(String key) {
        Object value = info.extra() == null ? null : info.extra().get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    /**
     * 云台指令转 WVP 命令。
     *
     * @param cmd 指令
     * @return WVP 命令
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
     * 速度归一化到 WVP 的 0~255。
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
     * @param fields 字段名
     * @return 文本；不存在返回 null
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
     * @param fields 字段名
     * @return 整数；不存在返回 null
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
                    // 继续尝试下一个字段
                }
            }
        }
        return null;
    }

    /**
     * 取长整数字段。
     *
     * @param node   节点
     * @param fields 字段名
     * @return 长整数；不存在返回 0
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
                    // 继续尝试下一个字段
                }
            }
        }
        return 0L;
    }

    /**
     * 取布尔字段。
     *
     * @param node   节点
     * @param fields 字段名
     * @return 布尔值；不存在返回 null
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
     * 解析时间字段，兼容秒/毫秒时间戳与常见日期格式。
     *
     * @param node   节点
     * @param fields 字段名
     * @return 时间；无法解析返回 null
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
                    return LocalDateTime.parse(text.substring(0, 19).replace('T', ' '), WVP_TIME);
                }
                return LocalDateTime.parse(text, WVP_TIME);
            } catch (Exception ignored) {
                // 尝试下一个字段
            }
        }
        return null;
    }

    /**
     * 简单 URL 路径编码。
     *
     * @param value 原值
     * @return 编码结果
     */
    private static String encode(String value) {
        return value == null ? "" : java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}

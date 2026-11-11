// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.dahua.DahuaIccApis;
import com.chua.starter.device.provider.sdk.dahua.DahuaIccSdk;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.OrganizationNode;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大华 ICC / DSS 私有化平台会话。
 *
 * <p>ICC 只有平台 SDK（{@link DahuaIccSdk}），此前没有会话实现，业务能力全部停在 TODO。
 * 本会话按 {@link DahuaIccApis} 已登记的 15 条目录接通 SPI：通道、取流、回放、录像、
 * 抓图、云台、门禁记录、远程开门、事件、组织、人员。</p>
 *
 * <p><b>未验证</b>：本机没有可达的 ICC 环境，请求字段按目录语义给出最小集合
 * （{@code deviceId}/{@code channelId}/{@code beginTime}/{@code endTime}），
 * 需在真实 ICC 上逐条核对后再把能力矩阵从 {@code IMPLEMENTED_UNVERIFIED} 转 {@code YES}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class DahuaIccSession extends AbstractOpenApiSession {

    /**
     * ICC 必须由凭据提供 baseUrl，缺省为空串（由 {@link DeviceConnectInfo#extra()} 覆盖）。
     */
    private static final String DEFAULT_BASE_URL = "";

    /**
     * 抓图下载客户端（图片地址通常是平台签名 URL，不再附带鉴权头）。
     */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * ICC 平台 SDK 适配器，负责签名与请求执行。
     */
    private final DahuaIccSdk delegate;

    /**
     * 创建 ICC 会话。
     *
     * @param info 连接信息（{@code platformType=dahua-icc}，需带 baseUrl）
     */
    public DahuaIccSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        this.delegate = new DahuaIccSdk(info);
    }

    /**
     * 按路径反查接口编码后委托 SDK 执行，保持与其它平台会话一致的 doRequest 形态。
     *
     * @param method HTTP 方法
     * @param path   接口路径
     * @param params 请求参数
     * @return 响应 JSON
     */
    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        String code = DahuaIccApis.all().stream()
                .filter(spec -> spec.path().equals(path))
                .map(PlatformApiSpec::code)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("ICC 接口未登记: " + path));
        return invoke(code, params);
    }

    @Override
    public boolean testConnection() {
        return delegate.testConnection();
    }

    /**
     * 通道列表。
     *
     * @return 通道
     */
    @Override
    public List<DeviceChannel> listChannels() {
        JsonNode data = invoke("device.channels", deviceParams());
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new DeviceChannel(
                    node.path("channelId").asText(node.path("channelCode").asText("")),
                    node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null,
                    node.path("channelName").asText(""),
                    info.deviceSerial(),
                    node.hasNonNull("online") ? node.get("online").asBoolean() : null,
                    node.path("model").asText(null),
                    null));
        }
        return result;
    }

    /**
     * 实时取流地址。
     *
     * @param channel  通道号
     * @param protocol 协议
     * @return 播放地址
     */
    @Override
    public String getLiveUrl(int channel, String protocol) {
        Map<String, Object> params = deviceParams();
        params.put("channelId", channelId(channel));
        params.put("protocol", protocol);
        JsonNode data = invoke("video.live", params);
        return data.path("url").asText(data.path("liveUrl").asText(""));
    }

    /**
     * 回放地址。
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 回放地址
     */
    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        Map<String, Object> params = deviceParams();
        params.put("channelId", channelId(channel));
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = invoke("video.playback", params);
        return data.path("url").asText(data.path("playbackUrl").asText(""));
    }

    /**
     * 录像片段查询。
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 录像片段
     */
    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        Map<String, Object> params = deviceParams();
        params.put("channelId", channelId(channel));
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = invoke("video.records", params);
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new RecordFile(
                    node.path("recordId").asText(""),
                    channelId(channel),
                    parseTime(node.path("beginTime").asText("")),
                    parseTime(node.path("endTime").asText("")),
                    node.path("size").asLong(0L)));
        }
        return result;
    }

    /**
     * 抓图：平台返回图片地址时二次下载。
     *
     * @param channel 通道号
     * @return 图片字节流
     */
    @Override
    public InputStream capture(int channel) {
        Map<String, Object> params = deviceParams();
        params.put("channelId", channelId(channel));
        JsonNode data = invoke("video.capture", params);
        String url = data.path("url").asText(data.path("picUrl").asText(""));
        if (url.isBlank()) {
            return emptyStream();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> response = DOWNLOADER.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new ByteArrayInputStream(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ICC 抓图下载被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("ICC 抓图下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 云台控制。
     *
     * @param cmd   方向
     * @param speed 速度（0~1）
     */
    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        if (cmd == PtzCommand.STOP) {
            return;
        }
        Map<String, Object> params = deviceParams();
        params.put("channelId", info.deviceSerial());
        params.put("command", cmd.name());
        params.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
        invoke("video.ptz", params);
    }

    /**
     * 门禁记录。
     *
     * @param range 时间范围
     * @return 出入记录
     */
    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        Map<String, Object> params = deviceParams();
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = invoke("access.record", params);
        List<AccessRecord> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new AccessRecord(
                    node.path("doorId").asText(""),
                    node.path("cardNo").asText(""),
                    node.path("personName").asText(""),
                    parseTime(node.path("eventTime").asText("")),
                    node.path("eventType").asText(""),
                    node.path("direction").asText(""),
                    node.path("picUrl").asText("")));
        }
        return result;
    }

    /**
     * 远程开门。
     *
     * @param doorId 门标识
     */
    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = deviceParams();
        params.put("doorId", doorId);
        invoke("access.door.open", params);
    }

    /**
     * 告警/事件拉取。
     *
     * @param range 时间范围
     * @return 事件
     */
    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        Map<String, Object> params = deviceParams();
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        JsonNode data = invoke("alarm.list", params);
        List<DeviceEvent> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new DeviceEvent(
                    info.deviceSerial(),
                    node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null,
                    EventType.OTHER,
                    node.path("alarmType").asText(node.path("eventType").asText("")),
                    parseTime(node.path("alarmTime").asText("")),
                    node));
        }
        return result;
    }

    /**
     * 组织列表。
     *
     * @return 组织节点
     */
    public List<OrganizationNode> fetchOrganizations() {
        JsonNode data = invoke("org.list", Map.of());
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new OrganizationNode(
                    node.path("orgId").asText(""),
                    node.path("orgName").asText(""),
                    node.path("parentId").asText(null)));
        }
        return result;
    }

    /**
     * 人员列表。
     *
     * @return 人员
     */
    public List<DevicePerson> fetchPersons() {
        JsonNode data = invoke("person.list", deviceParams());
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : listOf(data)) {
            result.add(new DevicePerson(
                    node.path("orgId").asText(""),
                    node.path("personId").asText(""),
                    node.path("personName").asText(""),
                    null,
                    node.path("phone").asText(null),
                    node.path("email").asText(null)));
        }
        return result;
    }

    /**
     * 设备维度公共参数。
     *
     * @return 参数
     */
    private Map<String, Object> deviceParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", info.deviceSerial());
        return params;
    }

    /**
     * 通道号换算通道标识（ICC 以设备序列号下的通道编码寻址，待真机核对）。
     *
     * @param channel 通道号
     * @return 通道标识
     */
    private String channelId(int channel) {
        String serial = info.deviceSerial();
        return serial == null ? String.valueOf(channel) : serial + "_" + channel;
    }

    /**
     * 从响应中取列表节点，兼容 {@code list}/{@code records}/{@code data} 三种返回形态。
     *
     * @param data 响应数据节点
     * @return 列表节点
     */
    private static JsonNode listOf(JsonNode data) {
        if (data.isArray()) {
            return data;
        }
        for (String field : new String[] {"list", "records", "data"}) {
            JsonNode node = data.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return data.path("list");
    }

    /**
     * 解析时间：ISO 本地时间，失败返回 {@code null}。
     *
     * @param text 时间文本
     * @return 本地时间
     */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            log.debug("ICC 时间解析失败: {}", text);
            return null;
        }
    }

    /**
     * 按接口编码调用 ICC，并统一换算成 data 节点。
     *
     * @param code   接口编码
     * @param params 请求参数
     * @return data 节点
     */
    private JsonNode invoke(String code, Map<String, Object> params) {
        PlatformApiResult result = delegate.invoke(code, params);
        if (result == null || !result.ok()) {
            throw new IllegalStateException("ICC 调用失败: "
                    + (result == null ? "无调用结果" : result.message()));
        }
        return MAPPER.valueToTree(result.data());
    }
}

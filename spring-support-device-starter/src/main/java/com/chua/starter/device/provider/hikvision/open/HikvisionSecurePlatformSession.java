// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisApis;
import com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisSdk;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;
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

    /**
     * 监控点分页查询接口
     */
    private static final String PATH_CAMERA_SEARCH = "/artemis/api/resource/v2/camera/search";

    /**
     * 录像片段查询接口
     */
    private static final String PATH_CAMERA_RECORDS = "/artemis/api/video/v2/cameras/records";

    /**
     * 监控点抓图接口
     */
    private static final String PATH_CAMERA_CAPTURE = "/artemis/api/video/v1/cameras/images";

    /**
     * 门禁点列表接口
     */
    private static final String PATH_DOOR_LIST = "/artemis/api/resource/v1/door/search";

    /**
     * 门禁权限同步下发（iSC ACPS）
     */
    private static final String PATH_ACCESS_DOWNLOAD = "/api/acps/v1/authDownload/task/simpleDownload";

    /**
     * 门禁出入记录接口
     */
    private static final String PATH_ACCESS_RECORD = "/artemis/api/access/v1/accessRecords";

    /**
     * 事件查询接口
     */
    private static final String PATH_EVENT_SEARCH = "/artemis/api/eventService/v1/eventRecords/page";

    /**
     * 预置位列表接口
     */
    private static final String PATH_PRESET_LIST = "/artemis/api/video/v1/ptz/presets";

    /**
     * 预置位设置接口
     */
    private static final String PATH_PRESET_SET = "/artemis/api/video/v1/ptz/presets/set";

    /**
     * 预置位删除接口
     */
    private static final String PATH_PRESET_DELETE = "/artemis/api/video/v1/ptz/presets/delete";

    /**
     * 组织列表接口
     */
    private static final String PATH_ORG_LIST = "/artemis/api/resource/v1/org/orgList";

    /**
     * 人员列表接口
     */
    private static final String PATH_PERSON_LIST = "/artemis/api/resource/v2/person/personList";

    /**
     * Artemis 单页上限
     */
    private static final int PAGE_SIZE = 1000;

    /**
     * 抓图下载客户端（图片地址为平台签名 URL，不再附带鉴权头）
     */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

    /**
     * 监控点分页查询：Artemis 按 cameraIndexCode 过滤，通道号由监控点自身的 channelNo 回填。
     *
     * @return 通道列表
     */
    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> params = new HashMap<>();
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        params.put("cameraIndexCodes", List.of(info.deviceSerial()));
        JsonNode data = assertOk(post(PATH_CAMERA_SEARCH, params));
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new DeviceChannel(
                    node.path("cameraIndexCode").asText(),
                    node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null,
                    node.path("cameraName").asText(""),
                    info.deviceSerial(),
                    node.hasNonNull("status") ? Boolean.valueOf("0".equals(node.get("status").asText())) : null,
                    node.path("cameraType").asText(null),
                    null));
        }
        return result;
    }

    /**
     * 查询录像片段：Artemis 按监控点 + 时间范围分页返回。
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 录像片段
     */
    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", cameraIndexCode(channel));
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        JsonNode data = assertOk(post(PATH_CAMERA_RECORDS, params));
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new RecordFile(
                    node.path("recordId").asText(node.path("fileId").asText("")),
                    cameraIndexCode(channel),
                    parseEventTime(node.path("beginTime").asText("")),
                    parseEventTime(node.path("endTime").asText("")),
                    node.path("size").asLong(0L)));
        }
        return result;
    }

    /**
     * 监控点抓图：平台返回签名图片地址，这里二次下载为字节流。
     *
     * @param channel 通道号
     * @return 图片字节流
     */
    @Override
    public InputStream capture(int channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", cameraIndexCode(channel));
        JsonNode data = assertOk(post(PATH_CAMERA_CAPTURE, params));
        String url = data.path("picUrl").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("Artemis 抓图未返回 picUrl");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> response = DOWNLOADER.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new ByteArrayInputStream(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Artemis 抓图下载被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("Artemis 抓图下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 门禁点列表。
     *
     * @return 门禁点
     */
    @Override
    public List<DeviceDoorInfo> listDoors() {
        Map<String, Object> params = new HashMap<>();
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        JsonNode data = assertOk(post(PATH_DOOR_LIST, params));
        List<DeviceDoorInfo> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new DeviceDoorInfo(
                    node.path("doorIndexCode").asText(),
                    node.path("doorName").asText(""),
                    node.path("acsDevIndexCode").asText(info.deviceSerial()),
                    node.path("status").asText(null),
                    node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null));
        }
        return result;
    }

    /**
     * 单人员单门权限同步下发。
     *
     * <p>按 iSC ACPS 的 {@code simpleDownload} 接口资料实现：
     * 先按 {@code doorId} 查门点，取所属门禁设备 {@code acsDevIndexCode}
     * 与门点通道号，再提交人员与资源对象。该接口一次只处理一个人员到一个门点，
     * 大批量场景应使用下载任务接口（未接入）。</p>
     *
     * @param personId 厂商侧人员标识
     * @param doorId   厂商侧门点标识
     */
    @Override
    public void grantDoorAccess(String personId, String doorId) {
        if (personId == null || personId.isBlank()) {
            throw new IllegalArgumentException("人员标识不能为空");
        }
        if (doorId == null || doorId.isBlank()) {
            throw new IllegalArgumentException("门点标识不能为空");
        }
        DeviceDoorInfo door = listDoors().stream()
                .filter(item -> doorId.equals(item.doorId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("门点不存在: " + doorId));
        if (door.channelNo() == null) {
            throw new IllegalStateException("门点缺少通道号，无法下发权限: " + doorId);
        }
        Map<String, Object> resourceInfo = new HashMap<>();
        resourceInfo.put("resourceIndexCode", door.deviceId());
        resourceInfo.put("resourceType", "acsDevice");
        resourceInfo.put("channelNos", List.of(door.channelNo()));
        Map<String, Object> personInfo = new HashMap<>();
        personInfo.put("personId", personId);
        Map<String, Object> params = new HashMap<>();
        params.put("resourceInfo", resourceInfo);
        params.put("personInfo", personInfo);
        assertOk(post(PATH_ACCESS_DOWNLOAD, params));
    }

    /**
     * 门禁出入记录。
     *
     * @param range 时间范围
     * @return 出入记录
     */
    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("beginTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        JsonNode data = assertOk(post(PATH_ACCESS_RECORD, params));
        List<AccessRecord> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new AccessRecord(
                    node.path("doorIndexCode").asText(""),
                    node.path("cardNo").asText(""),
                    node.path("personName").asText(""),
                    parseEventTime(node.path("eventTime").asText("")),
                    node.path("eventType").asText(""),
                    node.path("inAndOutType").asText(""),
                    node.path("picUrl").asText("")));
        }
        return result;
    }

    /**
     * 事件拉取：综合安防走事件服务分页查询，类型统一归为 {@link EventType#OTHER}，原始报文保留。
     *
     * @param range 时间范围
     * @return 事件列表
     */
    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("startTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        JsonNode data = assertOk(post(PATH_EVENT_SEARCH, params));
        List<DeviceEvent> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new DeviceEvent(
                    info.deviceSerial(),
                    node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null,
                    EventType.OTHER,
                    node.path("eventType").asText(node.path("eventName").asText("")),
                    parseEventTime(node.path("eventTime").asText(node.path("startTime").asText(""))),
                    node));
        }
        return result;
    }

    /**
     * 预置位列表。
     *
     * @return 预置位
     */
    @Override
    public List<PresetInfo> listPresets() {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.deviceSerial());
        JsonNode data = assertOk(post(PATH_PRESET_LIST, params));
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new PresetInfo(node.path("presetId").asInt(), node.path("presetName").asText("")));
        }
        return result;
    }

    /**
     * 设置预置位。
     *
     * @param presetId 预置位编号
     * @param name     预置位名称
     */
    @Override
    public void setPreset(int presetId, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.deviceSerial());
        params.put("presetId", presetId);
        params.put("presetName", name);
        post(PATH_PRESET_SET, params);
    }

    /**
     * 调用预置位。
     *
     * <p>Artemis 接口目录（{@link HikvisionArtemisApis}）只登记了预置位的查询/设置/删除，
     * 没有“调用到预置位”的独立路径，因此显式拒绝，避免静默空实现。</p>
     *
     * @param presetId 预置位编号
     */
    @Override
    public void callPreset(int presetId) {
        throw new UnsupportedOperationException("Artemis 接口目录未登记预置位调用接口，暂不支持");
    }

    /**
     * 删除预置位。
     *
     * @param presetId 预置位编号
     */
    @Override
    public void deletePreset(int presetId) {
        Map<String, Object> params = new HashMap<>();
        params.put("cameraIndexCode", info.deviceSerial());
        params.put("presetId", presetId);
        post(PATH_PRESET_DELETE, params);
    }

    /**
     * 通道号换算监控点编码：设备序列号本身就是监控点编码时直接使用；
     * 否则按 Artemis 的 {@code 设备编码$通道号} 口径拼接（待真机核对）。
     *
     * @param channel 通道号
     * @return 监控点编码
     */
    private String cameraIndexCode(int channel) {
        String serial = info.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalStateException("Artemis 会话缺少设备编码");
        }
        if (serial.contains("$")) {
            return serial;
        }
        return serial + "$" + (channel + 1);
    }

    /**
     * 解析事件时间：优先按 ISO 本地时间解析，带时区或格式不符时返回 {@code null}。
     *
     * @param text 时间文本
     * @return 本地时间
     */
    private static LocalDateTime parseEventTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            log.debug("Artemis 事件时间解析失败: {}", text);
            return null;
        }
    }

    /**
     * 组织树：综合安防 orgList 返回全量组织层级。
     *
     * @return 组织节点
     */
    public List<OrganizationNode> fetchOrganizations() {
        JsonNode data = assertOk(post(PATH_ORG_LIST, Map.of()));
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new OrganizationNode(
                    node.path("orgIndexCode").asText(),
                    node.path("orgName").asText(""),
                    node.path("parentOrgIndexCode").asText(null)));
        }
        return result;
    }

    /**
     * 人员列表：综合安防 personList 分页返回。
     *
     * @return 人员条目
     */
    public List<DevicePerson> fetchPersons() {
        Map<String, Object> params = new HashMap<>();
        params.put("pageNo", 1);
        params.put("pageSize", PAGE_SIZE);
        JsonNode data = assertOk(post(PATH_PERSON_LIST, params));
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            result.add(new DevicePerson(
                    node.path("orgIndexCode").asText(""),
                    node.path("personId").asText(""),
                    node.path("personName").asText(""),
                    null,
                    null,
                    null));
        }
        return result;
    }

    /**
     * 新增或修改组织：组织标识为空走 {@code org.add}，非空走 {@code org.update}。
     *
     * @param node 组织节点
     * @return 厂商侧组织标识
     */
    public String upsertOrganization(OrganizationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("组织节点不能为空");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("orgName", node.orgName());
        if (node.parentId() != null && !node.parentId().isBlank()) {
            params.put("parentOrgIndexCode", node.parentId());
        }
        boolean exists = node.orgId() != null && !node.orgId().isBlank();
        if (exists) {
            params.put("orgIndexCode", node.orgId());
        }
        JsonNode data = invoke(exists ? "org.update" : "org.add", params);
        return exists ? node.orgId() : data.path("orgIndexCode").asText(null);
    }

    /**
     * 删除组织。
     *
     * @param orgId 厂商侧组织标识
     * @return 是否成功
     */
    public boolean removeOrganization(String orgId) {
        Map<String, Object> params = new HashMap<>();
        params.put("orgIndexCode", orgId);
        invoke("org.delete", params);
        return true;
    }

    /**
     * 新增或修改人员：人员标识为空走 {@code person.add}，非空走 {@code person.update}。
     *
     * @param person 人员条目
     * @return 厂商侧人员标识
     */
    public String upsertPerson(DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("personName", person.name());
        if (person.orgId() != null && !person.orgId().isBlank()) {
            params.put("orgIndexCode", person.orgId());
        }
        boolean exists = person.externalId() != null && !person.externalId().isBlank();
        if (exists) {
            params.put("personId", person.externalId());
        }
        JsonNode data = invoke(exists ? "person.update" : "person.add", params);
        return exists ? person.externalId() : data.path("personId").asText(null);
    }

    /**
     * 删除人员。
     *
     * @param personId 厂商侧人员标识
     * @return 是否成功
     */
    public boolean removePerson(String personId) {
        Map<String, Object> params = new HashMap<>();
        params.put("personId", personId);
        invoke("person.delete", params);
        return true;
    }

    /**
     * 新增或修改设备：已存在走 {@code device.update}，否则走 {@code device.add}。
     *
     * @param device 设备条目
     * @param exists 平台侧是否已存在
     * @return 是否成功
     */
    public boolean upsertDevice(VendorDevice device, boolean exists) {
        if (device == null) {
            throw new IllegalArgumentException("设备不能为空");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("deviceName", device.name());
        if (device.serial() != null && !device.serial().isBlank()) {
            params.put("deviceSerial", device.serial());
        }
        if (exists && device.externalId() != null && !device.externalId().isBlank()) {
            params.put("deviceIndexCode", device.externalId());
        }
        invoke(exists ? "device.update" : "device.add", params);
        return true;
    }

    /**
     * 删除设备。
     *
     * @param externalId 厂商侧设备标识
     * @return 是否成功
     */
    public boolean removeDevice(String externalId) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceIndexCode", externalId);
        invoke("device.delete", params);
        return true;
    }

    /**
     * 按接口编码调用 Artemis，并统一换算成 data 节点。
     *
     * @param code   接口编码
     * @param params 请求参数
     * @return data 节点
     */
    private JsonNode invoke(String code, Map<String, Object> params) {
        PlatformApiResult result = delegate.invoke(code, params);
        if (result == null || !result.ok()) {
            throw new IllegalStateException("Artemis 调用失败: "
                    + (result == null ? "无调用结果" : result.message()));
        }
        return MAPPER.valueToTree(result.data());
    }
}
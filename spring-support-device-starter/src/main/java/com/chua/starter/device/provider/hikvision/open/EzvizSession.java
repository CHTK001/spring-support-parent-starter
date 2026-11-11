package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 萤石云（EZVIZ）设备会话。
 *
 * <h3>与其它云平台会话的差异</h3>
 * <p>萤石 OPEN API 全部为 <b>form 表单</b> 提交（{@code application/x-www-form-urlencoded}），
 * 不是 JSON；响应信封 {@code {code, msg, data}}，{@code code == "200"} 为成功。
 * 因此本类复写 {@link #doRequest} 而不用基类的 JSON 发送逻辑。</p>
 *
 * <h3>已实现的官方接口</h3>
 * <ul>
 *   <li>直播：{@code POST /api/lapp/v2/live/address/get}（取流地址）</li>
 *   <li>录像：{@code POST /api/lapp/v2/live/address/get}（type=2 回放）</li>
 *   <li>云台：{@code POST /api/lapp/device/ptz/start}、{@code /ptz/stop}、
 *       {@code /device/preset/add}、{@code /device/preset/move}、{@code /device/preset/clear}</li>
 *   <li>抓图：{@code POST /api/lapp/device/capture}</li>
 *   <li>通道：{@code POST /api/lapp/camera/list}</li>
 *   <li>门禁：{@code POST /api/lapp/keylock/remote/op}（远程开锁）、
 *       {@code POST /api/lapp/keylock/open/list}（开门记录）、
 *       {@code POST /api/lapp/building/device/smartlock/list}（智能锁列表）</li>
 *   <li>事件：{@code POST /api/lapp/alarm/list}、{@code /alarm/device/list}</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class EzvizSession extends AbstractOpenApiSession {

    /** 官方网关（固定，不允许凭据覆盖） */
    private static final String GATEWAY = "https://open.ys7.com";

    /** 取令牌 */
    private static final String P_TOKEN = "/api/lapp/token/get";

    /** 直播/回放地址 */
    private static final String P_LIVE_ADDRESS = "/api/lapp/v2/live/address/get";

    /** 云台开始 / 停止 */
    private static final String P_PTZ_START = "/api/lapp/device/ptz/start";
    private static final String P_PTZ_STOP = "/api/lapp/device/ptz/stop";

    /** 预置位：添加 / 调用 / 清除 */
    private static final String P_PRESET_ADD = "/api/lapp/device/preset/add";
    private static final String P_PRESET_MOVE = "/api/lapp/device/preset/move";
    private static final String P_PRESET_CLEAR = "/api/lapp/device/preset/clear";

    /** 抓图 */
    private static final String P_CAPTURE = "/api/lapp/device/capture";

    /** 通道列表 */
    private static final String P_CAMERA_LIST = "/api/lapp/camera/list";

    /** 远程开锁 / 开门记录 / 智能锁列表 */
    private static final String P_LOCK_OPEN = "/api/lapp/keylock/remote/op";
    private static final String P_LOCK_OPEN_LIST = "/api/lapp/keylock/open/list";
    private static final String P_LOCK_LIST = "/api/lapp/building/device/smartlock/list";

    /** 智能锁开锁 */
    private static final String P_LOCK_UNLOCK = "/api/lapp/building/device/smartlock/unlock";

    /** 告警列表 */
    private static final String P_ALARM_LIST = "/api/lapp/alarm/list";

    /** 企业人员：分页查询 / 新增 / 更新 / 删除（v3 域，GET 与 DELETE 走 query 参数） */
    private static final String P_MEMBER_PAGE = "/api/v3/device/company/member/queryByPage";
    private static final String P_MEMBER_ADD = "/api/v3/device/company/member/add";
    private static final String P_MEMBER_UPDATE = "/api/v3/device/company/member/update";
    private static final String P_MEMBER_DELETE = "/api/v3/device/company/member/delete";

    /** 设备在线状态 */
    private static final String P_DEVICE_INFO = "/api/lapp/device/info";

    /** 添加设备到账号下（官方要求 deviceSerial + validateCode 均必填） */
    private static final String P_DEVICE_ADD = "/api/lapp/device/add";

    /** 从账号下删除设备 */
    private static final String P_DEVICE_DELETE = "/api/lapp/device/delete";

    /** 修改云端设备名称 */
    private static final String P_DEVICE_NAME_UPDATE = "/api/lapp/device/name/update";

    /** 成功码 */
    private static final String OK = "200";

    /** 令牌有效期（秒） */
    private static final long TTL = 604_800L;

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 云台命令到萤石 direction 的映射 */
    private static final Map<PtzCommand, String> DIRECTIONS = Map.of(
            PtzCommand.UP, "0",
            PtzCommand.DOWN, "1",
            PtzCommand.LEFT, "2",
            PtzCommand.RIGHT, "3",
            PtzCommand.ZOOM_IN, "8",
            PtzCommand.ZOOM_OUT, "9");

    /** 设备序列号 */
    private final String deviceSerial;

    /**
     * 构造萤石会话。
     *
     * @param info 连接信息（deviceSerial 来自 info.deviceSerial() 或 extra("deviceSerial")）
     */
    public EzvizSession(DeviceConnectInfo info) {
        super(info, GATEWAY);
        Object serial = info.extra() == null ? null : info.extra().get("deviceSerial");
        String fromExtra = serial instanceof String s ? s : null;
        this.deviceSerial = fromExtra != null && !fromExtra.isBlank() ? fromExtra : info.deviceSerial();
    }

    @Override
    public String getDeviceId() {
        return deviceSerial;
    }

    @Override
    public boolean isOnline() {
        if (deviceSerial == null || deviceSerial.isBlank()) {
            return false;
        }
        try {
            JsonNode data = assertOk(doRequest("POST", P_DEVICE_INFO, Map.of("deviceSerial", deviceSerial)));
            return "1".equals(data.path("status").asText());
        } catch (Exception ex) {
            log.warn("[EzvizSession] 查询设备状态失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        online = false;
    }

    @Override
    public InputStream preview(int channel) {
        // 萤石为云端 URL 取流模式，不提供字节流；调用方应使用 getLiveUrl
        throw new UnsupportedOperationException("萤石为 URL 取流模式，请使用 getLiveUrl / startLive");
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", channel);
        params.put("protocol", protocol == null ? "3" : toEzvizProtocol(protocol));
        params.put("type", "1");
        params.put("quality", "1");
        JsonNode data = assertOk(doRequest("POST", P_LIVE_ADDRESS, params));
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("萤石未返回直播地址: " + data);
        }
        return url;
    }

    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        requireRange(range);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", channel);
        params.put("protocol", "3");
        params.put("type", "2");
        params.put("startTime", toEzvizTime(range.start()));
        params.put("stopTime", toEzvizTime(range.end()));
        JsonNode data = assertOk(doRequest("POST", P_LIVE_ADDRESS, params));
        return data.path("url").asText("");
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        // 萤石 OPEN API 未提供录像文件枚举接口（只有回放取址），此处按回放地址返回单条
        requireRange(range);
        String url = getPlaybackUrl(channel, range);
        if (url == null || url.isBlank()) {
            return List.of();
        }
        String fileId = requireSerial() + "_" + channel + "_" + toEzvizTime(range.start());
        return List.of(new RecordFile(fileId, String.valueOf(channel),
                range.start(), range.end(), 0L));
    }

    @Override
    public InputStream playback(int channel, TimeRange range) {
        throw new UnsupportedOperationException("萤石为 URL 回放模式，请使用 getPlaybackUrl");
    }

    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        JsonNode data = assertOk(doRequest("POST", P_CAMERA_LIST, params));
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            Integer no = node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null;
            result.add(new DeviceChannel(
                    node.path("id").asText(requireSerial() + "_" + no),
                    no,
                    node.path("channelName").asText(""),
                    requireSerial(),
                    onlineOf(node, "status"),
                    null,
                    2));
        }
        return result;
    }

    @Override
    public InputStream capture(int channel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", channel);
        JsonNode data = assertOk(doRequest("POST", P_CAPTURE, params));
        String url = data.path("picUrl").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("萤石未返回抓图地址: " + data);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            return new java.io.ByteArrayInputStream(body == null ? new byte[0] : body);
        } catch (Exception ex) {
            throw new IllegalStateException("萤石抓图下载失败: " + url + " -> " + ex.getMessage(), ex);
        }
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        String direction = DIRECTIONS.get(cmd);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", 1);
        params.put("direction", direction);
        params.put("speed", (int) Math.max(1, Math.min(7, Math.round(speed <= 0 ? 4 : speed))));
        assertOk(doRequest("POST", P_PTZ_START, params));
        // 萤石要求显式 stop，否则云台会一直转
        try {
            Thread.sleep(Math.max(120L, (long) (300L / Math.max(1.0, speed))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        assertOk(doRequest("POST", P_PTZ_STOP, params));
    }

    @Override
    public void setPreset(int presetId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", 1);
        params.put("presetName", name == null ? ("preset" + presetId) : name);
        assertOk(doRequest("POST", P_PRESET_ADD, params));
    }

    @Override
    public void callPreset(int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", 1);
        params.put("presetId", presetId);
        assertOk(doRequest("POST", P_PRESET_MOVE, params));
    }

    @Override
    public void deletePreset(int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", 1);
        params.put("presetId", presetId);
        assertOk(doRequest("POST", P_PRESET_CLEAR, params));
    }

    @Override
    public List<PresetInfo> listPresets() {
        // 萤石 OPEN API 未提供"查询预置位列表"（只有新增/调用/清除）
        throw new UnsupportedOperationException("萤石 OPEN API 未提供预置位列表接口");
    }

    @Override
    public List<DeviceDoorInfo> listDoors() {
        // 萤石智能锁列表 deviceSerial 必填（实测缺参返回 code=10001 "deviceSerial不能为空"）；
        // 本会话绑定的设备若不是智能锁会返回 20007 设备离线/不是锁，此时按无门禁设备处理。
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("pageStart", 0);
        params.put("pageSize", 50);
        JsonNode data;
        try {
            data = assertOk(doRequest("POST", P_LOCK_LIST, params));
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("20007")) {
                log.info("[EzvizSession] 设备 {} 不是智能锁，无门禁点位", requireSerial());
                return List.of();
            }
            throw ex;
        }
        List<DeviceDoorInfo> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            String serial = node.path("deviceSerial").asText("");
            String status = "1".equals(node.path("status").asText()) ? "open" : "closed";
            result.add(new DeviceDoorInfo(
                    serial,
                    node.path("deviceName").asText(serial),
                    serial,
                    status,
                    1));
        }
        return result;
    }

    @Override
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("channelNo", doorId);
        assertOk(doRequest("POST", P_LOCK_OPEN, params));
    }

    /**
     * 按门锁设备序列号远程开锁（{@code POST /api/lapp/building/device/smartlock/unlock}）。
     *
     * <p>与会话绑定的设备无关：对任意一台已授权的萤石智能锁开门。
     * 与 {@link #remoteUnlock(int)} 的区别在于后者作用于当前会话设备的门通道。</p>
     *
     * @param smartLockSerial 智能锁设备序列号
     * @return 是否开锁成功
     */
    public boolean unlockSmartLock(String smartLockSerial) {
        if (smartLockSerial == null || smartLockSerial.isBlank()) {
            throw new IllegalArgumentException("智能锁序列号不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", smartLockSerial);
        params.put("channelNo", 1);
        assertOk(doRequest("POST", P_LOCK_UNLOCK, params));
        return true;
    }

    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        requireRange(range);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        params.put("startTime", toEzvizTime(range.start()));
        params.put("endTime", toEzvizTime(range.end()));
        params.put("pageStart", 0);
        params.put("pageSize", 50);
        JsonNode data = assertOk(doRequest("POST", P_LOCK_OPEN_LIST, params));
        List<AccessRecord> result = new ArrayList<>();
        for (JsonNode node : toList(data)) {
            result.add(new AccessRecord(
                    node.path("deviceSerial").asText(requireSerial()),
                    node.path("cardNo").asText(""),
                    node.path("userName").asText(""),
                    toLocal(node.path("openTime").asLong(0L)),
                    node.path("openType").asText(""),
                    null,
                    node.path("picUrl").asText("")));
        }
        return result;
    }

    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        requireRange(range);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", requireSerial());
        // 萤石 /api/lapp/alarm/list 的 startTime/endTime 只接受毫秒时间戳
        // （实测 yyyy-MM-dd HH:mm:ss 会返回 code=10001 "startTime格式不合法"）
        params.put("startTime", toEpochMillis(range.start()));
        params.put("endTime", toEpochMillis(range.end()));
        params.put("pageStart", 0);
        params.put("pageSize", 50);
        JsonNode root = doRequest("POST", P_ALARM_LIST, params);
        assertOk(root);
        List<DeviceEvent> result = new ArrayList<>();
        for (JsonNode node : toList(root.get("data"))) {
            int channelNo = node.path("channelNo").asInt(1);
            result.add(new DeviceEvent(
                    requireSerial(),
                    channelNo,
                    EventType.IO_ALARM,
                    node.path("alarmName").asText(""),
                    toLocal(node.path("alarmTime").asLong(0L)),
                    node));
        }
        return result;
    }

    @Override
    public boolean testConnection() {
        try {
            assertOk(doRequest("POST", P_DEVICE_INFO, Map.of("deviceSerial", requireSerial())));
            return true;
        } catch (Exception ex) {
            log.warn("[EzvizSession] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 萤石 form 表单请求。
     *
     * @param method HTTP 方法（GET 参数拼 query，POST 参数放表单体）
     * @param path   接口路径
     * @param params 参数
     * @return 响应 JSON
     */
    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        ensureToken(path);
        Map<String, Object> form = new LinkedHashMap<>();
        if (params != null) {
            form.putAll(params);
        }
        if (!P_TOKEN.equals(path)) {
            form.put("accessToken", accessToken);
        }
        String query = encode(form);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));
            HttpRequest request;
            String uri = baseUrl + path + (query.isEmpty() ? "" : "?" + query);
            if ("GET".equalsIgnoreCase(method)) {
                request = builder.uri(URI.create(uri)).GET().build();
            } else if ("DELETE".equalsIgnoreCase(method)) {
                request = builder.uri(URI.create(uri)).DELETE().build();
            } else {
                request = builder.uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8))
                        .build();
            }
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("萤石 HTTP " + response.statusCode() + ": "
                        + (body == null ? "" : body.substring(0, Math.min(200, body.length()))));
            }
            JsonNode root = MAPPER.readTree(body == null || body.isBlank() ? "{}" : body);
            String code = successCodeOf(root);
            if (!OK.equals(code)) {
                if ("10001".equals(code) || "10002".equals(code)) {
                    this.accessToken = null;
                    this.tokenExpiry = Instant.EPOCH;
                }
                throw new IllegalStateException("萤石调用失败: path=" + path
                        + ", code=" + code + ", msg=" + messageOf(root));
            }
            return root;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("萤石 HTTP 调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 读取响应成功码。
     *
     * <p>萤石存在两套信封：</p>
     * <ul>
     *   <li>{@code /api/lapp/**}：{@code {code, msg, data}} —— 成功码 "200"</li>
     *   <li>{@code /api/service/**}：{@code {data, meta:{code, message}}} —— 成功码 200</li>
     * </ul>
     * <p>两种口径都归一成字符串 "200"。</p>
     *
     * @param root 响应根节点
     * @return 成功码
     */
    private static String successCodeOf(JsonNode root) {
        JsonNode meta = root.get("meta");
        if (meta != null && meta.has("code")) {
            return meta.path("code").asText("");
        }
        return root.path("code").asText("");
    }

    /**
     * 读取响应消息（兼容两套信封）。
     *
     * @param root 响应根节点
     * @return 消息文本
     */
    private static String messageOf(JsonNode root) {
        String msg = root.path("msg").asText("");
        if (!msg.isBlank()) {
            return msg;
        }
        return root.path("meta").path("message").asText("");
    }

    /**
     * 读取响应数据节点（兼容两套信封）。
     *
     * @param root 响应根节点
     * @return data 节点，缺失时返回根节点
     */
    private static JsonNode dataOf(JsonNode root) {
        JsonNode data = root.get("data");
        return data == null || data.isNull() ? root : data;
    }

    /**
     * 查询账户下企业人员（{@code GET /api/v3/device/company/member/queryByPage}）。
     *
     * <p>该接口是 GET + query 参数，与 lapp 域不同；返回为空数组表示公司下暂无人员。</p>
     *
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 人员条目
     */
    public List<DevicePerson> fetchCompanyMembers(int pageNo, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageNo", pageNo);
        params.put("pageSize", pageSize);
        JsonNode root = doRequest("GET", P_MEMBER_PAGE, params);
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : toList(dataOf(root))) {
            result.add(toPerson(node));
        }
        return result;
    }

    /**
     * 新增企业人员（{@code POST /api/v3/device/company/member/add}）。
     *
     * @param person 人员（姓名必填）
     * @return 厂商侧人员标识；平台未回传时返回入参的 externalId 或姓名
     */
    public String addCompanyMember(DevicePerson person) {
        if (person == null || person.name() == null || person.name().isBlank()) {
            throw new IllegalArgumentException("新增企业人员需要姓名");
        }
        Map<String, Object> params = memberBaseParams(person);
        JsonNode root = doRequest("POST", P_MEMBER_ADD, params);
        JsonNode data = dataOf(root);
        String empId = textOf(data, "empId");
        if (!empId.isBlank()) {
            return empId;
        }
        return person.externalId() == null || person.externalId().isBlank()
                ? person.name() : person.externalId();
    }

    /**
     * 更新企业人员（{@code POST /api/v3/device/company/member/update}）。
     *
     * <p>实测更新是部分更新语义：只传 {@code empId} 即可成功；若把原有的
     * {@code empNo} 一起回传，服务端会按「另一个同工号的人员」判断并返回
     * {@code code=60109 "人员工号重复"}。因此本方法故意不发送 {@code empNo}。</p>
     *
     * @param empId  厂商侧人员主键（新增时返回的 {@code data.empId}）
     * @param person 需要变更的字段（name/phone/email 为空表示不改）
     * @return 是否更新成功
     */
    public boolean updateCompanyMember(String empId, DevicePerson person) {
        if (empId == null || empId.isBlank()) {
            throw new IllegalArgumentException("更新企业人员需要 empId");
        }
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("empId", empId);
        putIfPresent(params, "empName", person.name());
        putIfPresent(params, "mobile", person.phone());
        putIfPresent(params, "email", person.email());
        putIfPresent(params, "orgId", person.orgId());
        doRequest("POST", P_MEMBER_UPDATE, params);
        return true;
    }

    /**
     * 删除企业人员（{@code DELETE /api/v3/device/company/member/delete}）。
     *
     * <p>实测该方法只接受 DELETE（GET/POST 均返回 405），参数为 {@code empId}。</p>
     *
     * @param empId 厂商侧人员主键（{@code data.empId}，不是 empNo）
     * @return 是否删除成功
     */
    public boolean deleteCompanyMember(String empId) {
        if (empId == null || empId.isBlank()) {
            throw new IllegalArgumentException("删除企业人员需要 empId");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("empId", empId);
        doRequest("DELETE", P_MEMBER_DELETE, params);
        return true;
    }

    /**
     * 添加设备到当前账号（{@code POST /api/lapp/device/add}）。
     *
     * <p>官方字段：{@code deviceSerial}（必填，含字母的序列号须大写）、
     * {@code validateCode}（必填，设备机身六位大写验证码）。添加成功后
     * 响应只回 {@code {code,msg}}，不返回序列号，故直接返回入参序列号。</p>
     *
     * <p>官方错误码里值得注意的几条：{@code 20013} 已被别人添加、{@code 20017}
     * 已被自己添加、{@code 20010} 验证码错误、{@code 60066} 不能使用出厂默认验证码、
     * {@code 60058}/{@code 60085} 需先确权。</p>
     *
     * @param serial       设备序列号
     * @param validateCode 设备验证码（机身上六位大写字母）
     * @return 已添加的设备序列号
     */
    public String addDevice(String serial, String validateCode) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("萤石添加设备需要设备序列号");
        }
        if (validateCode == null || validateCode.isBlank()) {
            throw new IllegalArgumentException("萤石添加设备需要设备验证码（机身上六位大写字母）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", serial.trim().toUpperCase(Locale.ROOT));
        params.put("validateCode", validateCode.trim().toUpperCase(Locale.ROOT));
        assertOk(doRequest("POST", P_DEVICE_ADD, params));
        return params.get("deviceSerial").toString();
    }

    /**
     * 修改云端设备名称（{@code POST /api/lapp/device/name/update}）。
     *
     * <p>官方约束：{@code deviceName} 长度不大于 50 字节，不能包含特殊字符。
     * 网关下子设备须用长序列号调用（形如 {@code C87654321-C12345678}）。</p>
     *
     * @param serial     设备序列号
     * @param deviceName 新名称
     * @return 是否修改成功
     */
    public boolean renameDevice(String serial, String deviceName) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("萤石修改设备名称需要设备序列号");
        }
        if (deviceName == null || deviceName.isBlank()) {
            throw new IllegalArgumentException("萤石修改设备名称需要设备名称");
        }
        if (deviceName.getBytes(StandardCharsets.UTF_8).length > 50) {
            throw new IllegalArgumentException("萤石设备名称不得超过 50 字节（官方约束）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", serial);
        params.put("deviceName", deviceName);
        assertOk(doRequest("POST", P_DEVICE_NAME_UPDATE, params));
        return true;
    }

    /**
     * 从当前账号删除设备（{@code POST /api/lapp/device/delete}）。
     *
     * <p>官方提示：为保证正常返回，请勿在萤石云 App 开启终端绑定，
     * 否则会报 {@code 20031}。</p>
     *
     * @param serial 设备序列号
     * @return 是否删除成功
     */
    public boolean deleteDevice(String serial) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("萤石删除设备需要设备序列号");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceSerial", serial);
        assertOk(doRequest("POST", P_DEVICE_DELETE, params));
        return true;
    }

    /**
     * 组装企业人员新增/更新的公共字段。
     *
     * <p>实测 {@code /api/v3/device/company/member/add} 的必填项为
     * {@code empName, empAge, empNo, empGender, mobile}，缺任何一项都会返回
     * {@code code=10001 "<字段>不能为空"}；{@code empName} 只允许 32 位以内的
     * 中文、英文、下划线与 {@code .}，含数字会被判格式非法。</p>
     *
     * @param person 人员
     * @return 表单参数
     */
    private static Map<String, Object> memberBaseParams(DevicePerson person) {
        if (person.name() == null || person.name().isBlank()) {
            throw new IllegalArgumentException("企业人员需要姓名（empName）");
        }
        String phone = person.phone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("萤石企业人员需要手机号（mobile，接口必填）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("empName", person.name());
        params.put("empNo", person.externalId() == null ? "" : person.externalId());
        // 萤石接口 empAge / empGender 均为必填，统一模型里没有性别与年龄，
        // 缺省填 0 与 0（未知），由调用方通过扩展字段覆盖时再改。
        params.put("empAge", 0);
        params.put("empGender", 0);
        params.put("mobile", phone);
        putIfPresent(params, "email", person.email());
        putIfPresent(params, "orgId", person.orgId());
        return params;
    }

    /**
     * 萤石人员节点转统一模型。
     *
     * @param node 厂商人员节点
     * @return 人员条目
     */
    private static DevicePerson toPerson(JsonNode node) {
        return new DevicePerson(
                textOf(node, "orgId", "groupId", "deptId"),
                textOf(node, "empNo", "personNo", "employeeNo"),
                textOf(node, "empName", "personName", "name"),
                textOf(node, "title", "position"),
                textOf(node, "mobile", "empPhone", "phone"),
                textOf(node, "email", "empEmail"));
    }

    /**
     * 按候选字段名读取第一个非空文本。
     *
     * @param node   节点
     * @param fields 候选字段名
     * @return 文本，未命中返回空串
     */
    private static String textOf(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull() && !v.asText("").isBlank()) {
                return v.asText();
            }
        }
        return "";
    }

    /**
     * 非空才放入参数。
     *
     * @param params 参数
     * @param key    键
     * @param value  值
     */
    private static void putIfPresent(Map<String, Object> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    /**
     * 确保令牌有效。
     *
     * @param path 当前请求路径（令牌接口自身不再换令牌）
     */
    private void ensureToken(String path) {
        if (P_TOKEN.equals(path) || tokenValid()) {
            return;
        }
        synchronized (this) {
            if (tokenValid()) {
                return;
            }
            JsonNode root = doRequest("POST", P_TOKEN, Map.of(
                    "appKey", nullSafe(info.appKey()),
                    "appSecret", nullSafe(info.appSecret())));
            String token = root.path("data").path("accessToken").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("萤石换取 accessToken 失败: " + root.path("msg").asText());
            }
            cacheToken(token, TTL);
        }
    }

    /**
     * 设备序列号非空校验。
     *
     * @return 序列号
     */
    private String requireSerial() {
        if (deviceSerial == null || deviceSerial.isBlank()) {
            throw new IllegalStateException("萤石会话缺少 deviceSerial");
        }
        return deviceSerial;
    }

    /**
     * 时间范围校验。
     *
     * @param range 时间范围
     */
    private static void requireRange(TimeRange range) {
        if (range == null) {
            throw new IllegalArgumentException("时间范围不能为空");
        }
    }

    /**
     * 协议名转萤石枚举（1 hls / 2 rtmp / 3 flv）。
     *
     * @param protocol 协议
     * @return 萤石协议码
     */
    private static String toEzvizProtocol(String protocol) {
        switch (protocol.toLowerCase()) {
            case "hls":
                return "1";
            case "rtmp":
                return "2";
            default:
                return "3";
        }
    }

    /**
     * 本地时间转毫秒时间戳（萤石告警接口只接受毫秒）。
     *
     * @param time 本地时间
     * @return 毫秒时间戳
     */
    private static long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 本地时间转萤石时间参数。
     *
     * @param time 本地时间
     * @return yyyy-MM-dd HH:mm:ss
     */
    private static String toEzvizTime(LocalDateTime time) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(time);
    }

    /**
     * 毫秒时间戳转本地时间。
     *
     * @param millis 毫秒时间戳
     * @return 本地时间，0 返回 null
     */
    private static LocalDateTime toLocal(long millis) {
        return millis <= 0 ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * form 编码。
     *
     * @param form 表单
     * @return 编码后的字符串
     */
    private static String encode(Map<String, Object> form) {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
        });
        return joiner.toString();
    }

    /**
     * 空值兜底。
     *
     * @param v 原值
     * @return 非空字符串
     */
    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    /**
     * data 节点转列表（萤石部分接口 data 直接是数组）。
     *
     * @param data 数据节点
     * @return 列表
     */
    private static List<JsonNode> toList(JsonNode data) {
        List<JsonNode> result = new ArrayList<>();
        if (data == null || data.isNull()) {
            return result;
        }
        if (data.isArray()) {
            data.forEach(result::add);
        } else if (data.has("list") && data.get("list").isArray()) {
            data.get("list").forEach(result::add);
        } else {
            result.add(data);
        }
        return result;
    }

    /**
     * 萤石状态码转在线布尔。
     *
     * <p>萤石语义：{@code status=1} 在线，{@code status=0} 离线。
     * 注意与基类 {@code bool()} 不同——那里把 {@code 0} 当 false，
     * 而萤石设备列表里 {@code 0} 确实表示离线，通道列表的 {@code status}
     * 需要按本方法判定以保持与设备列表一致。</p>
     *
     * @param node    节点
     * @param field   字段名
     * @return 是否在线
     */
    private static boolean onlineOf(JsonNode node, String field) {
        return "1".equals(node.path(field).asText(""));
    }
}

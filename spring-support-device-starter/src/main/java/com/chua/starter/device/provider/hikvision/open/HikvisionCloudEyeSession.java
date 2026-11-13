// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 海康云眸（Cloud Eye / 外部独立应用开发）OpenAPI 会话。
 *
 * <h3>官方接入（pic.hik-cloud.com 《外部独立应用开发》）</h3>
 * <ul>
 *   <li>固定网关 {@code https://api2.hik-cloud.com}，不允许凭据覆盖</li>
 *   <li>OAuth：{@code POST /oauth/token}，表单 {@code client_id/client_secret/grant_type=client_credentials/scope=app}</li>
 *   <li>业务请求头：{@code Authorization: Bearer <access_token>}，信封 {@code {code,message,data}}，
 *       成功码为 {@code 0} 或 {@code 200}</li>
 * </ul>
 *
 * <h3>接口口径（逐条取自官方文档，不推测）</h3>
 * <ul>
 *   <li>设备：{@code /api/v1/open/basic/devices/{list,get,create,update,delete}}；
 *       注册要 {@code deviceSerial + groupNo + validateCode}（组用<b>组编号</b>），
 *       改名只要 {@code deviceSerial + deviceName}（≤50），删除只收 query {@code deviceSerial}</li>
 *   <li>分组：{@code /api/v1/open/basic/groups/{create,update,delete,get}} 与
 *       {@code actions/listAll}/{@code actions/childrenList}（组编号 {@code groupNo}、父编号 {@code parentNo}）</li>
 *   <li>通道：{@code GET /api/v1/open/basic/channels/list}（{@code pageNo/pageSize} 必填），
 *       {@code POST .../channels/update} 改名（{@code syncLocal} 可选）</li>
 *   <li>抓图：{@code POST /api/v1/open/basic/channels/actions/capture}，
 *       响应 {@code data.picUrl}（2 小时有效），必须二次下载成字节流</li>
 *   <li>标准流：{@code GET /v1/carrier/wing/endpoint/video/getVideoUrl}（{@code protocol} 2-hls / 3-rtmp / 4-flv，
 *       {@code quality} 1-主码流 2-子码流，{@code type} 1-预览 2-本地回放 3-云存回放）；
 *       失效 {@code POST /siot/video/api/standardStreams/actions/disableLiveAddress}。
 *       官方注明 hls 不支持回放，因此回放固定取 flv</li>
 *   <li>录像片段：{@code GET /v1/carrier/communicate/record/daily}（毫秒时间戳，跨度≤1 天，
 *       响应 {@code data.list[].recType/startTime/endTime/channelNo}）</li>
 *   <li>云台：{@code POST /api/v1/open/basic/channels/actions/ptz/start|stop}，
 *       {@code direction} 0~11，{@code speed} 模式 0 时 0~2、模式 1 时 0~7，{@code mode} 缺省 0</li>
 *   <li>预置位：{@code presets/create|update|delete|actions/move|list}，编号字段是 {@code index}</li>
 *   <li>人员：{@code /api/v1/open/basic/persons/{create,update,get}} 与 query {@code persons/delete}；
 *       入参 {@code employeeNo + personName} 必填，{@code employeeNo} 只支持英文数字</li>
 *   <li>门禁：事件 {@code GET /api/v1/open/accessControl/accessEvents/actions/list}（{@code major=5}，
 *       {@code minor} 必填）、控门 {@code POST /api/v1/open/accessControl/remoteControl/actions/open}；
 *       人员列表 {@code GET /api/v1/community/access/persons/actions/getPersons}</li>
 *   <li>事件通道：{@code POST /api/v1/mq/consumer/group1} 建消费者、
 *       {@code POST /api/v1/mq/consumer/messages} 拉消息（30 秒内必须再拉一次）</li>
 * </ul>
 *
 * <h3>官方没有接口、只能显式失败的项</h3>
 * <ul>
 *   <li>门点位列表：云眸文档没有「门列表」接口（控门接口只认 {@code doorId} 数值，默认 1），
 *       {@link #listDoors()} 抛异常</li>
 *   <li>人员删除以外的批量授权、门禁设备增删改：文档未提供，
 *       {@code ACCESS_WRITE} 在能力矩阵中标记为不支持</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionCloudEyeSession extends AbstractOpenApiSession {

    /**
     * 海康云眸默认 baseurl（官方文档固定地址，旧 api 域名会 301）
     */
    private static final String DEFAULT_BASE_URL = "https://api2.hik-cloud.com";

    /**
     * 登录换取令牌接口（官方 OAuth client_credentials）。
     */
    private static final String PATH_LOGIN = "/oauth/token";

    /**
     * 设备列表（GET，groupNo/pageNo/pageSize 必填）。
     */
    private static final String PATH_DEVICE_LIST = "/api/v1/open/basic/devices/list";

    /**
     * 单个设备详情（GET）。
     */
    private static final String PATH_DEVICE_GET = "/api/v1/open/basic/devices/get";

    /**
     * 注册设备（POST JSON，deviceSerial/groupNo/validateCode）。
     */
    private static final String PATH_DEVICE_CREATE = "/api/v1/open/basic/devices/create";

    /**
     * 修改设备（POST JSON，deviceSerial/deviceName）。
     */
    private static final String PATH_DEVICE_UPDATE = "/api/v1/open/basic/devices/update";

    /**
     * 删除设备（POST query，deviceSerial）。
     */
    private static final String PATH_DEVICE_DELETE = "/api/v1/open/basic/devices/delete";

    /**
     * 设备是否在线（GET，queryDeviceStatus）。
     */
    private static final String PATH_DEVICE_STATUS = "/api/v1/ezviz/devices/queryDeviceStatus";

    /**
     * 新增组（POST JSON，groupName/groupNo/parentNo）。
     */
    private static final String PATH_GROUP_CREATE = "/api/v1/open/basic/groups/create";

    /**
     * 更新组（POST JSON，groupName/groupNo）。
     */
    private static final String PATH_GROUP_UPDATE = "/api/v1/open/basic/groups/update";

    /**
     * 删除组（POST query，groupNo）。
     */
    private static final String PATH_GROUP_DELETE = "/api/v1/open/basic/groups/delete";

    /**
     * 查询所有组织（GET）。
     */
    private static final String PATH_GROUP_LIST_ALL = "/api/v1/open/basic/groups/actions/listAll";

    /**
     * 通道列表（GET，deviceSerial/pageNo/pageSize 必填）。
     */
    private static final String PATH_CHANNEL_LIST = "/api/v1/open/basic/channels/list";

    /**
     * 修改通道名称（POST JSON）。
     */
    private static final String PATH_CHANNEL_UPDATE = "/api/v1/open/basic/channels/update";

    /**
     * 同步设备下通道（POST JSON，deviceSerial）。
     */
    private static final String PATH_CHANNEL_SYNC = "/api/v1/open/basic/channels/actions/sync";

    /**
     * 远程抓图（POST JSON，deviceSerial/channelNo/quality?）。
     */
    private static final String PATH_CAPTURE = "/api/v1/open/basic/channels/actions/capture";

    /**
     * 标准流播放地址（GET，预览/回放共用，type 区分）。
     */
    private static final String PATH_STANDARD_STREAM = "/v1/carrier/wing/endpoint/video/getVideoUrl";

    /**
     * 失效标准流播放地址（POST JSON）。
     */
    private static final String PATH_STANDARD_STREAM_DISABLE = "/siot/video/api/standardStreams/actions/disableLiveAddress";

    /**
     * 每天录像片段（GET，毫秒时间戳）。
     */
    private static final String PATH_RECORD_DAILY = "/v1/carrier/communicate/record/daily";

    /**
     * 云台开始（POST JSON）。
     */
    private static final String PATH_PTZ_START = "/api/v1/open/basic/channels/actions/ptz/start";

    /**
     * 云台停止（POST JSON）。
     */
    private static final String PATH_PTZ_STOP = "/api/v1/open/basic/channels/actions/ptz/stop";

    /**
     * 新增预置点（POST JSON）。
     */
    private static final String PATH_PRESET_CREATE = "/api/v1/open/basic/presets/create";

    /**
     * 更新预置点（POST JSON）。
     */
    private static final String PATH_PRESET_UPDATE = "/api/v1/open/basic/presets/update";

    /**
     * 删除预置点（POST JSON）。
     */
    private static final String PATH_PRESET_DELETE = "/api/v1/open/basic/presets/delete";

    /**
     * 调用预置点（POST JSON）。
     */
    private static final String PATH_PRESET_MOVE = "/api/v1/open/basic/presets/actions/move";

    /**
     * 预置点列表（GET，deviceSerial/channelNo）。
     */
    private static final String PATH_PRESET_LIST = "/api/v1/open/basic/presets/list";

    /**
     * 添加人员（POST JSON）。
     */
    private static final String PATH_PERSON_CREATE = "/api/v1/open/basic/persons/create";

    /**
     * 更新人员（POST JSON）。
     */
    private static final String PATH_PERSON_UPDATE = "/api/v1/open/basic/persons/update";

    /**
     * 删除人员（POST query，employeeNo）。
     */
    private static final String PATH_PERSON_DELETE = "/api/v1/open/basic/persons/delete";

    /** 人员人脸更新（官方 basic/faces/update） */
    private static final String PATH_FACE_UPDATE = "/api/v1/open/basic/faces/update";

    /** 人员人脸删除（官方 basic/faces/delete） */
    private static final String PATH_FACE_DELETE = "/api/v1/open/basic/faces/delete";

    /**
     * 人员列表（GET，pageNo/pageSize）。
     */
    private static final String PATH_PERSON_LIST = "/api/v1/community/access/persons/actions/getPersons";

    /**
     * 门禁事件查询（GET，major/minor 必填）。
     */
    private static final String PATH_ACCESS_EVENTS = "/api/v1/open/accessControl/accessEvents/actions/list";

    /**
     * 远程控门（POST JSON，deviceSerial/cmd/doorId）。
     */
    private static final String PATH_DOOR_CONTROL = "/api/v1/open/accessControl/remoteControl/actions/open";

    /**
     * 创建消息消费者（POST）。
     */
    private static final String PATH_MQ_CONSUMER = "/api/v1/mq/consumer/group1";

    /**
     * 拉取消息（POST form，consumerId/autoCommit）。
     */
    private static final String PATH_MQ_MESSAGES = "/api/v1/mq/consumer/messages";

    /**
     * 提交偏移量（POST form，consumerId）。
     */
    private static final String PATH_MQ_OFFSETS = "/api/v1/mq/consumer/offsets";

    /**
     * 设备在线状态取值。
     */
    private static final int ONLINE = 1;

    /**
     * 通道未上报状态（官方 channelStatus=-1）。
     */
    private static final int CHANNEL_UNREPORTED = -1;

    /**
     * 门禁事件主类型：5 = 门禁事件。
     */
    private static final int MAJOR_ACCESS = 5;

    /**
     * 门禁次事件类型：1 刷卡开锁 / 21 门锁打开 / 75 人脸开锁 / 1024 中心平台开锁。
     */
    private static final List<Integer> MINOR_OPEN = List.of(1, 21, 75, 1024);

    /**
     * 门禁次事件类型：6 未分配权限 / 7 无效时段 / 8 卡号过期 / 9 无此卡号 / 76 人脸认证失败。
     */
    private static final List<Integer> MINOR_DENY = List.of(6, 7, 8, 9, 76);

    /**
     * 默认通道号（云眸单通道设备用 1，接口不允许省略）。
     */
    private static final int DEFAULT_CHANNEL = 1;

    /**
     * 分页大小上限（官方 pageSize 1-999）。
     */
    private static final int PAGE_SIZE = 100;

    /**
     * 最大翻页数（防御平台不返回 total 时死循环）。
     */
    private static final int MAX_PAGES = 200;

    /**
     * 抓图下载重试次数。
     */
    private static final int DOWNLOAD_RETRY = 2;

    /**
     * 标准流协议：2-hls。
     */
    private static final String PROTOCOL_HLS = "2";

    /**
     * 标准流协议：3-rtmp。
     */
    private static final String PROTOCOL_RTMP = "3";

    /**
     * 标准流协议：4-flv。
     */
    private static final String PROTOCOL_FLV = "4";

    /**
     * 标准流类型：1-预览。
     */
    private static final String STREAM_TYPE_PREVIEW = "1";

    /**
     * 标准流类型：2-本地录像回放。
     */
    private static final String STREAM_TYPE_LOCAL_PLAYBACK = "2";

    /**
     * 码流：主码流。
     */
    private static final String QUALITY_MAIN = "1";

    /**
     * 码流：子码流。
     */
    private static final String QUALITY_SUB = "2";

    /**
     * 云台方向：0-上，1-下，2-左，3-右，8-放大，9-缩小，10-近焦距，11-远焦距。
     */
    private static final Map<PtzCommand, Integer> PTZ_DIRECTIONS = Map.of(
            PtzCommand.UP, 0,
            PtzCommand.DOWN, 1,
            PtzCommand.LEFT, 2,
            PtzCommand.RIGHT, 3,
            PtzCommand.ZOOM_IN, 8,
            PtzCommand.ZOOM_OUT, 9,
            PtzCommand.FOCUS_IN, 10,
            PtzCommand.FOCUS_OUT, 11,
            PtzCommand.IRIS_IN, 8,
            PtzCommand.IRIS_OUT, 9);

    /**
     * 云台转动时长（官方要求开始后必须显式停止，否则不能做别的操作）。
     */
    private static final long PTZ_HOLD_MILLIS = 500L;

    /**
     * 事件时间格式（门禁事件返回 2020-12-01T08:00:00+08:00）。
     */
    private static final DateTimeFormatter ACCESS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 点号时间格式（设备/通道接口不接受毫秒）。
     */
    private static final DateTimeFormatter PLAIN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 图片下载客户端（抓图落在萤石对象存储，不在云眸网关域）。
     */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 消息通道消费者 ID（官方要求同一 consumerId 消费与提交偏移）。
     */
    private volatile String consumerId;

    /**
     * 最近一次云台方向（官方 stop 接口虽把 direction 标为非必填，回传当前方向更稳妥）。
     */
    private volatile Integer lastPtzDirection;

    /**
     * 创建 hikvisioncloudeye会话 实例
     * @param info 信息
     */
    public HikvisionCloudEyeSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        login();
    }

    /**
     * 登录换取 会话标识 / 令牌
     */
    private synchronized void login() {
        if (tokenValid()) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", info.appKey());
        form.put("client_secret", info.appSecret());
        form.put("grant_type", "client_credentials");
        form.put("scope", "app");
        try {
            String body = formEncode(form);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_BASE_URL + PATH_LOGIN))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = MAPPER.readTree(response.body() == null ? "{}" : response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + root);
            }
            String code = root.path("code").asText("");
            if (!code.isEmpty() && !"0".equals(code) && !"200".equals(code)) {
                throw new IllegalStateException("code=" + code + ", message="
                        + root.path("message").asText(root.path("msg").asText("")));
            }
            String token = root.path("access_token").asText(null);
            long expires = root.path("expires_in").asLong(604_800L);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("OAuth 响应未返回 access_token");
            }
            cacheToken(token, expires);
            log.info("海康云眸登录成功: deviceSerial={}", info.deviceSerial());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("海康云眸登录被中断", ex);
        } catch (Exception e) {
            throw new IllegalStateException("海康云眸登录失败: " + e.getMessage(), e);
        }
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
        if (!PATH_LOGIN.equals(path)) {
            login();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (!PATH_LOGIN.equals(path) && accessToken != null) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return execute(method, path, params, headers);
    }

    /**
     * 校验云眸响应信封（官方成功码为 0 或 200）。
     *
     * @param response 响应根节点
     * @return data 节点；响应没有 data 时返回根节点
     */
    private JsonNode assertCloudEyeOk(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("云眸响应为空");
        }
        String code = response.path("code").asText("");
        if (!code.isEmpty() && !"0".equals(code) && !"200".equals(code)) {
            throw new IllegalStateException("云眸调用失败: code=" + code + ", message="
                    + response.path("message").asText(response.path("msg").asText("")));
        }
        JsonNode data = response.get("data");
        return data == null || data.isNull() ? response : data;
    }

    /**
     * 发 JSON POST 并校验信封。
     *
     * @param path 接口路径
     * @param body 包体
     * @return data 节点
     */
    private JsonNode postJson(String path, Object body) {
        login();
        Map<String, String> headers = new LinkedHashMap<>();
        if (accessToken != null) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return assertCloudEyeOk(executeJson("POST", path, body, headers));
    }

    /**
     * 发「POST + query」请求并校验信封（云眸删除类接口只收 query，不认 JSON 包体）。
     *
     * @param path  接口路径
     * @param key   查询参数名
     * @param value 查询参数值
     * @return data 节点
     */
    private JsonNode postQuery(String path, String key, String value) {
        String query = URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
        return postJson(path + "?" + query, Collections.emptyMap());
    }

    /**
     * 发 GET 并校验信封。
     *
     * @param path  接口路径
     * @param query 查询参数
     * @return data 节点
     */
    private JsonNode getData(String path, Map<String, Object> query) {
        return assertCloudEyeOk(get(path, query));
    }

    /**
     * 设备序列号（平台接口一律以序列号定位设备）。
     *
     * @return 序列号
     */
    private String requireSerial() {
        return deviceSerialOf(info.deviceSerial());
    }

    /**
     * 取序列号，缺失时报错而不是静默发空串。
     *
     * @param serial 序列号
     * @return 序列号
     */
    private static String deviceSerialOf(String serial) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalStateException("云眸接口要求设备序列号，请在连接信息中配置 deviceSerial");
        }
        return serial;
    }

    // ------------------------------------------------------------------ 设备目录

    /**
     * 拉取租户下设备目录。
     *
     * <p>官方 {@code devices/list} 要求 {@code groupNo} 必填，而连接信息只有设备序列号，
     * 因此按「组编号」逐组扫描（{@code groups/actions/listAll} 拿全部组编号）。</p>
     *
     * @return 设备条目
     */
    public List<VendorDevice> fetchDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (String groupNo : groupNumbers()) {
            for (JsonNode node : pageOf(PATH_DEVICE_LIST, Map.of("groupNo", groupNo))) {
                String serial = node.path("deviceSerial").asText("");
                if (serial.isEmpty()) {
                    continue;
                }
                result.add(new VendorDevice(
                        serial,
                        node.path("deviceName").asText(serial),
                        node.path("deviceModel").asText(null),
                        "camera",
                        serial,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        node.path("deviceStatus").asInt(ONLINE) == ONLINE));
            }
        }
        return result;
    }

    /**
     * 单个设备详情（官方 {@code devices/get}）。
     *
     * @param serial 设备序列号
     * @return 设备条目
     */
    public VendorDevice fetchDevice(String serial) {
        String target = deviceSerialOf(serial);
        JsonNode node = getData(PATH_DEVICE_GET, Map.of("deviceSerial", target));
        return new VendorDevice(
                target,
                node.path("deviceName").asText(target),
                node.path("deviceModel").asText(null),
                "camera",
                target,
                null,
                null,
                null,
                null,
                null,
                null,
                node.path("deviceStatus").asInt(ONLINE) == ONLINE);
    }

    /**
     * 设备在线状态（官方 {@code queryDeviceStatus} 只回能力状态，不含在线位，
     * 因此改用 {@code devices/get} 的 {@code deviceStatus}）。
     *
     * @param serial 设备序列号
     * @return 在线
     */
    public boolean onlineOf(String serial) {
        return fetchDevice(serial).online() != null && Boolean.TRUE.equals(fetchDevice(serial).online());
    }

    /**
     * 设备能力状态（官方 {@code /api/v1/ezviz/devices/queryDeviceStatus}）。
     *
     * @param serial 设备序列号
     * @return data 节点（privacyStatus / pirStatus / diskState / cloudStatus 等）
     */
    public JsonNode queryDeviceStatus(String serial) {
        return getData(PATH_DEVICE_STATUS, Map.of("deviceSerial", deviceSerialOf(serial)));
    }

    /**
     * 注册设备（官方 {@code devices/create}）。
     *
     * <p>官方要求 {@code deviceSerial + groupNo + validateCode}：组用<b>组编号</b>
     * （不是组 ID），验证码是设备机身上的六位大写字母。云眸没有按序列号反查验证码的接口，
     * 因此验证码与组编号只能由台账侧提供（连接信息扩展字段 {@code validateCode} / {@code groupNo}）。</p>
     *
     * @param device       设备条目
     * @param groupNo      组编号；为空时取凭据扩展字段
     * @param validateCode 设备验证码；为空时取凭据扩展字段
     * @return 平台设备 ID
     */
    public String addDevice(VendorDevice device, String groupNo, String validateCode) {
        if (device == null) {
            throw new IllegalArgumentException("设备不能为空");
        }
        String serial = device.serial() != null && !device.serial().isBlank()
                ? device.serial() : device.externalId();
        serial = deviceSerialOf(serial);
        String group = firstNonBlank(groupNo, extraOf("groupNo"));
        if (group == null) {
            throw new IllegalStateException(
                    "云眸注册设备要求组编号 groupNo（官方 devices/create 必填），请在凭据扩展参数或调用参数中提供");
        }
        String code = firstNonBlank(validateCode, extraOf("validateCode"));
        if (code == null) {
            throw new IllegalStateException(
                    "云眸注册设备要求设备验证码 validateCode（机身上的六位大写字母），平台没有按序列号反查验证码的接口");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", serial);
        body.put("groupNo", group);
        body.put("validateCode", code.toUpperCase(Locale.ROOT));
        JsonNode data = postJson(PATH_DEVICE_CREATE, body);
        String deviceId = data.path("deviceId").asText("");
        return deviceId.isEmpty() ? serial : deviceId;
    }

    /**
     * 修改设备名称（官方 {@code devices/update}，只接受序列号与新名称）。
     *
     * @param serial     设备序列号
     * @param deviceName 新名称（≤50）
     * @return 是否成功
     */
    public boolean renameDevice(String serial, String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new IllegalArgumentException("设备名称不能为空（云眸 devices/update 必填）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", deviceSerialOf(serial));
        body.put("deviceName", deviceName);
        postJson(PATH_DEVICE_UPDATE, body);
        return true;
    }

    /**
     * 删除设备（官方 {@code devices/delete}，只收 query {@code deviceSerial}）。
     *
     * @param serial 设备序列号
     * @return 是否成功
     */
    public boolean removeDevice(String serial) {
        postQuery(PATH_DEVICE_DELETE, "deviceSerial", deviceSerialOf(serial));
        return true;
    }

    // ------------------------------------------------------------------ 组织（设备分组）

    /**
     * 组织树（官方 {@code groups/actions/listAll}，所有节点一次返回，用 {@code parentId} 连边）。
     *
     * @return 组织节点
     */
    public List<OrganizationNode> fetchOrganizations() {
        JsonNode data = getData(PATH_GROUP_LIST_ALL, Collections.emptyMap());
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : asList(data)) {
            String groupNo = node.path("groupNo").asText("");
            if (groupNo.isEmpty()) {
                continue;
            }
            result.add(new OrganizationNode(
                    groupNo,
                    node.path("groupName").asText(groupNo),
                    blankToNull(node.path("parentId").asText(""))));
        }
        return result;
    }

    /**
     * 新增组织（官方 {@code groups/create}，{@code groupNo} 支持自定义，层级最多 5 级）。
     *
     * @param node 组织节点（{@code parentId} 作为 {@code parentNo}）
     * @return 组编号
     */
    public String addOrganization(OrganizationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("组织节点不能为空");
        }
        String groupNo = firstNonBlank(node.orgId(), randomGroupNo());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupName", node.orgName());
        body.put("groupNo", groupNo);
        if (node.parentId() != null && !node.parentId().isBlank()) {
            body.put("parentNo", node.parentId());
        }
        postJson(PATH_GROUP_CREATE, body);
        return groupNo;
    }

    /**
     * 修改组织名称（官方 {@code groups/update} 只支持改名）。
     *
     * @param node 组织节点（{@code orgId} 即组编号）
     * @return 是否成功
     */
    public boolean updateOrganization(OrganizationNode node) {
        if (node == null || node.orgId() == null || node.orgId().isBlank()) {
            throw new IllegalArgumentException("云眸更新组织要求组编号 groupNo");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupNo", node.orgId());
        body.put("groupName", node.orgName());
        postJson(PATH_GROUP_UPDATE, body);
        return true;
    }

    /**
     * 删除组织（官方 {@code groups/delete}；含下级节点或设备的组织不可删除）。
     *
     * @param groupNo 组编号
     * @return 是否成功
     */
    public boolean removeOrganization(String groupNo) {
        if (groupNo == null || groupNo.isBlank()) {
            throw new IllegalArgumentException("云眸删除组织要求组编号 groupNo");
        }
        postQuery(PATH_GROUP_DELETE, "groupNo", groupNo);
        return true;
    }

    /**
     * 全部组编号（供设备/通道巡检用）。
     *
     * @return 组编号
     */
    private List<String> groupNumbers() {
        List<String> result = new ArrayList<>();
        for (OrganizationNode node : fetchOrganizations()) {
            result.add(node.orgId());
        }
        return result;
    }

    // ------------------------------------------------------------------ 通道

    /**
     * 设备下的通道（官方 {@code channels/list}，{@code pageNo/pageSize} 必填）。
     *
     * @return 通道列表
     */
    @Override
    public List<DeviceChannel> listChannels() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("deviceSerial", requireSerial());
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : pageOf(PATH_CHANNEL_LIST, query)) {
            Integer channelNo = node.hasNonNull("channelNo") ? node.get("channelNo").asInt() : null;
            int status = node.path("channelStatus").asInt(CHANNEL_UNREPORTED);
            String ipcSerial = blankToNull(node.path("ipcSerial").asText(""));
            String model = blankToNull(node.path("childDeviceModel").asText(""));
            result.add(new DeviceChannel(
                    firstNonBlank(node.path("channelId").asText(""),
                            requireSerial() + "#" + (channelNo == null ? 0 : channelNo)),
                    channelNo,
                    node.path("channelName").asText(""),
                    ipcSerial == null ? requireSerial() : ipcSerial,
                    status == CHANNEL_UNREPORTED ? null : status == 1,
                    model,
                    2));
        }
        return result;
    }

    /**
     * 同步设备下通道（官方 {@code channels/actions/sync}，新注册设备后异步拉通道可能失败）。
     *
     * @param serial 设备序列号
     * @return 是否成功
     */
    public boolean syncChannels(String serial) {
        postJson(PATH_CHANNEL_SYNC, Map.of("deviceSerial", deviceSerialOf(serial)));
        return true;
    }

    /**
     * 修改通道名称（官方 {@code channels/update}）。
     *
     * @param channelNo   通道号
     * @param channelName 新名称（不同步本地时 ≤50，同步到设备时 ≤32）
     * @param syncLocal   是否同步到设备本地（需设备在线）
     * @return 是否成功
     */
    public boolean renameChannel(int channelNo, String channelName, boolean syncLocal) {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("通道名称不能为空（云眸 channels/update 必填）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channelNo);
        body.put("channelName", channelName);
        body.put("syncLocal", syncLocal ? 1 : 0);
        postJson(PATH_CHANNEL_UPDATE, body);
        return true;
    }

    // ------------------------------------------------------------------ 抓图

    /**
     * 远程抓图（官方 {@code channels/actions/capture}）。
     *
     * <p>平台只返回 {@code data.picUrl}（图片有效期 2 小时），必须二次下载；
     * 官方建议同一通道两次抓图间隔不低于 5 秒。</p>
     *
     * @param channel 通道号
     * @return JPEG 图片字节流
     */
    @Override
    public InputStream capture(int channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel <= 0 ? DEFAULT_CHANNEL : channel);
        JsonNode data = postJson(PATH_CAPTURE, body);
        String url = data.path("picUrl").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("云眸抓图未返回图片地址: " + data);
        }
        return download(url, "云眸");
    }

    /**
     * 下载图片（抓图落在萤石对象存储，不在云眸网关域下，必须单独 GET）。
     *
     * @param url     图片地址
     * @param tag     平台名（用于报错）
     * @return 图片字节流
     */
    private static InputStream download(String url, String tag) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= DOWNLOAD_RETRY; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = DOWNLOADER.send(request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = response.body();
                if (response.statusCode() == 200 && body != null && body.length > 0) {
                    return new ByteArrayInputStream(body);
                }
                last = new IllegalStateException(tag + "抓图下载失败: HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(tag + "抓图下载被中断", ex);
            } catch (Exception ex) {
                last = new IllegalStateException(tag + "抓图下载异常: " + ex.getMessage(), ex);
            }
        }
        throw last == null ? new IllegalStateException(tag + "抓图下载失败") : last;
    }

    // ------------------------------------------------------------------ 取流

    /**
     * 获取视频取流 URL（主码流预览）。
     * @param channel 通道
     * @return 获取视频url的结果
     */
    public JsonNode getVideoUrl(int channel) {
        return getVideoUrl(channel, "main");
    }

    /**
     * 获取标准流播放地址（官方 {@code getVideoUrl}，主/子码流）。
     *
     * @param channel    通道
     * @param streamType 码流：main / sub
     * @return data 节点（url / id / expireTime）
     */
    public JsonNode getVideoUrl(int channel, String streamType) {
        return standardStream(channel, PROTOCOL_HLS, streamType, STREAM_TYPE_PREVIEW, null);
    }

    /**
     * 标准流取地址（官方 {@code /v1/carrier/wing/endpoint/video/getVideoUrl}）。
     *
     * @param channel    通道号
     * @param protocol   协议编码：2-hls / 3-rtmp / 4-flv
     * @param streamType main 主码流 / sub 子码流
     * @param type       1-预览 / 2-本地录像回放 / 3-云存储录像回放
     * @param range      回放时间段（type 为 1 时可空）
     * @return data 节点
     */
    private JsonNode standardStream(int channel, String protocol, String streamType,
                                    String type, TimeRange range) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("deviceSerial", requireSerial());
        query.put("channelNo", channel <= 0 ? DEFAULT_CHANNEL : channel);
        query.put("protocol", protocol);
        query.put("quality", "sub".equalsIgnoreCase(streamType) ? QUALITY_SUB : QUALITY_MAIN);
        query.put("type", type);
        query.put("expireTime", 3600);
        if (range != null) {
            query.put("startTime", range.start().format(PLAIN_TIME));
            query.put("stopTime", range.end().format(PLAIN_TIME));
        }
        return getData(PATH_STANDARD_STREAM, query);
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        return urlOf(standardStream(channel, protocolCode(protocol), null, STREAM_TYPE_PREVIEW, null));
    }

    @Override
    /**
     * 获取指定码流的liveurl
     *
     * @param channel    通道
     * @param protocol   协议
     * @param streamType 码流
     * @return 拉流地址
     */
    public String getLiveUrl(int channel, String protocol, String streamType) {
        return urlOf(standardStream(channel, protocolCode(protocol), streamType, STREAM_TYPE_PREVIEW, null));
    }

    /**
     * 停止标准流（官方 {@code disableLiveAddress}，需要用取地址时返回的 {@code id}）。
     *
     * @param channel 通道
     * @param urlId   播放地址 ID
     * @return 是否成功
     */
    public boolean stopLiveUrl(int channel, String urlId) {
        if (urlId == null || urlId.isBlank()) {
            throw new IllegalArgumentException("失效标准流要求播放地址 ID（取地址接口返回的 id）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel <= 0 ? DEFAULT_CHANNEL : channel);
        body.put("urlId", urlId);
        postJson(PATH_STANDARD_STREAM_DISABLE, body);
        return true;
    }

    /**
     * 取流协议转官方编码（2-hls / 3-rtmp / 4-flv）。
     *
     * @param protocol 协议名
     * @return 官方编码
     */
    private static String protocolCode(String protocol) {
        String p = protocol == null ? "" : protocol.toLowerCase(Locale.ROOT);
        if (p.contains("rtmp")) {
            return PROTOCOL_RTMP;
        }
        if (p.contains("flv")) {
            return PROTOCOL_FLV;
        }
        return PROTOCOL_HLS;
    }

    /**
     * 从标准流响应里取地址。
     *
     * @param data data 节点
     * @return 地址
     */
    private static String urlOf(JsonNode data) {
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("云眸标准流未返回播放地址: " + data);
        }
        return url;
    }

    @Override
    /**
     * 获取playbackurl
     * @param channel 整数
     * @param range 参数
     * @return 返回值
    */
    public String getPlaybackUrl(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("云眸回放要求起止时间");
        }
        // 官方注明 hls 不支持回放，回放走 flv + 本地录像
        return urlOf(standardStream(channel, PROTOCOL_FLV, null, STREAM_TYPE_LOCAL_PLAYBACK, range));
    }

    // ------------------------------------------------------------------ 录像

    @Override
    /**
     * 录像片段（官方 {@code /v1/carrier/communicate/record/daily}，毫秒时间戳，跨度≤1 天）。
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 录像片段
     */
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("云眸录像查询要求起止时间");
        }
        long start = range.start().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = range.end().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (end - start > Duration.ofDays(1).toMillis()) {
            throw new IllegalArgumentException("云眸录像查询跨度不能超过 1 天（官方 20002048）");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("deviceSerial", requireSerial());
        query.put("channelNo", channel <= 0 ? DEFAULT_CHANNEL : channel);
        query.put("startTime", start);
        query.put("endTime", end);
        JsonNode data = getData(PATH_RECORD_DAILY, query);
        List<RecordFile> result = new ArrayList<>();
        for (JsonNode node : asList(data.path("list"))) {
            long begin = node.path("startTime").asLong(0L);
            long finish = node.path("endTime").asLong(0L);
            if (begin <= 0L || finish <= 0L) {
                continue;
            }
            result.add(new RecordFile(
                    requireSerial() + "#" + channel + "#" + begin,
                    requireSerial() + "#" + channel,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), ZoneId.systemDefault()),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(finish), ZoneId.systemDefault()),
                    0L));
        }
        return result;
    }

    // ------------------------------------------------------------------ 云台 / 预置位

    @Override
    /**
     * 云台控制（官方 {@code ptz/start} 后必须 {@code ptz/stop}）。
     *
     * @param cmd   动作
     * @param speed 速度（0~1 映射成官方模式 0 的 0 慢 / 1 适中 / 2 快）
     */
    public void ptzControl(PtzCommand cmd, double speed) {
        if (cmd == null) {
            return;
        }
        if (cmd == PtzCommand.STOP) {
            stopPtz(lastPtzDirection == null ? 0 : lastPtzDirection);
            return;
        }
        Integer direction = PTZ_DIRECTIONS.get(cmd);
        if (direction == null) {
            throw new UnsupportedOperationException("云眸云台不支持该动作: " + cmd);
        }
        this.lastPtzDirection = direction;
        postJson(PATH_PTZ_START, ptzBody(direction, speed));
        try {
            Thread.sleep(PTZ_HOLD_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        stopPtz(direction);
    }

    /**
     * 停止云台（官方 {@code ptz/stop}，{@code direction} 非必填但回传正在转动的方向最稳妥）。
     *
     * @param direction 方向编码
     */
    private void stopPtz(int direction) {
        Map<String, Object> stop = new LinkedHashMap<>();
        stop.put("deviceSerial", requireSerial());
        stop.put("channelNo", DEFAULT_CHANNEL);
        stop.put("direction", direction);
        postJson(PATH_PTZ_STOP, stop);
    }

    /**
     * 云台包体（官方模式 0 时 speed 取 0 慢 / 1 适中 / 2 快）。
     *
     * @param direction 方向编码
     * @param speed     速度
     * @return 包体
     */
    private Map<String, Object> ptzBody(Integer direction, double speed) {
        int gear = speed <= 0.34 ? 0 : (speed <= 0.67 ? 1 : 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", DEFAULT_CHANNEL);
        body.put("direction", direction);
        body.put("speed", gear);
        body.put("mode", 0);
        return body;
    }

    /**
     * 云台停止（显式下发一次停止，官方要求）。
     *
     * @param cmd 最近一次动作
     */
    public void stopPtz(PtzCommand cmd) {
        Integer direction = cmd == null ? null : PTZ_DIRECTIONS.get(cmd);
        stopPtz(direction == null ? 0 : direction);
    }

    @Override
    /**
     * 新增预置点（官方 {@code presets/create}，编号由平台分配，见响应 {@code data.index}）。
     *
     * @param presetId 调用方期望编号（云眸由平台分配，忽略）
     * @param name     预置点名称（≤50，必填）
     */
    public void setPreset(int presetId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", DEFAULT_CHANNEL);
        body.put("name", name == null || name.isBlank() ? "preset" + presetId : name);
        postJson(PATH_PRESET_CREATE, body);
    }

    @Override
    /**
     * 调用预置点（官方 {@code presets/actions/move}）。
     *
     * @param presetId 预置点序号
     */
    public void callPreset(int presetId) {
        postJson(PATH_PRESET_MOVE, presetBody(presetId));
    }

    @Override
    /**
     * 删除预置点（官方 {@code presets/delete}）。
     *
     * @param presetId 预置点序号
     */
    public void deletePreset(int presetId) {
        postJson(PATH_PRESET_DELETE, presetBody(presetId));
    }

    /**
     * 预置点列表（官方 {@code presets/list}）。
     *
     * @return 预置点
     */
    @Override
    public List<PresetInfo> listPresets() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("deviceSerial", requireSerial());
        query.put("channelNo", DEFAULT_CHANNEL);
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode node : asList(getData(PATH_PRESET_LIST, query))) {
            result.add(new PresetInfo(node.path("index").asInt(0),
                    node.path("name").asText("")));
        }
        return result;
    }

    /**
     * 预置点操作包体（官方字段是 {@code index}，不是 presetId）。
     *
     * @param presetId 预置点序号
     * @return 包体
     */
    private Map<String, Object> presetBody(int presetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", DEFAULT_CHANNEL);
        body.put("index", presetId);
        return body;
    }

    // ------------------------------------------------------------------ 人员

    /**
     * 人员列表（官方 {@code community/access/persons/actions/getPersons}，全租户分页）。
     *
     * <p>云眸「基础服务-人员管理」只提供增删改与单个查询（{@code persons/get} 必填 employeeNo），
     * 没有分页列表；分页列表在历史接口的批量管理下，字段 {@code personId/employeeNo/personName}。</p>
     *
     * @return 人员条目
     */
    public List<DevicePerson> fetchPersons() {
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : pageOf(PATH_PERSON_LIST, Collections.emptyMap())) {
            String employeeNo = node.path("employeeNo").asText("");
            if (employeeNo.isEmpty()) {
                continue;
            }
            result.add(new DevicePerson(
                    null,
                    employeeNo,
                    node.path("personName").asText(employeeNo),
                    node.path("personType").asText(null),
                    null,
                    null));
        }
        return result;
    }

    /**
     * 单个人员详情（官方 {@code persons/get}）。
     *
     * @param employeeNo 人员编号
     * @return 人员条目
     */
    public DevicePerson fetchPerson(String employeeNo) {
        JsonNode node = getData("/api/v1/open/basic/persons/get", Map.of("employeeNo", requireEmployeeNo(employeeNo)));
        return new DevicePerson(null,
                employeeNo,
                node.path("personName").asText(employeeNo),
                node.path("personType").asText(null),
                node.path("personPhone").asText(null),
                null);
    }

    /**
     * 添加人员（官方 {@code persons/create}，{@code employeeNo} 只支持英文数字）。
     *
     * @param person 人员
     * @return 人员编号
     */
    public String addPerson(DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        String employeeNo = requireEmployeeNo(person.externalId());
        if (person.name() == null || person.name().isBlank()) {
            throw new IllegalArgumentException("云眸添加人员要求姓名 personName");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employeeNo", employeeNo);
        body.put("personName", person.name());
        if (person.phone() != null && !person.phone().isBlank()) {
            body.put("personPhone", person.phone());
        }
        postJson(PATH_PERSON_CREATE, body);
        return employeeNo;
    }

    /**
     * 更新人员（官方 {@code persons/update}，非必填字段不传不覆盖）。
     *
     * @param person 人员
     * @return 是否成功
     */
    public boolean updatePerson(DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employeeNo", requireEmployeeNo(person.externalId()));
        body.put("personName", person.name());
        if (person.phone() != null && !person.phone().isBlank()) {
            body.put("personPhone", person.phone());
        }
        postJson(PATH_PERSON_UPDATE, body);
        return true;
    }

    /**
     * 人员删除（官方 {@code persons/delete}，query {@code employeeNo}）。
     *
     * @param employeeNo 人员编号
     * @return 是否成功
     */
    public boolean removePerson(String employeeNo) {
        postQuery(PATH_PERSON_DELETE, "employeeNo", requireEmployeeNo(employeeNo));
        return true;
    }

    /**
     * 录入/更新人脸（官方 {@code basic/faces/update}）。
     *
     * @param employeeNo      人员编号
     * @param faceImageBase64 人脸图片 base64（不含 data:image 前缀）
     */
    public void updateFace(String employeeNo, String faceImageBase64) {
        if (faceImageBase64 == null || faceImageBase64.isBlank()) {
            throw new IllegalArgumentException("人脸图片 faceImageBase64 不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employeeNo", requireEmployeeNo(employeeNo));
        body.put("faceImageBase64", faceImageBase64);
        body.put("verifyImage", true);
        postJson(PATH_FACE_UPDATE, body);
    }

    /**
     * 删除人脸（官方 {@code basic/faces/delete}，query {@code employeeNo}）。
     *
     * @param employeeNo 人员编号
     */
    public void deleteFace(String employeeNo) {
        postQuery(PATH_FACE_DELETE, "employeeNo", requireEmployeeNo(employeeNo));
    }

    /**
     * 人员编号校验（官方：同一租户不重复，只支持英文与数字）。
     *
     * @param employeeNo 编号
     * @return 编号
     */
    private static String requireEmployeeNo(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw new IllegalArgumentException("云眸人员接口要求人员编号 employeeNo");
        }
        if (!employeeNo.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("云眸人员编号只支持英文与数字: " + employeeNo);
        }
        return employeeNo;
    }

    // ------------------------------------------------------------------ 门禁

    @Override
    /**
     * 门点位：云眸文档没有「门列表」接口（控门只认 doorId 数值，默认 1）。
     *
     * @return 不会返回
     */
    public List<DeviceDoorInfo> listDoors() {
        throw new UnsupportedOperationException(
                "云眸开放平台未提供门点位列表接口（remoteControl 只接受 doorId 数值，缺省 1）");
    }

    @Override
    /**
     * 远程控门（官方 {@code accessControl/remoteControl/actions/open}，
     * {@code cmd=open}，{@code doorId} 为字符串，缺省 1，65535 表示门全开）。
     *
     * @param doorId 门编号
     */
    public void remoteUnlock(int doorId) {
        controlDoor(doorId, "open");
    }

    /**
     * 门禁控制（官方 cmd：open / close / alwaysOpen / alwaysClose / resume）。
     *
     * @param doorId 门编号
     * @param cmd    命令
     * @return 是否成功
     */
    public boolean controlDoor(int doorId, String cmd) {
        if (cmd == null || cmd.isBlank()) {
            throw new IllegalArgumentException("云眸控门命令 cmd 不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("cmd", cmd);
        body.put("doorId", doorId <= 0 ? "1" : String.valueOf(doorId));
        postJson(PATH_DOOR_CONTROL, body);
        return true;
    }

    @Override
    /**
     * 门禁事件（官方 {@code accessEvents/actions/list}，{@code major=5}，{@code minor} 必填）。
     *
     * <p>官方 {@code minor} 是必填单值，这里对「开门类」与「拒绝类」次事件各查一次再合并。</p>
     *
     * @param range 时间范围
     * @return 出入记录
     */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("云眸门禁事件查询要求起止时间");
        }
        String start = range.start().atZone(ZoneId.systemDefault()).format(ACCESS_TIME);
        String end = range.end().atZone(ZoneId.systemDefault()).format(ACCESS_TIME);
        List<AccessRecord> result = new ArrayList<>();
        for (List<Integer> group : List.of(MINOR_OPEN, MINOR_DENY)) {
            for (Integer minor : group) {
                Map<String, Object> query = new LinkedHashMap<>();
                query.put("deviceSerial", requireSerial());
                query.put("major", MAJOR_ACCESS);
                query.put("minor", minor);
                query.put("startTime", start);
                query.put("endTime", end);
                for (JsonNode node : pageOf(PATH_ACCESS_EVENTS, query)) {
                    result.add(toAccessRecord(node, minor));
                }
            }
        }
        return result;
    }

    /**
     * 门禁事件转出入记录。
     *
     * @param node  平台条目
     * @param minor 次事件类型
     * @return 出入记录
     */
    private AccessRecord toAccessRecord(JsonNode node, Integer minor) {
        String time = node.path("time").asText("");
        LocalDateTime occurred = null;
        try {
            if (!time.isEmpty()) {
                occurred = LocalDateTime.parse(time, ACCESS_TIME);
            }
        } catch (Exception ex) {
            log.debug("[HikvisionCloudEye] 门禁事件时间解析失败: {}", time);
        }
        return new AccessRecord(
                requireSerial(),
                blankToNull(node.path("cardNo").asText("")),
                node.path("employeeNo").asText(""),
                occurred,
                minorDescription(minor),
                null,
                null);
    }

    /**
     * 门禁次事件的中文含义（官方「常用次事件类型列表」）。
     *
     * @param minor 次事件类型
     * @return 描述
     */
    private static String minorDescription(Integer minor) {
        switch (minor) {
            case 1: return "刷卡开锁";
            case 6: return "未分配权限";
            case 7: return "无效时段";
            case 8: return "卡号过期";
            case 9: return "无此卡号";
            case 21: return "门锁打开";
            case 22: return "门锁关闭";
            case 75: return "人脸开锁";
            case 76: return "人脸认证失败";
            case 1024: return "中心平台开锁";
            default: return "门禁事件" + minor;
        }
    }

    // ------------------------------------------------------------------ 事件

    @Override
    /**
     * 拉取事件（官方消息通道 {@code /api/v1/mq/consumer/messages}）。
     *
     * <p>官方要求：消费者首次拉取只建立连接（返回空），之后需在 30 秒内再次拉取，
     * 否则连接断开；每次最多 500 条。这里单次拉取后按 {@code timestamp} 过滤时间窗。</p>
     *
     * @param range 时间范围（为空表示不过滤）
     * @return 事件
     */
    public List<DeviceEvent> pollEvents(TimeRange range) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("consumerId", ensureConsumer());
        form.put("autoCommit", "true");
        JsonNode data = assertCloudEyeOk(formPost(PATH_MQ_MESSAGES, form));
        List<DeviceEvent> result = new ArrayList<>();
        for (JsonNode node : asList(data)) {
            long ts = node.path("timestamp").asLong(0L);
            LocalDateTime occurred = ts > 0L
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
                    : LocalDateTime.now();
            if (range != null && range.start() != null && occurred.isBefore(range.start())) {
                continue;
            }
            if (range != null && range.end() != null && occurred.isAfter(range.end())) {
                continue;
            }
            String msgType = node.path("msgType").asText("");
            String content = node.path("content").asText("");
            result.add(new DeviceEvent(
                    requireSerial(),
                    null,
                    eventTypeOf(msgType),
                    msgType + " " + content,
                    occurred,
                    content));
        }
        return result;
    }

    /**
     * 消息类型转统一事件类型。
     *
     * @param msgType 消息类型
     * @return 事件类型
     */
    private static EventType eventTypeOf(String msgType) {
        String type = msgType == null ? "" : msgType.toLowerCase(Locale.ROOT);
        if (type.contains("access") || type.contains("door")) {
            return EventType.ACCESS;
        }
        if (type.contains("motion") || type.contains("alarm")) {
            return EventType.MOTION;
        }
        return EventType.OTHER;
    }

    /**
     * 确保消息消费者存在（官方：最多 5 个，5 分钟不拉取会被删除）。
     *
     * @return 消费者 ID
     */
    private synchronized String ensureConsumer() {
        if (consumerId != null && !consumerId.isBlank()) {
            return consumerId;
        }
        JsonNode data = postJson(PATH_MQ_CONSUMER, Collections.emptyMap());
        String id = data.path("consumerId").asText("");
        if (id.isBlank()) {
            throw new IllegalStateException("云眸创建消息消费者未返回 consumerId: " + data);
        }
        this.consumerId = id;
        return id;
    }

    /**
     * 手动提交偏移量（官方要求与消费使用同一 consumerId）。
     *
     * @return 是否成功
     */
    public boolean commitOffsets() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("consumerId", ensureConsumer());
        assertCloudEyeOk(formPost(PATH_MQ_OFFSETS, form));
        return true;
    }

    /**
     * 表单 POST（云眸消息通道接口要求 {@code application/x-www-form-urlencoded}）。
     *
     * @param path 接口路径
     * @param form 表单
     * @return 响应根节点
     */
    private JsonNode formPost(String path, Map<String, Object> form) {
        login();
        String body = formEncode(form);
        try {
            HttpClientBuilder builder = HttpClientFactory.of(baseUrl)
                    .path(path)
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .readTimeout(DEFAULT_READ_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Bearer " + accessToken);
            ClientResponse response = builder.body(body).post();
            String respBody = response.getBodyString();
            return MAPPER.readTree(respBody == null || respBody.isEmpty() ? "{}" : respBody);
        } catch (Exception ex) {
            throw new IllegalStateException("云眸表单调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * URL 编码表单（值可以为任意类型）。
     *
     * @param form 表单
     * @return 编码后的字符串
     */
    private static String formEncode(Map<?, ?> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(String.valueOf(key), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(value == null ? "" : String.valueOf(value), StandardCharsets.UTF_8));
        });
        return body.toString();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 分页拉取（官方分页响应固定 {@code data.rows[]} + {@code total/totalPage}）。
     *
     * @param path  接口路径
     * @param base  固定查询参数
     * @return 条目
     */
    private List<JsonNode> pageOf(String path, Map<String, Object> base) {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> query = new LinkedHashMap<>(base);
            query.put("pageNo", pageNo);
            query.put("pageSize", PAGE_SIZE);
            JsonNode data = getData(path, query);
            List<JsonNode> list = asList(data.path("rows"));
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            long total = data.path("total").asLong(0L);
            if (total > 0 && all.size() >= total) {
                break;
            }
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /**
     * JSON 数组转条目列表。
     *
     * @param node 节点
     * @return 条目
     */
    private static List<JsonNode> asList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        // [P3C CO-循环 豁免]
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
            result.add(it.next());
        }
        return result;
    }

    /**
     * 取凭据扩展字段。
     *
     * @param key 键
     * @return 值；缺失返回 null
     */
    private String extraOf(String key) {
        Object value = info.extra() == null ? null : info.extra().get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 取第一个非空字符串。
     *
     * @param values 候选值
     * @return 第一个非空值；全空返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 空串转 null。
     *
     * @param value 值
     * @return 结果
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 平台未给出组编号时生成本地编号（官方 groupNo 支持自定义，仅字母数字）。
     *
     * @return 组编号
     */
    private static String randomGroupNo() {
        return "g" + Long.toString(System.currentTimeMillis(), 36);
    }
}

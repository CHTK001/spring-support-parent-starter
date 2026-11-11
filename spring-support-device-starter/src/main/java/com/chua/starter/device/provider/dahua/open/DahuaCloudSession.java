// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.LiveHandle;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大华云开发者平台（open.cloud-dahua.com，IoT 开放平台）会话。
 *
 * <h3>平台归属（已用真实凭据探活确认）</h3>
 * <p>本会话对接的是<b>大华云开发者平台 IoT 开放平台</b>，不是「大华云睿 SaaS」
 * （{@code www.cloud-dahua.com/gateway}）。网关固定为
 * {@code https://open.cloud-dahua.com}，路径统一 {@code /open-api/...}，
 * 接口目录见 {@code DahuaCloudApis}。</p>
 *
 * <h3>认证</h3>
 * <ul>
 *   <li>换 token：{@code POST /open-api/api-base/auth/getAppAccessToken}，
 *       签名 {@code Sign = HMAC-SHA512(SK, AK + timestamp + nonce)}（十六进制大写），
 *       请求头 {@code AccessKey / Timestamp / Nonce / Sign / ProductId / Version=V1 / Sign-Type=simple}，
 *       返回 {@code data.appAccessToken} 与 {@code data.validitySeconds}（实测 604800 秒）。</li>
 *   <li>业务接口：同签名头 + {@code AppAccessToken}，
 *       签名因子 {@code AK + appAccessToken + timestamp + nonce}。</li>
 *   <li>统一信封 {@code {code:"200", success:true, data, msg}}。</li>
 * </ul>
 *
 * <h3>能力边界（2026-11-11 官方文档取证后更新）</h3>
 * <p>43 条「基础通用套件」接口里 42 条真实存在，唯一不存在的是
 * {@code /open-api/api-iot/device/getDeviceChannelInfo}（{@code BIZ120 The url does not exist}），
 * 因此通道列表改由已验证的
 * {@code /open-api/api-iot/device/listDeviceDetailsByIds}（入参
 * {@code deviceList:[{deviceId}]}）取回设备详情后再解析。</p>
 *
 * <p>视频监控、云存储、门禁、组织、用户套件的文档详情已从官方文档中心取证，
 * 本会话按官方路径与字段实现实时预览、回放、录像检索、抓图、云台/预置位、
 * 事件拉取、设备写、组织/人员写与门禁计划；全部标记为
 * {@code IMPLEMENTED_UNVERIFIED}，原因是缺少设备与凭据做真机验证，而不是缺文档。</p>
 *
 * <p>组织接口必须带 {@code templateId}：优先取凭据 {@code extra.orgTemplateId}，
 * 否则读当前登录用户的 {@code orgPriGroupList[].templateId}；新增用户必须提供
 * {@code extra.defaultPersonPassword}，新增设备必须提供 {@code extra.devCode}，
 * 不擅自用手机号/序列号充当密码。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DahuaCloudSession extends AbstractOpenApiSession {

    /**
     * 大华云开发者平台网关（固定，凭据不可覆盖）
     */
    private static final String DEFAULT_BASE_URL = "https://open.cloud-dahua.com";

    /**
     * 换 AppAccessToken
     */
    private static final String PATH_TOKEN = "/open-api/api-base/auth/getAppAccessToken";

    /**
     * 分页查询设备列表
     */
    private static final String PATH_DEVICE_LIST = "/open-api/api-iot/device/getDeviceList";

    /**
     * 查询设备在线状态
     */
    private static final String PATH_DEVICE_ONLINE = "/open-api/api-iot/device/deviceOnline";

    /**
     * 批量查询设备详细信息（含通道）
     */
    private static final String PATH_DEVICE_DETAILS = "/open-api/api-iot/device/listDeviceDetailsByIds";

    /**
     * 分页大小上限（官方无明示，取常用 100）
     */
    private static final int PAGE_SIZE = 100;

    /**
     * token 默认有效期（秒）：官方 validitySeconds 缺省时按官方文档 7 天
     */
    private static final long DEFAULT_TOKEN_SECONDS = 604_800L;

    /**
     * 业务网关前缀（设备/组织/用户写接口）
     */
    private static final String BIZ = "/open-api/api-biz";

    /**
     * IoT 设备网关前缀
     */
    private static final String IOT = "/open-api/api-iot/device";

    /**
     * AIoT 外设网关前缀（云台/预置位）
     */
    private static final String AIOT = "/open-api/api-aiot/device";

    /**
     * 消息网关前缀
     */
    private static final String MSG = "/open-api/api-message";

    /** 创建 HLS 直播 */
    private static final String PATH_LIVE_HLS = "/open-api/api-iot/device/createDeviceHlsLive";

    /** 删除 HLS 直播 */
    private static final String PATH_LIVE_HLS_DELETE = "/open-api/api-iot/device/delDeviceHlsLive";

    /** 创建 FLV 直播 */
    private static final String PATH_LIVE_FLV = "/open-api/api-iot/device/createDeviceFlvLive";

    /** 删除 FLV 直播 */
    private static final String PATH_LIVE_FLV_DELETE = "/open-api/api-iot/device/delDeviceFlvLive";

    /** 创建私有拉流地址（RTSP/RTSV） */
    private static final String PATH_STREAM_URL = "/open-api/api-iot/device/createDeviceStreamUrl";

    /** 本地录像查询 */
    private static final String PATH_LOCAL_RECORDS = "/open-api/api-iot/device/queryLocalRecords";

    /** 录像片段转 HLS 回放 */
    private static final String PATH_RECORD_HLS = "/open-api/api-iot/device/createDeviceRecordHls";

    /** 设备抓图 */
    private static final String PATH_CAPTURE = "/open-api/api-iot/device/setDeviceSnapEnhanced";

    /** 云台移动 */
    private static final String PATH_PTZ_MOVE = AIOT + "/controlMovePTZ";

    /** 设置预置点 */
    private static final String PATH_PRESET_SET = AIOT + "/configPreset";

    /** 转到预置点 */
    private static final String PATH_PRESET_TURN = AIOT + "/turnPreset";

    /** 查询预置点 */
    private static final String PATH_PRESET_QUERY = AIOT + "/queryPreset";

    /** 删除预置点 */
    private static final String PATH_PRESET_DELETE = AIOT + "/deletePreset";

    /** 开门记录 */
    private static final String PATH_ACCESS_RECORD =
            "/open-api/api-iot/device/accessControl/getDoorOpenRecord";

    /** 远程开门 V2 */
    private static final String PATH_ACCESS_OPEN =
            "/open-api/api-iot/v2/device/accessControl/openDoorRemote";

    /** 设置常开/常闭 */
    private static final String PATH_ACCESS_STATUS =
            "/open-api/api-iot/device/accessControl/setOpenDoorStatus";

    /** 开门计划列表 */
    private static final String PATH_ACCESS_PLANS =
            "/open-api/api-iot/device/accessControl/getPlanListByDeviceId";

    /** 新增开门计划 */
    private static final String PATH_ACCESS_PLAN_ADD =
            "/open-api/api-iot/device/accessControl/addPlan";

    /** 修改开门计划 */
    private static final String PATH_ACCESS_PLAN_UPDATE =
            "/open-api/api-iot/device/accessControl/updatePlan";

    /** 删除开门计划 */
    private static final String PATH_ACCESS_PLAN_DELETE =
            "/open-api/api-iot/device/accessControl/removePlan";

    /** 下发开门计划 */
    private static final String PATH_ACCESS_PLAN_DISPATCH =
            "/open-api/api-iot/device/accessControl/dispatchPlans";

    /** 人员关联开门计划 */
    private static final String PATH_ACCESS_USER_PLANS =
            "/open-api/api-iot/device/accessControl/setUserPlans";

    /** 分页查询消息 */
    private static final String PATH_MESSAGE = MSG + "/getMessage";

    /** 当前登录用户 */
    private static final String PATH_CURRENT_USER = BIZ + "/user/getUserInfo";

    /** 分页查询用户 */
    private static final String PATH_USER_PAGE = BIZ + "/user/page";

    /** 新增用户 */
    private static final String PATH_USER_ADD = BIZ + "/user/addUserWithPwd";

    /** 更新用户 */
    private static final String PATH_USER_UPDATE = BIZ + "/user/update";

    /** 批量删除用户 */
    private static final String PATH_USER_DELETE = BIZ + "/user/batchDelete";

    /** 组织树 */
    private static final String PATH_ORG_TREE = BIZ + "/org/getTree";

    /** 根组织 */
    private static final String PATH_ORG_ROOT = BIZ + "/org/getRootInfo";

    /** 组织详情 */
    private static final String PATH_ORG_INFO = BIZ + "/org/getOrgInfo";

    /** 新增组织 */
    private static final String PATH_ORG_ADD = BIZ + "/org/addOrg";

    /** 更新组织 */
    private static final String PATH_ORG_UPDATE = BIZ + "/org/updateOrg";

    /** 删除组织 */
    private static final String PATH_ORG_DELETE = BIZ + "/org/delByOrgId";

    /** 添加设备（业务侧，带名称/组织） */
    private static final String PATH_DEVICE_ADD = BIZ + "/device/addDevice";

    /** 修改设备/通道名称 */
    private static final String PATH_DEVICE_RENAME = BIZ + "/device/updateDeviceName";

    /** 删除设备 */
    private static final String PATH_DEVICE_DELETE = IOT + "/deleteDevice";

    /** 校验设备密码 */
    private static final String PATH_DEVICE_VERIFY = IOT + "/verifyDevCode";

    /** 时间格式：官方录像/回放接口 */
    private static final DateTimeFormatter F_RECORD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 抓图下载客户端 */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 直播 token 缓存：deviceId:channel:stream -> liveToken */
    private final Map<String, String> liveTokens = new ConcurrentHashMap<>();

    /** 设备名称缓存 */
    private volatile String cachedDeviceName;

    /** 组织模板 ID 缓存 */
    private volatile Long cachedTemplateId;

    /**
     * 创建 dahuacloud会话 实例
     * @param info 信息
     */
    public DahuaCloudSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        login();
    }

    /**
     * 计算 HMAC-SHA512 签名（十六进制大写）。
     *
     * @param data 签名因子
     * @return 签名串
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(utf8(info.appSecret()), "HmacSHA512"));
            byte[] digest = mac.doFinal(utf8(data));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02X", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("大华云签名失败", e);
        }
    }

    /**
     * 产品 ID：凭据 productCode / productId / appId 任一项。
     *
     * @return 产品 ID，取不到时为空串
     */
    private String productId() {
        for (String key : new String[]{"productCode", "productId", "appId"}) {
            Object value = info.extra() == null ? null : info.extra().get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return "";
    }

    /**
     * 构造签名请求头。
     *
     * @param timestamp 毫秒时间戳
     * @param nonce     随机串
     * @param signature 签名
     * @param withAuth  是否业务请求（带 AppAccessToken）
     * @return 请求头
     */
    private Map<String, String> headers(String timestamp, String nonce, String signature, boolean withAuth) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("AccessKey", info.appKey());
        headers.put("Timestamp", timestamp);
        headers.put("Nonce", nonce);
        headers.put("Sign", signature);
        headers.put("ProductId", productId());
        headers.put("Version", "V1");
        headers.put("Sign-Type", "simple");
        if (withAuth && accessToken != null) {
            headers.put("AppAccessToken", accessToken);
        }
        return headers;
    }

    /**
     * 换 AppAccessToken 并缓存。
     */
    private void login() {
        if (tokenValid()) {
            return;
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        JsonNode root = execute("POST", PATH_TOKEN, new LinkedHashMap<>(),
                headers(timestamp, nonce, sign(info.appKey() + timestamp + nonce), false));
        JsonNode data = dataOf(root, PATH_TOKEN);
        String token = text(data, "appAccessToken");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("大华云换取 appAccessToken 失败: " + text(root, "msg"));
        }
        long validity = DEFAULT_TOKEN_SECONDS;
        JsonNode validityNode = data.get("validitySeconds");
        if (validityNode != null && !validityNode.isNull()) {
            validity = validityNode.asLong(DEFAULT_TOKEN_SECONDS);
        }
        cacheToken(token, validity);
        log.info("大华云登录成功: productId={}, deviceSerial={}", productId(), info.deviceSerial());
    }

    @Override
    /**
     * 执行请求
     * @param method 方法
     * @param path 路径
     * @param params 参数
     * @return 返回值
     */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!PATH_TOKEN.equals(path)) {
            login();
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        boolean withAuth = !PATH_TOKEN.equals(path);
        String factor = withAuth
                ? info.appKey() + accessToken + timestamp + nonce
                : info.appKey() + timestamp + nonce;
        Map<String, Object> body = params == null ? new LinkedHashMap<>() : params;
        return execute(method, path, body, headers(timestamp, nonce, sign(factor), withAuth));
    }

    /**
     * 取信封里的 data，失败抛异常。
     *
     * @param root 响应根节点
     * @param api  接口路径（用于报错定位）
     * @return data 节点
     */
    private JsonNode dataOf(JsonNode root, String api) {
        if (root == null) {
            throw new IllegalStateException("大华云响应为空: " + api);
        }
        String code = text(root, "code");
        JsonNode successNode = root.get("success");
        boolean success = successNode == null || successNode.isNull() || successNode.asBoolean();
        if (!success || (code != null && !"200".equals(code) && !"0".equals(code))) {
            throw new IllegalStateException("大华云调用失败: api=" + api + ", code=" + code
                    + ", msg=" + text(root, "msg"));
        }
        JsonNode data = root.get("data");
        return data == null || data.isNull() ? root : data;
    }

    /**
     * 读取文本字段，缺失返回 null。
     *
     * @param node 节点
     * @param key  字段名
     * @return 字段值
     */
    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 读取首个存在的文本字段。
     *
     * @param node 节点
     * @param keys 候选字段名
     * @return 字段值，均缺失返回 null
     */
    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 拉取设备目录（分页）。
     *
     * <p>官方 {@code getDeviceList}：入 {@code pageNum/pageSize}，出
     * {@code data.pageData[].deviceList[]} 与 {@code totalRows/totalPage}。
     * 全部字段已用真实凭据探活确认。</p>
     *
     * @return 设备条目
     */
    public List<VendorDevice> fetchDevices() {
        List<VendorDevice> result = new ArrayList<>();
        int pageNum = 1;
        int totalPage = 1;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", PAGE_SIZE);
            JsonNode data = dataOf(post(PATH_DEVICE_LIST, params), PATH_DEVICE_LIST);
            JsonNode pageData = data.path("pageData");
            if (pageData.isArray()) {
                for (JsonNode page : pageData) {
                    collectDevices(page.path("deviceList"), result);
                }
            } else {
                collectDevices(data.path("deviceList"), result);
            }
            JsonNode totalPageNode = data.get("totalPage");
            totalPage = totalPageNode == null || totalPageNode.isNull() ? 0 : totalPageNode.asInt(0);
            pageNum++;
        } while (pageNum <= totalPage);
        return result;
    }

    /**
     * 把一页设备节点映射为厂商设备条目。
     *
     * @param nodes  设备节点数组
     * @param result 结果容器
     */
    private void collectDevices(JsonNode nodes, List<VendorDevice> result) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String deviceId = firstText(node, "deviceId", "deviceCode", "deviceSn");
            if (deviceId == null) {
                continue;
            }
            Integer channelNum = null;
            JsonNode channelNode = node.get("channelNum");
            if (channelNode != null && !channelNode.isNull()) {
                channelNum = channelNode.asInt();
            }
            result.add(new VendorDevice(
                    deviceId,
                    firstText(node, "deviceName", "name"),
                    firstText(node, "deviceModel", "model"),
                    firstText(node, "categoryCode", "deviceCategory"),
                    deviceId,
                    firstText(node, "deviceIp", "ip"),
                    null,
                    firstText(node, "deviceAddress", "address"),
                    null,
                    null,
                    channelNum,
                    onlineOf(firstText(node, "deviceStatus", "onLine", "status"))));
        }
    }

    /**
     * 在线状态：官方取值 1 在线 / 0 离线，兼容 true / online。
     *
     * @param value 状态文本
     * @return true 在线；未知返回 null
     */
    private static Boolean onlineOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if ("1".equals(v) || "true".equalsIgnoreCase(v) || "online".equalsIgnoreCase(v)) {
            return Boolean.TRUE;
        }
        if ("0".equals(v) || "false".equalsIgnoreCase(v) || "offline".equalsIgnoreCase(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 查询当前设备的在线状态。
     *
     * <p>官方 {@code deviceOnline}：入 {@code deviceId}；探活返回
     * {@code PRM0132} 表示设备不属于该产品（路径与参数正确）。</p>
     *
     * @return true 在线
     */
    public boolean fetchOnline() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        JsonNode data = dataOf(post(PATH_DEVICE_ONLINE, params), PATH_DEVICE_ONLINE);
        Boolean online = onlineOf(firstText(data, "onLine", "online", "deviceStatus"));
        return Boolean.TRUE.equals(online);
    }

    /**
     * 列出设备下的通道。
     *
     * <p>官方「获取设备通道信息」({@code getDeviceChannelInfo}) 在本产品<b>不存在</b>
     * （{@code BIZ120 The url does not exist}），因此改走已验证的
     * {@code listDeviceDetailsByIds}：入 {@code deviceList:[{deviceId}]}，
     * 从设备详情的通道数组解析。通道字段名按大华同源设备详情模型
     * （{@code channelList[].channelId/channelName}），响应字段未拿到真机样例，
     * 故能力位标记为 {@code IMPLEMENTED_UNVERIFIED}。</p>
     *
     * @return 通道列表
     */
    @Override
    public List<DeviceChannel> listChannels() {
        String deviceId = getDeviceId();
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", deviceId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceList", List.of(device));
        JsonNode data = dataOf(post(PATH_DEVICE_DETAILS, params), PATH_DEVICE_DETAILS);
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : detailsOf(data)) {
            JsonNode channels = node.has("channelList") ? node.path("channelList") : node.path("channels");
            if (!channels.isArray()) {
                continue;
            }
            for (JsonNode channel : channels) {
                String raw = firstText(channel, "channelId", "channelNo");
                if (raw == null) {
                    continue;
                }
                Integer no = parseInt(raw);
                result.add(new DeviceChannel(
                        deviceId + "_" + raw,
                        no,
                        firstText(channel, "channelName", "name"),
                        deviceId,
                        onlineOf(firstText(channel, "channelStatus", "onLine", "status")),
                        firstText(channel, "deviceModel", "channelModel"),
                        null));
            }
        }
        return result;
    }

    /**
     * 从详情响应里取设备节点数组，兼容 data.deviceList / data.list / data 本身。
     *
     * @param data 响应 data
     * @return 设备节点数组
     */
    private static List<JsonNode> detailsOf(JsonNode data) {
        List<JsonNode> nodes = new ArrayList<>();
        if (data == null) {
            return nodes;
        }
        for (String key : new String[]{"deviceList", "list", "deviceDetailList"}) {
            JsonNode array = data.get(key);
            if (array != null && array.isArray()) {
                array.forEach(nodes::add);
                return nodes;
            }
        }
        if (data.isArray()) {
            data.forEach(nodes::add);
        } else if (data.isObject()) {
            nodes.add(data);
        }
        return nodes;
    }

    /**
     * 解析正整数通道号。
     *
     * @param raw 原始通道号
     * @return 数字；非数字返回 null
     */
    private static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    /**
     * 测试connection
     * @return 返回值
     */
    public boolean testConnection() {
        try {
            login();
            return tokenValid();
        } catch (Exception e) {
            log.warn("大华云连通性探测失败: {}", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ 实时预览 / 回放

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
     */
    public String getLiveUrl(int channel, String protocol) {
        return getLiveUrl(channel, protocol, "main");
    }

    /**
     * 按协议与码流取实时预览地址。
     *
     * <p>官方视频监控套件：RTSP/RTSV 走 {@code createDeviceStreamUrl}（地址 10 秒有效，
     * 且是免插件私有协议，标准播放器不能直接播放）；HLS 走 {@code createDeviceHlsLive}
     * （地址长期有效，删除后失效）；FLV 走 {@code createDeviceFlvLive}。浏览器预览
     * 默认使用 HLS。</p>
     *
     * @param channel    通道
     * @param protocol   协议：hls / flv / rtsp / rtsv
     * @param streamType 码流：main / sub
     * @return 播放地址
     */
    @Override
    public String getLiveUrl(int channel, String protocol, String streamType) {
        int stream = streamOf(streamType);
        String proto = protocol == null || protocol.isBlank() ? "hls" : protocol.trim().toLowerCase();
        if ("rtsp".equals(proto) || "rtsv".equals(proto)) {
            return streamUrl(channel, proto, stream);
        }
        if ("flv".equals(proto)) {
            return flvUrl(channel, stream);
        }
        return hlsUrl(channel, stream);
    }

    /**
     * 创建私有拉流地址（RTSP/RTSV）。
     *
     * @param channel 通道
     * @param proto   协议
     * @param stream  0 主码流 / 1 辅码流
     * @return 拉流地址
     */
    private String streamUrl(int channel, String proto, int stream) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", String.valueOf(channel));
        params.put("businessType", "real");
        params.put("encryptMode", 0);
        params.put("protoType", proto);
        params.put("streamType", stream);
        JsonNode data = dataOf(post(PATH_STREAM_URL, params), PATH_STREAM_URL);
        String url = firstText(data, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("大华云未返回实时拉流地址: " + data);
        }
        return url;
    }

    /**
     * 创建 HLS 直播并取指定码流地址。
     *
     * @param channel 通道
     * @param stream  0 主码流 / 1 辅码流
     * @return HLS 地址
     */
    private String hlsUrl(int channel, int stream) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", String.valueOf(channel));
        params.put("streamType", stream);
        JsonNode data = dataOf(post(PATH_LIVE_HLS, params), PATH_LIVE_HLS);
        String token = firstText(data, "liveToken");
        if (token != null && !token.isBlank()) {
            liveTokens.put(liveKey(channel, stream), token);
        }
        String url = pickStream(data.path("streamList"), stream, "hls");
        if (url == null || url.isBlank()) {
            url = firstText(data, "hls");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("大华云未返回 HLS 直播地址: " + data);
        }
        return url;
    }

    /**
     * 创建 FLV 直播地址。
     *
     * @param channel 通道
     * @param stream  0 主码流 / 1 辅码流
     * @return FLV 地址
     */
    private String flvUrl(int channel, int stream) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", String.valueOf(channel));
        JsonNode data = dataOf(post(PATH_LIVE_FLV, params), PATH_LIVE_FLV);
        String url = firstText(data, stream == 0 ? "flvHD" : "flv");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("大华云未返回 FLV 直播地址: " + data);
        }
        return url;
    }

    /**
     * 从 streamList 里挑指定码流的地址，同码流优先 https。
     *
     * @param streams streamList
     * @param stream  目标码流
     * @param field   地址字段
     * @return 地址，取不到返回 null
     */
    private static String pickStream(JsonNode streams, int stream, String field) {
        if (streams == null || !streams.isArray()) {
            return null;
        }
        String fallback = null;
        for (JsonNode item : streams) {
            if (item.path("streamType").asInt(-1) != stream) {
                continue;
            }
            String url = item.path(field).asText("");
            if (url.isBlank()) {
                continue;
            }
            if (url.startsWith("https://")) {
                return url;
            }
            fallback = url;
        }
        return fallback;
    }

    /**
     * 开启直播：HLS/FLV 地址长期有效，只需把地址与直播 token 绑定成句柄。
     *
     * @param channel    通道
     * @param protocol   协议
     * @param streamType 码流
     * @return 直播句柄
     */
    @Override
    public LiveHandle startLive(int channel, String protocol, String streamType) {
        String url = getLiveUrl(channel, protocol, streamType);
        return new LiveHandle(liveKey(channel, streamOf(streamType)), url, protocol,
                streamType == null ? "main" : streamType, Instant.now().plusSeconds(3600));
    }

    /**
     * 停止直播：仅 HLS 有删除接口，按创建时缓存的 liveToken 失效地址。
     *
     * @param streamId 直播句柄
     */
    @Override
    public void stopLive(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        String token = liveTokens.remove(streamId);
        if (token != null && !token.isBlank()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("liveToken", token);
            post(PATH_LIVE_HLS_DELETE, params);
        }
        String[] parts = streamId.split(":");
        if (parts.length >= 4) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", parts[0]);
            params.put("channelId", parts[2]);
            try {
                post(PATH_LIVE_FLV_DELETE, params);
            } catch (RuntimeException e) {
                log.debug("[DahuaCloud] 删除 FLV 直播地址失败（可能未创建）: {}", e.getMessage());
            }
        }
    }

    /**
     * 直播 token 缓存键。
     *
     * @param channel 通道
     * @param stream  码流
     * @return 键
     */
    private String liveKey(int channel, int stream) {
        return getDeviceId() + ":live:" + channel + ":" + stream;
    }

    /**
     * 码流文本转官方数字：main=0，sub=1。
     *
     * @param streamType 码流
     * @return 0 / 1
     */
    private static int streamOf(String streamType) {
        return "1".equals(streamType) || "sub".equalsIgnoreCase(streamType)
                || "extra1".equalsIgnoreCase(streamType) ? 1 : 0;
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
            throw new IllegalArgumentException("大华云回放必须提供起止时间");
        }
        LocalDateTime begin = range.start();
        LocalDateTime end = range.end();
        if (end.toLocalDate().isAfter(begin.toLocalDate())) {
            end = begin.toLocalDate().atTime(23, 59, 59);
            log.warn("[DahuaCloud] createDeviceRecordHls 不支持跨天，已截断为 {} ~ {}", begin, end);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", String.valueOf(channel));
        params.put("beginTime", begin.format(F_RECORD));
        params.put("endTime", end.format(F_RECORD));
        JsonNode data = dataOf(post(PATH_RECORD_HLS, params), PATH_RECORD_HLS);
        String url = firstText(data, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("大华云未返回回放地址: " + data);
        }
        return url;
    }

    @Override
    /**
     * 查询Records
     * @param channel 整数
     * @param range 参数
     * @return 返回值
     */
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("大华云录像检索必须提供起止时间");
        }
        List<RecordFile> result = new ArrayList<>();
        LocalDateTime begin = range.start();
        LocalDateTime end = range.end();
        for (int page = 0; page < 1000 && !begin.isAfter(end); page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", getDeviceId());
            params.put("channelId", String.valueOf(channel));
            params.put("beginTime", begin.format(F_RECORD));
            params.put("endTime", end.format(F_RECORD));
            params.put("count", PAGE_SIZE);
            JsonNode data = dataOf(post(PATH_LOCAL_RECORDS, params), PATH_LOCAL_RECORDS);
            JsonNode records = data.path("records");
            if (!records.isArray() || records.isEmpty()) {
                break;
            }
            LocalDateTime maxEnd = begin;
            for (JsonNode node : records) {
                LocalDateTime start = parseTime(firstText(node, "beginTime"));
                LocalDateTime stop = parseTime(firstText(node, "endTime"));
                if (stop != null && stop.isAfter(maxEnd)) {
                    maxEnd = stop;
                }
                result.add(new RecordFile(
                        firstText(node, "recordFileName", "fileName"),
                        firstText(node, "channelId"),
                        start,
                        stop,
                        node.path("fileLength").asLong(0L)));
            }
            if (records.size() < PAGE_SIZE || !maxEnd.isAfter(begin)) {
                break;
            }
            begin = maxEnd.plusSeconds(1);
        }
        return result;
    }

    @Override
    /**
     * Preview
     * @param channel 整数
     * @return 返回值
     */
    public InputStream preview(int channel) {
        throw new UnsupportedOperationException("大华云为 URL 播放模式，请使用 getLiveUrl/startLive 获取 HLS/FLV 地址");
    }

    // ------------------------------------------------------------------ 抓图

    @Override
    /**
     * 抓图
     * @param channel 通道
     * @return 返回值
     */
    public InputStream capture(int channel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", String.valueOf(channel));
        JsonNode data = dataOf(post(PATH_CAPTURE, params), PATH_CAPTURE);
        String url = firstText(data, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("大华云未返回抓图地址: " + data);
        }
        return download(url);
    }

    /**
     * 下载抓图地址。
     *
     * @param url 图片地址
     * @return 图片字节流
     */
    private static InputStream download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = DOWNLOADER.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length == 0) {
                throw new IllegalStateException("大华云抓图下载失败: HTTP " + response.statusCode());
            }
            return new ByteArrayInputStream(body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大华云抓图下载被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("大华云抓图下载异常: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------ 云台 / 预置位

    @Override
    /**
     * ptzcontrol
     * @param cmd 参数
     * @param speed 双精度数
     * @return 返回值
     */
    public void ptzControl(PtzCommand cmd, double speed) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", "0");
        params.put("duration", Math.max(300L, Math.round(1500L * clamp(speed, 0, 1))));
        double s = Math.max(0.05, clamp(speed, 0, 1));
        switch (cmd) {
            case UP:
                params.put("operation", "0");
                params.put("verticalSpeed", s);
                break;
            case DOWN:
                params.put("operation", "1");
                params.put("verticalSpeed", -s);
                break;
            case LEFT:
                params.put("operation", "2");
                params.put("horizontalSpeed", -s);
                break;
            case RIGHT:
                params.put("operation", "3");
                params.put("horizontalSpeed", s);
                break;
            case ZOOM_IN:
                params.put("operation", "8");
                break;
            case ZOOM_OUT:
                params.put("operation", "9");
                break;
            case STOP:
                params.put("operation", "10");
                break;
            default:
                throw new UnsupportedOperationException(
                        "大华云 controlMovePTZ 不支持该云台指令: " + cmd + "（只支持八向移动、变倍与停止）");
        }
        post(PATH_PTZ_MOVE, params);
    }

    @Override
    /**
     * 设置预置点
     * @param presetId 编号
     * @param name 名称
     */
    public void setPreset(int presetId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", "0");
        params.put("name", name);
        params.put("index", presetId);
        post(PATH_PRESET_SET, params);
    }

    @Override
    /**
     * 转到预置点
     * @param presetId 编号
     */
    public void callPreset(int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", "0");
        params.put("index", presetId);
        post(PATH_PRESET_TURN, params);
    }

    @Override
    /**
     * 删除预置点
     * @param presetId 编号
     */
    public void deletePreset(int presetId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", "0");
        params.put("index", presetId);
        post(PATH_PRESET_DELETE, params);
    }

    @Override
    /**
     * 查询预置点
     * @return 预置点列表
     */
    public List<PresetInfo> listPresets() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("channelId", "0");
        JsonNode data = dataOf(post(PATH_PRESET_QUERY, params), PATH_PRESET_QUERY);
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode node : data.path("presetList")) {
            result.add(new PresetInfo(node.path("index").asInt(0),
                    firstText(node, "name")));
        }
        return result;
    }

    // ------------------------------------------------------------------ 门禁

    @Override
    /**
     * 查询accessrecords
     * @param range 参数
     * @return 返回值
     */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("大华云门禁记录必须提供起止时间");
        }
        List<AccessRecord> result = new ArrayList<>();
        int pageNum = 1;
        int totalPage = 1;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", getDeviceId());
            params.put("beginTime", millisOf(range.start()));
            params.put("endTime", millisOf(range.end()));
            params.put("pageNum", pageNum);
            params.put("pageSize", PAGE_SIZE);
            JsonNode data = dataOf(get(PATH_ACCESS_RECORD, params), PATH_ACCESS_RECORD);
            JsonNode pageData = data.path("pageData");
            for (JsonNode node : pageData) {
                JsonNode pics = node.path("picUrlArray");
                String photo = pics.isArray() && !pics.isEmpty() ? pics.get(0).asText("") : null;
                result.add(new AccessRecord(
                        node.path("channelId").asText(""),
                        node.path("cardNo").asText(""),
                        firstText(node, "userName", "personName", "userId"),
                        timeOf(node.path("time").asLong(0L)),
                        node.path("openType").asText(""),
                        null,
                        photo));
            }
            totalPage = data.path("totalPage").asInt(1);
            pageNum++;
        } while (pageNum <= totalPage);
        return result;
    }

    /**
     * 门禁设备即一个门点位（官方无独立门点位列表接口）。
     *
     * <p>{@code doorId} 固定为 {@code 0}：大华云远程开门按设备维度调用，
     * 不接收门点位 ID；设备序列号放在 {@code deviceId} 字段。</p>
     *
     * @return 单门点位
     */
    @Override
    public List<DeviceDoorInfo> listDoors() {
        return List.of(new DeviceDoorInfo("0", deviceName(), getDeviceId(), "unknown", 0));
    }

    @Override
    /**
     * 远程解锁
     * @param doorId 门标识（大华云按设备开门，忽略该参数）
     */
    public void remoteUnlock(int doorId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        post(PATH_ACCESS_OPEN, params);
    }

    /**
     * 设置常开 / 常闭 / 正常。
     *
     * @param status Normal / CloseAlways / OpenAlways
     */
    public void setOpenDoorStatus(String status) {
        if (status == null || (!"Normal".equals(status) && !"CloseAlways".equals(status)
                && !"OpenAlways".equals(status))) {
            throw new IllegalArgumentException("openDoorStatus 只允许 Normal / CloseAlways / OpenAlways");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("openDoorStatus", status);
        post(PATH_ACCESS_STATUS, params);
    }

    /**
     * 查询设备上的普通/假期开门计划。
     *
     * @return 计划列表节点
     */
    public JsonNode listAccessPlans() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        return dataOf(get(PATH_ACCESS_PLANS, params), PATH_ACCESS_PLANS);
    }

    /**
     * 新增普通开门计划。
     *
     * @param planName     计划名称
     * @param timeSchedule 时间计划
     * @return 计划 ID
     */
    public String addAccessPlan(String planName, Map<String, Object> timeSchedule) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("planName", planName);
        params.put("timeSchedule", timeSchedule);
        JsonNode data = dataOf(post(PATH_ACCESS_PLAN_ADD, params), PATH_ACCESS_PLAN_ADD);
        String planId = firstText(data, "planId");
        if (planId == null || planId.isBlank()) {
            throw new IllegalStateException("大华云新增开门计划未返回 planId: " + data);
        }
        return planId;
    }

    /**
     * 修改普通开门计划。
     *
     * @param planId       计划 ID
     * @param planName     名称
     * @param timeSchedule 时间计划
     */
    public void updateAccessPlan(String planId, String planName, Map<String, Object> timeSchedule) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId 不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("planId", planId);
        params.put("planName", planName);
        params.put("timeSchedule", timeSchedule);
        post(PATH_ACCESS_PLAN_UPDATE, params);
    }

    /**
     * 删除普通开门计划。
     *
     * <p>官方要求先调用 {@link #dispatchAccessPlans(List)} 传空数组清空设备计划，再删除。</p>
     *
     * @param planId 计划 ID
     */
    public void removeAccessPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId 不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("planId", planId);
        post(PATH_ACCESS_PLAN_DELETE, params);
    }

    /**
     * 下发普通开门计划；空数组表示清空设备计划。
     *
     * @param planIds 计划 ID 列表
     */
    public void dispatchAccessPlans(List<String> planIds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("planIds", planIds == null ? List.of() : planIds);
        post(PATH_ACCESS_PLAN_DISPATCH, params);
    }

    /**
     * 人员关联开门计划。
     *
     * @param userId  人员 ID
     * @param planIds 计划 ID 列表；空列表表示清空
     */
    public void setUserAccessPlans(String userId, List<String> planIds) {
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("planIdList", planIds == null ? List.of() : planIds);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", userId);
        user.put("accessTimeSchedule", schedule);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", getDeviceId());
        params.put("users", List.of(user));
        post(PATH_ACCESS_USER_PLANS, params);
    }

    // ------------------------------------------------------------------ 事件

    /**
     * 分页拉取设备消息（官方仅保留 7 天）。
     *
     * @param range 时间段
     * @return 设备事件
     */
    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        LocalDateTime end = range == null || range.end() == null ? LocalDateTime.now() : range.end();
        LocalDateTime start = range == null || range.start() == null ? end.minusHours(1) : range.start();
        LocalDateTime earliest = end.minusDays(7);
        if (start.isBefore(earliest)) {
            log.warn("[DahuaCloud] 消息只保留 7 天，查询起点从 {} 收紧到 {}", start, earliest);
            start = earliest;
        }
        String deviceList = json(List.of(Map.of("deviceId", getDeviceId(),
                "channelIdList", List.of("0"))));
        List<DeviceEvent> result = new ArrayList<>();
        int pageNum = 1;
        int totalPage = 1;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("beginTime", millisOf(start));
            params.put("endTime", millisOf(end));
            params.put("deviceList", deviceList);
            params.put("sortOrder", "desc");
            params.put("pageNum", pageNum);
            params.put("pageSize", 50);
            JsonNode data = dataOf(get(PATH_MESSAGE, params), PATH_MESSAGE);
            for (JsonNode node : data.path("pageData")) {
                result.add(new DeviceEvent(
                        node.path("deviceId").asText(getDeviceId()),
                        node.path("channelId").asInt(0),
                        eventTypeOf(node.path("msgType").asText("")),
                        node.path("msgType").asText(""),
                        timeOf(node.path("time").asLong(0L)),
                        MAPPER.convertValue(node, Map.class)));
            }
            totalPage = data.path("totalPage").asInt(1);
            pageNum++;
        } while (pageNum <= totalPage && pageNum <= 200);
        return result;
    }

    /**
     * 大华消息类型映射为统一事件类型。
     *
     * @param msgType 消息类型
     * @return 事件类型
     */
    private static EventType eventTypeOf(String msgType) {
        String type = msgType == null ? "" : msgType.toLowerCase();
        if (type.contains("access") || type.contains("door") || type.contains("card")
                || type.contains("face") || type.contains("fingerprint")) {
            return EventType.ACCESS;
        }
        if (type.contains("motion") || type.contains("human") || type.contains("vehicle")
                || type.contains("cross") || type.contains("smd")) {
            return EventType.MOTION;
        }
        if (type.contains("alarm") || type.contains("io")) {
            return EventType.IO_ALARM;
        }
        if (type.contains("temp")) {
            return EventType.TEMP;
        }
        if (type.contains("offline") || type.contains("online") || type.contains("network")) {
            return EventType.NETWORK;
        }
        return EventType.OTHER;
    }

    // ------------------------------------------------------------------ 组织

    /**
     * 拉取组织树。
     *
     * <p>官方 {@code getTree} 必须传 {@code templateId}；模板 ID 优先取凭据
     * {@code extra.orgTemplateId/templateId}，否则从当前登录用户的
     * {@code orgPriGroupList[].templateId} 取。两者都取不到时明确报错，不返回空树。</p>
     *
     * @return 扁平化后的组织节点
     */
    public List<OrganizationNode> fetchOrganizations() {
        long templateId = requireTemplateId();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("templateId", templateId);
        params.put("searchKey", "");
        JsonNode data = dataOf(get(PATH_ORG_TREE, params), PATH_ORG_TREE);
        List<OrganizationNode> result = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                flattenOrg(node, null, result);
            }
        } else if (data.isObject()) {
            flattenOrg(data, null, result);
        }
        return result;
    }

    /**
     * 递归扁平化组织树。
     *
     * @param node   当前节点
     * @param parent 父标识
     * @param result 结果
     */
    private static void flattenOrg(JsonNode node, String parent, List<OrganizationNode> result) {
        String id = firstText(node, "id", "orgId");
        if (id == null || id.isBlank()) {
            return;
        }
        String parentId = firstText(node, "parentId");
        if (parentId == null || "0".equals(parentId)) {
            parentId = parent;
        }
        result.add(new OrganizationNode(id, firstText(node, "name", "orgName"), parentId));
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                flattenOrg(child, id, result);
            }
        }
    }

    /**
     * 新增或更新组织。
     *
     * <p>新增时 {@code parentId} 为空则挂到根节点；{@code orgType} 优先取凭据
     * {@code extra.orgType}，否则继承父节点类型。</p>
     *
     * @param node 组织节点（externalId 非空表示更新）
     * @return 组织 ID
     */
    public String upsertOrganization(OrganizationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("组织节点不能为空");
        }
        if (node.orgId() != null && !node.orgId().isBlank()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("orgId", requireLong(node.orgId(), "orgId"));
            params.put("name", node.orgName());
            post(PATH_ORG_UPDATE, params);
            return node.orgId();
        }
        if (node.orgName() == null || node.orgName().isBlank()) {
            throw new IllegalArgumentException("新增组织必须提供 orgName");
        }
        long parentId;
        if (node.parentId() != null && !node.parentId().isBlank()) {
            parentId = requireLong(node.parentId(), "parentId");
        } else {
            Long configured = extraLong("parentOrgId");
            if (configured != null) {
                parentId = configured;
            } else {
                JsonNode root = dataOf(get(PATH_ORG_ROOT,
                        Map.of("templateId", requireTemplateId())), PATH_ORG_ROOT);
                parentId = root.path("orgId").asLong(0L);
            }
        }
        if (parentId <= 0) {
            throw new IllegalStateException(
                    "大华云新增组织需要父组织 ID：请在组织节点填 parentId，或凭据额外参数配置 parentOrgId");
        }
        Long orgType = extraLong("orgType");
        if (orgType == null) {
            orgType = orgTypeOf(parentId);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", node.orgName());
        params.put("parentId", parentId);
        params.put("orgType", orgType);
        JsonNode data = dataOf(post(PATH_ORG_ADD, params), PATH_ORG_ADD);
        String orgId = firstText(data, "orgId");
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalStateException("大华云新增组织未返回 orgId: " + data);
        }
        return orgId;
    }

    /**
     * 取父节点组织类型。
     *
     * @param parentId 父组织 ID
     * @return 组织类型
     */
    private long orgTypeOf(long parentId) {
        JsonNode data = dataOf(get(PATH_ORG_INFO, Map.of("orgId", parentId)), PATH_ORG_INFO);
        long orgType = data.path("orgType").asLong(0L);
        if (orgType <= 0) {
            throw new IllegalStateException("无法从父组织 " + parentId
                    + " 获取 orgType：请在凭据额外参数配置 orgType");
        }
        return orgType;
    }

    /**
     * 删除组织（会级联删除子节点，平台会校验挂设与用户）。
     *
     * @param externalId 组织 ID
     * @return 是否删除成功
     */
    public boolean removeOrganization(String externalId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orgId", requireLong(externalId, "orgId"));
        post(PATH_ORG_DELETE, params);
        return true;
    }

    /**
     * 取组织模板 ID。
     *
     * @return 模板 ID
     */
    private long requireTemplateId() {
        Long configured = extraLong("orgTemplateId");
        if (configured == null) {
            configured = extraLong("templateId");
        }
        if (configured != null) {
            return configured;
        }
        Long cached = cachedTemplateId;
        if (cached != null) {
            return cached;
        }
        JsonNode data = dataOf(get(PATH_CURRENT_USER, Map.of("withPriOrg", 1)), PATH_CURRENT_USER);
        for (JsonNode group : data.path("orgPriGroupList")) {
            long templateId = group.path("templateId").asLong(0L);
            if (templateId > 0) {
                cachedTemplateId = templateId;
                return templateId;
            }
        }
        throw new IllegalStateException("大华云组织接口需要 templateId：当前用户无 orgPriGroupList，"
                + "请在凭据额外参数配置 orgTemplateId");
    }

    // ------------------------------------------------------------------ 人员

    /**
     * 分页拉取平台用户。
     *
     * @return 人员条目
     */
    public List<DevicePerson> fetchPersons() {
        List<DevicePerson> result = new ArrayList<>();
        int pageNum = 1;
        int totalPage = 1;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", PAGE_SIZE);
            JsonNode data = dataOf(post(PATH_USER_PAGE, params), PATH_USER_PAGE);
            for (JsonNode node : data.path("pageData")) {
                result.add(new DevicePerson(
                        node.path("orgId").asText(null),
                        node.path("userId").asText(""),
                        node.path("name").asText(""),
                        null,
                        node.path("telephone").asText(null),
                        null,
                        null,
                        null));
            }
            totalPage = data.path("totalPage").asInt(1);
            pageNum++;
        } while (pageNum <= totalPage);
        return result;
    }

    /**
     * 新增或更新平台用户。
     *
     * <p>大华平台用户模型只有姓名/手机/组织，不含职称、邮箱、证件字段；
     * 新增用户必须提供初始密码，从凭据 {@code extra.defaultPersonPassword} 读取，
     * 不擅自用手机号后几位充当密码。</p>
     *
     * @param person 人员
     * @return 用户 ID
     */
    public String upsertPerson(DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        if (person.phone() == null || person.phone().isBlank()) {
            throw new IllegalArgumentException("大华平台用户以手机号登录，person.phone 不能为空");
        }
        Long orgId = person.orgId() == null || person.orgId().isBlank()
                ? null : requireLong(person.orgId(), "orgId");
        if (person.externalId() != null && !person.externalId().isBlank()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("userId", requireLong(person.externalId(), "userId"));
            params.put("name", person.name());
            if (orgId != null) {
                params.put("organizationId", orgId);
            }
            post(PATH_USER_UPDATE, params);
            return person.externalId();
        }
        String password = extraText("defaultPersonPassword");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "大华云新增用户需要初始密码：请在凭据额外参数配置 defaultPersonPassword（明文，写入时 Base64 编码）");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("telephone", person.phone());
        params.put("password", Base64.getEncoder().encodeToString(utf8(password)));
        params.put("name", person.name() == null || person.name().isBlank() ? person.phone() : person.name());
        if (orgId != null) {
            params.put("organizationId", orgId);
        }
        JsonNode data = dataOf(post(PATH_USER_ADD, params), PATH_USER_ADD);
        String userId = firstText(data, "userId");
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("大华云新增用户未返回 userId: " + data);
        }
        return userId;
    }

    /**
     * 删除平台用户。
     *
     * @param personId 用户 ID
     * @return 是否删除成功
     */
    public boolean removePerson(String personId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userIds", List.of(requireLong(personId, "userId")));
        post(PATH_USER_DELETE, params);
        return true;
    }

    // ------------------------------------------------------------------ 设备写

    /**
     * 添加设备（业务侧接口，可同时设置名称与组织）。
     *
     * @param deviceId 设备序列号
     * @param name     设备名称
     * @param devCode  设备密码（API 调试口径：未加密原始密码）
     * @param orgId    所属组织，可为空
     */
    public void addDevice(String deviceId, String name, String devCode, Long orgId) {
        if (deviceId == null || deviceId.isBlank() || devCode == null || devCode.isBlank()) {
            throw new IllegalArgumentException("添加设备必须提供 deviceId 与 devCode");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", deviceId);
        params.put("name", name == null || name.isBlank() ? deviceId : name);
        params.put("devCode", devCode);
        if (orgId != null) {
            params.put("orgId", orgId);
        }
        post(PATH_DEVICE_ADD, params);
    }

    /**
     * 修改设备名称，或指定通道时修改通道名称。
     *
     * @param deviceId  设备 ID
     * @param channelId 通道 ID，可为空
     * @param name      新名称
     */
    public void renameDevice(String deviceId, String channelId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", deviceId);
        params.put("name", name);
        if (channelId != null && !channelId.isBlank()) {
            params.put("channelId", channelId);
        }
        post(PATH_DEVICE_RENAME, params);
    }

    /**
     * 删除设备。
     *
     * @param deviceId 设备序列号
     */
    public void removeDevice(String deviceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", deviceId);
        post(PATH_DEVICE_DELETE, params);
    }

    /**
     * 校验设备密码。
     *
     * @param deviceId 设备序列号
     * @param devCode  设备密码
     */
    public void verifyDevCode(String deviceId, String devCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", deviceId);
        params.put("devCode", devCode);
        post(PATH_DEVICE_VERIFY, params);
    }

    // ------------------------------------------------------------------ 通用工具

    /**
     * 解析时间文本（官方 yyyy-MM-dd HH:mm:ss），失败返回 null。
     *
     * @param value 时间文本
     * @return 时间
     */
    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, F_RECORD);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 毫秒时间戳转本地时间。
     *
     * @param millis 毫秒
     * @return 本地时间；0 返回 null
     */
    private static LocalDateTime timeOf(long millis) {
        return millis <= 0 ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * 本地时间转毫秒时间戳。
     *
     * @param time 本地时间
     * @return 毫秒
     */
    private static long millisOf(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * JSON 序列化，失败抛异常。
     *
     * @param value 对象
     * @return JSON
     */
    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("大华云请求参数序列化失败", e);
        }
    }

    /**
     * 取凭据扩展字段文本。
     *
     * @param key 键
     * @return 文本，缺失返回 null
     */
    private String extraText(String key) {
        Object value = info.extra() == null ? null : info.extra().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 取凭据扩展字段长整数。
     *
     * @param key 键
     * @return 数字，缺失或非法返回 null
     */
    private Long extraLong(String key) {
        String value = extraText(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 校验并解析长整数。
     *
     * @param value 文本
     * @param field 字段名
     * @return 数字
     */
    private static long requireLong(String value, String field) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " 必须为数字: " + value);
        }
    }

    /**
     * 取设备名称：详情接口取不到时退回设备序列号。
     *
     * @return 设备名称
     */
    private String deviceName() {
        String cached = cachedDeviceName;
        if (cached != null) {
            return cached;
        }
        try {
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("deviceId", getDeviceId());
            JsonNode data = dataOf(post(PATH_DEVICE_DETAILS,
                    Map.of("deviceList", List.of(device))), PATH_DEVICE_DETAILS);
            for (JsonNode node : detailsOf(data)) {
                String name = firstText(node, "deviceName", "name");
                if (name != null && !name.isBlank()) {
                    cachedDeviceName = name;
                    return name;
                }
            }
        } catch (Exception e) {
            log.debug("[DahuaCloud] 取设备名称失败，退回序列号: {}", e.getMessage());
        }
        cachedDeviceName = getDeviceId();
        return cachedDeviceName;
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
}
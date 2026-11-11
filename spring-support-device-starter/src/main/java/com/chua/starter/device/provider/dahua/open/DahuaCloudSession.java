// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * <h3>能力边界（2026-11-11 逐条探活结论）</h3>
 * <p>43 条「基础通用套件」接口里 42 条真实存在，唯一不存在的是
 * {@code /open-api/api-iot/device/getDeviceChannelInfo}（{@code BIZ120 The url does not exist}），
 * 因此通道列表改由已验证的
 * {@code /open-api/api-iot/device/listDeviceDetailsByIds}（入参
 * {@code deviceList:[{deviceId}]}）取回设备详情后再解析。</p>
 *
 * <p>视频监控、云存储、云联、门禁子系统<b>不在这 43 条接口里</b>，其官方文档详情接口
 * 一律返回 {@code OPF600001 鉴权失败}（需控制台登录态），请求字段无法取证。
 * 按「不编造未确认字段」的约定，实时预览、回放、录像检索、抓图、云台、预置位、
 * 门禁记录与远程开门在本平台一律显式抛 {@link UnsupportedOperationException}，
 * 能力矩阵对应位置为 {@code TODO}，前端据此隐藏入口。</p>
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
     * 未取证能力的统一说明
     */
    private static final String NO_DOC_FIELDS =
            "大华云 IoT 开放平台未提供该接口文档字段（视频监控/门禁子系统的文档详情需控制台登录，返回 OPF600001），未取证不猜字段";

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
        return execute("POST", path, body, headers(timestamp, nonce, sign(factor), withAuth));
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

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
     */
    public String getLiveUrl(int channel, String protocol) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供实时预览接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * 获取playbackurl
     * @param channel 整数
     * @param range 参数
     * @return 返回值
     */
    public String getPlaybackUrl(int channel, TimeRange range) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供录像回放接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * 查询Records
     * @param channel 整数
     * @param range 参数
     * @return 返回值
     */
    public List<com.chua.starter.device.spi.model.RecordFile> queryRecords(int channel, TimeRange range) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供录像检索接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * Preview
     * @param channel 整数
     * @return 返回值
     */
    public java.io.InputStream preview(int channel) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供实时预览接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * 抓图
     * @param channel 通道
     * @return 返回值
     */
    public java.io.InputStream capture(int channel) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供抓图接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * ptzcontrol
     * @param cmd 参数
     * @param speed 双精度数
     * @return 返回值
     */
    public void ptzControl(com.chua.starter.device.spi.model.PtzCommand cmd, double speed) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供云台控制接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * 查询accessrecords
     * @param range 参数
     * @return 返回值
     */
    public List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(TimeRange range) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供门禁记录接口：" + NO_DOC_FIELDS);
    }

    @Override
    /**
     * 远程解锁
     * @param doorId 整数
     * @return 返回值
     */
    public void remoteUnlock(int doorId) {
        throw new UnsupportedOperationException("大华云 IoT 平台未提供远程开门接口：" + NO_DOC_FIELDS);
    }
}
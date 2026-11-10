package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import com.hikvision.artemis.sdk.Client;
import com.hikvision.artemis.sdk.Request;
import com.hikvision.artemis.sdk.Response;
import com.hikvision.artemis.sdk.enums.Method;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海康综合安防平台（iSecure Center）SDK 实现。
 *
 * <p><b>官方 SDK</b>：{@code com.hikvision.ga:artemis-http-client:1.1.3}。
 * 签名（x-ca-key / x-ca-timestamp / x-ca-nonce / x-ca-signature）由 SDK 内部完成，
 * 业务代码不再手写 HMAC。</p>
 *
 * <p><b>为什么用 {@link Client#execute(Request)} 而不是 {@code ArtemisHttpUtil} 静态方法</b>：
 * {@code ArtemisConfig.host/appKey/appSecret} 是 <b>static 字段</b>，多套凭据并发调用会互相覆盖；
 * {@link Request} 每次自带 host/appKey/appSecret，天然线程安全，符合本模块「同一厂商多套凭据」的模型。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class HikvisionArtemisSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "secure-platform";

    /** 默认网关地址（官方 SDK 要求带协议前缀） */
    private static final String DEFAULT_HOST = "https://artemis.hikvision.com";

    /** 设备分页查询（综合安防 resource 服务） */
    private static final String PATH_DEVICE_PAGE = "/artemis/api/resource/v2/device/search";

    /** 组织分页查询 */
    private static final String PATH_ORG_PAGE = "/artemis/api/resource/v1/org/orgList";

    /** 监控点分页查询 */
    private static final String PATH_CAMERA_PAGE = "/artemis/api/resource/v2/camera/search";

    /** 取流地址 */
    private static final String PATH_PREVIEW = "/artemis/api/video/v2/cameras/previewURLs";

    /** 云台控制 */
    private static final String PATH_PTZ = "/artemis/api/video/v1/ptz/controlling";

    /** 请求超时（毫秒） */
    private static final int TIMEOUT = 20_000;

    /** 分页大小 */
    private static final int PAGE_SIZE = 200;

    /**
     * 构造 SDK 实现。
     *
     * @param info 连接信息
     */
    public HikvisionArtemisSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public boolean officialSdk() {
        return true;
    }

    @Override
    public com.chua.starter.device.provider.sdk.PlatformApiVersion apiVersion() {
        // 综合安防：设备/监控点分页用 v2（resource/v2），组织与云台用 v1，此处按「主接口版本」记录
        return com.chua.starter.device.provider.sdk.PlatformApiVersion.documented(
                "artemis-sdk-1.1.3",
                "https://open.hikvision.com/docs/docId?tagPath=%E5%BC%80%E5%8F%91%E5%89%8D%E5%87%86%E5%A4%87");
    }

    @Override
    public java.util.List<com.chua.starter.device.provider.sdk.PlatformApiSpec> apis() {
        return HikvisionArtemisApis.all();
    }

    @Override
    public com.chua.starter.device.provider.sdk.PlatformApiResult invoke(
            String code, java.util.Map<String, Object> params) {
        com.chua.starter.device.provider.sdk.PlatformApiSpec spec = HikvisionArtemisApis.all().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElse(null);
        if (spec == null) {
            return com.chua.starter.device.provider.sdk.PlatformApiResult.fail(
                    code, "未登记的接口编码: " + code, null, 0L);
        }
        long start = System.currentTimeMillis();
        try {
            JsonNode data = post(spec.path(), params == null ? new java.util.LinkedHashMap<>() : params);
            Object normalized = data == null || data.isNull() ? null : MAPPER.convertValue(data, Object.class);
            return com.chua.starter.device.provider.sdk.PlatformApiResult.ok(
                    code, normalized, data == null ? null : data.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return com.chua.starter.device.provider.sdk.PlatformApiResult.fail(
                    code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 网关 host（官方 SDK 的 Request.setHost 需要完整的 URL 前缀，含协议）。
     *
     * <p>海康 Artemis SDK 的 {@code HttpUtil.initUrl(host, path, ...)} 直接拼接
     * {@code host + path}，若 host 不带 {@code http(s)://}，生成的 URL 是相对路径，
     * Apache HttpClient 会抛 {@code IllegalStateException: Target host is null}。</p>
     *
     * @return 带协议前缀的 base url，无尾斜杠
     */
    private String host() {
        String base = extra("baseUrl");
        if (base == null || base.isBlank()) {
            base = DEFAULT_HOST;
        }
        String normalized = base.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized;
    }

    @Override
    public boolean testConnection() {
        try {
            JsonNode data = post(PATH_ORG_PAGE, Map.of("pageNo", 1, "pageSize", 1));
            // code 为 0/200 且能取到 data 即视为凭据有效；组织为空也算连通
            return data != null;
        } catch (Exception ex) {
            log.warn("[HikvisionArtemis] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : page(PATH_DEVICE_PAGE)) {
            result.add(new VendorDevice(
                    str(node, "indexCode", "deviceIndexCode", "id"),
                    str(node, "name", "deviceName"),
                    str(node, "deviceType", "model", "deviceModel"),
                    "camera",
                    str(node, "indexCode", "deviceIndexCode", "serial"),
                    str(node, "ip", "deviceIp"),
                    integer(node, "port", "devicePort"),
                    str(node, "regionName", "address"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    integer(node, "channelNum", "channelCount"),
                    bool(node, "online", "status")));
        }
        return result;
    }

    @Override
    public List<VendorDevice> listMonitors() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : page(PATH_CAMERA_PAGE)) {
            result.add(new VendorDevice(
                    str(node, "cameraIndexCode", "indexCode", "id"),
                    str(node, "cameraName", "name"),
                    str(node, "cameraType", "model"),
                    "camera",
                    str(node, "deviceIndexCode", "cameraIndexCode", "serial"),
                    str(node, "deviceIp", "ip"),
                    integer(node, "devicePort", "port"),
                    str(node, "regionName", "address"),
                    decimal(node, "latitude", "lat"),
                    decimal(node, "longitude", "lng"),
                    1,
                    bool(node, "online", "status")));
        }
        return result;
    }

    @Override
    public List<OrganizationNode> listOrganizations() {
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : page(PATH_ORG_PAGE)) {
            result.add(new OrganizationNode(
                    str(node, "orgIndexCode", "indexCode", "id"),
                    str(node, "orgName", "name"),
                    str(node, "parentOrgIndexCode", "parentIndexCode", "parentId")));
        }
        return result;
    }

    @Override
    public String liveUrl(int channel, String protocol, String streamType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", info.deviceSerial());
        body.put("streamType", "sub".equalsIgnoreCase(streamType) ? 1 : 0);
        body.put("protocol", protocol == null || protocol.isBlank() ? "hls" : protocol);
        body.put("transmode", 1);
        JsonNode data = post(PATH_PREVIEW, body);
        if (data == null) {
            return "";
        }
        String url = str(data, "url", "hls", "rtsp");
        return url == null ? "" : url;
    }

    @Override
    public void ptz(int channel, PtzCommand cmd, double speed) {
        if (cmd == null || cmd == PtzCommand.STOP) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", info.deviceSerial());
        body.put("action", direction(cmd));
        body.put("speed", (int) Math.max(1, Math.min(255, Math.round(speed * 255))));
        post(PATH_PTZ, body);
    }

    /**
     * 方向映射（综合安防云台 action 码）。
     *
     * @param cmd 方向
     * @return action 码
     */
    private static int direction(PtzCommand cmd) {
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

    /**
     * 分页拉取全部条目。
     *
     * @param path 接口路径
     * @return 全量条目
     */
    private List<JsonNode> page(String path) {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= 200; pageNo++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode data = post(path, body);
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /**
     * 发起一次带签名的 POST，返回 data 节点。
     *
     * @param path 接口路径
     * @param body 请求体
     * @return data 节点；平台报错时抛异常
     */
    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String json;
            try {
                json = MAPPER.writeValueAsString(body);
            } catch (Exception ex) {
                throw new IllegalStateException("请求体序列化失败", ex);
            }
            Request request = new Request();
            request.setMethod(Method.POST_STRING);
            request.setHost(host());
            request.setPath(path);
            request.setAppKey(info.appKey());
            request.setAppSecret(info.appSecret());
            request.setTimeout(TIMEOUT);
            request.setStringBody(json);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            request.setHeaders(headers);
            Response response = Client.execute(request);
            String content = response == null ? null : response.getBody();
            JsonNode root = parse(content);
            if (root == null) {
                throw new IllegalStateException("海康安防响应非 JSON: HTTP "
                        + (response == null ? "-" : response.getStatusCode())
                        + (content == null ? "" : " " + content.substring(0, Math.min(200, content.length()))));
            }
            String code = str(root, "code");
            if (code != null && !"0".equals(code) && !"200".equals(code)) {
                throw new IllegalStateException("海康安防返回失败: code=" + code + ", msg=" + str(root, "msg", "message"));
            }
            return root.get("data");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("海康安防 SDK 调用失败: " + path + " -> " + ex.getMessage(), ex);
        }
    }
}

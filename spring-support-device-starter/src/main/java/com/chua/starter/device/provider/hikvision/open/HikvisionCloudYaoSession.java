// [P3C CO-循环 豁免]

// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceDoorInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 海康云曜（hikyun 外网云）OpenAPI 会话。
 *
 * <h3>凭据与认证（官方文档 3.1 节）</h3>
 * <ul>
 *   <li>换令牌：{@code POST https://open.hikyun.com/artemis/oauth/token/v2}，JSON 包体
 *   {@code accessKey / secretKey / productCode / projectId}；返回 {@code data.access_token}
 *   与 {@code data.expires_in}（43200 秒）</li>
 *   <li>业务请求头：{@code access_token: <token>}。<b>不是</b> {@code Authorization: Bearer}，
 *   实测后者会被网关当成「未传 access_token」返回 {@code 0x02401005}</li>
 *   <li>官方文档里部分接口的「请求入参」写的是 {@code Authorization: Head}（用户凭证，
 *   由 {@code /api/eits/v1/global/jwt} 换取）。AK/SK 换不到用户凭证，因此本会话在凭据
 *   扩展参数带 {@code userToken} 时补发该请求头，否则只用 {@code access_token}；
 *   缺用户凭证的接口由调用方按平台报错处理，不静默吞掉</li>
 * </ul>
 *
 * <h3>接口口径（全部取自官方文档，路径均带 {@code /artemis} 前缀）</h3>
 * <ul>
 *   <li>设备分页：{@code POST /artemis/api/eits/v2/global/device/page}，
 *       包体是<b>条件数组</b> {@code [{key,option,value}]}（pageNo / pageSize / projectId），
 *       响应 {@code data.list[]} 含 deviceSerial / deviceName / model / modelType /
 *       channumCount / onlineStatus / deviceOrgName</li>
 *   <li>监控点分页：{@code POST /artemis/api/eits/v2/global/monitor/page}，
 *       包体 {@code {projectId, pageNo, pageSize}}，响应 list 项含 monitorCode（序列号#通道号）/
 *       monitorName / channum / onlineStatus / moduleType / deviceName / model</li>
 *   <li>通道：{@code POST /artemis/api/eits/v1/global/v1/device/camera/list}，
 *       包体 {@code {deviceSerial, projectId?}}，响应 {@code data[]} 含 channelNo / channelName /
 *       status / deviceName</li>
 *   <li>抓图：{@code POST /artemis/api/eits/v1/global/monitor/capture}，
 *       包体 {@code {deviceSerial, channelNo, projectId?}}，<b>data 就是图片 URL 字符串</b>（2 小时有效），
 *       需要二次下载成字节流</li>
 *   <li>取流：{@code POST /artemis/api/eits/v1/global/live/address/get/by/deviceSerial}，
 *       预览与回放共用，靠 {@code type}（1 预览 / 2 本地回放 / 3 云存回放）+
 *       {@code startTime}/{@code stopTime} 区分；回放官方只支持 rtmp / ezopen / flv</li>
 *   <li>云台：{@code POST /artemis/api/eits/v1/global/device/ptz}，包体
 *       {@code {channelNo, deviceSerial, direction, speed, type}}，direction 0~11 与 16，
 *       speed 0 慢 / 1 适中 / 2 快，type 0 开始 / 1 停止</li>
 *   <li>预置位：{@code preset/add}（只收通道与序列号，编号由平台分配并在 {@code data.index} 返回）、
 *       {@code preset/move}、{@code preset/clear}（都收 {@code index}）</li>
 *   <li>组织/人员：{@code /artemis/api/eits/fdcs/v1/personOrg/*} 与 {@code .../person/*}
 *       （personOrg/page、personOrg/add、personOrg/update、personOrg/batchDelete；
 *       person/page、person/add、person/update、person/delete）</li>
 * </ul>
 *
 * <h3>平台未提供的能力（显式失败，不返回空列表冒充成功）</h3>
 * <ul>
 *   <li>门禁点：只有路径 {@code /api/eits/v1/global/acspoint/search/byDeviceSerial}，
 *       官方文档没有请求/响应字段，无法按字段实现 → {@link #listDoors()} 抛异常</li>
 *   <li>远程开门：artemis 接口目录里没有开门接口 → {@link #remoteUnlock(int)} 抛异常</li>
 *   <li>预置位查询：artemis 目录里只有 add / move / clear → 抛异常</li>
 *   <li>录像片段查询：有 {@code /api/eits/v1/global/monitor/video/by/time} 路径，
 *       但缺官方字段文档 → 抛异常（回放地址用 {@link #getPlaybackUrl(int, TimeRange)}）</li>
 *   <li>门禁出入记录、事件拉取：均无对应接口 → 抛异常</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionCloudYaoSession extends AbstractOpenApiSession {

    /**
     * 海康云曜固定网关。
     * <p>云曜对外开放地址为 <a href="https://open.hikyun.com">https://open.hikyun.com</a>；
     * 仅综合安防等私有化平台允许凭据覆盖 baseUrl。</p>
     */
    private static final String DEFAULT_BASE_URL = "https://open.hikyun.com";

    /** 换 access_token 接口（官方：JSON 包体，无业务请求头） */
    private static final String PATH_TOKEN = "/artemis/oauth/token/v2";

    /** 设备分页（官方 v2 全局接口，条件数组包体，access_token 头） */
    private static final String PATH_DEVICE_PAGE = "/artemis/api/eits/v2/global/device/page";

    /** 监控点分页（官方 v2 全局接口，对象包体） */
    private static final String PATH_MONITOR_PAGE = "/artemis/api/eits/v2/global/monitor/page";

    /** 按设备序列号取通道（官方接口） */
    private static final String PATH_CAMERA_LIST = "/artemis/api/eits/v1/global/v1/device/camera/list";

    /** 监控点抓图（官方接口，data 是图片 URL） */
    private static final String PATH_CAPTURE = "/artemis/api/eits/v1/global/monitor/capture";

    /** 取播放地址（官方接口，预览/回放共用） */
    private static final String PATH_LIVE_ADDRESS = "/artemis/api/eits/v1/global/live/address/get/by/deviceSerial";

    /** 设备云台控制（官方接口） */
    private static final String PATH_PTZ = "/artemis/api/eits/v1/global/device/ptz";

    /** 设置预置点（官方接口，编号由平台分配） */
    private static final String PATH_PRESET_ADD = "/artemis/api/eits/v1/global/device/preset/add";

    /** 调用预置点（官方接口） */
    private static final String PATH_PRESET_MOVE = "/artemis/api/eits/v1/global/device/preset/move";

    /** 删除预置点（官方接口） */
    private static final String PATH_PRESET_CLEAR = "/artemis/api/eits/v1/global/device/preset/clear";

    /** 人员分页（官方 fdcs 接口） */
    private static final String PATH_PERSON_PAGE = "/artemis/api/eits/fdcs/v1/person/page";

    /** 新增人员（官方 fdcs 接口） */
    private static final String PATH_PERSON_ADD = "/artemis/api/eits/fdcs/v1/person/add";

    /** 更新人员（官方 fdcs 接口） */
    private static final String PATH_PERSON_UPDATE = "/artemis/api/eits/fdcs/v1/person/update";

    /** 删除人员（官方 fdcs 接口，批量） */
    private static final String PATH_PERSON_DELETE = "/artemis/api/eits/fdcs/v1/person/delete";

    /** 组织分页（官方 fdcs 接口） */
    private static final String PATH_ORG_PAGE = "/artemis/api/eits/fdcs/v1/personOrg/page";

    /** 新增组织（官方 fdcs 接口） */
    private static final String PATH_ORG_ADD = "/artemis/api/eits/fdcs/v1/personOrg/add";

    /** 更新组织（官方 fdcs 接口） */
    private static final String PATH_ORG_UPDATE = "/artemis/api/eits/fdcs/v1/personOrg/update";

    /** 批量删除组织（官方 fdcs 接口） */
    private static final String PATH_ORG_BATCH_DELETE = "/artemis/api/eits/fdcs/v1/personOrg/batchDelete";

    /** 添加设备（官方 v2 全局接口，萤石/NB-IOT/国标/部标协议设备，添加后自动激活） */
    private static final String PATH_DEVICE_SAVE = "/artemis/api/eits/v2/global/device/ezviz/save";

    /** 批量修改设备（官方 v2 全局接口） */
    private static final String PATH_DEVICE_UPDATE = "/artemis/api/eits/v2/global/device/update";

    /** 批量删除设备（官方 v2 全局接口） */
    private static final String PATH_DEVICE_DELETE = "/artemis/api/eits/v2/global/device/delete";

    /** 默认接入协议：EZVIZ 私有协议（官方 treatyType 枚举里最通用的一个） */
    private static final String TREATY_EZVIZ_NET = "ezviz_net";

    /** 设备大类：视频产品（官方 deviceCategory 枚举值） */
    private static final String CATEGORY_VIDEO = "Video";

    /** 分页每页条数（官方示例 20，这里取 100 减少往返） */
    private static final int PAGE_SIZE = 100;

    /** 分页翻页上限，防御平台不返回 total 时死循环 */
    private static final int MAX_PAGES = 200;

    /** 取流协议：播放地址接口 protocol=2 表示 HLS */
    private static final int PROTOCOL_HLS = 2;

    /** 取流协议：播放地址接口 protocol=3 表示 RTMP */
    private static final int PROTOCOL_RTMP = 3;

    /** 取流协议：播放地址接口 protocol=4 表示 FLV */
    private static final int PROTOCOL_FLV = 4;

    /** 取流协议：播放地址接口 protocol=1 表示 ezopen */
    private static final int PROTOCOL_EZOPEN = 1;

    /** 地址类型：1 预览 */
    private static final String LIVE_TYPE_PREVIEW = "1";

    /** 地址类型：2 本地录像回放 */
    private static final String LIVE_TYPE_LOCAL_PLAYBACK = "2";

    /** 清晰度：1 高清（主码流） */
    private static final int QUALITY_MAIN = 1;

    /** 清晰度：2 流畅（子码流） */
    private static final int QUALITY_SUB = 2;

    /** 云台控制类型：0 开始 */
    private static final int PTZ_START = 0;

    /** 云台控制类型：1 停止 */
    private static final int PTZ_STOP = 1;

    /** 云台速度：0 慢 */
    private static final int PTZ_SPEED_SLOW = 0;

    /** 云台速度：1 适中 */
    private static final int PTZ_SPEED_NORMAL = 1;

    /** 云台速度：2 快 */
    private static final int PTZ_SPEED_FAST = 2;

    /** 官方文档约定 -1 表示设备未上报在线状态 */
    private static final int ONLINE_UNREPORTED = -1;

    /** 官方取流时间格式：yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 抓图下载客户端（图片在萤石对象存储，需要单独 GET） */
    private static final HttpClient DOWNLOADER = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 抓图下载重试次数 */
    private static final int DOWNLOAD_RETRY = 1;

    /** 云曜产品编码（ProductCode）：平台侧按产品维度隔离设备 */
    private final String productCode;

    /** 云曜项目 ID（ProjectID）：平台侧按项目维度隔离设备 */
    private final String projectId;

    /**
     * 用户凭证（{@code Authorization: Head <token>}）。
     * <p>官方文档部分接口标注「用户凭证，调用获取全局token接口
     * {@code /api/eits/v1/global/jwt} 获取」。AK/SK 换不到该凭证，
     * 由凭据扩展参数 {@code userToken} 显式提供；缺省为 {@code null}。</p>
     */
    private final String userToken;

    /** 云台默认通道号（取流/云台/预置位共用，凭据扩展参数 channelNo 可覆盖） */
    private final int defaultChannel;

    /** 云台 STOP 需要带回方向，记录最近一次方向码 */
    private volatile int lastDirection;

    /**
     * 构造云曜会话。
     *
     * @param info 设备连接信息（appKey=AccessKey，appSecret=SecretKey，extra 可带
     *             productCode / projectId / userToken / channelNo）
     */
    public HikvisionCloudYaoSession(DeviceConnectInfo info) {
        super(info, DEFAULT_BASE_URL);
        this.productCode = extraString(info, "productCode");
        this.projectId = extraString(info, "projectId");
        this.userToken = firstNonBlank(extraString(info, "userToken"),
                extraString(info, "authorization"), extraString(info, "userAuthorization"));
        Integer channel = extraInt(info, "channelNo");
        this.defaultChannel = channel == null || channel <= 0 ? 1 : channel;
        login();
    }

    // ------------------------------------------------------------------ 认证

    /**
     * 登录换取 access_token（官方 {@code POST /artemis/oauth/token/v2}）。
     *
     * <p>字段名必须是 accessKey / secretKey；用 appKey / appSecret 会被网关拒绝为
     * 「参数 secretKey 不能为空」。令牌有效期默认 43200 秒（12 小时）。</p>
     */
    private void login() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("accessKey", info.appKey());
        params.put("secretKey", info.appSecret());
        if (productCode != null) {
            params.put("productCode", productCode);
        }
        if (projectId != null) {
            params.put("projectId", projectId);
        }
        try {
            JsonNode resp = execute("POST", PATH_TOKEN, params, null);
            JsonNode data = assertOk(resp);
            String token = firstToken(data);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("海康云曜 accessToken 为空");
            }
            cacheToken(token, firstExpires(data));
            log.info("海康云曜登录成功: productCode={}, accessToken={}...", productCode, maskToken(token));
        } catch (Exception e) {
            throw new IllegalStateException("海康云曜登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取 access_token 的值（新旧字段名都接受：access_token / accessToken）。
     *
     * @param data 响应的 data 节点
     * @return 令牌；均未找到时返回 {@code null}
     */
    private static String firstToken(JsonNode data) {
        String token = data.path("access_token").asText(null);
        if (token == null || token.isEmpty()) {
            token = data.path("accessToken").asText(null);
        }
        return token;
    }

    /**
     * 取 access_token 有效期（秒，新格式 expires_in、旧格式 expiresIn 都接受）。
     *
     * @param data 响应的 data 节点
     * @return 有效期秒数，缺省 43200
     */
    private static long firstExpires(JsonNode data) {
        long seconds = data.path("expires_in").asLong(0L);
        if (seconds <= 0L) {
            seconds = data.path("expiresIn").asLong(43200L);
        }
        return seconds;
    }

    /**
     * 脱敏 access_token 后显示（令牌不回显全文）。
     *
     * @param token 令牌
     * @return 脱敏后的令牌
     */
    private static String maskToken(String token) {
        if (token == null || token.length() <= 6) {
            return "****";
        }
        return token.substring(0, 2) + "****" + token.substring(token.length() - 4);
    }

    /**
     * 组装请求头：始终带 {@code access_token}；凭据带 userToken 时补
     * {@code Authorization: Head <userToken>}（官方文档要求用户凭证的接口）。
     *
     * @return 请求头
     */
    private Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (accessToken != null) {
            headers.put("access_token", accessToken);
        }
        if (userToken != null) {
            headers.put("Authorization", "Head " + userToken);
        }
        return headers;
    }

    @Override
    /**
     * 执行请求：自动续令牌，不向包体注水（云曜各接口包体口径不一致，注入会破坏条件数组）。
     *
     * @param method HTTP 方法
     * @param path   接口路径（含 /artemis 前缀）
     * @param params 业务包体
     * @return 响应 JSON
     */
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        if (!PATH_TOKEN.equals(path) && !tokenValid()) {
            login();
        }
        return execute(method, path, params, PATH_TOKEN.equals(path) ? null : authHeaders());
    }

    // ------------------------------------------------------------------ 通道

    @Override
    /**
     * 通道列表：官方 {@code POST /artemis/api/eits/v1/global/v1/device/camera/list}。
     *
     * <p>包体只有 {@code deviceSerial}（设备在项目下时补 {@code projectId}）；
     * 响应 {@code data[]} 直接是通道数组，含 channelNo / channelName / status。</p>
     *
     * @return 通道列表
     */
    public List<DeviceChannel> listChannels() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        if (projectId != null) {
            body.put("projectId", projectId);
        }
        JsonNode data = call(PATH_CAMERA_LIST, body);
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : data.isArray() ? data : data.path("data")) {
            Integer channelNo = toInt(node, "channelNo");
            String serial = toStr(node, "deviceSerial");
            String channelName = toStr(node, "channelName", "deviceName");
            int status = node.path("status").asInt(ONLINE_UNREPORTED);
            result.add(new DeviceChannel(
                    (serial == null ? requireSerial() : serial) + "#" + (channelNo == null ? 0 : channelNo),
                    channelNo,
                    channelName,
                    serial == null ? requireSerial() : serial,
                    status == ONLINE_UNREPORTED ? null : status == 1,
                    null,
                    1));
        }
        return result;
    }

    // ------------------------------------------------------------------ 抓图

    @Override
    /**
     * 抓图：官方 {@code POST /artemis/api/eits/v1/global/monitor/capture}。
     *
     * <p>平台返回的 {@code data} 是图片 URL 字符串（有效期 2 小时），需要二次下载；
     * 这里下成 JPEG 字节流返回。</p>
     *
     * @param channel 通道号
     * @return 图片字节流
     */
    public InputStream capture(int channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel <= 0 ? defaultChannel : channel);
        if (projectId != null) {
            body.put("projectId", projectIdValue());
        }
        JsonNode resp = assertOk(executeJson("POST", PATH_CAPTURE, body, authHeaders()));
        String url = resp.isTextual() ? resp.asText() : toStr(resp, "url", "picUrl");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("海康云曜抓图未返回图片地址: " + resp);
        }
        return download(url);
    }

    /**
     * 下载抓图（图片在萤石对象存储，不在云曜网关域下，必须单独 GET）。
     *
     * @param url 图片地址
     * @return 图片字节流
     */
    private static InputStream download(String url) {
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
                last = new IllegalStateException("海康云曜抓图下载失败: HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("海康云曜抓图下载被中断", ex);
            } catch (Exception ex) {
                last = new IllegalStateException("海康云曜抓图下载异常: " + ex.getMessage(), ex);
            }
        }
        throw last == null ? new IllegalStateException("海康云曜抓图下载失败") : last;
    }

    // ------------------------------------------------------------------ 取流

    @Override
    /**
     * 实时预览地址（官方 {@code live/address/get/by/deviceSerial}，{@code type=1}）。
     *
     * @param channel  通道号
     * @param protocol 协议：hls / rtmp / flv / ezopen（缺省 hls）
     * @return 播放地址
     */
    public String getLiveUrl(int channel, String protocol) {
        return liveUrl(channel, protocol, null, LIVE_TYPE_PREVIEW, null);
    }

    @Override
    /**
     * 实时预览地址（带码流选择）。
     *
     * @param channel    通道号
     * @param protocol   协议
     * @param streamType 码流：main 主码流 / sub 子码流
     * @return 播放地址
     */
    public String getLiveUrl(int channel, String protocol, String streamType) {
        return liveUrl(channel, protocol, streamType, LIVE_TYPE_PREVIEW, null);
    }

    @Override
    /**
     * 录像回放地址（官方同一接口，{@code type=2} 本地录像回放）。
     *
     * <p>官方明确「回放仅支持 rtmp、ezopen、flv 协议」，因此固定用 FLV；
     * {@code startTime}/{@code stopTime} 按 {@code yyyy-MM-dd HH:mm:ss} 下发。</p>
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 回放地址
     */
    public String getPlaybackUrl(int channel, TimeRange range) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new IllegalArgumentException("云曜回放必须给时间范围");
        }
        return liveUrl(channel, "flv", null, LIVE_TYPE_LOCAL_PLAYBACK, range);
    }

    /**
     * 取播放地址（预览与回放共用官方接口）。
     *
     * @param channel    通道号
     * @param protocol   协议名
     * @param streamType 码流：main / sub
     * @param type       地址类型：1 预览 / 2 本地回放 / 3 云存回放
     * @param range      回放时间范围，预览传 {@code null}
     * @return 播放地址
     */
    private String liveUrl(int channel, String protocol, String streamType, String type, TimeRange range) {
        if (projectId == null) {
            throw new IllegalStateException(
                    "云曜取流要求 projectId（官方 live/address/get/by/deviceSerial 的 projectId 为必填），"
                            + "请在凭据扩展参数配置 projectId");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectIdValue());
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", channel <= 0 ? defaultChannel : channel);
        body.put("protocol", protocolOf(protocol));
        body.put("quality", streamTypeOf(streamType));
        body.put("type", type);
        if (range != null) {
            body.put("startTime", range.start().format(TIME_FORMAT));
            body.put("stopTime", range.end().format(TIME_FORMAT));
        }
        JsonNode data = call(PATH_LIVE_ADDRESS, body);
        String url = toStr(data, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("海康云曜未返回播放地址: " + data);
        }
        return url;
    }

    /**
     * 协议名转官方协议码：1 ezopen / 2 hls / 3 rtmp / 4 flv。
     *
     * @param protocol 协议名
     * @return 协议码
     */
    private static int protocolOf(String protocol) {
        String value = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "rtmp":
                return PROTOCOL_RTMP;
            case "flv":
                return PROTOCOL_FLV;
            case "ezopen":
                return PROTOCOL_EZOPEN;
            case "hls":
            case "":
                return PROTOCOL_HLS;
            default:
                throw new UnsupportedOperationException(
                        "海康云曜取流不支持该协议: " + protocol + "（只支持 ezopen / hls / rtmp / flv）");
        }
    }

    /**
     * 码流名归一：主码流 1、子码流 2。
     *
     * @param streamType 码流名
     * @return 清晰度码
     */
    private static int streamTypeOf(String streamType) {
        if (streamType == null) {
            return QUALITY_MAIN;
        }
        return "sub".equalsIgnoreCase(streamType.trim()) ? QUALITY_SUB : QUALITY_MAIN;
    }

    // ------------------------------------------------------------------ 云台 / 预置位

    @Override
    /**
     * 云台控制：官方 {@code POST /artemis/api/eits/v1/global/device/ptz}。
     *
     * <p>direction：0 上 / 1 下 / 2 左 / 3 右 / 4 左上 / 5 左下 / 6 右上 / 7 右下 /
     * 8 放大 / 9 缩小 / 10 近焦距 / 11 远焦距 / 16 自动。type：0 开始 / 1 停止。
     * 官方没有光圈指令，{@code IRIS_*} 显式拒绝。</p>
     *
     * @param cmd   指令
     * @param speed 速度（0~1，换算成 0 慢 / 1 适中 / 2 快）
     */
    public void ptzControl(PtzCommand cmd, double speed) {
        if (cmd == null) {
            throw new IllegalArgumentException("云台指令不能为空");
        }
        if (cmd == PtzCommand.IRIS_IN || cmd == PtzCommand.IRIS_OUT) {
            throw new UnsupportedOperationException(
                    "海康云曜 device/ptz 没有光圈指令（direction 只到 0~11 与 16）: " + cmd);
        }
        boolean stop = cmd == PtzCommand.STOP;
        int direction = stop ? lastDirection : directionOf(cmd);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel);
        body.put("direction", direction);
        body.put("type", stop ? PTZ_STOP : PTZ_START);
        body.put("speed", speedOf(speed));
        call(PATH_PTZ, body);
        if (!stop) {
            lastDirection = direction;
        }
    }

    /**
     * 云台指令转官方 direction 码。
     *
     * @param cmd 指令
     * @return direction 码
     */
    private static int directionOf(PtzCommand cmd) {
        switch (cmd) {
            case UP:
                return 0;
            case DOWN:
                return 1;
            case LEFT:
                return 2;
            case RIGHT:
                return 3;
            case ZOOM_IN:
                return 8;
            case ZOOM_OUT:
                return 9;
            case FOCUS_IN:
                return 10;
            case FOCUS_OUT:
                return 11;
            default:
                return 0;
        }
    }

    /**
     * 速度（0~1）换算官方 speed 档位。
     *
     * @param speed 速度
     * @return 档位：0 慢 / 1 适中 / 2 快
     */
    private static int speedOf(double speed) {
        if (speed <= 0.34d) {
            return PTZ_SPEED_SLOW;
        }
        return speed <= 0.67d ? PTZ_SPEED_NORMAL : PTZ_SPEED_FAST;
    }

    @Override
    /**
     * 设置预置位：官方 {@code POST /artemis/api/eits/v1/global/device/preset/add}。
     *
     * <p><b>官方接口只收 channelNo 与 deviceSerial</b>，预置位编号由平台分配并在
     * {@code data.index} 返回；入参 {@code presetId} / {@code name} 无法下发，
     * 仅用于与平台分配的编号核对（不一致时记警告）。</p>
     *
     * @param presetId 期望的预置位编号（平台不支持指定）
     * @param name     预置位名称（平台不支持命名）
     */
    public void setPreset(int presetId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel);
        JsonNode data = call(PATH_PRESET_ADD, body);
        int assigned = data.path("index").asInt(-1);
        if (assigned > 0 && presetId > 0 && assigned != presetId) {
            log.warn("海康云曜预置位编号由平台分配: 期望={}, 实际={}, 名称={}（官方 preset/add 不收编号与名称）",
                    presetId, assigned, name);
        }
    }

    @Override
    /**
     * 调用预置位：官方 {@code POST /artemis/api/eits/v1/global/device/preset/move}。
     *
     * @param presetId 预置位编号
     */
    public void callPreset(int presetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel);
        body.put("index", presetId);
        call(PATH_PRESET_MOVE, body);
    }

    @Override
    /**
     * 删除预置位：官方 {@code POST /artemis/api/eits/v1/global/device/preset/clear}。
     *
     * @param presetId 预置位编号
     */
    public void deletePreset(int presetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", requireSerial());
        body.put("channelNo", defaultChannel);
        body.put("index", presetId);
        call(PATH_PRESET_CLEAR, body);
    }

    @Override
    /**
     * 预置位列表：云曜 artemis 接口目录里只有 preset/add、preset/move、preset/clear，
     * 没有查询预置位列表的接口，按约定显式失败。
     *
     * @return 不会返回
     */
    public List<PresetInfo> listPresets() {
        throw new UnsupportedOperationException(
                "海康云曜 artemis 未提供预置点查询接口（官方仅有 device/preset/add、move、clear）");
    }

    // ------------------------------------------------------------------ 门禁

    @Override
    /**
     * 门禁点列表：官方只有一个路径
     * {@code POST /artemis/api/eits/v1/global/acspoint/search/byDeviceSerial}，
     * 文档没有给出请求与响应字段，无法按字段实现。
     *
     * @return 不会返回
     */
    public List<DeviceDoorInfo> listDoors() {
        throw new UnsupportedOperationException(
                "海康云曜 acspoint/search/byDeviceSerial 只有路径、缺官方字段文档，"
                        + "无法确定请求体与门点字段，暂不实现");
    }

    @Override
    /**
     * 远程开门：云曜 artemis 接口目录（748 条）中没有门禁开门接口，
     * ISAPI 透传需要设备侧具体的门禁 URL 与报文，不能凭空构造。
     *
     * @param doorId 门标识
     */
    public void remoteUnlock(int doorId) {
        throw new UnsupportedOperationException(
                "海康云曜 artemis 未提供远程开门接口（仅 /api/eits/v3/global/isapi 透传，"
                        + "需设备侧门禁 ISAPI 路径与报文），current doorId=" + doorId);
    }

    // ------------------------------------------------------------------ 设备 / 监控点 / 组织 / 人员

    /**
     * 云曜设备目录：官方 {@code POST /artemis/api/eits/v2/global/device/page}。
     *
     * <p>包体是<b>条件数组</b> {@code [{key:"pageNo",option:"eq",value:1}, ...]}，
     * 不是对象；响应 {@code data.list[]} 的 modelType 决定设备分类
     * （CAMERA 视频 / ALARM 报警 / ACCESS 门禁）。</p>
     *
     * @return 厂商设备条目
     */
    public List<VendorDevice> fetchDevices() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : devicePage()) {
            result.add(new VendorDevice(
                    toStr(node, "deviceSerial", "id"),
                    toStr(node, "deviceName"),
                    toStr(node, "model"),
                    categoryOf(toStr(node, "modelType", "modelTypeStr")),
                    toStr(node, "deviceSerial"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    toInt(node, "channumCount"),
                    onlineOf(node)));
        }
        log.info("海康云曜 设备目录: count={}", result.size());
        return result;
    }

    /**
     * 云曜监控点目录：官方 {@code POST /artemis/api/eits/v2/global/monitor/page}。
     *
     * <p>包体 {@code {projectId, pageNo, pageSize}}，projectId 必填；
     * 监控点编号是 {@code 序列号#通道号}，一个设备多路监控点按通道落成独立条目。
     * 缺少 projectId 或该接口未授权（需用户凭证）时，退化为按设备逐台调
     * {@code camera/list} 汇总通道，保证台账仍有监控点数据。</p>
     *
     * @return 厂商设备条目（监控点口径）
     */
    public List<VendorDevice> fetchMonitors() {
        if (projectId == null) {
            log.info("海康云曜 监控点目录: 未配置 projectId，改用 camera/list 按设备汇总通道");
            return monitorByChannel();
        }
        try {
            return monitorByPage();
        } catch (Exception e) {
            log.warn("海康云曜 监控点分页不可用（{}），改用 camera/list 按设备汇总通道", e.getMessage());
            return monitorByChannel();
        }
    }

    /**
     * 监控点分页（官方接口）转厂商设备条目。
     *
     * @return 监控点条目
     */
    private List<VendorDevice> monitorByPage() {
        List<VendorDevice> result = new ArrayList<>();
        for (JsonNode node : monitorPage()) {
            result.add(new VendorDevice(
                    toStr(node, "monitorCode", "monitorSerial", "id"),
                    toStr(node, "monitorName", "osdName", "deviceName"),
                    toStr(node, "model"),
                    categoryOf(toStr(node, "moduleType", "modelTypeStr")),
                    toStr(node, "monitorSerial"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    1,
                    onlineOf(node)));
        }
        log.info("海康云曜 监控点目录: count={}", result.size());
        return result;
    }

    /**
     * 按设备逐台取通道，汇总成监控点条目（{@code camera/list} 官方接口）。
     *
     * @return 监控点条目
     */
    private List<VendorDevice> monitorByChannel() {
        List<VendorDevice> result = new ArrayList<>();
        for (VendorDevice device : fetchDevices()) {
            try {
                for (DeviceChannel channel : channelsOf(device.serial())) {
                    result.add(new VendorDevice(
                            channel.channelId(),
                            channel.name(),
                            channel.model(),
                            "camera",
                            channel.deviceSerial(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            1,
                            channel.online()));
                }
            } catch (Exception e) {
                log.warn("海康云曜 通道拉取失败: serial={}, err={}", device.serial(), e.getMessage());
            }
        }
        log.info("海康云曜 监控点目录（camera/list 汇总）: count={}", result.size());
        return result;
    }

    /**
     * 指定设备序列号的通道（camera/list）。
     *
     * @param serial 设备序列号
     * @return 通道列表
     */
    private List<DeviceChannel> channelsOf(String serial) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", serial);
        if (projectId != null) {
            body.put("projectId", projectId);
        }
        JsonNode data = call(PATH_CAMERA_LIST, body);
        List<DeviceChannel> result = new ArrayList<>();
        for (JsonNode node : data.isArray() ? data : data.path("data")) {
            Integer channelNo = toInt(node, "channelNo");
            int status = node.path("status").asInt(ONLINE_UNREPORTED);
            result.add(new DeviceChannel(
                    serial + "#" + (channelNo == null ? 0 : channelNo),
                    channelNo,
                    toStr(node, "channelName", "deviceName"),
                    serial,
                    status == ONLINE_UNREPORTED ? null : status == 1,
                    null,
                    null));
        }
        return result;
    }

    /**
     * 云曜组织树：官方 {@code POST /artemis/api/eits/fdcs/v1/personOrg/page}。
     *
     * <p>包体 {@code {projectId, productCode, pageNo, pageSize}}（前两个必填），
     * 响应 list 项含 orgId / orgName / parentOrgId / orgPathId / orgCode。</p>
     *
     * @return 组织节点
     */
    public List<OrganizationNode> fetchOrganizations() {
        List<OrganizationNode> result = new ArrayList<>();
        for (JsonNode node : orgPage()) {
            result.add(new OrganizationNode(
                    toStr(node, "orgId", "orgCode"),
                    toStr(node, "orgName"),
                    toStr(node, "parentOrgId")));
        }
        log.info("海康云曜 组织树: count={}", result.size());
        return result;
    }

    /**
     * 云曜人员列表：官方 {@code POST /artemis/api/eits/fdcs/v1/person/page}。
     *
     * <p>包体 {@code {projectId, productCode, pageNo, pageSize}}，响应 list 项含
     * personId / personName / phone / email / orgId / jobPost。</p>
     *
     * @return 人员条目
     */
    public List<DevicePerson> fetchPersons() {
        List<DevicePerson> result = new ArrayList<>();
        for (JsonNode node : personPage()) {
            result.add(new DevicePerson(
                    toStr(node, "orgId"),
                    toStr(node, "personId"),
                    toStr(node, "personName"),
                    toStr(node, "jobPost", "personNum"),
                    toStr(node, "phone", "phoneNo"),
                    toStr(node, "email")));
        }
        log.info("海康云曜 人员列表: count={}", result.size());
        return result;
    }

    // ------------------------------------------------------------------ 组织/人员写入

    /**
     * 新增人员：官方 {@code POST /artemis/api/eits/fdcs/v1/person/add}。
     *
     * <p>官方响应把人员编号放在 {@code msg.personId}（{@code data} 只回 "1"），
     * 因此取编号时先看 {@code msg.personId}，再回落 {@code data.personId}。</p>
     *
     * @param person 人员条目（name 必填）
     * @return 厂商侧人员编号
     */
    public String addPerson(DevicePerson person) {
        if (person == null || person.name() == null || person.name().isBlank()) {
            throw new IllegalArgumentException("云曜新增人员必须给姓名");
        }
        Map<String, Object> body = personBaseBody();
        body.put("personName", person.name());
        putIfPresent(body, "orgId", person.orgId());
        putIfPresent(body, "phone", person.phone());
        putIfPresent(body, "email", person.email());
        JsonNode resp = executeJson("POST", PATH_PERSON_ADD, body, authHeaders());
        assertOk(resp);
        // 官方响应把人员编号放在 msg.personId，data 只回字符串 "1"。
        String personId = resp.path("msg").path("personId").asText(null);
        if (personId == null || personId.isBlank()) {
            log.warn("海康云曜新增人员未返回 personId（响应 {}），人员已创建但无法回填厂商编号", resp);
            return null;
        }
        return personId;
    }

    /**
     * 更新人员：官方 {@code POST /artemis/api/eits/fdcs/v1/person/update}。
     *
     * <p>{@code isFullyUpdate=0} 走增量，未传字段不动。</p>
     *
     * @param person 人员条目（externalId 必填）
     * @return 是否成功
     */
    public boolean updatePerson(DevicePerson person) {
        if (person == null || person.externalId() == null || person.externalId().isBlank()) {
            throw new IllegalArgumentException("云曜更新人员必须给人员编号");
        }
        Map<String, Object> body = personBaseBody();
        body.put("personId", person.externalId());
        body.put("isFullyUpdate", 0);
        putIfPresent(body, "personName", person.name());
        putIfPresent(body, "orgId", person.orgId());
        putIfPresent(body, "phone", person.phone());
        putIfPresent(body, "email", person.email());
        call(PATH_PERSON_UPDATE, body);
        return true;
    }

    /**
     * 删除人员：官方 {@code POST /artemis/api/eits/fdcs/v1/person/delete}（批量、全成功或全失败）。
     *
     * @param personId 厂商侧人员编号
     * @return 是否成功
     */
    public boolean removePerson(String personId) {
        if (personId == null || personId.isBlank()) {
            throw new IllegalArgumentException("云曜删除人员必须给人员编号");
        }
        Map<String, Object> body = personBaseBody();
        body.put("personIds", List.of(personId));
        call(PATH_PERSON_DELETE, body);
        return true;
    }

    /**
     * 新增组织：官方 {@code POST /artemis/api/eits/fdcs/v1/personOrg/add}。
     *
     * <p>官方要求 {@code parentOrgId} 必填，根节点由平台自动生成，可用
     * {@code personOrg/search/root} 查询；调用方未给父节点时显式报错。</p>
     *
     * @param node 组织节点（orgName 必填，parentId 必填）
     * @return 厂商侧组织编号
     */
    public String addOrganization(OrganizationNode node) {
        if (node == null || node.orgName() == null || node.orgName().isBlank()) {
            throw new IllegalArgumentException("云曜新增组织必须给组织名称");
        }
        if (node.parentId() == null || node.parentId().isBlank()) {
            throw new IllegalArgumentException(
                    "云曜新增组织必须给上级组织编号（官方 parentOrgId 必填，根节点由平台自动生成）");
        }
        Map<String, Object> body = orgBaseBody();
        body.put("orgName", node.orgName());
        body.put("parentOrgId", node.parentId());
        JsonNode data = call(PATH_ORG_ADD, body);
        return data.path("orgId").asText(null);
    }

    /**
     * 更新组织：官方 {@code POST /artemis/api/eits/fdcs/v1/personOrg/update}。
     *
     * @param node 组织节点（orgId / orgName / parentId 必填）
     * @return 是否成功
     */
    public boolean updateOrganization(OrganizationNode node) {
        if (node == null || node.orgId() == null || node.orgId().isBlank()) {
            throw new IllegalArgumentException("云曜更新组织必须给组织编号");
        }
        if (node.orgName() == null || node.orgName().isBlank()
                || node.parentId() == null || node.parentId().isBlank()) {
            throw new IllegalArgumentException("云曜更新组织必须给组织名称与上级组织编号（官方均必填）");
        }
        Map<String, Object> body = orgBaseBody();
        body.put("orgId", node.orgId());
        body.put("orgName", node.orgName());
        body.put("parentOrgId", node.parentId());
        call(PATH_ORG_UPDATE, body);
        return true;
    }

    /**
     * 删除组织：官方 {@code POST /artemis/api/eits/fdcs/v1/personOrg/batchDelete}。
     *
     * @param orgId 厂商侧组织编号
     * @return 是否成功
     */
    public boolean removeOrganization(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException("云曜删除组织必须给组织编号");
        }
        Map<String, Object> body = orgBaseBody();
        body.put("orgIds", List.of(orgId));
        call(PATH_ORG_BATCH_DELETE, body);
        return true;
    }

    // ------------------------------------------------------------------ 设备写入

    /**
     * 添加设备：官方 {@code POST /artemis/api/eits/v2/global/device/ezviz/save}。
     *
     * <p>支持萤石协议、NB-IOT、GB/T28181 国标、JT808 部标设备，添加后由平台自动激活，
     * 激活失败接口会报错。包体字段：{@code devices[].deviceSerial / treatyType /
     * deviceCategory / deviceTypeCode / projectId / deviceName / connection.validateCode /
     * deviceReleattInfo.deviceOrgId}（官方字段名就是 {@code deviceReleattInfo}，拼写照抄）。</p>
     *
     * @param device   设备条目（externalId 或 serial 作为序列号，name 为设备名称）
     * @param category 设备大类（Video / AccessControl / Alarm，空则 Video）
     * @param typeCode 设备二级分类（IPCamera 等，可空）
     * @param orgId    区域 ID（可空，空则挂在根节点下）
     * @param validateCode 设备验证码（可空）
     * @return 平台返回的设备序列号；无有效返回时为 {@code null}
     */
    public String addDevice(VendorDevice device, String category, String typeCode,
                            String orgId, String validateCode) {
        if (device == null) {
            throw new IllegalArgumentException("云曜添加设备必须给设备信息");
        }
        if (projectId == null) {
            throw new IllegalStateException(
                    "云曜添加设备要求 projectId（官方 device/ezviz/save 必填），请在凭据扩展参数配置 projectId");
        }
        String serial = firstNonBlank(device.serial(), device.externalId());
        if (serial == null) {
            throw new IllegalArgumentException("云曜添加设备必须给设备序列号");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("deviceSerial", serial);
        item.put("treatyType", TREATY_EZVIZ_NET);
        item.put("deviceCategory", category == null || category.isBlank() ? CATEGORY_VIDEO : category);
        item.put("projectId", projectId);
        item.put("deviceName", device.name() == null || device.name().isBlank() ? serial : device.name());
        putIfPresent(item, "deviceTypeCode", typeCode);
        if (validateCode != null && !validateCode.isBlank()) {
            Map<String, Object> connection = new LinkedHashMap<>();
            connection.put("validateCode", validateCode);
            item.put("connection", connection);
        }
        if (orgId != null && !orgId.isBlank()) {
            Map<String, Object> related = new LinkedHashMap<>();
            related.put("deviceOrgId", toNumber(orgId));
            item.put("deviceReleattInfo", related);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", List.of(item));
        JsonNode resp = executeJson("POST", PATH_DEVICE_SAVE, body, authHeaders());
        assertOk(resp);
        JsonNode devices = resp.path("devices");
        String assigned = devices.isArray() && devices.size() > 0
                ? toStr(devices.get(0), "deviceSerial") : null;
        if (assigned == null) {
            log.warn("海康云曜添加设备响应未带 deviceSerial（{}），设备可能已创建但无法回填序列号", resp);
        }
        return assigned;
    }

    /**
     * 修改设备：官方 {@code POST /artemis/api/eits/v2/global/device/update}（批量）。
     *
     * <p>可改设备名称、连接信息（IP/端口/用户名/密码）与所属区域。</p>
     *
     * @param device 设备条目（serial 必填，name / ip / port 可空）
     * @param orgId  区域 ID（可空表示不改）
     * @return 是否成功
     */
    public boolean updateDevice(VendorDevice device, String orgId) {
        if (device == null) {
            throw new IllegalArgumentException("云曜修改设备必须给设备信息");
        }
        String serial = firstNonBlank(device.serial(), device.externalId());
        if (serial == null) {
            throw new IllegalArgumentException("云曜修改设备必须给设备序列号");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("deviceSerial", serial);
        if (projectId != null) {
            item.put("projectId", projectId);
        }
        putIfPresent(item, "deviceName", device.name());
        Map<String, Object> connection = new LinkedHashMap<>();
        putIfPresent(connection, "addr", device.ip());
        if (device.port() != null) {
            connection.put("port", device.port());
        }
        if (!connection.isEmpty()) {
            item.put("connection", connection);
        }
        if (orgId != null && !orgId.isBlank()) {
            Map<String, Object> related = new LinkedHashMap<>();
            related.put("deviceOrgId", toNumber(orgId));
            item.put("deviceRelatedInfo", related);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", List.of(item));
        call(PATH_DEVICE_UPDATE, body);
        return true;
    }

    /**
     * 删除设备：官方 {@code POST /artemis/api/eits/v2/global/device/delete}（按序列号批量）。
     *
     * @param serial 设备序列号
     * @return 是否成功
     */
    public boolean removeDevice(String serial) {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("云曜删除设备必须给设备序列号");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerials", List.of(serial));
        if (projectId != null) {
            body.put("projectId", projectIdValue());
        }
        call(PATH_DEVICE_DELETE, body);
        return true;
    }

    // ------------------------------------------------------------------ 平台未提供的能力

    @Override
    /**
     * 录像文件列表：云曜 artemis 接口目录里没有「按时间查设备录像片段」的接口，
     * 只有 {@code live/address} 的回放地址与 v3 的录像计划配置，按约定显式失败。
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 不会返回
     */
    public List<com.chua.starter.device.spi.model.RecordFile> queryRecords(int channel, TimeRange range) {
        throw new UnsupportedOperationException(
                "海康云曜有 /api/eits/v1/global/monitor/video/by/time 路径（按设备序列号与通道查录像片段），"
                        + "但缺官方请求/响应字段文档，未实现；回放地址请用 getPlaybackUrl");
    }

    @Override
    /**
     * 门禁出入记录：云曜 artemis 接口目录里没有门禁通行记录接口
     * （fdcs/message/page 是人员/组织变更消息，不是通行记录），按约定显式失败。
     *
     * @param range 时间范围
     * @return 不会返回
     */
    public List<com.chua.starter.device.spi.model.AccessRecord> queryAccessRecords(TimeRange range) {
        throw new UnsupportedOperationException(
                "海康云曜 artemis 未提供门禁出入记录接口（fdcs/message/page 是人员组织变更消息）");
    }

    @Override
    /**
     * 事件拉取：云曜没有「按时间范围拉事件」的接口，事件走平台侧消息推送；
     * 本会话不伪造事件，按约定显式失败。
     *
     * @param range 时间范围
     * @return 不会返回
     */
    public List<com.chua.starter.device.spi.model.DeviceEvent> pollEvents(TimeRange range) {
        throw new UnsupportedOperationException(
                "海康云曜没有事件拉取接口（事件由平台侧推送），会话不缓冲伪造事件");
    }

    // ------------------------------------------------------------------ 分页与解析

    /**
     * 设备分页（条件数组包体）逐页拉取。
     *
     * @return 设备条目
     */
    private List<JsonNode> devicePage() {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            List<Map<String, Object>> conditions = new ArrayList<>();
            conditions.add(condition("pageNo", pageNo));
            conditions.add(condition("pageSize", PAGE_SIZE));
            if (projectIdValue() != null) {
                conditions.add(condition("projectId", projectIdValue()));
            }
            JsonNode data = assertOk(executeJson("POST", PATH_DEVICE_PAGE, conditions, authHeaders()));
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || reachedLastPage(data, pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 监控点分页逐页拉取。
     *
     * @return 监控点条目
     */
    private List<JsonNode> monitorPage() {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectId", projectIdValue());
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode data = call(PATH_MONITOR_PAGE, body);
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || reachedLastPage(data, pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 组织分页逐页拉取（fdcs personOrg/page）。
     *
     * @return 组织条目
     */
    private List<JsonNode> orgPage() {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> body = orgBaseBody();
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode data = call(PATH_ORG_PAGE, body);
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || reachedLastPage(data, pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 人员分页逐页拉取（fdcs person/page）。
     *
     * @return 人员条目
     */
    private List<JsonNode> personPage() {
        List<JsonNode> all = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            Map<String, Object> body = personBaseBody();
            body.put("pageNo", pageNo);
            body.put("pageSize", PAGE_SIZE);
            JsonNode data = call(PATH_PERSON_PAGE, body);
            List<JsonNode> list = toList(data);
            if (list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < PAGE_SIZE || reachedLastPage(data, pageNo)) {
                break;
            }
        }
        return all;
    }

    /**
     * 组织/人员写接口的公共前缀字段：projectId + productCode（官方均必填）。
     *
     * @return 包体
     */
    private Map<String, Object> orgBaseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (projectId == null) {
            throw new IllegalStateException(
                    "云曜组织接口要求 projectId（官方 personOrg/* 必填），请在凭据扩展参数配置 projectId");
        }
        body.put("projectId", projectId);
        if (productCode == null) {
            throw new IllegalStateException(
                    "云曜组织接口要求 productCode（官方 personOrg/* 必填），请在凭据扩展参数配置 productCode");
        }
        body.put("productCode", productCode);
        return body;
    }

    /**
     * 人员写接口的公共前缀字段（官方 person/* 要求 projectId + productCode）。
     *
     * @return 包体
     */
    private Map<String, Object> personBaseBody() {
        return orgBaseBody();
    }

    /**
     * 平台分页是否已到最后一页（响应带 totalPage / total 时据此提前结束）。
     *
     * @param data   响应 data 节点
     * @param pageNo 当前页
     * @return true 已到末页
     */
    private static boolean reachedLastPage(JsonNode data, int pageNo) {
        if (data == null || !data.isObject()) {
            return false;
        }
        int totalPage = data.path("totalPage").asInt(0);
        if (totalPage > 0) {
            return pageNo >= totalPage;
        }
        long total = data.path("total").asLong(0L);
        long size = data.path("pageSize").asLong(PAGE_SIZE);
        return total > 0 && size > 0 && pageNo * size >= total;
    }

    /**
     * 构造设备分页的条件项 {@code {key, option:"eq", value}}。
     *
     * @param key   字段名
     * @param value 匹配值
     * @return 条件项
     */
    private static Map<String, Object> condition(String key, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("option", "eq");
        item.put("value", value);
        return item;
    }

    /**
     * 平台返回的 data 节点转条目列表：官方分页响应固定是 {@code data.list[]}，
     * 兼容 {@code data} 直接是数组（camera/list）。
     *
     * @param data 响应 data 节点
     * @return 条目
     */
    private static List<JsonNode> toList(JsonNode data) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        if (data.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
                result.add(it.next());
            }
            return result;
        }
        for (String field : List.of("list", "records", "rows", "data")) {
            if (data.has(field)) {
                return toList(data.get(field));
            }
        }
        return List.of();
    }

    /**
     * 平台在线状态：1 在线 / 0 离线 / -1 未上报（官方 device/page 与 monitor/page 一致）。
     *
     * @param node 平台条目
     * @return 在线状态；未上报时返回 {@code null}
     */
    private static Boolean onlineOf(JsonNode node) {
        JsonNode value = node.path("onlineStatus");
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        int status = value.asInt(ONLINE_UNREPORTED);
        return status == ONLINE_UNREPORTED ? null : status == 1;
    }

    /**
     * 设备品类映射：官方 modelType 取 CAMERA / ALARM / ACCESS，中文名走 modelTypeStr。
     *
     * @param code 平台类型码
     * @return 设备类别
     */
    private static String categoryOf(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (value.contains("ACCESS") || value.contains("门禁")) {
            return "accessControl";
        }
        if (value.contains("ALARM") || value.contains("报警")) {
            return "alarm";
        }
        if (value.contains("INTERCOM") || value.contains("对讲")) {
            return "intercom";
        }
        return "camera";
    }

    /**
     * 设备序列号（取流/抓图/云台/预置位都必须带）。
     *
     * @return 设备序列号
     */
    private String requireSerial() {
        String serial = info.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalStateException("海康云曜接口要求设备序列号（凭据未绑定设备序列号）");
        }
        return serial;
    }

    /**
     * 项目 ID 的包体取值：官方文档 device/page 的条件值是数字，
     * 纯数字时按数字下发，非数字原样下发。
     *
     * @return projectId 或 {@code null}
     */
    private Object projectIdValue() {
        if (projectId == null) {
            return null;
        }
        try {
            return Long.parseLong(projectId);
        } catch (NumberFormatException e) {
            return projectId;
        }
    }

    /**
     * 调用一个业务接口并返回 data 节点。
     *
     * @param path 接口路径
     * @param body 包体
     * @return data 节点
     */
    private JsonNode call(String path, Map<String, Object> body) {
        return assertOk(doRequest("POST", path, body));
    }

    /**
     * 放入非空字段。
     *
     * @param body  包体
     * @param key   字段名
     * @param value 字段值
     */
    private static void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    /**
     * 从连接信息扩展参数里取字符串。
     *
     * @param info 连接信息
     * @param key  参数名
     * @return 值；空串按 {@code null} 处理
     */
    private static String extraString(DeviceConnectInfo info, String key) {
        if (info == null || info.extra() == null) {
            return null;
        }
        Object value = info.extra().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 从连接信息扩展参数里取整数。
     *
     * @param info 连接信息
     * @param key  参数名
     * @return 整数；无法解析时返回 {@code null}
     */
    private static Integer extraInt(DeviceConnectInfo info, String key) {
        String text = extraString(info, key);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            log.warn("海康云曜 扩展参数 {} 不是整数: {}", key, text);
            return null;
        }
    }

    /**
     * 字符串转包体数值：纯数字下发数字（官方示例 projectId / deviceOrgId 都是数字），否则原样。
     *
     * @param text 文本
     * @return 数字或原字符串
     */
    private static Object toNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return text.trim();
        }
    }

    /**
     * 取第一个非空字符串。
     *
     * @param values 候选值
     * @return 第一个非空值
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
     * 取平台条目的第一个非空字符串。
     *
     * @param node   平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static String toStr(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value == null || value.isNull() || (value.isTextual() && value.asText().trim().isEmpty())) {
                continue;
            }
            return value.asText().trim();
        }
        return null;
    }

    /**
     * 取平台条目的首项整型。
     *
     * @param node   平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static Integer toInt(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value == null || value.isNull() || !value.isNumber()) {
                continue;
            }
            return value.asInt();
        }
        return null;
    }
}
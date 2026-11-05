// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision.open;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 海康云耀（云曜 / hikyun 外网云）打开api 会话
 * <p>对接海康云耀 OpenAPI，基于 common-starter HttpClient，全平台可用（无需厂商 SDK 运行库）。</p>
 *
 * <h3>接入说明</h3>
 * <ul>
 *   <li>云曜网关地址为 https://open.hikyun.com（"云曜的对外开放接口，地址 https://open.hikyun.com"）</li>
 *   <li>换 access_token：POST https://open.hikyun.com/artemis/oauth/token/v2（JSON 包体），参数为
 *   appKey / appSecret / productCode （产品标识，必须）/ projectId（项目编号，文档标注非必需）；
 *   返回的 access_token 有效期 43200 秒（12 小时）</li>
 *   <li>调用云曜接口时，将 access_token 放入 Headers 中（Authorization: Bearer &lt;access_token&gt;）；
 *   不得作为 Query / Body 参数转发</li>
 *   <li>同步接口需凭据侧先完成「3.1.6 接口授权」：联系云曜方提供环境地址及产品 key 进行授权</li>
 *   <li>具体 endpoint、字段与签名以海康云耀官方文档为准</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionCloudYaoSession extends AbstractOpenApiSession {

    /**
     * 海康云耀默认 baseurl（按官方文档配置）
     * <p>云曜对外开放的网关地址为 <a href="https://open.hikyun.com">https://open.hikyun.com</a>；
     * 旧地址 https://api.hik-cloud.com 不是云曜网关，用它换令牌必然失败。</p>
     * <p>还支持凭据级覆盖：在 {@code DeviceConnectInfo.extra("baseUrl")} /
     * {@code device_credential_base_url} 填写云曜的接入地址即可更换环境。</p>
     */
    private static final String DEFAULT_BASE_URL = "https://open.hikyun.com";

    /**
     * access令牌 接口路径
     * <p>云曜文档注明的换 access_token 接口：
     * {@code POST https://open.hikyun.com/artemis/oauth/token/v2}（JSON 包体）</p>
     */
    private static final String PATH_TOKEN = "/artemis/oauth/token/v2";

    /**
     * 获取视频直播地址接口
     */
    private static final String PATH_LIVE_ADDRESS = "/api/v1/video/live/address";

    /**
     * 云台控制接口
     */
    private static final String PATH_PTZ_CONTROL = "/api/v1/ptz/control";

    /**
     * 远程开关门接口
     */
    private static final String PATH_DOOR_CONTROL = "/api/v1/access/control";

    /**
     * 构造云耀会话
     *
     * @param info 设备连接信息
     */
    /**
     * 云曜产品编码（ProductCode）：平台侧按产品维度隔离设备
     */
    private final String productCode;

    /**
     * 云曜项目 ID（ProjectID）：平台侧按项目维度隔离设备
     */
    private final String projectId;

    public HikvisionCloudYaoSession(DeviceConnectInfo info) {
        /**
         * 父。
         * @param info 信息
         * @param DEFAULT_BASE_URL 默认baseurl
         */
        super(info, DEFAULT_BASE_URL);
        this.productCode = extraString(info, "productCode");
        this.projectId = extraString(info, "projectId");
        /**
         * login。
         */
        login();
    }

    /**
     * 从连接信息扩展参数里取字符串
     * @param info 参数
     * @param key 字符串
     * @return 返回值
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
     * 登录换取 access令牌（按海康云耀文档约定的 oauth 流程）
     * <p>接口地址 {@code POST https://open.hikyun.com/artemis/oauth/token/v2}（JSON 包体），
     * 参数为 appKey / appSecret / productCode（产品标识，必须）/ projectId（项目编号，文档标注非必需），
     * 换得后 access_token 有效期默认 43200 秒（12 小时）。</p>
     */
    private void login() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appKey", info.appKey());
        params.put("appSecret", info.appSecret());
        // 产品/项目标识参加换令牌的包体
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
            long expires = firstExpires(data);
            if (token == null || token.isEmpty()) {
                /**
                 * illegal状态异常。
                 * @param 为空" 为空"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("海康云耀 accessToken 为空");
            }
            /**
             * 缓存令牌。
             * @param token 令牌
             * @param expires expires
             */
            cacheToken(token, expires);
            log.info("海康云耀登录成功: deviceSerial={}: accessToken={}...", info.deviceSerial(), maskToken(token));
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("海康云耀登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取 access_token 的值（新旧字段名都接受：access_token / accessToken）
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
     * 取 access_token 有效期（秒，旧格式 expiresIn、新格式 expires_in 都接受），
     * 云曜文档约定默认有效期 43200 秒（12 小时）
     * @param data 响应的 data 节点
     * @return 有效期秒数
     */
    private static long firstExpires(JsonNode data) {
        long seconds = data.path("expires_in").asLong(0L);
        if (seconds <= 0L) {
            seconds = data.path("expiresIn").asLong(43200L);
        }
        return seconds;
    }

    /**
     * 脱敏 access_token 后显示（令牌不回显全文）
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
     * 海康云耀通用签名：按参数键排序后拼接 + appsecret，HMAC-SHA256。
     *
     * @param params 参数
     * @return 签名值
     */
    private String sign(Map<String, Object> params) {
        String sorted = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(utf8(info.appSecret()), "HmacSHA256"));
            byte[] digest = mac.doFinal(utf8(sorted));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "海康云耀签名失败" "海康云耀签名失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("海康云耀签名失败", e);
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
        if (!tokenValid() && !PATH_TOKEN.equals(path)) {
            /**
             * login。
             */
            login();
        }
        Map<String, Object> realParams = new LinkedHashMap<>();
        if (params != null) {
            realParams.putAll(params);
        }
        if (!PATH_TOKEN.equals(path)) {
            // 云曜按产品 + 项目维度隔离设备，取流 / 云台 / 门禁都要带
            if (productCode != null) {
                realParams.put("productCode", productCode);
            }
            if (projectId != null) {
                realParams.put("projectId", projectId);
            }
            String sign = sign(realParams);
            realParams.put("sign", sign);
            if (debugEnabled()) {
                log.info("[OpenAPI-DEBUG] 海康云耀 签名: {}", sign.length() > 12 ? sign.substring(0, 12) + "..." : sign);
            }
        }
        // 云曜文档约定：access_token 必须放在 Headers 中（Authorization: Bearer <token>），
        // 不作为 Query / Body 参数转发，否则会被拒绝为"参数不够"。
        Map<String, String> headers = new LinkedHashMap<>();
        if (accessToken != null) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return execute(method, path, realParams, headers);
    }

    /**
     * 获取视频直播地址（HLS）
     *
     * @param channel  通道号
     * @param protocol 协议类型
     * @return 视频地址数据
     */
    public JsonNode getLiveAddress(int channel, String protocol) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        params.put("protocol", protocol);
        return assertOk(post(PATH_LIVE_ADDRESS, params));
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        String p = protocol == null || protocol.isEmpty() ? "hls" : protocol;
        JsonNode data = getLiveAddress(channel, p);
 // 海康云耀返回字段示例：数据.url / 数据.hls / 数据.hls地址
        return data.path("url").asText(data.path("hls").asText(data.path("hlsAddress").asText("")));
    }

    /**
     * 获取录像回放 URL（海康云耀）
     *
     * @param channel 通道号
     * @param range   时间范围
     * @return 回放 HLS URL
     */
    @Override
    public String getPlaybackUrl(int channel, com.chua.starter.device.spi.model.TimeRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", channel);
        params.put("startTime", range.start().toString());
        params.put("endTime", range.end().toString());
        params.put("protocol", "hls");
        JsonNode data = assertOk(post("/api/v1/video/playback/url", params));
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
        params.put("deviceSerial", info.deviceSerial());
        params.put("channel", 1);
        params.put("direction", mapDirection(cmd));
        params.put("speed", Math.max(1, Math.min(255, Math.round(speed * 255))));
        /**
         * post。
         * @param PATH_PTZ_CONTROL 路径ptzcontrol
         * @param params 参数
         */
        post(PATH_PTZ_CONTROL, params);
    }

    /**
     * 海康云耀云台方向码（按官方文档约定）
     * @param cmd CMD
     * @return 映射direction的结果
     */
    private int mapDirection(com.chua.starter.device.spi.model.PtzCommand cmd) {
        switch (cmd) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            case ZOOM_IN: return 4;
            case ZOOM_OUT: return 5;
            case FOCUS_IN: return 6;
            case FOCUS_OUT: return 7;
            case IRIS_IN: return 8;
            case IRIS_OUT: return 9;
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
        params.put("deviceSerial", info.deviceSerial());
        params.put("doorId", doorId);
        params.put("command", 1);
        /**
         * post。
         * @param PATH_DOOR_CONTROL 路径门control
         * @param params 参数
         */
        post(PATH_DOOR_CONTROL, params);
    }

    /**
     * 云耀 OpenAPI 路径：组织列表（按 productCode/projectId 隔离）
     */
    private static final String PATH_ORG_TREE = "/api/v1/organizations";

    /**
     * 云耀 OpenAPI 路径：组织维度设备列表（{0} 位厂商侧组织标识，按 productCode/projectId 隔离）
     */
    private static final String PATH_ORG_DEVICES = "/api/v1/organizations/%s/devices";

    /**
     * 云耀 OpenAPI 路径：组织维度人员列表（{0} 位厂商侧组织标识，按 productCode/projectId 隔离）
     */
    private static final String PATH_ORG_USERS = "/api/v1/organizations/%s/users";

    /** 分页查询每页条数 */
    private static final int PAGE_SIZE = 100;

    /**
     * 云耀 OpenAPI 查询组织列表：单页翻页，按 productCode/projectId 隔离
     *
     * <p>路径 {@code GET /api/v1/organizations}，按 productCode/projectId 隔离；每页
     * {@code PAGE_SIZE} 条。组织节点用厂商侧 orgId 为 key、parentOrgId 为父。
     * 平台组织字段可能是 {@code orgId} / {@code id} / {@code orgCode}，
     * {@code toStr} 里逐一尝试后取第一个非空；未填 parentOrgId 时迁入空值。</p>
     *
     * @return 组织节点（厂商侧标识 / 名称 / 父标识）
     */
    public List<OrganizationNode> fetchOrganizations() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("pageSize", PAGE_SIZE);
            JsonNode data = assertOk(get(PATH_ORG_TREE, params));
            List<JsonNode> orgs = toList(data);
            return orgs.stream()
                    .map(n -> new OrganizationNode(
                            toStr(n, "orgId", "id", "orgCode", "merchantId"),
                            toStr(n, "orgName", "name", "orgName"),
                            toStr(n, "parentOrgId", "parentId")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("海康云耀 组织树拉取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 云耀 OpenAPI 查询设备目录：按厂商侧组织维度翻页拉取
     *
     * <p>组织列表由 {@link #fetchOrganizations()} 拉取，逐个组织翻页取
     * {@code /api/v1/organizations/{orgId}/devices}；按 productCode/projectId 隔离。
     * 字段以平台实际返回为准，{@code toStr}/{@code decimal}/{@code bool}
     * 逐一尝试同一含义的常见字段名，避免错配；未识别标签的云耀平台归入摄像头。</p>
     *
     * @return 厂商设备条目
     */
    public List<VendorDevice> fetchDevices() {
        try {
            List<OrganizationNode> orgs = fetchOrganizations();
            List<VendorDevice> result = new ArrayList<>();
            for (OrganizationNode org : orgs) {
                result.addAll(fetchDevicesByOrg(org.orgId()));
            }
            return result;
        } catch (Exception e) {
            log.warn("海康云耀 设备目录拉取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 厂商 side 组织维度下翻页拉取设备列表
     *
     * @param orgId 厂商 side 组织标识
     * @return 设备条目
     */
    private List<VendorDevice> fetchDevicesByOrg(String orgId) {
        List<VendorDevice> result = new ArrayList<>();
        for (int pageNo = 1; ; pageNo++) {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNo", pageNo);
            params.put("pageSize", PAGE_SIZE);
            List<JsonNode> list;
            try {
                JsonNode data = assertOk(get(String.format(PATH_ORG_DEVICES, orgId), params));
                list = toList(data);
            } catch (Exception e) {
                log.warn("海康云耀 设备列表拉取失败: orgId={}, page={}", orgId, pageNo, e.getMessage());
                break;
            }
            if (list == null || list.isEmpty()) {
                break;
            }
            result.addAll(toVendorDevices(list));
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("海康云耀 设备目录: orgId={}, count={}", orgId, result.size());
        return result;
    }

    /**
     * 云耀 OpenAPI 查询人员列表：按厂商侧组织维度翻页拉取
     *
     * <p>组织列表由 {@link #fetchOrganizations()} 拉取，逐个组织翻页取
     *>{@code /api/v1/organizations/{orgId}/users}；按 productCode/projectId 隔离。
     * 人员只保留低门槛字段：姓名/职务/手机号/邮箱；行政归属挂接厂商侧组织标识
     * （与 {@code fetchOrganizations()} 组织标识同口径），避免人员 API 结构换表时
     * 耦合到 org-person 关联表。</p>
     *
     * @return 人员条目（厂商侧组织标识 / 厂商 side 人员标识 / 姓名 / 职务 / 手机 / 邮箱）
     */
    public List<DevicePerson> fetchPersons() {
        try {
            List<OrganizationNode> orgs = fetchOrganizations();
            List<DevicePerson> result = new ArrayList<>();
            for (OrganizationNode org : orgs) {
                result.addAll(fetchPersonsByOrg(org.orgId()));
            }
            return result;
        } catch (Exception e) {
            log.warn("海康云耀 人员列表拉取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 厂商 side 组织维度下翻页拉取人员列表
     *
     * @param orgId 厂商 side 组织标识
     * @return 人员条目
     */
    private List<DevicePerson> fetchPersonsByOrg(String orgId) {
        List<DevicePerson> result = new ArrayList<>();
        for (int pageNo = 1; ; pageNo++) {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNo", pageNo);
            params.put("pageSize", PAGE_SIZE);
            List<JsonNode> list;
            try {
                JsonNode data = assertOk(get(String.format(PATH_ORG_USERS, orgId), params));
                list = toList(data);
            } catch (Exception e) {
                log.warn("海康云耀 人员列表拉取失败: orgId={}, page={}", orgId, pageNo, e.getMessage());
                break;
            }
            if (list == null || list.isEmpty()) {
                break;
            }
            result.addAll(toDevicePersons(list));
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("海康云耀 人员列表: orgId={}, count={}", orgId, result.size());
        return result;
    }

    /**
     * 平台返回的响应 data 节点转条目列表：可能是 {@code {list: [...]}} /
     * {@code {pageList: [...]}} / {@code {records: [...]}} / 匿名数组，逐一尝试后
     * 仍不是列表时退回空列表。
     *
     * @param data 响应的 data 节点
     * @return 平台条目
     */
    private static List<JsonNode> toList(JsonNode data) {
        if (data == null || data.isNull() || !data.isObject() && !data.isArray()) {
            return List.of();
        }
        if (data.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
                result.add(it.next());
            }
            return result;
        }
        for (String field : List.of("list", "pageList", "pageInfo", "records", "data",
                "nodes", "table", "rows", "items", "deviceList", "users")) {
            if (data.has(field)) {
                return toList(data.get(field));
            }
        }
        return List.of();
    }

    /**
     * 平台设备条目转厂商侧设备目录模型
     *
     * @param nodes 平台返回的设备页条目
     * @return 厂商设备条目
     */
    private List<VendorDevice> toVendorDevices(List<JsonNode> nodes) {
        return nodes.stream()
                .map(n -> new VendorDevice(
                        toStr(n, "deviceSerial", "deviceId", "outId", "serial", "externalId"),
                        toStr(n, "deviceName", "name", "outName"),
                        toStr(n, "productModel", "model", "deviceModel"),
                        categorize(n),
                        toStr(n, "deviceSerial", "serial", "outId", "externalId"),
                        toStr(n, "ip", "deviceIp", "ipAddress"),
                        toInt(n, "port", "devicePort"),
                        toStr(n, "address", "deviceAddress"),
                        decimal(n, "latitude", "lat", "lon"),
                        decimal(n, "longitude", "lng", "lat"),
                        toInt(n, "channelCount", "channels", "channel"),
                        bool(n, "online", "onlineFlag", "status")))
                .collect(Collectors.toList());
    }

    /**
     * 平台人员条目转 {@code DevicePerson}
     *
     * <p>行政归属取平台的 {@code orgId} / {@code departmentId}，
     * 平台未提供时为 null，由落地层按组织归口修复。</p>
     *
     * @param nodes 平台返回的人员页条目
     * @return 人员条目
     */
    private List<DevicePerson> toDevicePersons(List<JsonNode> nodes) {
        return nodes.stream()
                .map(n -> new DevicePerson(
                        toStr(n, "orgId", "departmentId", "organizationId", "deptId", "departmentCode"),
                        toStr(n, "userId", "personId", "externalId", "outId", "employeeNumber"),
                        toStr(n, "userName", "name", "username", "outName"),
                        toStr(n, "memberTitle", "title", "roleName", "jobTitle"),
                        toStr(n, "phone", "mobile", "cell", "outMobile"),
                        toStr(n, "email", "mailbox", "outEmail")))
                .collect(Collectors.toList());
    }

    /**
     * 平台设备品类码 → 设备目录类。
     * <p>未能识别时退回到设备名称/型号的关键词，默认归入摄像头，避免配件列表无类可言。</p>
     *
     * @param n 平台返回的设备条目
     * @return 设备分类
     */
    private static String categorize(JsonNode n) {
        String code = toStr(n, "category", "deviceType", "productType", "productTypeCode",
                "deviceCategory", "type");
        String name = toStr(n, "deviceName", "name");
        String model = toStr(n, "productModel", "model");
        String lower = (name == null ? "" : name) + (model == null ? "" : model);
        lower = lower.toLowerCase();
        if ("0".equals(code) || "camera".equalsIgnoreCase(code) || "01".equals(code)
                || "dcam".equalsIgnoreCase(code) || "0101".equalsIgnoreCase(code)) {
            return "camera";
        }
        if ("access".equalsIgnoreCase(code) || "accessControl".equalsIgnoreCase(code) || "02".equals(code)
                || "0103".equalsIgnoreCase(code) || lower.contains("gate") || lower.contains("door")
                || lower.contains("relais")) {
            return "accessControl";
        }
        if ("alarm".equalsIgnoreCase(code) || "03".equals(code) || "0102".equalsIgnoreCase(code)
                || lower.contains("alarm") || lower.contains("fire")) {
            return "alarm";
        }
        if ("intercom".equalsIgnoreCase(code) || "intercomPhone".equalsIgnoreCase(code) || "04".equals(code)
                || "0104".equalsIgnoreCase(code) || lower.contains("intercom") || lower.contains("audio")) {
            return "intercom";
        }
        return "camera";
    }

    /**
     * 取平台条目的第一个非空字符串
     *
     * @param n      平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static String toStr(JsonNode n, String... fields) {
        if (n == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.path(f);
            if (v == null || v.isNull() || (v.isTextual() && v.asText().trim().isEmpty())) {
                continue;
            }
            return v.asText().trim();
        }
        return null;
    }

    /**
     * 取平台条目的首项整型
     *
     * @param n      平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static Integer toInt(JsonNode n, String... fields) {
        if (n == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.path(f);
            if (v == null || v.isNull()) {
                continue;
            }
            try {
                return v.asInt();
            } catch (Exception e) {
                log.debug("海康云耀 整型字段 {} 解析失败: {}", f, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 取平台条目的首项布尔（平台常用 1/0 表示）
     *
     * @param n      平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static Boolean bool(JsonNode n, String... fields) {
        if (n == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.path(f);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isBoolean()) {
                return v.asBoolean();
            }
            if (v.isInt()) {
                return v.asInt() == 1;
            }
            if (v.isTextual()) {
                String text = v.asText().trim();
                if ("1".equals(text) || "true".equalsIgnoreCase(text) || "YES".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("0".equals(text) || "false".equalsIgnoreCase(text) || "NO".equalsIgnoreCase(text)) {
                    return false;
                }
            }
        }
        return null;
    }

    /**
     * 取平台条目的首项数值（字符串或数字均可）
     *
     * @param n      平台条目
     * @param fields 尝试字段名
     * @return 字段值；均未找到时返回 {@code null}
     */
    private static BigDecimal decimal(JsonNode n, String... fields) {
        if (n == null) {
            return null;
        }
        for (String f : fields) {
            JsonNode v = n.path(f);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isInt() || v.isLong()) {
                return new BigDecimal(v.asLong());
            }
            if (v.isTextual()) {
                try {
                    return new BigDecimal(v.asText().trim());
                } catch (Exception e) {
                    log.debug("海康云耀 数值字段 {} 解析失败: {}", f, e.getMessage());
                }
            }
        }
        return null;
    }
}
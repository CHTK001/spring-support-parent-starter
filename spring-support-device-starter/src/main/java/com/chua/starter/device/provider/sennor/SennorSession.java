package com.chua.starter.device.provider.sennor;

import com.chua.starter.device.provider.sdk.sennor.SennorApis;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.EventListener;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 精讯畅通（Sennor）精讯云设备会话。
 *
 * <h3>两种鉴权模式</h3>
 * <ul>
 *   <li><b>控制台账号</b>：凭据的 {@code appKey} 填登录账号、{@code appSecret} 填密码。
 *       会话调用 {@code POST /index/login} 建立 {@code JSESSIONID}，再走控制台的
 *       {@code /deviceManage/getDeviceList} 拉取设备目录。该登录接口不带图形验证码，
 *       适合后端长期运行。</li>
 *   <li><b>HTTP 取数 secretKey</b>：凭据的 {@code appKey} 填 16 位 {@code secretKey}，
 *       {@code appSecret} 留空。会话按官方文档的 {@code /device/getDeviceList}、
 *       {@code /device/getDeviceInfo}、{@code /relay/getDeviceList} 取数。</li>
 * </ul>
 *
 * <p>精讯云是传感器/继电器设备平台，没有视频通道、录像、云台、门禁和停车场业务；
 * 这些 SPI 方法一律显式抛出 {@link UnsupportedOperationException}，不返回空数据
 * 冒充成功。能力矩阵只声明 {@code DEVICE}。</p>
 *
 * <p><b>验证状态</b>：控制台账号已实测登录成功并返回空设备列表（账号名下 0 台设备）；
 * 因为没有真实设备，字段映射与在线状态仍标 {@code IMPLEMENTED_UNVERIFIED}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
public class SennorSession implements DeviceSession {

    /** 精讯云默认平台地址 */
    private static final String DEFAULT_BASE_URL = "http://www.sennor.net";

    /** 控制台账号登录路径（不在官方 HTTP 取数文档中，属于控制台登录入口） */
    private static final String LOGIN_PATH = "/index/login";

    /** 控制台设备列表路径 */
    private static final String PLATFORM_DEVICE_LIST = "/deviceManage/getDeviceList";

    /** 分页大小 */
    private static final int PAGE_SIZE = 100;

    /** 最大翻页数，防止平台异常时无限循环 */
    private static final int MAX_PAGES = 100;

    /** 在线检测最大翻页数 */
    private static final int MAX_ONLINE_PAGES = 10;

    /** JSON 解析器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 请求超时 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 连接信息 */
    private final DeviceConnectInfo info;

    /** 平台地址（可由凭据 extra.baseUrl 覆盖） */
    private final String baseUrl;

    /** HTTP 取数 secretKey；为空表示走控制台账号 */
    private final String secretKey;

    /** 控制台登录账号；secretKey 模式为空 */
    private final String username;

    /** 控制台登录密码；secretKey 模式为空 */
    private final String password;

    /** 带回话 Cookie 的 HTTP 客户端 */
    private final HttpClient httpClient;

    /** 控制台会话 Cookie */
    private final CookieManager cookieManager;

    /** 是否已登录控制台 */
    private volatile boolean loggedIn;

    /** 最近一次在线状态 */
    private volatile boolean online = true;

    /**
     * 构造精讯云会话。
     *
     * @param info 连接信息；appKey/appSecret 承载账号或 secretKey
     */
    public SennorSession(DeviceConnectInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("精讯云连接信息不能为空");
        }
        this.info = info;
        this.baseUrl = resolveBaseUrl(info);
        String appKey = trimToNull(info.appKey());
        String appSecret = trimToNull(info.appSecret());
        if (appKey != null && appSecret == null) {
            this.secretKey = appKey;
            this.username = null;
            this.password = null;
        } else {
            this.secretKey = null;
            this.username = firstNonBlank(appKey, trimToNull(info.username()));
            this.password = firstNonBlank(appSecret, trimToNull(info.password()));
        }
        if (secretKey == null && (username == null || password == null)) {
            throw new IllegalArgumentException("精讯云凭据不完整：账号模式需要账号和密码，secretKey 模式需要 16 位 secretKey");
        }
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String getDeviceId() {
        return firstNonBlank(trimToNull(info.deviceSerial()), trimToNull(info.ip()), "sennor");
    }

    @Override
    public boolean isOnline() {
        String deviceNumber = trimToNull(info.deviceSerial());
        if (deviceNumber == null) {
            return online;
        }
        try {
            online = queryOnline(deviceNumber);
        } catch (RuntimeException ex) {
            online = false;
            log.warn("精讯云在线状态查询失败: device={}, reason={}", deviceNumber, rootMessage(ex));
        }
        return online;
    }

    @Override
    public void close() {
        online = false;
        loggedIn = false;
        cookieManager.getCookieStore().removeAll();
        log.info("精讯云会话已关闭: {}", getDeviceId());
    }

    /**
     * 拉取设备目录：传感器设备与继电器设备合并，按设备号去重。
     *
     * @return 设备条目
     */
    public List<VendorDevice> fetchDevices() {
        return secretKey != null ? fetchPublicDevices() : fetchPlatformDevices();
    }

    /**
     * 测试平台连通性。
     *
     * @return 登录成功或取数接口返回成功时 true
     */
    @Override
    public boolean testConnection() {
        try {
            if (secretKey != null) {
                requestJson("GET", SennorApis.P_DEVICE_LIST,
                        Map.of("page", 1, "size", 1));
            } else {
                ensureLogin();
                requestJson("GET", PLATFORM_DEVICE_LIST,
                        Map.of("page", 1, "limit", 1));
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("精讯云连通性检测失败: {}", rootMessage(ex));
            return false;
        }
    }

    /**
     * 按官方 HTTP 取数接口拉取设备。
     *
     * @return 设备条目
     */
    private List<VendorDevice> fetchPublicDevices() {
        Map<String, VendorDevice> devices = new LinkedHashMap<>();
        int page = 1;
        while (page <= MAX_PAGES) {
            JsonNode root = requestJson("GET", SennorApis.P_DEVICE_LIST,
                    Map.of("page", page, "size", PAGE_SIZE));
            JsonNode rows = firstArray(root, "datas", "data");
            for (JsonNode row : rows) {
                VendorDevice device = mapDevice(row, "传感器");
                if (device != null) {
                    devices.put(device.externalId(), enrichPublicDevice(device));
                }
            }
            if (rows.isEmpty() || root.path("isLast").asBoolean(false)) {
                break;
            }
            page++;
        }

        int relayPage = 1;
        while (relayPage <= MAX_PAGES) {
            JsonNode root = requestJson("GET", SennorApis.P_RELAY_LIST,
                    Map.of("page", relayPage, "size", PAGE_SIZE));
            JsonNode rows = firstArray(root, "datas", "data");
            for (JsonNode row : rows) {
                VendorDevice device = mapDevice(row, "继电器");
                if (device != null) {
                    devices.putIfAbsent(device.externalId(), device);
                }
            }
            if (rows.isEmpty() || root.path("isLast").asBoolean(false)) {
                break;
            }
            relayPage++;
        }
        return new ArrayList<>(devices.values());
    }

    /**
     * 设备列表只返回编号，详情接口补齐名称、类型与在线状态。
     *
     * @param base 列表条目
     * @return 补齐后的条目；详情失败时保留列表条目
     */
    private VendorDevice enrichPublicDevice(VendorDevice base) {
        try {
            JsonNode root = requestJson("GET", SennorApis.P_DEVICE_INFO,
                    Map.of("deviceNumber", base.externalId()));
            JsonNode data = root.path("data");
            if (data.isObject()) {
                VendorDevice detail = mapDevice(data, "传感器");
                if (detail != null) {
                    return detail;
                }
            }
        } catch (RuntimeException ex) {
            log.debug("精讯云设备详情获取失败，保留列表字段: device={}, reason={}",
                    base.externalId(), rootMessage(ex));
        }
        return base;
    }

    /**
     * 按控制台账号拉取设备。
     *
     * @return 设备条目
     */
    private List<VendorDevice> fetchPlatformDevices() {
        ensureLogin();
        Map<String, VendorDevice> devices = new LinkedHashMap<>();
        int page = 1;
        int total = -1;
        while (page <= MAX_PAGES) {
            JsonNode root = requestJson("GET", PLATFORM_DEVICE_LIST,
                    Map.of("page", page, "limit", PAGE_SIZE));
            JsonNode rows = firstArray(root, "data", "datas");
            for (JsonNode row : rows) {
                VendorDevice device = mapDevice(row, "传感器");
                if (device != null) {
                    devices.put(device.externalId(), device);
                }
            }
            total = root.path("count").asInt(total);
            if (rows.isEmpty() || (total >= 0 && devices.size() >= total)) {
                break;
            }
            page++;
        }
        return new ArrayList<>(devices.values());
    }

    /**
     * 查询设备在线状态。
     *
     * @param deviceNumber 设备号
     * @return 在线返回 true
     */
    private boolean queryOnline(String deviceNumber) {
        if (secretKey != null) {
            JsonNode root = requestJson("GET", SennorApis.P_DEVICE_INFO,
                    Map.of("deviceNumber", deviceNumber));
            Boolean state = parseOnline(root.path("data"));
            if (state == null) {
                state = parseOnline(root);
            }
            return Boolean.TRUE.equals(state);
        }

        ensureLogin();
        for (int page = 1; page <= MAX_ONLINE_PAGES; page++) {
            JsonNode root = requestJson("GET", PLATFORM_DEVICE_LIST,
                    Map.of("page", page, "limit", PAGE_SIZE, "deviceCode", deviceNumber));
            JsonNode rows = firstArray(root, "data", "datas");
            for (JsonNode row : rows) {
                String number = text(row, "deviceNumber", "device_number", "deviceCode");
                if (deviceNumber.equalsIgnoreCase(number)) {
                    return Boolean.TRUE.equals(parseOnline(row));
                }
            }
            int total = root.path("count").asInt(-1);
            if (rows.isEmpty() || total < 0 || page * PAGE_SIZE >= total) {
                break;
            }
        }
        return false;
    }

    /**
     * 控制台登录。精讯云控制台登录接口不带图形验证码，适合服务端会话复用。
     */
    private void ensureLogin() {
        if (secretKey != null || loggedIn) {
            return;
        }
        synchronized (this) {
            if (loggedIn) {
                return;
            }
            login();
            loggedIn = true;
        }
    }

    /**
     * 执行控制台登录并校验响应。
     */
    private void login() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("userName", username);
        form.put("password", password);
        HttpResponse<String> response = sendForm(LOGIN_PATH, form);
        JsonNode root = parseResponse(response, LOGIN_PATH);
        if (!"success".equalsIgnoreCase(root.path("state").asText())) {
            throw new IllegalStateException("精讯云登录失败: " + root.path("msg").asText("账号或密码错误"));
        }
    }

    /**
     * 发起 GET/POST 请求并返回通过业务状态校验的 JSON。
     *
     * @param method HTTP 方法
     * @param path   接口路径
     * @param params 业务参数
     * @return 响应根节点
     */
    private JsonNode requestJson(String method, String path, Map<String, Object> params) {
        try {
            return requestJsonOnce(method, path, params);
        } catch (IllegalStateException ex) {
            // 控制台会话过期时平台返回登录页或“未登录”JSON；重新登录后重试一次。
            if (secretKey == null && isSessionExpired(ex)) {
                loggedIn = false;
                ensureLogin();
                return requestJsonOnce(method, path, params);
            }
            throw ex;
        }
    }

    /**
     * 判断异常是否由控制台会话过期引起。
     *
     * @param ex 异常
     * @return 会话过期返回 true
     */
    private static boolean isSessionExpired(IllegalStateException ex) {
        String message = ex.getMessage();
        return message != null
                && (message.contains("登录页") || message.contains("未登录") || message.contains("登录超时"));
    }

    /**
     * 单次请求（不处理会话过期）。
     *
     * @param method HTTP 方法
     * @param path   接口路径
     * @param params 业务参数
     * @return 响应根节点
     */
    private JsonNode requestJsonOnce(String method, String path, Map<String, Object> params) {
        if (secretKey == null) {
            ensureLogin();
        }
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        if (secretKey != null) {
            query.put("secretKey", secretKey);
        }
        HttpRequest request = "POST".equalsIgnoreCase(method)
                ? postFormRequest(path, query)
                : getRequest(path, query);
        HttpResponse<String> response = send(request, path);
        return parseResponse(response, path);
    }

    /**
     * 构造 GET 请求。
     *
     * @param path  路径
     * @param query 查询参数
     * @return 请求
     */
    private HttpRequest getRequest(String path, Map<String, Object> query) {
        return HttpRequest.newBuilder(buildUri(path, query))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "SCNet-Device/4.0")
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    /**
     * 构造表单 POST 请求。
     *
     * @param path 路径
     * @param form 表单参数
     * @return 请求
     */
    private HttpRequest postFormRequest(String path, Map<String, Object> form) {
        return HttpRequest.newBuilder(buildUri(path, Map.of()))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "SCNet-Device/4.0")
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build();
    }

    /**
     * 发送控制台登录表单。
     *
     * @param path 登录路径
     * @param form 表单
     * @return 响应
     */
    private HttpResponse<String> sendForm(String path, Map<String, Object> form) {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, Map.of()))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "SCNet-Device/4.0")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build();
        return send(request, path);
    }

    /**
     * 发送请求。
     *
     * @param request 请求
     * @param path    接口路径（仅用于错误信息，不带查询串）
     * @return 响应
     */
    private HttpResponse<String> send(HttpRequest request, String path) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("精讯云接口调用被中断: " + path, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("精讯云接口调用失败: " + path + " -> " + ex.getClass().getSimpleName(), ex);
        }
    }

    /**
     * 校验 HTTP 与业务状态。
     *
     * @param response 响应
     * @param path     接口路径
     * @return JSON 根节点
     */
    private JsonNode parseResponse(HttpResponse<String> response, String path) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("精讯云接口返回 HTTP " + response.statusCode() + ": " + path);
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("精讯云接口返回空响应: " + path);
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("<")) {
            throw new IllegalStateException("精讯云接口返回登录页，请检查账号密码或 secretKey: " + path);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(trimmed);
        } catch (Exception ex) {
            throw new IllegalStateException("精讯云接口返回非 JSON: " + path, ex);
        }
        JsonNode state = root.get("state");
        if (state != null && !"success".equalsIgnoreCase(state.asText())) {
            throw new IllegalStateException("精讯云接口失败: " + path
                    + " -> " + root.path("msg").asText(""));
        }
        JsonNode success = root.get("success");
        if (success != null && success.isBoolean() && !success.asBoolean()) {
            throw new IllegalStateException("精讯云接口失败: " + path
                    + " -> " + root.path("msg").asText(""));
        }
        return root;
    }

    /**
     * 拼接平台地址与查询参数。
     *
     * @param path  路径
     * @param query 查询参数
     * @return URI
     */
    private URI buildUri(String path, Map<String, Object> query) {
        StringBuilder uri = new StringBuilder(baseUrl).append(path);
        String encoded = formEncode(query);
        if (!encoded.isEmpty()) {
            uri.append(path.contains("?") ? '&' : '?').append(encoded);
        }
        return URI.create(uri.toString());
    }

    /**
     * 表单编码。
     *
     * @param values 参数
     * @return 编码后的字符串
     */
    private static String formEncode(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    /**
     * 映射一条精讯云设备记录。
     *
     * @param node         记录
     * @param defaultModel 默认型号
     * @return 设备条目；缺少设备号时返回 null
     */
    private VendorDevice mapDevice(JsonNode node, String defaultModel) {
        String deviceNumber = text(node, "deviceNumber", "device_number", "deviceCode");
        if (deviceNumber == null) {
            return null;
        }
        String name = text(node, "name", "deviceName");
        if (name == null) {
            name = "未命名设备";
        }
        String model = text(node, "type", "deviceType", "model", "templateName");
        if (model == null) {
            model = defaultModel;
        }
        return new VendorDevice(
                deviceNumber,
                name,
                model,
                "other",
                deviceNumber,
                null,
                null,
                text(node, "address", "location", "installAddress"),
                decimal(node, "latitude", "lat"),
                decimal(node, "longitude", "lng"),
                0,
                parseOnline(node)
        );
    }

    /**
     * 取第一个数组字段。
     *
     * @param root  根节点
     * @param names 候选字段
     * @return 数组节点；找不到时返回空数组
     */
    private static JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        JsonNode data = root.get("data");
        if (data != null) {
            if (data.isArray()) {
                return data;
            }
            for (String name : new String[]{"records", "rows", "list"}) {
                JsonNode nested = data.get(name);
                if (nested != null && nested.isArray()) {
                    return nested;
                }
            }
        }
        return MAPPER.createArrayNode();
    }

    /**
     * 取第一个非空文本字段。
     *
     * @param node  节点
     * @param names 候选字段
     * @return 文本；没有时返回 null
     */
    private static String text(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    /**
     * 取第一个可解析的数值字段。
     *
     * @param node  节点
     * @param names 候选字段
     * @return 数值；没有时返回 null
     */
    private static BigDecimal decimal(JsonNode node, String... names) {
        String text = text(node, names);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 解析在线状态。
     *
     * @param node 节点
     * @return true 在线、false 离线、null 平台未返回
     */
    private static Boolean parseOnline(JsonNode node) {
        String value = text(node, "onLineState", "online", "onlineState", "state");
        if (value == null) {
            return null;
        }
        if ("1".equals(value) || "true".equalsIgnoreCase(value) || "online".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value) || "offline".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 解析平台地址。
     *
     * @param info 连接信息
     * @return 去掉末尾斜杠的地址
     */
    private static String resolveBaseUrl(DeviceConnectInfo info) {
        Object override = info.extra() == null ? null : info.extra().get("baseUrl");
        String value = override instanceof String text && !text.isBlank()
                ? text.trim() : DEFAULT_BASE_URL;
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param values 候选值
     * @return 第一个非空白值；没有时返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    /**
     * 去空白，空串归一为 null。
     *
     * @param value 原值
     * @return 处理后的值
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 取最内层异常信息。
     *
     * @param ex 异常
     * @return 非空描述
     */
    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }

    @Override
    public InputStream preview(int channel) {
        throw new UnsupportedOperationException("精讯云不支持实时视频预览");
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        throw new UnsupportedOperationException("精讯云不支持录像检索");
    }

    @Override
    public InputStream playback(int channel, TimeRange range) {
        throw new UnsupportedOperationException("精讯云不支持录像回放");
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        throw new UnsupportedOperationException("精讯云不支持视频取流");
    }

    @Override
    public String getPlaybackUrl(int channel, TimeRange range) {
        throw new UnsupportedOperationException("精讯云不支持录像回放地址");
    }

    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        throw new UnsupportedOperationException("精讯云不支持门禁出入记录");
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        throw new UnsupportedOperationException("精讯云不支持云台控制");
    }

    @Override
    public void setPreset(int presetId, String name) {
        throw new UnsupportedOperationException("精讯云不支持预置位");
    }

    @Override
    public void callPreset(int presetId) {
        throw new UnsupportedOperationException("精讯云不支持预置位");
    }

    @Override
    public void deletePreset(int presetId) {
        throw new UnsupportedOperationException("精讯云不支持预置位");
    }

    @Override
    public List<PresetInfo> listPresets() {
        throw new UnsupportedOperationException("精讯云不支持预置位");
    }

    @Override
    public void remoteUnlock(int doorId) {
        throw new UnsupportedOperationException("精讯云不支持远程开门");
    }

    @Override
    public void subscribeEvent(EventType type, EventListener listener) {
        throw new UnsupportedOperationException("精讯云不支持事件订阅");
    }

    @Override
    public void unsubscribeEvent(EventType type) {
        throw new UnsupportedOperationException("精讯云不支持事件订阅");
    }

    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        throw new UnsupportedOperationException("精讯云不支持事件拉取");
    }
}

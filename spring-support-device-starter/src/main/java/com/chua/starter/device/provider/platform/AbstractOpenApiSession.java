package com.chua.starter.device.provider.platform;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 厂商平台 打开api 会话基类（基于 common-starter http客户端）
 * <p>复用公共模块的 {@link HttpClient}（JDK HttpClient 封装），不依赖任何本地动态库，全平台可用。</p>
 *
 * <p>覆盖海康云耀、云眸、安防平台以及大华云睿、乐橙等厂商平台，</p>
 * <p>各平台接入的 endpoint、签名算法需按平台官方 OpenAPI 文档配置。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public abstract class AbstractOpenApiSession implements DeviceSession {

    /**
     * JSON 解析器
     */
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 默认连接超时（毫秒）
     */
    protected static final long DEFAULT_CONNECT_TIMEOUT = 10_000L;

    /**
     * 默认读取超时（毫秒）
     */
    protected static final long DEFAULT_READ_TIMEOUT = 30_000L;

    /**
     * 调试开关系统属性：{@code -Ddevice.openapi.debug=true} 开启，
     * <p>开启后将打印每个 OpenAPI 请求的 URL、headers（脱敏）、请求体、响应码与响应体前 N 字节，</p>
     * <p>便于本地/灰度联调时与厂商官方 OpenAPI 文档核对 endpoint、字段与签名。</p>
     */
    public static final String DEBUG_PROPERTY = "plugin.device.openapi.debug";

    /**
     * 响应体打印长度上限（字节）
     */
    private static final int RESPONSE_LOG_MAX_BYTES = 2048;

    /**
     * 设备连接信息
     */
    protected final DeviceConnectInfo info;

    /**
     * 在线状态
     */
    protected volatile boolean online = true;

    /**
     * access令牌 / 会话标识
     */
    protected volatile String accessToken;

    /**
     * 凭证过期时间
     */
    protected volatile Instant tokenExpiry = Instant.EPOCH;

    /**
     * 平台 baseurl
     */
    protected final String baseUrl;

    /**
     * 事件订阅监听器
     */
    protected final Map<EventType, List<EventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 事件缓冲队列
     */
    protected final List<DeviceEvent> eventQueue = new CopyOnWriteArrayList<>();

    /**
     * 构造 打开api 会话
     *
     * @param info    设备连接信息
     * @param baseUrl 平台默认 baseurl
     */
    protected AbstractOpenApiSession(DeviceConnectInfo info, String baseUrl) {
        this.info = info;
        Object extra = info.getExtra() == null ? null : info.getExtra().get("baseUrl");
        this.baseUrl = extra instanceof String s ? s : baseUrl;
    }

    /**
     * 子类实现：执行带签名/认证的 HTTP 请求并返回 JSON 节点。
     *
     * @param method HTTP 方法（获取 / POST）
     * @param path   接口路径（相对于平台 baseurl）
     * @param params 请求参数
     * @return 响应 JSON
     */
    protected abstract JsonNode doRequest(String method, String path, Map<String, Object> params);

    /**
     * 通用 获取
     * @param path 路径
     * @param params 参数
     * @return 获取的结果
     */
    protected JsonNode get(String path, Map<String, Object> params) {
        return doRequest("GET", path, params);
    }

    /**
     * 通用 POST
     * @param path 路径
     * @param params 参数
     * @return post的结果
     */
    protected JsonNode post(String path, Map<String, Object> params) {
        return doRequest("POST", path, params);
    }

    /**
     * 调用 common-starter http客户端 发起请求并解析响应 JSON
     *
     * @param method  HTTP 方法
     * @param path    接口路径
     * @param params  业务参数
     * @param headers 自定义请求头（如签名头）
     * @return 响应 JSON
     */
    protected JsonNode execute(String method, String path, Map<String, Object> params,
                               Map<String, String> headers) {
        boolean debug = debugEnabled();
        if (debug) {
            /**
             * 日志请求。
             * @param method 方法
             * @param path 路径
             * @param headers 头部
             * @param params 参数
             */
            logRequest(method, path, headers, params);
        }
        try {
            HttpClientBuilder builder = HttpClientFactory.of(baseUrl)
                    .path(path)
                    .json()
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .readTimeout(DEFAULT_READ_TIMEOUT);
            if (headers != null) {
                headers.forEach(builder::header);
            }
            ClientResponse response;
            if ("GET".equalsIgnoreCase(method)) {
                if (params != null) {
                    params.forEach((k, v) -> {
                        if (v != null) {
                            builder.query(k, String.valueOf(v));
                        }
                    });
                }
                response = builder.get();
            } else {
                Map<String, Object> body = params == null ? Collections.emptyMap() : params;
                response = builder.body(body).post();
            }
            String respBody = response.getBodyString();
            if (debug) {
                /**
                 * 日志响应。
                 * @param method 方法
                 * @param path 路径
                 * @param response 响应
                 * @param respBody resp主体
                 */
                logResponse(method, path, response, respBody);
            }
            return MAPPER.readTree(respBody == null ? "{}" : respBody);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAPI HTTP 调用失败: " + method + " " + path + " -> " + e.getMessage(), e);
        }
    }

    /**
     * 调试开关是否启用
     *
     * @return true 启用
     */
    protected static boolean debugEnabled() {
        return Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
    }

    /**
     * 打印请求调试日志（脱敏 secret/appKey）
     * @param method 方法
     * @param path 路径
     * @param headers 头部
     * @param params 参数
     */
    private void logRequest(String method, String path, Map<String, String> headers, Map<String, Object> params) {
        log.info("[OpenAPI-DEBUG] -> {} {}{}", method, baseUrl, path);
        if (headers != null && !headers.isEmpty()) {
            log.info("[OpenAPI-DEBUG] -> headers: {}", maskHeaders(headers));
        }
        if (params != null && !params.isEmpty()) {
            log.info("[OpenAPI-DEBUG] -> body: {}", maskParams(params));
        }
    }

    /**
     * 打印响应调试日志
     * @param method 方法
     * @param path 路径
     * @param response 响应
     * @param respBody resp主体
     */
    private void logResponse(String method, String path, ClientResponse response, String respBody) {
        log.info("[OpenAPI-DEBUG] <- {} {}{} status={}", method, baseUrl, path, response.getStatusCode());
        if (respBody != null && !respBody.isEmpty()) {
            String snippet = respBody.length() > RESPONSE_LOG_MAX_BYTES
                    ? respBody.substring(0, RESPONSE_LOG_MAX_BYTES) + "...(truncated)"
                    : respBody;
            log.info("[OpenAPI-DEBUG] <- body: {}", snippet);
        }
    }

    /**
     * 脱敏请求头：将含敏感字段（secret、令牌、签名）的值替换为 {@code ****xxxx} 形式。
     * @param headers 头部
     * @return mask头部的结果
     */
    private static Map<String, String> maskHeaders(Map<String, String> headers) {
        java.util.LinkedHashMap<String, String> masked = new java.util.LinkedHashMap<>();
        headers.forEach((k, v) -> {
            String lk = k == null ? "" : k.toLowerCase();
            if (lk.contains("secret") || lk.contains("signature") || lk.contains("token")) {
                masked.put(k, v == null ? "null" : maskTail(v));
            } else {
                masked.put(k, v);
            }
        });
        return masked;
    }

    /**
     * 脱敏参数：将 appsecret/appKey 等敏感字段值替换。
     * @param params 参数
     * @return mask参数的结果
     */
    private static Map<String, Object> maskParams(Map<String, Object> params) {
        java.util.LinkedHashMap<String, Object> masked = new java.util.LinkedHashMap<>();
        params.forEach((k, v) -> {
            String lk = k == null ? "" : k.toLowerCase();
            if (lk.contains("secret")) {
                masked.put(k, v == null ? "null" : maskTail(String.valueOf(v)));
            } else {
                masked.put(k, v);
            }
        });
        return masked;
    }

    /**
     * 保留值前 2 位 + 末 2 位，中间用 **** 替换。
     * @param v v
     * @return maskTail的结果
     */
    private static String maskTail(String v) {
        if (v == null) {
            return "null";
        }
        int len = v.length();
        if (len <= 6) {
            return "****";
        }
        return v.substring(0, 2) + "****" + v.substring(len - 2);
    }

    @Override
    /**
     * 获取deviceid
    */
    public String getDeviceId() {
        return info.getDeviceSerial() != null ? info.getDeviceSerial() : info.getIp();
    }

    @Override
    /**
     * 是否Online
    */
    public boolean isOnline() {
        return online;
    }

    @Override
    /**
     * 关闭
    */
    public void close() {
        online = false;
        log.info("OpenAPI 会话已关闭: {}", getDeviceId());
    }

    /**
     * 校验响应码：约定 编码 == 200 / "200" 表示成功，其它抛出异常。
     *
     * @param resp 响应 JSON
     * @return data 节点
     */
    protected JsonNode assertOk(JsonNode resp) {
        if (resp == null) {
            /**
             * illegal状态异常。
             * @param 响应为空" 响应为空"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("OpenAPI 响应为空");
        }
        JsonNode code = resp.get("code");
        if (code != null && !code.asText("200").equals("200")) {
            String msg = resp.path("msg").asText("");
            /**
             * illegal状态异常。
             * @param code.asText( 编码.as文本(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("OpenAPI 调用失败: code=" + code.asText() + ", msg=" + msg);
        }
        JsonNode data = resp.get("data");
        return data != null ? data : resp;
    }

    /**
     * 平台会话通用方法：打开api 拉流返回 URL，由前端直接播放 HLS/FLV，
     * 此处返回只读空流，避免阻塞调用方。
     * @return 空流的结果
     */
    protected InputStream emptyStream() {
        /**
         * bytearray输入流。
         * @param byte[0] byte[0]
         * @return bytearray输入流的结果
         */
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    /**
     * Preview
    */
    public InputStream preview(int channel) {
        return emptyStream();
    }

    @Override
    /**
     * 查询Records
    */
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        return Collections.emptyList();
    }

    @Override
    /**
     * Playback
    */
    public InputStream playback(int channel, TimeRange range) {
        return emptyStream();
    }

    @Override
    /**
     * 获取liveurl
    */
    public String getLiveUrl(int channel, String protocol) {
        // 子类按平台实现获取 HLS/FLV URL；默认返回空
        return "";
    }

    @Override
    /**
     * 获取playbackurl
    */
    public String getPlaybackUrl(int channel, TimeRange range) {
        // 子类按平台实现获取回放 URL
        return null;
    }

    @Override
    /**
     * 测试connection
    */
    public boolean testConnection() {
 // 默认通过 令牌 缓存判断：有效即认为可达
        return tokenValid();
    }

    @Override
    /**
     * 列表presets
    */
    public List<PresetInfo> listPresets() {
        return Collections.emptyList();
    }

    @Override
    /**
     * 查询accessrecords
    */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        return Collections.emptyList();
    }

    @Override
    /**
     * 订阅事件
    */
    public void subscribeEvent(EventType type, EventListener listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    /**
     * 取消订阅事件
    */
    public void unsubscribeEvent(EventType type) {
        listeners.remove(type);
    }

    @Override
    /**
     * 取出事件
    */
    public List<DeviceEvent> pollEvents(TimeRange range) {
        if (eventQueue.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceEvent> result = new ArrayList<>(eventQueue);
        eventQueue.clear();
        return result;
    }

    @Override
    /**
     * 远程解锁
    */
    public void remoteUnlock(int doorId) {
 // 平台 打开api 远程开门由具体平台实现重写
    }

    @Override
    /**
     * 设置Preset
    */
    public void setPreset(int presetId, String name) {
 // 平台 打开api 预置位操作由具体平台实现重写
    }

    @Override
    /**
     * 调用Preset
    */
    public void callPreset(int presetId) {
 // 平台 打开api 预置位操作由具体平台实现重写
    }

    @Override
    /**
     * 删除Preset
    */
    public void deletePreset(int presetId) {
 // 平台 打开api 预置位操作由具体平台实现重写
    }

    @Override
    /**
     * ptzcontrol
    */
    public void ptzControl(PtzCommand cmd, double speed) {
 // 平台 打开api 云台控制由具体平台实现重写
    }

    /**
     * 缓存 access令牌 等凭证
     *
     * @param token     凭证
     * @param ttlSecond 有效期（秒）
     */
    protected void cacheToken(String token, long ttlSecond) {
        this.accessToken = token;
        this.tokenExpiry = Instant.now().plus(Duration.ofSeconds(Math.max(60, ttlSecond - 60)));
    }

    /**
     * 令牌 是否仍然有效
     * @return 令牌valid的结果
     */
    protected boolean tokenValid() {
        return accessToken != null && Instant.now().isBefore(tokenExpiry);
    }

    /**
     * UTF-8 字节
     * @param s s
     * @return utf8的结果
     */
    protected static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}

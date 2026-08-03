package com.chua.starter.device.provider.platform;

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
 * 厂商平台 OpenAPI 会话基类（纯 Java HTTP）
 * <p>基于 JDK HttpClient 调用厂商 HTTP OpenAPI，不依赖任何本地动态库，全平台可用。</p>
 *
 * <p>当前覆盖海康云耀、云眸、安防平台以及大华云睿、乐橙等厂商平台，</p>
 * <p>各平台接入的 endpoint、签名算法需按平台官方 OpenAPI 文档配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
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
     * 设备连接信息
     */
    protected final DeviceConnectInfo info;

    /**
     * 在线状态
     */
    protected volatile boolean online = true;

    /**
     * accessToken / sessionId
     */
    protected volatile String accessToken;

    /**
     * 凭证过期时间
     */
    protected volatile Instant tokenExpiry = Instant.EPOCH;

    /**
     * 事件订阅监听器
     */
    protected final Map<EventType, List<EventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 事件缓冲队列
     */
    protected final List<DeviceEvent> eventQueue = new CopyOnWriteArrayList<>();

    /**
     * 构造 OpenAPI 会话
     *
     * @param info 设备连接信息
     */
    protected AbstractOpenApiSession(DeviceConnectInfo info) {
        this.info = info;
    }

    /**
     * 子类实现：执行带签名/认证的 HTTP 请求并返回 JSON 节点。
     *
     * @param method HTTP 方法（GET / POST）
     * @param path   接口路径（相对于平台 baseUrl）
     * @param params 请求参数
     * @return 响应 JSON
     */
    protected abstract JsonNode doRequest(String method, String path, Map<String, Object> params);

    /**
     * 通用 GET
     */
    protected JsonNode get(String path, Map<String, Object> params) {
        return doRequest("GET", path, params);
    }

    /**
     * 通用 POST
     */
    protected JsonNode post(String path, Map<String, Object> params) {
        return doRequest("POST", path, params);
    }

    @Override
    public String getDeviceId() {
        return info.getDeviceSerial() != null ? info.getDeviceSerial() : info.getIp();
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public void close() {
        online = false;
        log.info("OpenAPI 会话已关闭: {}", getDeviceId());
    }

    /**
     * 校验响应码：约定 code == 200 / "200" 表示成功，其它抛出异常。
     *
     * @param resp 响应 JSON
     * @return data 节点
     */
    protected JsonNode assertOk(JsonNode resp) {
        if (resp == null) {
            throw new IllegalStateException("OpenAPI 响应为空");
        }
        JsonNode code = resp.get("code");
        if (code != null && !code.asText("200").equals("200")) {
            String msg = resp.path("msg").asText("");
            throw new IllegalStateException("OpenAPI 调用失败: code=" + code.asText() + ", msg=" + msg);
        }
        JsonNode data = resp.get("data");
        return data != null ? data : resp;
    }

    /**
     * 平台会话通用方法：OpenAPI 拉流返回 URL，由前端直接播放 HLS/FLV，
     * 此处返回只读空流，避免阻塞调用方。
     */
    protected InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream preview(int channel) {
        return emptyStream();
    }

    @Override
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        return Collections.emptyList();
    }

    @Override
    public InputStream playback(int channel, TimeRange range) {
        return emptyStream();
    }

    @Override
    public void remoteUnlock(int doorId) {
        // 平台 OpenAPI 远程开门由具体平台实现重写
    }

    @Override
    public void setPreset(int presetId, String name) {
        // 平台 OpenAPI 预置位操作由具体平台实现重写
    }

    @Override
    public void callPreset(int presetId) {
        // 平台 OpenAPI 预置位操作由具体平台实现重写
    }

    @Override
    public void deletePreset(int presetId) {
        // 平台 OpenAPI 预置位操作由具体平台实现重写
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        // 平台 OpenAPI 云台控制由具体平台实现重写
    }

    @Override
    public List<PresetInfo> listPresets() {
        return Collections.emptyList();
    }

    @Override
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        return Collections.emptyList();
    }

    @Override
    public void subscribeEvent(EventType type, EventListener listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribeEvent(EventType type) {
        listeners.remove(type);
    }

    @Override
    public List<DeviceEvent> pollEvents(TimeRange range) {
        if (eventQueue.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceEvent> result = new ArrayList<>(eventQueue);
        eventQueue.clear();
        return result;
    }

    /**
     * 缓存 accessToken 等凭证
     *
     * @param token     凭证
     * @param ttlSecond 有效期（秒）
     */
    protected void cacheToken(String token, long ttlSecond) {
        this.accessToken = token;
        this.tokenExpiry = Instant.now().plus(Duration.ofSeconds(Math.max(60, ttlSecond - 60)));
    }

    /**
     * token 是否仍然有效
     */
    protected boolean tokenValid() {
        return accessToken != null && Instant.now().isBefore(tokenExpiry);
    }

    /**
     * UTF-8 字节
     */
    protected static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}

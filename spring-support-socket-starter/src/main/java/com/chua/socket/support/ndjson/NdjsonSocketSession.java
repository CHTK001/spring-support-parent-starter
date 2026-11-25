package com.chua.socket.support.ndjson;

import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NDJSON Socket 会话实现
 * 封装 ResponseBodyEmitter 为统一的 Socket会话 接口，
 * 每条消息按「一行一个 JSON」写出：{@code {"event":"..","data":..}}。
 *
 * <p>与 SSE 版本（SseSocketSession）行为对齐：data 为 JSON 文本时原样内嵌，
 * 非 JSON 字符串按 JSON 字符串字面量内嵌，客户端按 event 分发、data 直接可用。</p>
 *
 * @since 2026-11-25
 * @author CH
 */
public class NdjsonSocketSession implements SocketSession {

    /**
     * 心跳事件名：客户端收到后跳过，不分发给业务监听器
     */
    public static final String PING_EVENT = "ping";

    /**
     * Objectmapper
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 标识
     */
    private final String id;

    /**
     * 客户端标识
     */
    private String clientId;

    /**
     * 用户
     */
    private SocketUser user;

    /**
     * Emitter
     */
    private final ResponseBodyEmitter emitter;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>(); // attributes

    private volatile boolean connected = true; // 连接

    /**
     * 创建 ndjsonSocket会话 实例
     * @param id 标识
     * @param emitter responsebodyemitter
     */
    public NdjsonSocketSession(String id, ResponseBodyEmitter emitter) {
        this.id = id;
        this.clientId = id;
        this.emitter = emitter;

        // 设置回调
        emitter.onCompletion(() -> this.connected = false);
        emitter.onTimeout(() -> this.connected = false);
        emitter.onError(e -> this.connected = false);
    }

    @Override
    /**
     * 获取标识
     * @return 返回值
     */
    public String getId() {
        return id;
    }

    @Override
    /**
     * 获取客户端标识
     * @return 返回值
     */
    public String getClientId() {
        return clientId;
    }

    @Override
    /**
     * 设置客户端标识
     * @param clientId 字符串
     * @return 返回值
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    /**
     * 获取用户
     * @return 返回值
     */
    public SocketUser getUser() {
        return user;
    }

    @Override
    /**
     * 设置用户
     * @param user 参数
     * @return 返回值
     */
    public void setUser(SocketUser user) {
        this.user = user;
    }

    @Override
    /**
     * 设置Attribute
     * @param key 字符串
     * @param value 对象
     * @return 返回值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * 获取Attribute
     *
     * @param key Key
     * @return 获取attribute的结果
     */
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    /**
     * 移除Attribute
     * @param key 字符串
     * @return 返回值
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    /**
     * 获取Attributes
     * @return 返回值
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    /**
     * 是否连接
     * @return 返回值
     */
    public boolean isConnected() {
        return connected;
    }

    @Override
    /**
     * 断开
     */
    public void disconnect() {
        if (connected) {
            connected = false;
            emitter.complete();
        }
    }

    @Override
    /**
     * 发送：写出一行 NDJSON（JSON + 换行符）
     *
     * @param event 事件名称
     * @param data 消息数据
     */
    public void send(String event, Object data) {
        if (!connected) {
            return;
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("event", event);
            node.set("data", toDataNode(data));
            // 显式按 UTF-8 字节写出，避免 StringHttpMessageConverter 字符集歧义
            emitter.send(objectMapper.writeValueAsBytes(node));
            emitter.send("\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            connected = false;
        }
    }

    /**
     * data 归一化：JSON 文本解析为树原样内嵌，普通字符串按字符串字面量内嵌，其余对象转树。
     *
     * @param data 消息数据
     * @return JsonNode
     */
    private JsonNode toDataNode(Object data) {
        if (data instanceof String text) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception e) {
                return objectMapper.getNodeFactory().textNode(text);
            }
        }
        if (data == null) {
            return objectMapper.nullNode();
        }
        return objectMapper.valueToTree(data);
    }

    /**
     * 心跳：发一条 {@code {"event":"ping"}} 行，保持连接不被代理/网关按空闲关闭。
     *
     * @return 连接仍可用返回 true
     */
    public boolean ping() {
        if (!connected) {
            return false;
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("event", PING_EVENT);
            emitter.send(objectMapper.writeValueAsBytes(node));
            emitter.send("\n".getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            connected = false;
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * 获取Connection
     *
     * @return 获取connection的结果
     */
    public <T> T getConnection() {
        return (T) emitter;
    }

    /**
     * 获取 responsebodyemitter
     *
     * @return ResponseBodyEmitter
     */
    public ResponseBodyEmitter getEmitter() {
        return emitter;
    }
}

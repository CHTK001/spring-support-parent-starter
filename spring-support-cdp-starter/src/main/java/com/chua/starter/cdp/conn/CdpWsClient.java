package com.chua.starter.cdp.conn;

import com.chua.common.support.lang.json.Json;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 轻量 CDP JSON-RPC WebSocket 客户端
 *
 * <p>基于 JDK 原生的 {@link WebSocket}（{@code java.net.http.WebSocket}）实现，
 * 要求宿主机上有一个已经启动的 Chromium（通过 {@code --remote-debugging-port} 或
 * {@code --remote-debugging-port=0} 后读 {@code DevToolsActivePort}）。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>{@code call}：按方法名发送 CDP 命令并等待结果；</li>
 *   <li>{@code on}：按方法名订阅 CDP 事件；</li>
 * </ul>
 *
 * <p>JSON-RPC 协议约定：</p>
 * <ul>
 *   <li>调用：{"id":1,"method":"Page.navigate","params":{...}}，回复：{"id":1,"result":{...}} 或 {"id":1,"error":{...}}；</li>
 *   <li>事件：{"method":"Runtime.consoleAPI","params":{...}} 或 {"id":...};</li>
 *   <li>没有 sessionId 的命令广播到浏览器级（browser），有 sessionId 的命令发到页面级（page）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class CdpWsClient implements AutoCloseable {

    /**
     * 断连后最长重试间隔（ms）
     */
    private static final long MAX_RETRY_MILLIS = 30_000L;

    /**
     * 底层的 WebSocket 连接
     */
    private final WebSocket socket;

    /**
     * 已经发出但未收到回复的命令
     */
    private final Map<Long, CompletableFuture<Map<String, Object>>> pending;

    /**
     * 事件监听器（method -> listeners）
     */
    private final Map<String, List<Consumer<Map<String, Object>>>> handlers;

    /**
     * 序列号
     */
    private final AtomicLong sequence;

    /**
     * 是否正在关闭（杀掉未完成的调用）
     */
    private volatile boolean closing;

    /**
     * 连接是否已经断开（浏览器被关掉、进程崩溃、端口被回收）
     *
     * <p>断开后所有 {@code call} 都会以 {@code Output closed} 直接失败，
     * 上层必须先看到这个标记再重建连接，否则界面只会显示「操作处理失败」。</p>
     */
    private volatile boolean closed;

    /**
     * 本地发送锁
     */
    private final Object sendLock;

    /**
     * @param socket 已经连接好的 WebSocket
     */
    CdpWsClient(WebSocket socket) {
        pending = new ConcurrentHashMap<>();
        handlers = new ConcurrentHashMap<>();
        sequence = new AtomicLong();
        sendLock = new Object();
        this.socket = socket;
    }

    /**
     * 连接浏览器的 DevTools 端点
     *
     * @param endpoint  如 {@code ws://127.0.0.1:9222/devtools/browser/<uuid>}
     * @param timeout   连接超时
     * @return 客户端（连接已成功）
     */
    public static CdpWsClient connect(String endpoint, Duration timeout) {
        AtomicReference<CdpWsClient> reference = new AtomicReference<>();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(timeout)
                .buildAsync(URI.create(endpoint), new Listener(reference))
                .join();
        CdpWsClient client = new CdpWsClient(socket);
        reference.set(client);
        return client;
    }

    /**
     * 发送 CDP 命令并等待结果
     *
     * @param method 方法名，如 {@code Target.createTarget}
     * @param params 调用参数
     * @param timeout 等待超时，为 null 时默认 30 s
     * @return 返回的 JSON Map（包含 {@code __error} 时调用已失败）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String method, Map<String, Object> params, Duration timeout) {
        long id = sequence.incrementAndGet();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", id);
        message.put("method", method);
        message.put("params", params == null ? Collections.emptyMap() : params);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            synchronized (sendLock) {
                socket.sendText(Json.toJson(message, new String[0]), true).join();
            }
            Map<String, Object> result = future.get(
                    (timeout == null ? Duration.ofSeconds(30) : timeout).toMillis(), TimeUnit.MILLISECONDS);
            Object error = result.get("__error");
            if (error != null) {
                throw new IllegalStateException("CDP " + method + " failed: " + Json.toJson(error, new String[0]));
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CDP command was interrupted: " + method, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("CDP command exception: " + method, e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("CDP command timed out: " + method, e);
        } finally {
            pending.remove(id);
        }
    }

    /**
     * 注册 CDP 事件监听器（按方法名），支持粘贴和事件传递
     *
     * @param method 事件方法名，如 {@code Runtime.consoleAPI} / {@code Network.requestWillBeSent}
     * @param handler 处理代码，抛异常时在 service 中被隔离
     */
    public void on(String method, Consumer<Map<String, Object>> handler) {
        handlers.computeIfAbsent(method, key -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    /**
     * 注册所有事件的监听器
     */
    public void onAll(Consumer<Map<String, Object>> handler) {
        handlers.computeIfAbsent("all", key -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    /**
     * 协接 no selector; 返回是否有打开的 session
     */
    public boolean isClosing() {
        return closing;
    }

    /**
     * 连接是否已经断开（不可再发送）
     */
    public boolean isClosed() {
        return closed || closing;
    }

    /**
     * 主动标记为已断开（由上层在重建连接前调用）
     */
    public void markClosed() {
        closed = true;
    }

    /**
     * 分发收到的原始文本
     *
     * @param text 原始 JSON
     */
    private void dispatch(String text) {
        Map<String, Object> message;
        try {
            message = Json.fromJson(text);
        } catch (Exception e) {
            log.debug("[cdp] 丢弃无法解析的 WebSocket 消息: {}", e.getMessage());
            return;
        }
        if (message == null) {
            return;
        }

        Object rawId = message.get("id");
        if (rawId instanceof Number number) {
            CompletableFuture<Map<String, Object>> future = pending.remove(number.longValue());
            if (future != null) {
                Map<String, Object> value = new LinkedHashMap<>();
                Object error = message.get("error");
                if (error != null) {
                    value.put("__error", error);
                } else {
                    Object result = message.get("result");
                    if (result instanceof Map<?, ?> map) {
                        map.forEach((key, item) -> value.put(String.valueOf(key), item));
                    }
                }
                future.complete(value);
            }
            return;
        }

        Object method = message.get("method");
        if (!(method instanceof String name)) {
            return;
        }
        dispatchEvent(name, message);
    }

    /**
     * 投递事件到监听器
     */
    private void dispatchEvent(String name, Map<String, Object> message) {
        List<Consumer<Map<String, Object>>> snapshot = new ArrayList<>();
        List<Consumer<Map<String, Object>>> list = handlers.get(name);
        if (list != null && !list.isEmpty()) {
            synchronized (list) {
                list = new ArrayList<>(list);
            }
            snapshot.addAll(list);
        }
        List<Consumer<Map<String, Object>>> allList = handlers.get("all");
        if (allList != null && !allList.isEmpty()) {
            synchronized (allList) {
                list = new ArrayList<>(allList);
            }
            snapshot.addAll(list);
        }
        for (Consumer<Map<String, Object>> handler : snapshot) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                log.warn("[cdp] 事件处理器异常 {}: {}", name, e.getMessage());
            }
        }
    }

    /**
     * 关闭连接（通知远端已离开）
     */
    @Override
    public void close() {
        closing = true;
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[cdp] 关闭连接失败: {}", e.getMessage());
        }
        for (CompletableFuture<Map<String, Object>> future : pending.values()) {
            future.completeExceptionally(new IllegalStateException("CDP client was closed"));
        }
        pending.clear();
        List<List<Consumer<Map<String, Object>>>> containers = new ArrayList<>(handlers.values());
        for (List<Consumer<Map<String, Object>>> container : containers) {
            container.clear();
        }
        handlers.clear();
        try {
            socket.abort();
        } catch (Exception e) {
            log.debug("[cdp] 关闭 socket 失败: {}", e.getMessage());
        }
    }

    /**
     * WebSocket 监听器实现
     */
    private static final class Listener implements WebSocket.Listener {

        /**
         * 客户端引用，提供顺序，必须在广播时注销
         */
        private final AtomicReference<CdpWsClient> reference;

        /**
         * 面板分段文本（前一个是长文本的片段）
         */
        private final StringBuilder buffer;

        private Listener(AtomicReference<CdpWsClient> reference) {
            this.buffer = new StringBuilder();
            this.reference = reference;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                CdpWsClient client = reference.get();
                if (client != null) {
                    client.dispatch(text);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            CdpWsClient client = reference.get();
            if (client != null) {
                client.closed = true;
                if (client.isClosing()) {
                    return;
                }
            }
            log.warn("[cdp] WebSocket error: {}", error.getMessage(), error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            CdpWsClient client = reference.get();
            if (client != null) {
                client.closed = true;
                if (!client.isClosing()) {
                    log.warn("[cdp] WebSocket 连接已断开: code={} reason={}", statusCode, reason);
                }
            }
            return null;
        }
    }
}

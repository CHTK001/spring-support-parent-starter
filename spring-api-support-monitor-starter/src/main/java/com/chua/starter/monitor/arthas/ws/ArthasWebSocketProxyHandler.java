package com.chua.starter.monitor.arthas.ws;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.monitor.pojo.OnlineNodeInfo;
import com.chua.starter.monitor.service.NodeManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arthas 控制台 WebSocket 反向代理处理器
 *
 * 前端通过 /v1/arthas/console/{nodeId}/ws 建立 WS 连接，本处理器读取节点元数据中的
 * report.client.arthas.port（ws(s)://host:port/ws），然后在后端与上游建立 WS 连接，
 * 实现双向帧转发，做到“由后端连接 arthas-console，而非前端”。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArthasWebSocketProxyHandler extends AbstractWebSocketHandler {

    private static final String ARTHAS_META_KEY = "report.client.arthas.port";

    private final NodeManagementService nodeManagementService;

    private final Map<String, WebSocket> sessionToUpstream = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri() == null ? null : session.getUri().toString();
        String nodeId = extractNodeId(session.getUri());
        log.info("Arthas WS 建立: sessionId={}, uri={}, nodeId={}", session.getId(), uri, nodeId);

        if (StringUtils.isBlank(nodeId)) {
            session.close(CloseStatus.BAD_DATA.withReason("缺少 nodeId"));
            return;
        }

        ReturnResult<OnlineNodeInfo> nodeResult = nodeManagementService.getNodeDetails(nodeId);
        if (!nodeResult.isSuccess() || nodeResult.getData() == null) {
            session.close(CloseStatus.BAD_DATA.withReason("节点不可用"));
            return;
        }

        OnlineNodeInfo node = nodeResult.getData();
        Object tunnel = node.getMetadata() == null ? null : node.getMetadata().get(ARTHAS_META_KEY);
        if (tunnel == null) {
            session.close(CloseStatus.BAD_DATA.withReason("节点未配置 Arthas 元数据"));
            return;
        }
        String upstream = String.valueOf(tunnel).trim();
        if (StringUtils.isBlank(upstream)) {
            session.close(CloseStatus.BAD_DATA.withReason("Arthas 地址为空"));
            return;
        }

        try {
            // 确保为 ws(s)://.../ws
            if (!(StringUtils.startsWithIgnoreCase(upstream, "ws://") || StringUtils.startsWithIgnoreCase(upstream, "wss://"))) {
                // 尝试从 http(s):// 转换
                if (StringUtils.startsWithIgnoreCase(upstream, "http://")) {
                    upstream = "ws://" + upstream.substring("http://".length());
                } else if (StringUtils.startsWithIgnoreCase(upstream, "https://")) {
                    upstream = "wss://" + upstream.substring("https://".length());
                }
            }

            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<WebSocket> fut = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(upstream), new UpstreamListener(session));

            fut.whenComplete((ws, ex) -> {
                if (ex != null) {
                    log.error("连接上游 Arthas WS 失败: {}", upstream, ex);
                    tryClose(session, CloseStatus.SERVER_ERROR.withReason("上游连接失败"));
                } else {
                    sessionToUpstream.put(session.getId(), ws);
                    log.info("上游 Arthas WS 已连接: {}", upstream);
                }
            });
        } catch (Exception e) {
            log.error("建立上游 WS 连接异常", e);
            tryClose(session, CloseStatus.SERVER_ERROR.withReason("上游连接异常"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocket upstream = sessionToUpstream.get(session.getId());
        if (upstream != null) {
            upstream.sendText(message.getPayload(), true);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        WebSocket upstream = sessionToUpstream.get(session.getId());
        if (upstream != null) {
            upstream.sendBinary(message.getPayload().asByteBuffer(), true);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Arthas WS 传输错误: sessionId={}, err={}", session.getId(), exception.getMessage());
        tryClose(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocket upstream = sessionToUpstream.remove(session.getId());
        if (upstream != null) {
            try { upstream.sendClose(WebSocket.NORMAL_CLOSURE, "client closed"); } catch (Exception ignored) {}
        }
        log.info("Arthas WS 关闭: sessionId={}, status={}", session.getId(), status);
    }

    private void tryClose(WebSocketSession session, CloseStatus status) {
        try { session.close(status); } catch (Exception ignored) {}
    }

    /**
     * 从路径 /v1/arthas/console/{nodeId}/ws 中提取 nodeId
     */
    private String extractNodeId(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath();
        // 简单解析：/v1/arthas/console/{nodeId}/ws
        String prefix = "/v1/arthas/console/";
        String suffix = "/ws";
        int i = StringUtils.indexOf(path, prefix);
        int j = StringUtils.lastIndexOf(path, suffix);
        if (i >= 0 && j > i) {
            String middle = path.substring(i + prefix.length(), j);
            // 去除可能的尾部 /
            if (middle.endsWith("/")) middle = middle.substring(0, middle.length() - 1);
            return middle;
        }
        return null;
    }

    /** 上游 WS 监听器：将上游消息转发给前端会话 */
    private static class UpstreamListener implements WebSocket.Listener {
        private final WebSocketSession downstream;
        UpstreamListener(WebSocketSession downstream) { this.downstream = downstream; }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                if (downstream.isOpen()) downstream.sendMessage(new TextMessage(data.toString()));
            } catch (Exception ignored) {}
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            try {
                if (downstream.isOpen()) downstream.sendMessage(new BinaryMessage(data));
            } catch (Exception ignored) {}
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            try { if (downstream.isOpen()) downstream.close(CloseStatus.NORMAL); } catch (Exception ignored) {}
            return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) {
            try { if (downstream.isOpen()) downstream.close(CloseStatus.SERVER_ERROR); } catch (Exception ignored) {}
        }
    }
}


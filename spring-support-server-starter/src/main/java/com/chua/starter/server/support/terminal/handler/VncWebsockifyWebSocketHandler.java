package com.chua.starter.server.support.terminal.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.net.NetUtils;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VNC websockify 桥接处理器
 * <p>
 * 浏览器侧使用 noVNC 直接讲 RFB 协议，本处理器只做 WebSocket 与原生 TCP 之间的
 * 透明字节转发，<b>不包含任何 RFB 协议代码</b>（握手、认证、编码解码全部在 noVNC 内完成）。
 * 取代此前自研的 VncDirectWebSocketHandler + RfbClient，桌面协议维护交给业界标准库 noVNC。
 * </p>
 * <p>
 * 帧约定：出/入向均为二进制帧（RFB 字节流）；文本帧不转发（noVNC 不发文本，
 * 收到即视为异常）。RFB 除口令外不加密，本桥只应在受信网络内使用。
 * </p>
 * 端点：{@code /ws/ms/server/terminal/vnc?hostId=&identityId=&user=}
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VncWebsockifyWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * TCP 连接超时（毫秒）
     */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /**
     * TCP 读缓冲：RFB 流按块转发，noVNC 侧自行重组，块大小不影响协议
     */
    private static final int BUFFER_SIZE = 32 * 1024;

    /**
     * 单帧二进制上限：放行整屏原始更新（1920x1080 原始约 6MB），取 16MB 余量
     */
    private static final int MAX_BINARY_FRAME = 16 * 1024 * 1024;

    /**
     * 审计与在线列表使用的协议名
     */
    private static final String PROTOCOL = "VNC";

    /**
     * 主机服务（地址/端口解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 会话审计服务
     */
    private final TerminalSessionService terminalSessionService;

    /**
     * 活跃桥接会话：WS 会话标识 -> 桥接上下文
     */
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    /**
     * 连接建立：解析目标并打开原生 TCP，随后启动 TCP->WS 泵
     *
     * @param wsSession 浏览器 WS 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        wsSession.setBinaryMessageSizeLimit(MAX_BINARY_FRAME);
        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        Long identityId = WebSocketSessions.parseLong(params.get("identityId"));
        String operator = params.get("user");
        // 显式目标端口：允许前端按当前协议（VNC）指定，覆盖台账 remotePort。
        // 与 /ws/ms/server/terminal 的 resolveTargetPort 同口径：显式请求优先，
        // 其次主机远控端口，最后 VNC 默认端口。
        Integer requestedPort = WebSocketSessions.parseInteger(params.get("targetPort"));

        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();
        // 本机地址去回环：桥接自身 LAN IP，不使用 127.0.0.1
        boolean isLocal = host.getServerHostCollectMode() != null
                && "LOCAL".equalsIgnoreCase(host.getServerHostCollectMode().trim());
        String address = isLocal ? NetUtils.getPreferredLocalIp() : host.getServerHostAddress();
        int port = requestedPort != null && requestedPort > 0
                ? requestedPort
                : (host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0
                        ? host.getServerHostRemotePort() : ServerConstants.DEFAULT_VNC_PORT);

        Socket tcp = new Socket();
        try {
            tcp.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            tcp.setTcpNoDelay(true);
        } catch (Exception e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[vnc-websockify] TCP 连接失败 hostId={} {}:{} {}", hostId, address, port, cause);
            closeQuietly(tcp);
            reject(wsSession, "VNC 连接失败 " + address + ":" + port + " - " + cause);
            return;
        }

        Bridge bridge = new Bridge(wsSession, hostId, tcp);
        String userName = WebSocketSessions.firstNonBlank(operator, host.getServerHostName());
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, userName, WebSocketSessions.clientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        bridge.start();
        log.info("[vnc-websockify] 桥接建立 sessionId={} hostId={} {}:{}",
                wsSession.getId(), hostId, address, port);
    }

    /**
     * 二进制帧：noVNC 来的 RFB 字节原样写入 TCP
     *
     * @param wsSession 浏览器 WS 会话
     * @param message   RFB 字节
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null) {
            return;
        }
        bridge.touch();
        bridge.toTcp(message.getPayload());
    }

    /**
     * 文本帧：websockify 通道不转发文本（noVNC 只发二进制）
     *
     * @param wsSession 浏览器 WS 会话
     * @param message   文本
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        log.debug("[vnc-websockify] 忽略文本帧 sessionId={} length={}",
                wsSession.getId(), message.getPayloadLength());
    }

    /**
     * 连接关闭：断开 TCP 并闭环审计
     *
     * @param wsSession WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            log.info("[vnc-websockify] 浏览器关闭 sessionId={} {}", wsSession.getId(), status);
            closeBridge(bridge, "浏览器断开");
        }
    }

    /**
     * 传输异常：统一走关闭流程
     *
     * @param wsSession WS 会话
     * @param exception  异常
     */
    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.warn("[vnc-websockify] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString());
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            closeBridge(bridge, "传输异常: " + WebSocketSessions.describeCause(exception));
        }
    }

    /**
     * 本通道在线会话摘要
     *
     * @return 在线会话列表
     */
    public List<TerminalOnlineVO> onlineSessions() {
        List<TerminalOnlineVO> result = new ArrayList<>(bridges.size());
        for (Bridge bridge : bridges.values()) {
            result.add(bridge.toOnlineView());
        }
        return result;
    }

    /**
     * 按审计记录标识强制关闭会话
     *
     * @param recordId 审计记录标识
     * @param reason   关闭原因
     * @return true 表示本通道持有该会话并已关闭
     */
    public boolean closeSession(Long recordId, String reason) {
        if (recordId == null) {
            return false;
        }
        for (Bridge bridge : bridges.values()) {
            if (recordId.equals(bridge.recordId)) {
                closeBridge(bridge, reason);
                return true;
            }
        }
        return false;
    }

    /**
     * 关闭空闲超时会话
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话数
     */
    public int sweepIdleSessions(int idleMinutes) {
        List<Bridge> idle = new ArrayList<>();
        for (Bridge bridge : bridges.values()) {
            if (ChronoUnit.MINUTES.between(bridge.lastActiveTime, LocalDateTime.now()) >= idleMinutes) {
                idle.add(bridge);
            }
        }
        for (Bridge bridge : idle) {
            closeBridge(bridge, "空闲超时");
        }
        return idle.size();
    }

    /**
     * 关闭桥接：幂等清理 TCP、审计记录与注册表
     *
     * @param bridge 桥接上下文
     * @param reason 原因
     */
    private void closeBridge(Bridge bridge, String reason) {
        if (bridge.closed) {
            return;
        }
        bridge.closed = true;
        bridges.remove(bridge.wsSession.getId());
        closeQuietly(bridge.tcp);
        terminalSessionService.closeRecord(bridge.recordId, reason);
        try {
            if (bridge.wsSession.isOpen()) {
                bridge.wsSession.close(CloseStatus.NORMAL.withReason(reason));
            }
        } catch (IOException e) {
            log.debug("[vnc-websockify] 关闭 WS 失败 sessionId={}: {}", bridge.wsSession.getId(), e.getMessage());
        }
        log.info("[vnc-websockify] 桥接关闭 sessionId={} reason={}", bridge.wsSession.getId(), reason);
    }

    /**
     * 拒绝连接：发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason     原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[vnc-websockify] 拒绝桥接 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                    + WebSocketSessions.escapeJson(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (IOException e) {
            log.debug("[vnc-websockify] 关闭被拒连接失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
        }
    }

    /**
     * 安静关闭套接字
     *
     * @param socket 套接字
     */
    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // 无需处理
        }
    }

    /**
     * 单条桥接上下文
     */
    private final class Bridge {

        /**
         * 浏览器 WS 会话
         */
        private final WebSocketSession wsSession;

        /**
         * 主机标识
         */
        private final Long hostId;

        /**
         * 原生 TCP 套接字
         */
        private final Socket tcp;

        /**
         * 审计记录标识，可空
         */
        private volatile Long recordId;

        /**
         * 最近活跃时间
         */
        private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

        /**
         * 是否已关闭（幂等标记）
         */
        private volatile boolean closed;

        /**
         * TCP->WS 泵线程
         */
        private Thread pump;

        /**
         * 构造桥接
         *
         * @param wsSession 浏览器 WS 会话
         * @param hostId    主机标识
         * @param tcp       原生 TCP
         */
        private Bridge(WebSocketSession wsSession, Long hostId, Socket tcp) {
            this.wsSession = wsSession;
            this.hostId = hostId;
            this.tcp = tcp;
        }

        /**
         * 刷新活跃时间
         */
        private void touch() {
            lastActiveTime = LocalDateTime.now();
        }

        /**
         * 启动 TCP->WS 泵
         */
        private void start() {
            pump = new Thread(this::pumpTcp, "vnc-websockify-" + hostId);
            pump.setDaemon(true);
            pump.start();
        }

        /**
         * 生成在线会话摘要
         *
         * @return 在线视图
         */
        private TerminalOnlineVO toOnlineView() {
            return new TerminalOnlineVO(wsSession.getId(), hostId, recordId, PROTOCOL, lastActiveTime);
        }

        /**
         * 持续读 TCP 并以二进制帧转发给浏览器；读到 EOF 或异常即收口
         */
        private void pumpTcp() {
            Throwable failure = null;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = tcp.getInputStream()) {
                int read;
                while (!closed && (read = input.read(buffer)) > 0) {
                    byte[] copy = Arrays.copyOf(buffer, read);
                    synchronized (wsSession) {
                        if (wsSession.isOpen()) {
                            wsSession.sendMessage(new BinaryMessage(copy));
                        }
                    }
                }
            } catch (Exception e) {
                if (!closed) {
                    failure = e;
                }
            } finally {
                if (!closed) {
                    closeBridge(this, failure == null
                            ? "远端会话结束" : "TCP 读取异常: " + WebSocketSessions.describeCause(failure));
                }
            }
        }

        /**
         * noVNC 来的字节写入 TCP
         *
         * @param payload WS 载荷
         */
        private void toTcp(ByteBuffer payload) {
            try {
                OutputStream output = tcp.getOutputStream();
                if (payload.hasArray()) {
                    output.write(payload.array(),
                            payload.arrayOffset() + payload.position(), payload.remaining());
                } else {
                    byte[] bytes = new byte[payload.remaining()];
                    payload.get(bytes);
                    output.write(bytes);
                }
                output.flush();
            } catch (IOException e) {
                closeBridge(this, "写入 VNC 失败: " + WebSocketSessions.describeCause(e));
            }
        }
    }
}

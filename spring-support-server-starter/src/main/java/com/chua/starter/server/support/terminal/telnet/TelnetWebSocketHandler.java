package com.chua.starter.server.support.terminal.telnet;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * Telnet WebSocket 处理：把 WebSocket 二进制帧与远端 Telnet TCP 连接互相透传。
 * <p>
 * 远端 {@code MsServerHost} 作为目标（DIRECT 模式下直连 socket），帧方向：
 * <pre>
 *    WS 桥接器收到帧 -> 写远端 TCP 套接字
 *    TCP 套接字回馈   -> 包装为 WS 二进制帧推送给会话
 * </pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Component
public class TelnetWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelnetWebSocketHandler.class);

    /** 协议代号 */
    private static final String PROTOCOL = "TELNET";

    /** 文本帧缓冲上限（字） */
    private static final int MAX_TEXT_BUFFER = 1024;

    /** 处理为 TCP IO 字节的块 */
    private static final int BUFFER_SIZE = 8192;

    /** 建连超时兜底（毫秒，配置缺失时使用） */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 8000;

    private final ServerHostService serverHostService;
    private final ServerProperties serverProperties;
    private final TerminalSessionService terminalSessionService;

    /** sessionId -> 对应桥接体 */
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    public TelnetWebSocketHandler(ServerHostService serverHostService,
            ServerProperties serverProperties, TerminalSessionService terminalSessionService) {
        this.serverHostService = serverHostService;
        this.serverProperties = serverProperties;
        this.terminalSessionService = terminalSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        // [P3C A20 豁免]
        wsSession.setBinaryMessageSizeLimit(8 * 1024 * 1024);

        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        // 远控闸门：主机被禁用、或远控入口已停用时，任何远程终端都不再放行
        ReturnResult<Void> access = serverHostService.checkRemoteAccess(hostId);
        if (!access.isOk()) {
            reject(wsSession, access.getMsg());
            return;
        }
        String operator = params.get("user");

        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();

        // 显式 targetPort 优先，其次台账远控端口，否则使用配置的 Telnet 默认端口
        Integer requestedPort = WebSocketSessions.parseInteger(params.get("targetPort"));
        ServerProperties.Telnet telnet = serverProperties.getTelnet();
        int configuredPort = requestedPort != null && requestedPort > 0
                ? requestedPort
                : (host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0
                        ? host.getServerHostRemotePort()
                        : (telnet != null && telnet.getPort() > 0
                                ? telnet.getPort()
                                : ServerConstants.DEFAULT_TELNET_PORT));
        int connectTimeout = telnet != null && telnet.getTimeout() > 0 ? telnet.getTimeout() : DEFAULT_CONNECT_TIMEOUT_MS;
        String hostAddress = host.getServerHostAddress();
        if (hostAddress == null || hostAddress.isBlank()) {
            reject(wsSession, "主机未配置地址");
            return;
        }

        Socket socket = null;
        try {
            socket = new Socket();
            // 不设 SO_TIMEOUT：交互式终端可能长时间没有输出，读超时会把空闲会话误杀；
            // 收口统一由 WS 关闭、强制关闭与空闲扫描触发（关闭套接字会唤醒阻塞中的读）。
            socket.connect(new InetSocketAddress(hostAddress, configuredPort), connectTimeout);
            log.info("[telnet] TCP 连接建立 sessionId={} hostId={} {}:{} real={}:{} sessionOpen={}"
                    , wsSession.getId(), hostId, hostAddress, configuredPort,
                    socket.getLocalSocketAddress(), socket.isConnected());
        } catch (Exception e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[telnet] TCP 连接失败 hostId={} {}:{} {}"
                    , hostId, hostAddress, configuredPort, cause, e);
            reject(wsSession, "Telnet 连接失败 " + hostAddress + ":" + configuredPort + " - " + cause);
            return;
        }

        Bridge bridge = new Bridge(wsSession, hostId, hostAddress, socket);
        String userName = WebSocketSessions.firstNonBlank(operator, host.getServerHostName());
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, userName, WebSocketSessions.clientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        bridge.start();
        log.info("[telnet] 桥接建立 sessionId={} hostId={} {}:{} record={}",
                wsSession.getId(), hostId, hostAddress, configuredPort, bridge.recordId);
    }

    /**
     * 二进制帧：Telnet 输入原样写入远端
     *
     * @param wsSession WS 会话
     * @param message   输入字节
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null) {
            log.debug("[telnet] 未找到桥接体 session={} payloadLen={}", wsSession.getId(), message.getPayloadLength());
            return;
        }
        log.debug("[telnet] 收到帧 session={} len={}", wsSession.getId(), message.getPayloadLength());
        bridge.toTelnet(message.getPayload());
    }

    /**
     * 文本帧：转换为字节后写入远端
     *
     * @param wsSession WS 会话
     * @param message   输入文本
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null) {
            return;
        }
        byte[] bytes = message.getPayload().toString().getBytes(StandardCharsets.UTF_8);
        bridge.toTelnet(ByteBuffer.wrap(bytes));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        log.info("[telnet] WS 连接关闭 sessionId={} {}", wsSession.getId(), status);
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            bridge.closeQuietly("WS 关闭连接");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.error("[telnet] WS 传输异常 sessionId={}: {}", wsSession.getId(), WebSocketSessions.describeCause(exception), exception);
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            bridge.closeQuietly("连接异常: " + WebSocketSessions.describeCause(exception));
        }
    }

    /**
     * 拒绝连接并返回推送
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        try {
            wsSession.sendMessage(new TextMessage("{\"code\":50001,\"msg\":\"" + reason + "\"}"));
        } catch (IOException e) {
            log.warn("[telnet] 发送拒绝信息失败 sessionId={}", wsSession.getId(), e);
        }
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            bridge.closeQuietly(reason);
        }
    }

    /**
     * 在线会话
     *
     * @return 在线会话摘要
     */
    public List<TerminalOnlineVO> onlineSessions() {
        List<TerminalOnlineVO> result = new ArrayList<>();
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
                bridge.closeQuietly(reason);
                return true;
            }
        }
        return false;
    }

    /**
     * 关闭空闲超时的会话
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话数
     */
    public int sweepIdleSessions(int idleMinutes) {
        List<Bridge> idle = new ArrayList<>();
        for (Bridge bridge : bridges.values()) {
            if (ChronoUnit.MINUTES.between(bridge.lastActiveTime, LocalDateTime.now()) > idleMinutes) {
                idle.add(bridge);
            }
        }
        for (Bridge bridge : idle) {
            bridge.closeQuietly("空闲超时关闭");
        }
        return idle.size();
    }

    /**
     * 单个 WS 连接对应的桥接体，处理 TCP 两侧数据泵
     */
    private final class Bridge {
        private final WebSocketSession wsSession;
        private final Long hostId;
        private final String address;
        private final Socket socket;
        private volatile boolean closed = false;
        private volatile Thread pump;
        /** 本连接的 Telnet 协商状态机（跨数据块保持） */
        private final TelnetNegotiation negotiation = new TelnetNegotiation();
        private LocalDateTime lastActiveTime = LocalDateTime.now();
        private Long recordId;

        /**
         * @param wsSession WS 会话
         * @param hostId    审计用主机标识
         * @param address   远端地址
         * @param socket    物理套接字
         */
        private Bridge(WebSocketSession wsSession, Long hostId, String address, Socket socket) {
            this.wsSession = wsSession;
            this.hostId = hostId;
            this.address = address;
            this.socket = socket;
        }

        /**
         * 刷新活跃时间
         */
        private void touch() {
            lastActiveTime = LocalDateTime.now();
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
         * 启动 TCP→WS 泵
         */
        private void start() {
        // [P3C C1 豁免]
        pump = new Thread(this::pumpTcp, "telnet-" + address);
            pump.setDaemon(true);
            pump.start();
            log.info("[telnet] 泵启动 sessionId={} thread={}", wsSession.getId(), pump.getName());
        }

        /**
         * 持续读 TCP 并向 WS 推送二进制帧；EOF 或异常即收口
         */
        private void pumpTcp() {
            Throwable failure = null;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = socket.getInputStream()) {
                int read;
                while (!closed && (read = input.read(buffer)) > 0) {
                    byte[] raw = java.util.Arrays.copyOf(buffer, read);
                    // 先剥掉 IAC 协商命令，只把可显示数据推给浏览器；
                    // 对端的选项请求按「一律拒绝」回写，否则 xterm 会把 0xFF 序列画成乱码
                    byte[] visible = negotiation.filterServerData(raw);
                    byte[] replies = negotiation.drainReplies();
                    if (replies.length > 0) {
                        socket.getOutputStream().write(replies);
                        socket.getOutputStream().flush();
                    }
                    if (visible.length > 0) {
                        log.debug("[telnet] TCP 收到 {} 字节 sessionId={} 显示 {} 字节",
                                read, wsSession.getId(), visible.length);
                        sendToBrowser(visible);
                    }
                }
                log.info("[telnet] TCP 读结束 sessionId={} closed={}", wsSession.getId(), closed);
            } catch (Exception e) {
                if (!closed) {
                    failure = e;
                    log.info("[telnet] TCP 异常 sessionId={} 原因={}", wsSession.getId(), WebSocketSessions.describeCause(e));
                } else {
                    // 通道已由 WS 侧收口时 socket 读阻塞被唤醒，属正常收尾，不打异常栈
                    log.info("[telnet] TCP 收尾 sessionId={} 通道已关闭 message={}", wsSession.getId(), e.getMessage());
                }
            } finally {
                if (!closed) {
                    closeQuietly(finalReason(failure));
                }
            }
        }

        /**
         * 远端输出回推到浏览器的二进制帧
         *
         * @param bytes 远端输出字节
         */
        private void sendToBrowser(byte[] bytes) {
            if (closed || !wsSession.isOpen()) {
                log.debug("[telnet] 回推失败 closed={} isOpen={} sessionId={}", closed, wsSession.isOpen(), wsSession.getId());
                return;
            }
            touch();
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new BinaryMessage(bytes));
                }
                log.debug("[telnet] 回推 {} 字节至 WS sessionId={}", bytes.length, wsSession.getId());
            } catch (IOException | IllegalStateException e) {
                log.debug("[telnet] 远端输出回推失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }

        /**
         * Telnet 输入输出：节点用户键入的字符原样写入远端
         *
         * @param payload WS 载荷
         */
        private void toTelnet(ByteBuffer payload) {
            if (closed) {
                log.debug("[telnet] toTelnet 通道已关闭 sessionId={}", wsSession.getId());
                return;
            }
            try {
                byte[] bytes = new byte[payload.remaining()];
                payload.get(bytes);
                if (bytes.length == 0) {
                    return;
                }
                // 回车统一成 NVT 的 CR LF，并把数据里的 0xFF 转义，避免被对端当成命令起始
                byte[] wire = TelnetNegotiation.escapeClientData(TelnetNegotiation.normalizeNewline(bytes));
                socket.getOutputStream().write(wire);
                socket.getOutputStream().flush();
                touch();
                log.debug("[telnet] 发送 {} 字节至远端 {} session={}", wire.length, address, wsSession.getId());
            } catch (IOException e) {
                closeQuietly("Telnet 发送失败: " + WebSocketSessions.describeCause(e));
            }
        }

        /**
         * 关闭桥接：幂等关闭套接字与注册表
         *
         * @param reason 原因
         */
        private void closeQuietly(String reason) {
            if (closed) {
                return;
            }
            closed = true;
            bridges.remove(wsSession.getId());
            log.info("[telnet] 桥接关闭 sessionId={} {} reason={}", wsSession.getId(), address, reason);
            if (recordId != null) {
                terminalSessionService.closeRecord(recordId, reason);
            }
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[telnet] 关闭远端连接失败 {}: {}", address, e.getMessage());
            }
            try {
                wsSession.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("[telnet] 关闭 WS 失败 sessionId={}", wsSession.getId());
            }
        }

        /**
         * 获取连接关闭原因的简洁描述
         */
        private String finalReason(Throwable failure) {
            return failure == null
                    ? "远端 TCP 读结束"
                    : "TCP 读取异常: " + WebSocketSessions.describeCause(failure);
        }
    }
}

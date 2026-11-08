// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.terminal.telnet;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.config.ServerProperties;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Telnet console 直连 webSocket 处理器
 * <p>
 * 把远端 Telnet 服务（默认 23 端口）直接暴露为 WebSocket：
 * 远端输出走二进制帧，键盘输入走二进制帧。核心套接字层与 VNC websockify
 * 一致，本处理器只做字节透明转发，不包含任何 Telnet 协议代码（IAC 协商
 * 交给目标主机的行协议处理）。
 * </p>
 * <p>
 * 帧约定：出/入向均为二进制帧（Telnet 字节流）；文本帧不转发。
 * </p>
 * 端点：{/ws/ms/server/terminal/telnet?hostId=&identityId=&targetPort=}
 *
 * @author CH
 * @since 4.0.0.46
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelnetWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 单帧文本上限（控制指令很短，留余量即可）
     */
    private static final int MAX_TEXT_BUFFER = 64 * 1024;

    /**
     * TCP 读缓冲：输出按块转发，块大小不影响协议
     */
    private static final int BUFFER_SIZE = 4 * 1024;

    /**
     * 审计与在线列表使用的协议名
     */
    private static final String PROTOCOL = "TELNET";

    /**
     * 主机服务（地址/端口解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 会话审计服务
     */
    private final TerminalSessionService terminalSessionService;

    /**
     * 模块配置：Telnet 默认端口与连接超时
     */
    private final ServerProperties serverProperties;

    /**
     * 活跃直连会话：WS 会话 标识 -> 桥接上下文
     */
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    /**
     * 连接建立：解析目标、打开 Telnet 连接并启动 TCP→WS 泵
     *
     * @param wsSession 浏览器 WS 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        wsSession.setBinaryMessageSizeLimit(8 * 1024 * 1024);
        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
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
                        ? host.getServerHostRemotePort() : telnet.getPort());
        String hostAddress = host.getServerHostAddress();
        if (hostAddress == null || hostAddress.isBlank()) {
            reject(wsSession, "主机未配置地址");
            return;
        }

        Socket socket = null;
        try {
            socket = new Socket();
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(120));
            socket.connect(new InetSocketAddress(hostAddress, configuredPort), telnet.getTimeout());
        } catch (Exception e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[telnet] TCP 连接失败 hostId={} {}:{} {}", hostId, hostAddress, configuredPort, cause, e);
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
        log.info("[telnet] 桥接建立 sessionId={} hostId={} {}:{}"
                        , wsSession.getId(), hostId, hostAddress, configuredPort);
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
            return;
        }
        bridge.touch();
        bridge.toTelnet(message.getPayload());
    }

    /**
     * 文本帧：telnet 通道不转发文本
     *
     * @param wsSession WS 会话
     * @param message   文本
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        log.debug("[telnet] 忽略文本帧 sessionId={} length={}",
                wsSession.getId(), message.getPayloadLength());
    }

    /**
     * 连接关闭：断开 Telnet 并闭环审计
     *
     * @param wsSession WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            log.info("[telnet] 浏览器关闭 sessionId={} {}", wsSession.getId(), status);
            bridge.closeQuietly("浏览器断开");
        }
    }

    /**
     * 传输异常：统一走关闭流程
     *
     * @param wsSession WS 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.warn("[telnet] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString());
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            bridge.closeQuietly("传输异常: " + WebSocketSessions.describeCause(exception));
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
            if (ChronoUnit.MINUTES.between(bridge.lastActiveTime, LocalDateTime.now()) >= idleMinutes) {
                idle.add(bridge);
            }
        }
        for (Bridge bridge : idle) {
            bridge.closeQuietly("空闲超时");
        }
        return idle.size();
    }

    /**
     * 拒绝连接：发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason     原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[telnet] 拒绝连接 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                    + WebSocketSessions.escapeJson(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (IOException e) {
            log.debug("[telnet] 关闭被拒连接失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
        }
    }

    /**
     * 静默关闭套接字（无 I/O 失败）
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
     * 单条直连会话的桥接上下文
     */
    private final class Bridge {

        /**
         * 浏览器 WS 会话
         */
        private final WebSocketSession wsSession;

        /**
         * 审计关联的主机标识
         */
        private final Long hostId;

        /**
         * 远端地址（描述用）
         */
        private final String address;

        /**
         * 物理套接字
         */
        private final Socket socket;

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
         * TCP→WS 读泵线程
         */
        private Thread pump;

        /**
         * 构造桥接
         *
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
            pump = new Thread(this::pumpTcp, "telnet-" + address);
            pump.setDaemon(true);
            pump.start();
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
                    byte[] copy = java.util.Arrays.copyOf(buffer, read);
                    sendToBrowser(copy);
                }
            } catch (Exception e) {
                if (!closed) {
                    failure = e;
                }
            } finally {
                if (!closed) {
                    closeQuietly(finalReason(failure));
                }
            }
        }

        /**
         * 远端输出回推 WebSocket 二进制帧
         *
         * @param bytes 远端输出字节
         */
        private void sendToBrowser(byte[] bytes) {
            if (closed || !wsSession.isOpen()) {
                return;
            }
            touch();
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new BinaryMessage(bytes));
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("[telnet] 远端输出回推失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }

        /**
         * Telnet 输入输出：终端用户键入的字符原样写入远端
         *
         * @param payload WS 载荷
         */
        private void toTelnet(ByteBuffer payload) {
            if (closed) {
                return;
            }
            try {
                byte[] bytes = new byte[payload.remaining()];
                payload.get(bytes);
                socket.getOutputStream().write(bytes);
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
            log.debug("[telnet] 关闭远端连接 {}: {}", address, reason);
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[telnet] 关闭远端连接失败 {}: {}", address, e.getMessage());
            }
            log.info("[telnet] 桥接关闭 sessionId={} {} reason={}"
                            , wsSession.getId(), address, reason);
        }

        /**
         * 获取连接关闭原因的简洁描述
         */
        private String finalReason(Throwable failure) {
            return failure == null
                    ? "远端 TCP 读结束" : "TCP 读取异常: " + WebSocketSessions.describeCause(failure);
        }
    }
}

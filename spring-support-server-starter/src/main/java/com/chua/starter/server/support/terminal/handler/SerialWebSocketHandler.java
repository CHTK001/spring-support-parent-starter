// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.terminal.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.config.SerialProperties;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.SerialConnectionFactory;
import com.chua.starter.server.support.terminal.SerialSession;
import com.chua.starter.server.support.terminal.SerialSessionRegistry;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 串口（Serial）远控 webSocket 处理器
 * <p>
 * 与 {@link SshDirectWebSocketHandler} 同理不经 guacd：
 * 由 jSerialComm 直接打开主板上的串口设备（/dev/ttyUSB0、COM3…），
 * 浏览器侧只依赖一个标准终端仿真组件（xterm），点对点收发。
 * </p>
 * <p>
 * 帧约定：文本帧仅承载 JSON 控制指令（与所连设备对齐的终端尺寸调整），
 * 键盘输入与远端输出一律走二进制帧（UTF-8），避免与 shell 中合法的
 * { } 字符冲突。
 * </p>
 * 端点：{@code /ws/ms/server/terminal/serial?hostId=&device=&cols=&rows=&user=}
 *
 * @author CH
 * @since 4.0.0.48
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerialWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 单帧文本上限（控制指令很短，留余量即可）
     */
    private static final int MAX_TEXT_BUFFER = 64 * 1024;

    /**
     * 初始终端尺寸兜底
     */
    private static final int DEFAULT_COLS = 120;
    private static final int DEFAULT_ROWS = 30;

    /**
     * 审计与在线列表使用的协议名
     */
    private static final String PROTOCOL = "SERIAL";

    /**
     * 主机服务（地址/端口/凭据解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 串口参数（波特率/数据位等）
     */
    private final SerialProperties properties;

    /**
     * 串口连接工厂（取主机标号里存的设备路径，打开串口）
     */
    private final SerialConnectionFactory connectionFactory;

    /**
     * 会话审计服务
     */
    private final TerminalSessionService terminalSessionService;

    /**
     * 端到点协议注册表，由 {@link SerialSessionRegistry} 集中持有会话上下文
     */
    private final SerialSessionRegistry sessionRegistry;

    /**
     * 连接建立：解析参数、串口打开通道并启动输出回推
     *
     * @param wsSession 浏览器 WS 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();
        // 本机主机不能直连串口（平台自身就是串口宿主），规范业务语义
        if (host.getServerHostCollectMode() != null
                && "LOCAL".equalsIgnoreCase(host.getServerHostCollectMode().trim())) {
            reject(wsSession, "本机不支持串口远控，请直连本机终端");
            return;
        }
        // 设备路径：显式 device 参数优先，否则主机台账地址字段
        String device = (params.get("device") != null && !params.get("device").isBlank())
                ? params.get("device").trim() : SerialConnectionFactory.resolveDevice(host);
        if (device == null || device.isBlank()) {
            reject(wsSession, "未配置串口设备，请设置主机台账的「主机地址」字段");
            return;
        }
        int cols = WebSocketSessions.parsePositiveInt(params.get("cols"), DEFAULT_COLS);
        int rows = WebSocketSessions.parsePositiveInt(params.get("rows"), DEFAULT_ROWS);
        String operator = params.get("user");

        SerialPort port;
        try {
            port = connectionFactory.openDevice(device);
        } catch (Throwable e) {
            // jSerialComm 原生库缺失时为 UnsatisfiedLinkError（Error，非 Exception），必须一并兜住
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[serial] 串口连接失败 hostId={} device={}: {}", hostId, device, cause, e);
            reject(wsSession, "串口连接失败 " + device + " - " + cause);
            return;
        }
        SerialSession session = new SerialSession(WebSocketSessions.uuid(), wsSession, port, device,
                hostId, null, PROTOCOL);
        String userName = WebSocketSessions.firstNonBlank(operator, host.getServerHostName());
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, userName, WebSocketSessions.clientIp(wsSession));
        session.setSessionId(record == null ? null : record.getId());
        session.setCols(cols);
        session.setRows(rows);
        // [P3C C1 豁免] I/O 阻塞循环（串口/终端），不适合交给通用执行器
        Thread readerThread = new Thread(() -> pumpSerialToBrowser(session), "serial-reader-" + session.getTunnelId());
        readerThread.setDaemon(true);
        session.setReaderThread(readerThread);
        session.setLastActiveTime(LocalDateTime.now());
        sessionRegistry.register(session);
        readerThread.start();
        log.info("[serial] 会话建立 sessionId={} hostId={} device={} {}x{}",
                wsSession.getId(), hostId, device, cols, rows);
    }

    /**
     * 文本帧：仅接受 JSON 控制指令
     *
     * @param wsSession WS 会话
     * @param message   控制指令
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        SerialSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session == null) {
            log.warn("[serial] 收到未注册会话的指令 sessionId={}", wsSession.getId());
            return;
        }
        String payload = message.getPayload();
        if (payload == null || !payload.startsWith("{")) {
            return;
        }
        session.touch();
        if (payload.contains("\"resize\"")) {
            int nextCols = readInt(payload, "cols", session.getCols());
            int nextRows = readInt(payload, "rows", session.getRows());
            if (nextCols > 0 && nextRows > 0) {
                session.setCols(nextCols);
                session.setRows(nextRows);
                // 串口类型设备的终端尺寸通常由应用层负责，这里只维持会话信息
            }
        }
    }

    /**
     * 二进制帧：浏览器键盘输入原样写入串口
     *
     * @param wsSession WS 会话
     * @param message   输入字节
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        SerialSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session == null) {
            return;
        }
        session.touch();
        byte[] bytes = new byte[message.getPayload().remaining()];
        message.getPayload().get(bytes);
        try {
            session.write(bytes);
        } catch (IOException e) {
            log.warn("[serial] 输入写入失败 sessionId={}: {}", wsSession.getId(), e.getMessage(), e);
            closeBridge(session, "输入写入失败");
        }
    }

    /**
     * 连接关闭：断开串口并闭环审计
     *
     * @param wsSession WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        SerialSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session != null) {
            log.info("[serial] 浏览器关闭 sessionId={} {}", wsSession.getId(), status);
            closeBridge(session, "浏览器断开");
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
        log.warn("[serial] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString(), exception);
        SerialSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session != null) {
            closeBridge(session, "传输异常");
        }
    }

    /**
     * 本通道在线会话摘要（供管理端在线列表调用）
     *
     * @return 在线会话列表
     */
    public List<TerminalOnlineVO> onlineSessions() {
        return SerialWebSocketHandler.toOnlineViews(sessionRegistry.snapshot());
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
        return sessionRegistry.closeByRecordId(recordId, reason);
    }

    /**
     * 关闭空闲超时的会话
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话数
     */
    public int sweepIdleSessions(int idleMinutes) {
        return sessionRegistry.sweepIdle(idleMinutes);
    }

    /**
     * 关闭串接：由注册表统一负责关串口 / 关 WS / 清注册表 / 闭合审计记录
     *
     * @param session 串口会话
     * @param reason 原因
     */
    private void closeBridge(SerialSession session, String reason) {
        if (session == null) {
            return;
        }
        // 注册表承担全部关闭与日志，避免重复
        sessionRegistry.close(session, reason);
    }

    /**
     * 将当前活跃会话列表拍平成在线视图
     *
     * @param sessions 会话快照
     * @return 在线视图列表
     */
    private static List<TerminalOnlineVO> toOnlineViews(List<SerialSession> sessions) {
        return sessions.stream().map(SerialSession::toOnlineView).toList();
    }

    /**
     * 读线程主循环：持续读串口并推送给浏览器
     * <p>
     * 注意：{@link SerialSession#markClosed()} 首次调用即返回 true，因此循环条件不能用它，
     * 只能判断读线程是否被中断；非阻塞读返回 0 表示暂时无数据（让出 CPU）；
     * 发生异常或端口关闭时由注册表统一执行关闭流程。
     * </p>
     *
     * @param session 会话
     */
    private void pumpSerialToBrowser(SerialSession session) {
        byte[] buffer = new byte[4096];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int len = session.getPort().readBytes(buffer, 0);
                if (len <= 0) {
                    Thread.sleep(1);
                    continue;
                }
                byte[] copy = Arrays.copyOf(buffer, len);
                session.sendToBrowser(copy);
            }
        } catch (Throwable e) {
            if (session.markClosed()) {
                log.info("[serial] 串口读通道结束 session={} {}: {}",
                        session.getTunnelId(), session.getDevice(), e.getMessage());
            }
        } finally {
            // 关闭() 内部幂等：先标记关闭，再关串口 / WS / 移除注册表
            closeBridge(session, "远端关闭或读取结束");
        }
    }

    /**
     * 拒绝连接并发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[serial] 拒绝直连 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                    + WebSocketSessions.escapeJson(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭动作
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[serial] 关闭被拒连接失败 sessionId={}", wsSession.getId());
        }
    }

    /**
     * 从控制指令 JSON 中取整数字段
     *
     * @param json 原始 JSON
     * @param key  字段名
     * @param def  缺省值
     * @return 字段值
     */
    private int readInt(String json, String key, int def) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) {
            return def;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return def;
        }
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return def;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

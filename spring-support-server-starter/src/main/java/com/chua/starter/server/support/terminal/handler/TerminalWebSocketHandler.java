package com.chua.starter.server.support.terminal.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.enums.ProtocolType;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.terminal.GuacdSession;
import com.chua.starter.server.support.terminal.GuacdSessionRegistry;
import com.chua.starter.server.support.terminal.GuacdTunnel;
import com.chua.starter.server.support.terminal.GuacdConnectionFactory;
import com.chua.starter.server.support.service.RemoteTokenService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

/**
   * 远程终端 web套接字 处理器
 * <p>
   * 桥接浏览器（guacamole-通用-js）与 guacd 守护进程：
 * <ul>
 *   <li>握手阶段：解析查询参数 -> 解析主机凭据 -> 与 guacd 握手 -> 写入审计记录</li>
 *   <li>数据阶段：浏览器指令写入 guacd；读线程将 guacd 输出推回浏览器</li>
 *   <li>关闭阶段：双向断开、审计闭环、注册表清理</li>
 * </ul>
 * 端点：{@code /ws/ms/server/terminal?hostId=&protocol=ssh|vnc|rdp&identityId=&targetPort=&user=}
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    /** 单帧文本上限（guacd 指令可能较大，如全屏图像帧） */
    private static final int MAX_TEXT_BUFFER = 8 * 1024 * 1024;

    /** 主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    /** 会话审计服务 */
    private final TerminalSessionService terminalSessionService;

    /** guacd 连接工厂 */
    private final GuacdConnectionFactory connectionFactory;

    /** 远控短令牌服务（令牌 模式反解连接参数） */
    private final RemoteTokenService remoteTokenService;

    /** 会话注册表 */
    private final GuacdSessionRegistry sessionRegistry;

    /** 服务属性（空闲超时等） */
    private final ServerProperties properties;

    /**
     * 连接建立：完成鉴权参数校验、guacd 握手并启动读线程
     *
     * @param wsSession 浏览器 WS 会话
     * @throws Exception 参数非法或 guacd 握手失败时抛出，框架将关闭连接
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        Map<String, String> params = parseQuery(wsSession);
        String token = params.get("token");
        Long hostId = parseLong(params.get("hostId"));
        String protocolName = params.getOrDefault("protocol", "ssh");
        Long identityId = parseLong(params.get("identityId"));
        Integer targetPort = parseInteger(params.get("targetPort"));
        String userName = params.getOrDefault("user", "anonymous");

 // ---- 令牌 模式：连接参数由短令牌反解（服务器信息不出后端，免查库） ----
        if (token != null && !token.isBlank()) {
            var specOpt = remoteTokenService.resolve(token);
            if (specOpt.isEmpty()) {
                reject(wsSession, "令牌无效或已过期");
                return;
            }
            var spec = specOpt.get();
            String tokenProtocolName = spec.protocol().name().toLowerCase();
            ProtocolType tokenProtocol;
            try {
                tokenProtocol = ProtocolType.valueOf(spec.protocol().name().toUpperCase());
            } catch (IllegalArgumentException e) {
                reject(wsSession, "令牌协议不支持: " + spec.protocol());
                return;
            }
            if (!tokenProtocol.isGuacdProtocol()) {
                reject(wsSession, "令牌协议不支持经 guacd 连接: " + spec.protocol());
                return;
            }
            String tunnelId = java.util.UUID.randomUUID().toString();
            GuacdTunnel tokenTunnel;
            try {
                tokenTunnel = connectionFactory.create(tokenProtocol, spec.host(),
                        spec.port(), new SshCredential(spec.username(), spec.password(), null), tunnelId);
            } catch (RuntimeException e) {
                log.warn("[terminal] guacd 握手失败(token) protocol={}: {}", tokenProtocolName, e.getMessage());
                reject(wsSession, "guacd 握手失败: " + e.getMessage());
                return;
            }
            // token 模式无 hostId 上下文，审计记录以 host 地址标记（hostId=空）
            MsTerminalSession tokenRecord = terminalSessionService.openRecord(
                    null, spec.host(), tokenProtocol.name(), userName, resolveClientIp(wsSession));
            var tokenSession = new GuacdSession(tunnelId, wsSession, tokenTunnel, null,
                    tokenRecord == null ? null : tokenRecord.getId(), tokenProtocolName, null);
            Thread tokenReader = new Thread(() -> pumpGuacdToBrowser(tokenSession), "guac-reader-" + tunnelId);
            tokenReader.setDaemon(true);
            tokenReader.start();
            log.info("[terminal] 会话建立(token) tunnelId={} host={} protocol={}", tunnelId, spec.host(), tokenProtocolName);
            return;
        }

        if (hostId == null) {
            /**
             * reject。
             * @param wsSession ws会话
             * @param 参数" 参数"
             */
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        ProtocolType protocolType;
        try {
            protocolType = ProtocolType.valueOf(protocolName.toUpperCase());
        } catch (IllegalArgumentException e) {
            /**
             * reject。
             * @param wsSession ws会话
             * @param protocolName 协议名称
             */
            reject(wsSession, "未知协议: " + protocolName);
            return;
        }
        if (!protocolType.isGuacdProtocol()) {
            /**
             * reject。
             * @param wsSession ws会话
             * @param protocolName 协议名称
             */
            reject(wsSession, "协议不支持经 guacd 连接: " + protocolName);
            return;
        }

        // 解析凭据（含解密）
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId);
        if (!resolved.isOk()) {
            /**
             * reject。
             * @param wsSession ws会话
             * @param resolved.getMsg( resolved.获取msg(
             */
            reject(wsSession, resolved.getMsg());
            return;
        }
        var hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk()) {
            /**
             * reject。
             * @param wsSession ws会话
             * @param hostResult.getMsg( 主机结果.getmsg(
             */
            reject(wsSession, hostResult.getMsg());
            return;
        }

        // 与 guacd 握手（阻塞操作，失败即拒绝连接）
        String tunnelId = java.util.UUID.randomUUID().toString();
        GuacdTunnel tunnel;
        try {
            tunnel = connectionFactory.create(protocolType,
                    hostResult.getData().getServerHostAddress(), targetPort, resolved.getData(), tunnelId);
        } catch (RuntimeException e) {
            log.warn("[terminal] guacd 握手失败 hostId={} protocol={}: {}", hostId, protocolName, e.getMessage());
            /**
             * reject。
             * @param wsSession ws会话
             * @param e.getMessage( e.获取消息(
             */
            reject(wsSession, "guacd 握手失败: " + e.getMessage());
            return;
        }

        // 写入审计记录
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, hostResult.getData().getServerHostName(),
                protocolType.name(), userName, resolveClientIp(wsSession));

        // 组装会话并启动 guacd -> 浏览器方向的读线程
        var session = new GuacdSession(tunnelId, wsSession, tunnel, hostId,
                record == null ? null : record.getId(), protocolType.name().toLowerCase(), null);
        Thread readerThread = new Thread(() -> pumpGuacdToBrowser(session), "guac-reader-" + tunnelId);
        readerThread.setDaemon(true);
        session.setReaderThread(readerThread); // 写回引用，供 关闭() 中断
        session.setLastActiveTime(LocalDateTime.now());
        sessionRegistry.register(session);
        readerThread.start();
        log.info("[terminal] 会话建立 tunnelId={} hostId={} protocol={} user={}",
                tunnelId, hostId, protocolName, userName);
    }

    /**
     * 接收浏览器指令并转发给 guacd
     *
     * @param wsSession WS 会话
     * @param message   文本消息（一条或多条 guacd 指令）
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        GuacdSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session == null) {
            log.warn("[terminal] 收到未注册会话的指令 sessionId={}", wsSession.getId());
            return;
        }
        String payload = message.getPayload();
 // 过滤 guacamole-通用-js 自定义隧道的心跳/内部控制指令，避免透传给 guacd 导致 解析 错误
        if (payload != null && payload.startsWith("internal.")) {
            log.debug("[terminal] 丢弃内部控制指令 tunnelId={} payload={}", session.getTunnelId(), payload);
            session.setLastActiveTime(LocalDateTime.now());
            return;
        }
        try {
            // 浏览器 -> guacd 方向：原样透传指令文本（底层接口按字符数组写入）
            session.getTunnel().write(payload);
            session.setLastActiveTime(LocalDateTime.now());
        } catch (GuacamoleException e) {
            log.warn("[terminal] 指令写入 guacd 失败 tunnelId={}: {}", session.getTunnelId(), e.getMessage());
            sessionRegistry.close(session, "指令写入失败");
            if (session.getSessionId() != null) {
                terminalSessionService.closeRecord(session.getSessionId(), "指令写入失败");
            }
        }
    }

    /**
     * 连接关闭：断开 guacd、闭环审计记录
     *
     * @param wsSession  WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        GuacdSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session != null) {
            sessionRegistry.close(session, "浏览器断开: " + status);
            if (session.getSessionId() != null) {
                terminalSessionService.closeRecord(session.getSessionId(), "浏览器断开");
            }
        }
    }

    /**
     * 处理传输错误：统一走关闭流程
     *
     * @param wsSession WS 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.warn("[terminal] WS 传输异常 sessionId={} {}",
                wsSession.getId(), exception.toString(), exception);
        GuacdSession session = sessionRegistry.findByWsSessionId(wsSession.getId());
        if (session != null) {
            sessionRegistry.close(session, "传输异常");
            if (session.getSessionId() != null) {
                terminalSessionService.closeRecord(session.getSessionId(), "传输异常");
            }
        }
    }

    /**
     * 读线程主循环：持续读取 guacd 输出并推送给浏览器
     * <p>
     * {@code GuacamoleReader.read()} 返回 null 表示 Socket 已关闭，
     * 此时主动触发整条会话的关闭流程。
     * </p>
     *
     * @param session 会话
     */
    private void pumpGuacdToBrowser(GuacdSession session) {
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                char[] buffer = session.getTunnel().read();
                if (buffer == null) {
                    break;
                }
                String instruction = new String(buffer);
                session.sendToBrowser(instruction);
            }
        } catch (IOException | GuacamoleException e) {
 // 不预调 mark关闭()——统一由 关闭() 负责标记+清理，
 // 避免异常分支跳过 套接字 关闭和注册表移除导致泄漏
            log.info("[terminal] guacd 读通道结束 tunnelId={}: {}", session.getTunnelId(), e.getMessage());
        }
 // 关闭() 内部幂等：先标记 关闭，再关 套接字 / WS / 移除注册表
        sessionRegistry.close(session, "guacd 关闭或读取结束");
        if (session.getSessionId() != null) {
            terminalSessionService.closeRecord(session.getSessionId(), "远端关闭");
        }
    }

    /**
     * 拒绝连接并发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[terminal] 拒绝终端连接 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("5.error;" + reason.length() + "."
                    + reason + ";"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭动作
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[terminal] 关闭被拒连接失败 sessionId={}", wsSession.getId());
        }
    }

    /**
     * 解析 WS 握手 URL 的查询参数
     *
     * @param wsSession WS 会话
     * @return 参数表
     */
    private Map<String, String> parseQuery(WebSocketSession wsSession) throws IOException {
        URI uri = wsSession.getUri();
        Map<String, String> params = new java.util.HashMap<>();
        if (uri == null || uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            params.put(java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8));
        }
        return params;
    }

    /**
     * 提取客户端 IP
     *
     * @param wsSession WS 会话
     * @return IP 字符串
     */
    private String resolveClientIp(WebSocketSession wsSession) {
        var remoteAddress = wsSession.getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 安全解析长整型参数
     *
     * @param value 原始字符串
     * @return 长整型或 空
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析整型参数
     *
     * @param value 原始字符串
     * @return 整型或 空
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

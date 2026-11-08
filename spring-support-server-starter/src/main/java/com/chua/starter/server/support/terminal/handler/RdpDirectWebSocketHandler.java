package com.chua.starter.server.support.terminal.handler;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import com.chua.nativerdp.support.RdpjBridge;
import com.sun.jna.LongByReference;
import com.sun.jna.Memory;
import com.sun.jna.NativeSize;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RDP 直连桌面 webSocket 处理器
 * <p>
 * 与 {@link SshDirectWebSocketHandler} / {@link com.chua.starter.server.support.terminal.telnet.TelnetWebSocketHandler}
 * 同构：不经过 guacd——由 {@link RdpjBridge}（FreeRDP 3.x C 扩展）在主机侧接出到目标
 * RDP 服务器的连接，远端桌面按 32 字节帧头 + BGRA 像素的帧定时推向浏览器，
 * 浏览器的键鼠事件回传为 JSON 文本帧，由 Pump 线程交给 FreeRDP。
 * </p>
 * <p>
 * 帧约定：二进制帧 = 帧头 32 字节（uint32_x8: width, height, stride, bpp, x, y, cx, cy）
 * + BGRA 像素（bpp 字节/像素、每行 stride 字节；自底向上扫描线，canvas 绘制时翻转）；
 * 文本帧 = JSON 控制指令（{"type":"mouse","x":..,"y":..,"button":..,"down":..} 等）。
 * </p>
 * 端点：{@code /ws/ms/server/terminal/rdp?hostId=&identityId=&width=&height=&user=}
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdpDirectWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 帧泵循环休眠（对应约 30fps 的桌面刷新）
     */
    private static final int FRAME_INTERVAL_MS = 33;

    /**
     * 单帧文本上限（控制指令很短，留余量即可）
     */
    private static final int MAX_TEXT_BUFFER = 64 * 1024;

    /**
     * 初始终端尺寸兜底
     */
    private static final int DEFAULT_WIDTH = 1024;
    private static final int DEFAULT_HEIGHT = 768;

    /**
     * 审计与在线列表使用的协议名
     */
    private static final String PROTOCOL = "RDP";

    /**
     * 主机服务（地址/端口/凭据解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 会话审计服务
     */
    private final TerminalSessionService terminalSessionService;

    /**
     * 活跃直连会话：WS 会话 标识 -> 桥接上下文
     */
    private final Map<String, RdpBridge> bridges = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        Long identityId = WebSocketSessions.parseLong(params.get("identityId"));
        int width = WebSocketSessions.parsePositiveInt(params.get("width"), DEFAULT_WIDTH);
        int height = WebSocketSessions.parsePositiveInt(params.get("height"), DEFAULT_HEIGHT);
        String operator = params.get("user");

        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();
        // 本机不支持 RDP 远控，仅 VNC 直连
        if (host.getServerHostCollectMode() != null
                && "LOCAL".equalsIgnoreCase(host.getServerHostCollectMode().trim())) {
            reject(wsSession, "本机不支持 RDP 远控，请使用 VNC");
            return;
        }
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId, PROTOCOL);
        if (!resolved.isOk()) {
            reject(wsSession, resolved.getMsg());
            return;
        }
        int port = host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0
                ? host.getServerHostRemotePort()
                : (host.getServerHostPort() == null ? ServerConstants.DEFAULT_RDP_PORT : host.getServerHostPort());
        String user = WebSocketSessions.firstNonBlank(operator, resolved.getData().username());

        Pointer handle;
        try {
            handle = RdpjBridge.instance().rdpj_create();
        } catch (Throwable e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-rdp] RDP 连接创建失败 hostId={} {}", hostId, cause, e);
            reject(wsSession, "RDP 连接创建失败 - " + cause);
            return;
        }
        if (handle == null || handle.equals(Pointer.NULL)) {
            reject(wsSession, "RDP 连接创建失败（RDP 实例未创建）");
            return;
        }

        RdpBridge bridge = new RdpBridge(handle, wsSession, hostId, width, height, user);
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, user, WebSocketSessions.clientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        try {
            RdpjBridge.instance().rdpj_connect(handle, host.getServerHostAddress(), port,
                    resolved.getData().username(), resolved.getData().password(), "zh-CN");
            log.info("[terminal-rdp] 会话建立 sessionId={} hostId={} {}:{}",
                    wsSession.getId(), hostId, host.getServerHostAddress(), port);
            // 连接手柄后启动帧泵（关闭通道），ramp 另一个线程外感受
            bridge.running = true;
            new Thread(() -> pumpFrames(bridge), "rdp-pump-" + wsSession.getId()).start();
        } catch (Throwable e) {
            bridges.remove(wsSession.getId());
            try {
                RdpjBridge.instance().rdpj_destroy(handle);
            } catch (Throwable ex) {
                log.debug("[terminal-rdp] 销毁 RDP 句柄异常 sessionId={}: {}", wsSession.getId(), ex.getMessage());
            }
            terminalSessionService.closeRecord(bridge.recordId, "RDP 连接失败");
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-rdp] 会话建立失败 hostId={} {}", hostId, cause, e);
            reject(wsSession, "RDP 连接失败 - " + cause);
        }
    }

    /**
     * 文本帧：JSON 控制指令（键鼠 / 尺寸调整）
     *
     * @param wsSession WS 会话
     * @param message   控制指令
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        RdpBridge bridge = bridges.get(wsSession.getId());
        if (bridge == null) {
            return;
        }
        bridge.touch();
        String payload = message.getPayload();
        if (payload == null || !payload.startsWith("{")) {
            return;
        }
        RdpjBridge.Rdpj rdpj = RdpjBridge.instance();
        if (rdpj == null) {
            return;
        }
        if (payload.contains("\"mouse\"")) {
            int x = readInt(payload, "x", 0);
            int y = readInt(payload, "y", 0);
            int button = readInt(payload, "button", 1);
            boolean down = readBoolean(payload, "down", false);
            try {
                int rc = rdpj.rdpj_mouse(bridge.handle, x, y, button, down);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 鼠标事件推送失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 鼠标事件推送失败 sessionId={} {}", wsSession.getId(), e.getMessage());
            }
        } else if (payload.contains("\"keyboard\"")) {
            int keysym = readInt(payload, "keysym", 0);
            boolean down = readBoolean(payload, "down", false);
            try {
                int rc = rdpj.rdpj_key(bridge.handle, (short) keysym, down ? 1 : 0);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 键盘事件推送失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 键盘事件推送失败 sessionId={} {}", wsSession.getId(), e.getMessage());
            }
        } else if (payload.contains("\"resize\"")) {
            int nextWidth = readInt(payload, "width", bridge.width);
            int nextHeight = readInt(payload, "height", bridge.height);
            if (nextWidth <= 0 || nextHeight <= 0) {
                return;
            }
            bridge.width = nextWidth;
            bridge.height = nextHeight;
            try {
                int rc = rdpj.rdpj_resize(bridge.handle, nextWidth, nextHeight);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 尺寸调整失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 尺寸调整失败 sessionId={} {}", wsSession.getId(), e.getMessage());
            }
        }
    }

    /**
     * 保留位：桌面输出由泵循环统一推向浏览器，输入事件走文本帧，二进制帧不消费。
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        // 保留位
    }

    /**
     * 连接关闭：断开 RDP 并闭环审计
     *
     * @param wsSession WS 会话
     * @param status    关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        RdpBridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            log.info("[terminal-rdp] 浏览器关闭 sessionId={} {}", wsSession.getId(), status);
            closeBridge(bridge, "浏览器断开");
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
        log.warn("[terminal-rdp] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString());
        RdpBridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            closeBridge(bridge, "传输异常");
        }
    }

    /**
     * 帧泵：按 FreeRDP 推帧数轮询桌面，转推二进制帧
     *
     * @param bridge 桥接上下文
     */
    private void pumpFrames(RdpBridge bridge) {
        Pointer handle = bridge.handle;
        RdpjBridge.Rdpj rdpj = RdpjBridge.instance();
        if (rdpj == null || handle == null) {
            return;
        }
        try {
            while (bridge.running) {
                Pointer lastFrame = rdpj.rdpj_last_frame(handle);
                if (lastFrame == null || lastFrame.equals(Pointer.NULL)) {
                    Thread.sleep(FRAME_INTERVAL_MS);
                    continue;
                }
                int width = lastFrame.getInt(0);
                int height = lastFrame.getInt(4);
                int stride = lastFrame.getInt(8);
                int bpp = lastFrame.getInt(12);
                int payload = stride * height;
                int size = 32 + payload;
                if (bridge.bufferLen != size || bridge.buffer == null) {
                    bridge.buffer = new Memory(size);
                    bridge.bufferLen = size;
                }
                LongByReference outLen = new LongByReference(0);
                int written = rdpj.rdpj_frame(handle, bridge.buffer, new NativeSize(size), outLen);
                if (written > 0) {
                    byte[] frame = new byte[written];
                    bridge.buffer.read(0, frame, 0, written);
                    sendFrameToBrowser(bridge, frame);
                } else {
                    Thread.sleep(FRAME_INTERVAL_MS);
                }
            }
        } catch (Throwable e) {
            log.warn("[terminal-rdp] 帧泵异常 sessionId={} {}", bridge.wsSession.getId(), e.getMessage());
        } finally {
            closeBridge(bridge, "帧泵结束");
        }
    }

    /**
     * 向浏览器远端帧（二进制帧，BGRA 像素）
     *
     * @param bridge 桥接上下文
     * @param frame  帧 byte[]
     */
    private void sendFrameToBrowser(RdpBridge bridge, byte[] frame) {
        if (bridge == null || bridge.closed || !bridge.wsSession.isOpen()) {
            return;
        }
        bridge.touch();
        try {
            synchronized (bridge.wsSession) {
                bridge.wsSession.sendMessage(new BinaryMessage(frame));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("[terminal-rdp] 帧输出失败 sessionId={}: {}", bridge.wsSession.getId(), e.getMessage());
        }
    }

    /**
     * 在线会话摘要（供管理端在线列表调用）
     *
     * @return 在线会话列表
     */
    public List<TerminalOnlineVO> onlineSessions() {
        List<TerminalOnlineVO> result = new ArrayList<>(bridges.size());
        for (RdpBridge bridge : bridges.values()) {
            result.add(bridge.toOnlineView());
        }
        return result;
    }

    /**
     * 按审计记录标识强制关闭会话
     *
     * @param recordId 审计记录 标识
     * @param reason   关闭原因
     * @return true 表示本通道持有该会话并已触发关闭
     */
    public boolean closeSession(Long recordId, String reason) {
        if (recordId == null) {
            return false;
        }
        for (RdpBridge bridge : bridges.values()) {
            if (recordId.equals(bridge.recordId)) {
                closeBridge(bridge, reason);
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
        List<RdpBridge> idle = new ArrayList<>();
        for (RdpBridge bridge : bridges.values()) {
            if (ChronoUnit.MINUTES.between(bridge.lastActiveTime, LocalDateTime.now()) >= idleMinutes) {
                idle.add(bridge);
            }
        }
        for (RdpBridge bridge : idle) {
            closeBridge(bridge, "空闲超时");
        }
        return idle.size();
    }

    /**
     * 关闭桥接：幂等清理 RDP 句柄、审计记录与注册表
     *
     * @param bridge 桥接上下文
     * @param reason 原因
     */
    private void closeBridge(RdpBridge bridge, String reason) {
        if (bridge.closed) {
            return;
        }
        bridge.closed = true;
        bridges.remove(bridge.wsSession.getId());
        bridge.running = false;
        RdpjBridge.Rdpj rdpj = RdpjBridge.instance();
        Pointer handle = bridge.handle;
        if (handle != null && !handle.equals(Pointer.NULL) && rdpj != null) {
            try {
                rdpj.rdpj_close(handle);
            } catch (Throwable e) {
                log.debug("[terminal-rdp] 关闭 RDP 连接异常 sessionId={}: {}",
                        bridge.wsSession.getId(), e.getMessage());
            }
        }
        try {
            Thread.sleep(200);  // 让泵线程自行静默收口
        } catch (InterruptedException ignored) {
        }
        if (handle != null && !handle.equals(Pointer.NULL) && rdpj != null) {
            try {
                rdpj.rdpj_destroy(handle);
            } catch (Throwable e) {
                log.debug("[terminal-rdp] 销毁 RDP 句柄异常 sessionId={}: {}",
                        bridge.wsSession.getId(), e.getMessage());
            }
        }
        terminalSessionService.closeRecord(bridge.recordId, reason);
        try {
            bridge.wsSession.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.debug("[terminal-rdp] 关闭 WS 失败 sessionId={}: {}", bridge.wsSession.getId(), e.getMessage());
        }
    }

    /**
     * 拒绝连接（返回错误并关闭 WS）
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[terminal-rdp] 拒绝直连 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                    + WebSocketSessions.escapeJson(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭动作
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[terminal-rdp] 关闭被拒连接失败 sessionId={}", wsSession.getId());
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
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return def;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return def;
        }
        int start = colon +  1;
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

    /**
     * 从控制指令 JSON 中取布尔字段
     *
     * @param json 原始 JSON
     * @param key  字段名
     * @param def  缺省值
     * @return 字段值
     */
    private boolean readBoolean(String json, String key, boolean def) {
        int idx = json.indexOf("\"" + key + "\"");
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
        if (json.startsWith("true", start)) {
            return true;
        }
        if (json.startsWith("false", start)) {
            return false;
        }
        return def;
    }

    /**
     * 单条直连 RDP 会话的桥接上下文
     */
    private static final class RdpBridge {

        /** RDP 连接句柄 */
        private final Pointer handle;

        /** 主机 标识 */
        private final Long hostId;

        /** 浏览器 WS 会话 */
        private final WebSocketSession wsSession;

        /** 目标宽度 */
        private volatile int width;

        /** 目标高度 */
        private volatile int height;

        /** 用户（审计记录用） */
        private final String userName;

        /** 释放标志（幂等） */
        private volatile boolean closed;

        /** 泵循环标志 */
        private volatile boolean running = false;

        /** 审计记录 标识，可空 */
        private volatile Long recordId;

        /** 最近活跃时间（管理端在线列表与空闲清理依据） */
        private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

        /** 帧缓存（避免按 stride*height 重复分配） */
        private volatile Memory buffer;

        /** 帧缓存大小 */
        private volatile int bufferLen;

        /** 构造桥接上下文 */
        private RdpBridge(Pointer handle, WebSocketSession wsSession, Long hostId, int width, int height, String userName) {
            this.handle = handle;
            this.wsSession = wsSession;
            this.hostId = hostId;
            this.width = width;
            this.height = height;
            this.userName = userName;
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
         * @return 在线会话视图
         */
        private TerminalOnlineVO toOnlineView() {
            return new TerminalOnlineVO(wsSession.getId(), hostId, recordId,
                    PROTOCOL, lastActiveTime);
        }

        @Override
        public String toString() {
            return "RdpBridge{hostId=" + hostId + ", now=" + LocalDateTime.now() + "}";
        }
    }
}

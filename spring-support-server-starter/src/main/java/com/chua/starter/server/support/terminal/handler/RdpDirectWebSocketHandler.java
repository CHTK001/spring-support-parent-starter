package com.chua.starter.server.support.terminal.handler;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import com.chua.starter.server.support.terminal.util.RdpjNative;
import com.chua.common.support.nativeapi.NativeServices;
import com.chua.common.support.nativeapi.RdpNativeSpi;

import com.chua.common.support.lang.code.ReturnResult;

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
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RDP 直连桌面 webSocket 处理器
 * <p>
 * 与 {@link SshDirectWebSocketHandler} / {@link com.chua.starter.server.support.terminal.telnet.TelnetWebSocketHandler}
 * 同构：不经过 guacd——由 {@link RdpNativeSpi}（FreeRDP 3.x C 扩展）在主机侧接出到目标
 * RDP 服务器的连接，远端桌面按 32 字节帧头 + BGRA 像素的帧定时推向浏览器，
 * 浏览器的键鼠事件回传为 JSON 文本帧，由 Pump 线程交给 FreeRDP。
 * </p>
 * <p>
 * 帧约定：二进制帧 = 帧头 32 字节（uint32_x8: width, height, stride, bpp, x, y, cx, cy）
 * + RGBA 像素（4 字节/像素、每行 stride 字节，自上而下扫描线，可直接喂 canvas ImageData）；
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
        // 原生桥是可选的部署件：没带 utils-support-native-rdp 时直接提示不支持，
        // 不要等到握手阶段才暴露成「连接失败」；带了就完全走原来的直连逻辑。
        if (!RdpjNative.exists()) {
            reject(wsSession, RdpjNative.UNSUPPORTED_MESSAGE);
            return;
        }
        // 远控闸门：主机被禁用、或远控入口已停用时，任何远程终端都不再放行
        ReturnResult<Void> access = serverHostService.checkRemoteAccess(hostId);
        if (!access.isOk()) {
            reject(wsSession, access.getMsg());
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
        // 与 SSH 直连同口径：不做「本机不支持 RDP」的一刀切，
        // 本机开了 RDP 服务（127.0.0.1 / 本机网卡）同样应能直连；能否连上交给真实握手结果说话。
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId, PROTOCOL);
        if (!resolved.isOk()) {
            reject(wsSession, resolved.getMsg());
            return;
        }
        int port = host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0
                ? host.getServerHostRemotePort()
                : (host.getServerHostPort() == null ? ServerConstants.DEFAULT_RDP_PORT : host.getServerHostPort());
        String user = WebSocketSessions.firstNonBlank(operator, resolved.getData().username());

        long handle;
        try {
            handle = Native.create();
        } catch (Throwable e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-rdp] RDP 连接创建失败 hostId={} {}", hostId, cause, e);
            reject(wsSession, "RDP 连接创建失败 - " + cause);
            return;
        }
        if (handle <= 0L) {
            reject(wsSession, "RDP 连接创建失败（RDP 实例未创建）");
            return;
        }
        // 把浏览器申请的分辨率写进 FreeRDP 设置：必须在 rdpj_connect 之前，
        // 否则原生层用 1024×768 的兜底尺寸建连，画面会被拉伸。
        try {
            Native.resize(handle, width, height);
        } catch (Throwable e) {
            log.debug("[terminal-rdp] 设置请求分辨率失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
        }

        RdpBridge bridge = new RdpBridge(handle, wsSession, hostId, width, height, user);
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, user, WebSocketSessions.clientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        try {
            Native.connect(handle, host.getServerHostAddress(), port,
                    resolved.getData().username(), resolved.getData().password(), "zh-CN");
            log.info("[terminal-rdp] 会话建立 sessionId={} hostId={} {}:{}",
                    wsSession.getId(), hostId, host.getServerHostAddress(), port);
            // 连接手柄后启动帧泵（关闭通道），ramp 另一个线程外感受
            bridge.running = true;
            // [P3C C1 豁免]
            new Thread(() -> pumpFrames(bridge), "rdp-pump-" + wsSession.getId()).start();
        } catch (Throwable e) {
            bridges.remove(wsSession.getId());
            try {
                Native.destroy(handle);
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
        if (!RdpjNative.exists()) {
            return;
        }
        if (payload.contains("\"mouse\"")) {
            int x = readInt(payload, "x", 0);
            int y = readInt(payload, "y", 0);
            int button = readInt(payload, "button", 1);
            boolean down = readBoolean(payload, "down", false);
            try {
                int rc = Native.mouse(bridge.handle, x, y, button, down ? 1 : 0);
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
                int rc = Native.key(bridge.handle, (short) keysym, down ? 1 : 0);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 键盘事件推送失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 键盘事件推送失败 sessionId={} {}", wsSession.getId(), e.getMessage());
            }
        } else if (payload.contains("\"wheel\"")) {
            int x = readInt(payload, "x", 0);
            int y = readInt(payload, "y", 0);
            int delta = readInt(payload, "delta", 0);
            try {
                int rc = Native.wheel(bridge.handle, x, y, delta);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 滚轮事件推送失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 滚轮事件推送失败 sessionId={} {}", wsSession.getId(), e.getMessage());
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
                int rc = Native.resize(bridge.handle, nextWidth, nextHeight);
                if (rc != 0) {
                    log.warn("[terminal-rdp] 尺寸调整失败 sessionId={} {}", wsSession.getId(), rc);
                }
            } catch (Throwable e) {
                log.warn("[terminal-rdp] 尺寸调整失败 sessionId={} {}", wsSession.getId(), e.getMessage());
            }
        } else if (payload.contains("\"ping\"")) {
            /* 应用层心跳：原样回包序号，浏览器据此算链路往返延迟（指标卡「延迟」） */
            int t = readInt(payload, "t", 0);
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new TextMessage("{\"c\":\"pong\",\"t\":" + t + "}"));
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("[terminal-rdp] 心跳回包失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
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
        long handle = bridge.handle;
        if (!RdpjNative.exists() || handle <= 0L) {
            return;
        }
        int[] header = new int[4];
        try {
            while (bridge.running) {
                int state = Native.state(handle);
                if (state < 0 || state == 2) {
                    // 连接失败/被远端断开时，浏览器一直等首帧会永远停在加载层，
                    // 这里把原生层的错误原因明确回推一次。
                    String reason = Native.lastError(handle);
                    sendErrorToBrowser(bridge, reason == null || reason.isEmpty() ? "RDP 连接已中断" : reason);
                    return;
                }
                if (!Native.frameHeader(handle, header)) {
                    Thread.sleep(FRAME_INTERVAL_MS);
                    continue;
                }
                int height = header[1];
                int stride = header[2];
                int payload = stride * height;
                int size = 32 + payload;
                if (bridge.bufferLen != size || bridge.buffer == null) {
                    bridge.buffer = new byte[size];
                    bridge.bufferLen = size;
                }
                int written = Native.frame(handle, bridge.buffer);
                if (written > 0) {
                    byte[] frame = new byte[written];
                    System.arraycopy(bridge.buffer, 0, frame, 0, written);
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
     * 向浏览器推送错误文本帧（前端按字符串帧显示错误层）
     *
     * @param bridge 桥接上下文
     * @param reason 错误原因
     */
    private void sendErrorToBrowser(RdpBridge bridge, String reason) {
        if (bridge == null || bridge.closed || !bridge.wsSession.isOpen()) {
            return;
        }
        try {
            synchronized (bridge.wsSession) {
                bridge.wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                        + WebSocketSessions.escapeJson(reason) + "\"}"));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("[terminal-rdp] 错误帧输出失败 sessionId={}: {}", bridge.wsSession.getId(), e.getMessage());
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
        long handle = bridge.handle;
        boolean nativeReady = RdpjNative.exists();
        if (handle > 0L && nativeReady) {
            try {
                Native.close(handle);
            } catch (Throwable e) {
                log.debug("[terminal-rdp] 关闭 RDP 连接异常 sessionId={}: {}",
                        bridge.wsSession.getId(), e.getMessage());
            }
        }
        try {
            // [P3C A20 豁免]
            Thread.sleep(200);  // 让泵线程自行静默收口
        } catch (InterruptedException ignored) {
        }
        if (handle > 0L && nativeReady) {
            try {
                Native.destroy(handle);
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
        if (end < json.length() && json.charAt(end) == '-') {
            end++;
        }
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
        private final long handle;

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
        private volatile byte[] buffer;

        /** 帧缓存大小 */
        private volatile int bufferLen;

        /** 构造桥接上下文 */
        private RdpBridge(long handle, WebSocketSession wsSession, Long hostId, int width, int height, String userName) {
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

    /**
     * 原生桥调用收口。
     * <p>
     * 只依赖 utils 侧 {@link RdpNativeSpi} 契约；native 制品缺失时
     * {@link NativeServices#get(Class)} 返回 {@code null}，业务侧只提示“不支持”，
     * 不会在类校验阶段触发 {@code NoClassDefFoundError}。
     * </p>
     * <p>
     * 本类的方法签名只用 {@code long} / {@code byte[]} / {@code String}，
     * 不向外暴露 native 模块或 JNA 的任何类型。
     * </p>
     */
    private static final class Native {

        private Native() {
        }

        /**
         * 取可用的 RDP 原生实现
         *
         * @return 原生实现
         */
        private static RdpNativeSpi spi() {
            RdpNativeSpi spi = NativeServices.get(RdpNativeSpi.class);
            if (spi == null) {
                throw new IllegalStateException(RdpjNative.UNSUPPORTED_MESSAGE);
            }
            return spi;
        }

        /**
         * 创建 RDP 实例
         *
         * @return 句柄
         */
        private static long create() {
            return spi().create();
        }

        /**
         * 请求桌面尺寸
         *
         * @param handle 句柄
         * @param width  宽
         * @param height 高
         * @return 0
         */
        private static int resize(long handle, int width, int height) {
            return spi().resize(handle, width, height);
        }

        /**
         * 发起连接
         *
         * @param handle   句柄
         * @param host     主机
         * @param port     端口
         * @param user     用户名
         * @param password 口令
         * @param locale   区域
         * @return 0 表示已启动
         */
        private static int connect(long handle, String host, int port, String user, String password, String locale) {
            return spi().connect(handle, host, port, user, password, locale);
        }

        /**
         * 连接状态
         *
         * @param handle 句柄
         * @return 0 连接中 1 已连接 2 已关闭 -1 失败
         */
        private static int state(long handle) {
            return spi().state(handle);
        }

        /**
         * 最近一次错误描述
         *
         * @param handle 句柄
         * @return 错误文本
         */
        private static String lastError(long handle) {
            return spi().lastError(handle);
        }

        /**
         * 读取当前帧头
         *
         * @param handle 句柄
         * @param header 输出数组
         * @return 有新帧返回 true
         */
        private static boolean frameHeader(long handle, int[] header) {
            return spi().frameHeader(handle, header);
        }

        /**
         * 取增量帧
         *
         * @param handle 句柄
         * @param buffer 输出缓冲
         * @return 写入字节数
         */
        private static int frame(long handle, byte[] buffer) {
            return spi().frame(handle, buffer);
        }

        /**
         * 投递鼠标事件
         *
         * @param handle 句柄
         * @param x      坐标 X
         * @param y      坐标 Y
         * @param button 按钮
         * @param down   按下标志
         * @return 0 表示已入队
         */
        private static int mouse(long handle, int x, int y, int button, int down) {
            return spi().mouse(handle, x, y, button, down);
        }

        /**
         * 投递键盘事件
         *
         * @param handle 句柄
         * @param code   RDP scancode
         * @param down   按下标志
         * @return 0 表示已入队
         */
        private static int key(long handle, short code, int down) {
            return spi().key(handle, code, down);
        }

        /**
         * 投递滚轮事件
         *
         * @param handle 句柄
         * @param x      坐标 X
         * @param y      坐标 Y
         * @param delta  滚动量
         * @return 0 表示已入队
         */
        private static int wheel(long handle, int x, int y, int delta) {
            return spi().wheel(handle, x, y, delta);
        }

        /**
         * 关闭连接
         *
         * @param handle 句柄
         */
        private static void close(long handle) {
            spi().close(handle);
        }

        /**
         * 释放句柄
         *
         * @param handle 句柄
         */
        private static void destroy(long handle) {
            spi().destroy(handle);
        }
    }
}

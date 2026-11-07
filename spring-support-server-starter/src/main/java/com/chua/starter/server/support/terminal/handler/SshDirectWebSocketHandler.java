// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.terminal.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.WebSocketSessions;
import com.chua.starter.server.support.util.SshClients;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 直连终端 webSocket 处理器
 * <p>
 * 与 {@link TerminalWebSocketHandler} 的区别是不经过 guacd：
 * 由 {@link SshClients} 直接以 SSH 协议连到目标主机的 PTY，
 * 浏览器侧只依赖一个标准终端仿真组件（xterm）。
 * </p>
 * <p>
 * 帧约定：文本帧仅承载 JSON 控制指令（尺寸调整），键盘输入与远端输出
 * 一律走二进制帧（UTF-8），避免与 shell 中合法的 {'"'"'{'} 字符冲突。
 * </p>
 * 端点：{@code /ws/ms/server/terminal/ssh?hostId=&identityId=&cols=&rows=&user=}
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshDirectWebSocketHandler extends AbstractWebSocketHandler {

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
    private static final String PROTOCOL = "SSH";

    /**
     * 主机服务（地址/端口/凭据解析）
    */
    private final ServerHostService serverHostService;

    /**
     * SSH 连接缓存管理器（复用已建立连接，避免每次远控重复握手）
     */
    private final ConnectionManager connectionManager;

    /**
     * 会话审计服务
    */
    private final TerminalSessionService terminalSessionService;

    /**
     * 终端共享记录存储器（按共享记录的可见性判定加入者是否有写权限）
     */
    private final com.chua.starter.server.support.share.TerminalShareStore shareStore;

    /**
     * 活跃直连会话：WS 会话 标识 -> 桥接上下文
    */
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    /**
     * 共享房间注册表：房间 标识 -> 共享房间上下文
     *
     * <p>一个房间持有一条 SSH 连接与一个远端 伪终端，多个 WS 会话共享同一条
     * 终端输出流；输入仅当前控制者（controller）可写，观察者只读。
     * 房间由首个进入者创建（owner），最后一人退出时销毁。</p>
     */
    private final Map<String, ShareRoom> rooms = new ConcurrentHashMap<>();

    /**
     * WS 会话归属：WS 会话 标识 -> 房间 标识（仅共享成员登记，直连会话不登记）
     */
    private final Map<String, String> memberRoom = new ConcurrentHashMap<>();

    /**
     * 连接建立：解析参数、SSH 握手并启动输出回推
     *
     * @param wsSession 浏览器 WS 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        Map<String, String> params = WebSocketSessions.parseQuery(wsSession);
        Long hostId = WebSocketSessions.parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        // 共享终端：带 roomId 时加入/创建共享房间（不新建 SSH 连接，复用房间 PTY）
        String roomId = params.get("roomId");
        if (roomId != null && !roomId.isBlank()) {
            joinSharedRoom(wsSession, params, roomId.trim(), hostId);
            return;
        }
        Long identityId = WebSocketSessions.parseLong(params.get("identityId"));
        int cols = WebSocketSessions.parsePositiveInt(params.get("cols"), DEFAULT_COLS);
        int rows = WebSocketSessions.parsePositiveInt(params.get("rows"), DEFAULT_ROWS);
        String operator = params.get("user");

        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();
        // 本机（采集方式 LOCAL）同样可以走 SSH：平台所在主机自己也可能开着 sshd。
        // 这里不做「本机不支持 SSH」的一刀切，能不能连上交给真实握手结果说话。
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId, PROTOCOL);
        if (!resolved.isOk()) {
            reject(wsSession, resolved.getMsg());
            return;
        }
        // SSH 端口就是台账里的主机端口（serverHostPort，缺省 22）。
        // 不能用 remotePort：那是远控协议（VNC 5900 / RDP 3389）的端口，
        // 一台机器同时开着 VNC 与 sshd 时会把 SSH 连到 VNC 上去。
        int port = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort()
                : ServerConstants.DEFAULT_SSH_PORT;

        SshClients.Handle handle;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
        } catch (Exception e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-ssh] SSH 连接失败 hostId={} {}:{} {}", hostId, host.getServerHostAddress(), port, cause, e);
            reject(wsSession, "SSH 连接失败 " + host.getServerHostAddress() + ":" + port + " - " + cause);
            return;
        }

        Bridge bridge = new Bridge(handle, wsSession, hostId);
        String userName = WebSocketSessions.firstNonBlank(operator, resolved.getData().username());
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, userName, WebSocketSessions.clientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        try {
            SshClient.TerminalOperation operation = handle.client().terminal()
                    .pty(true)
                    .width(cols)
                    .height(rows)
                    .onOutput(bridge::writeToBrowser)
                    .onClose(() -> closeBridge(bridge, "远端会话结束"))
                    .connect();
            bridge.operation = operation;
            log.info("[terminal-ssh] 会话建立 sessionId={} hostId={} {}:{} {}x{}",
                    wsSession.getId(), hostId, host.getServerHostAddress(), port, cols, rows);
        } catch (Exception e) {
            bridges.remove(wsSession.getId());
            closeHandle(handle, hostId);
            terminalSessionService.closeRecord(bridge.recordId, "PTY 打开失败");
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-ssh] PTY 打开失败 hostId={} {}", hostId, cause, e);
            reject(wsSession, "PTY 打开失败 - " + cause);
        }
    }

    /**
     * 文本帧：仅接受 JSON 控制指令
     *
     * @param wsSession WS 会话
     * @param message   控制指令
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        String roomId = memberRoom.get(wsSession.getId());
        if (bridge == null && roomId == null) {
            return;
        }
        String payload = message.getPayload();
        if (payload == null || !payload.startsWith("{")) {
            return;
        }
        // 共享成员：处理房间控制指令（尺寸 / 控制权）
        if (roomId != null) {
            handleSharedText(wsSession, roomId, payload);
            return;
        }
        bridge.touch();
        if (payload.contains("\"resize\"")) {
            int nextCols = readInt(payload, "cols", bridge.cols);
            int nextRows = readInt(payload, "rows", bridge.rows);
            if (nextCols <= 0 || nextRows <= 0) {
                return;
            }
            bridge.cols = nextCols;
            bridge.rows = nextRows;
            bridge.resize(nextCols, nextRows);
        }
    }

    /**
     * 二进制帧：浏览器键盘输入原样写入远端 伪终端
     *
     * @param wsSession WS 会话
     * @param message   输入字节
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        // 共享成员：只要共享记录授予写权限（公开 / 发起者 / 被授权用户）即可写入远端 伪终端。
        // 与文件协同同一判定口径 —— 被邀请用户是「协同操作」，不是只读观看。
        String roomId = memberRoom.get(wsSession.getId());
        if (roomId != null) {
            ShareRoom room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            ShareMember member = room.members.get(wsSession.getId());
            if (room.operation == null || member == null || !member.writable) {
                return;
            }
            ByteBuffer buffer = message.getPayload();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try {
                room.touch();
                // 协同输入：把这次写入方登记为控制者（PTY 只有一套窗口尺寸，尺寸跟随最后操作者）
                room.controllerId = wsSession.getId();
                room.operation.sendRaw(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("[terminal-ssh] 共享输入写入失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
            return;
        }
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null || bridge.operation == null) {
            return;
        }
        bridge.touch();
        ByteBuffer buffer = message.getPayload();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        try {
            bridge.operation.sendRaw(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[terminal-ssh] 输入写入失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            closeBridge(bridge, "输入写入失败");
        }
    }

    /**
     * 连接关闭：断开 SSH 并闭环审计
     *
     * @param wsSession  WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        // 共享成员断开：移出房间（空房销毁）
        String roomId = memberRoom.get(wsSession.getId());
        if (roomId != null) {
            leaveSharedRoom(wsSession, roomId, "浏览器断开");
            return;
        }
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            log.info("[terminal-ssh] 浏览器关闭 sessionId={} {}", wsSession.getId(), status);
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
        log.warn("[terminal-ssh] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString());
        String roomId = memberRoom.get(wsSession.getId());
        if (roomId != null) {
            leaveSharedRoom(wsSession, roomId, "传输异常");
            return;
        }
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            closeBridge(bridge, "传输异常");
        }
    }

    /**
     * 本通道在线会话摘要（供管理端在线列表调用）
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
     * @param recordId 审计记录 标识
     * @param reason   关闭原因
     * @return true 表示本通道持有该会话并已触发关闭
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
            closeBridge(bridge, "空闲超时");
        }
        return idle.size();
    }

    /**
     * 关闭桥接：幂等清理 SSH 句柄、审计记录与注册表
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
        if (bridge.operation != null) {
            try {
                bridge.operation.close();
            } catch (Exception e) {
                log.debug("[terminal-ssh] 关闭 PTY 异常: {}", e.getMessage());
            }
        }
        closeHandle(bridge.handle, bridge.hostId);
        terminalSessionService.closeRecord(bridge.recordId, reason);
        try {
            bridge.wsSession.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.debug("[terminal-ssh] 关闭 WS 失败 sessionId={}", bridge.wsSession.getId());
        }
        log.info("[terminal-ssh] 会话结束 sessionId={} reason={}", bridge.wsSession.getId(), reason);
    }

    /**
     * 归还借用连接（释放引用计数；连接实体仍由连接池缓存，供后续复用）
     *
     * @param handle SSH 句柄
     * @param hostId 主机 标识（归还键）
     */
    private void closeHandle(SshClients.Handle handle, Long hostId) {
        try {
            connectionManager.release(hostId);
        } catch (Exception e) {
            log.debug("[terminal-ssh] 归还 SSH 连接异常: {}", e.getMessage());
        }
    }

        /**
     * 加入共享房间：房间不存在则创建（建 SSH + PTY），存在则作为协同成员 attach。
     *
     * <p>创建者即房间 owner；后续进入者按共享记录判定权限（与文件协同同口径：
     * 公开全员可协同操作，私有仅发起者与被授权用户），无权限者直接拒绝，
     * 不会拿到一个「看着能动、实际写不进去」的假终端。主机必须与房间一致，防止串台。</p>
     *
     * @param wsSession WS 会话
     * @param params    握手参数
     * @param roomId    房间 标识
     * @param hostId    目标主机 标识
     */
    private void joinSharedRoom(WebSocketSession wsSession, Map<String, String> params, String roomId, Long hostId) {
        // 权限判定与文件协同同口径：公开全员可协同操作；私有仅发起者与被授权用户。
        // 无权限者一律拒绝 —— 与「私有文档未授权看不到」同一语义，不给假终端。
        // 必须在建房间之前判：先建 SSH 再拒绝会白占一条连接与一个远端 PTY。
        String userId = params.get("userId");
        com.chua.starter.server.support.share.TerminalShareStore.TerminalShare share =
                shareStore.byRoom(roomId);
        boolean writable = share != null
                && com.chua.starter.server.support.share.TerminalShareStore.canWrite(share, userId);
        if (!writable) {
            reject(wsSession, share == null
                    ? "该共享终端不存在或已取消"
                    : "无权加入该共享终端，请联系发起者授权");
            return;
        }
        ShareRoom room = rooms.get(roomId);
        if (room == null) {
            room = createShareRoom(wsSession, params, roomId, hostId);
            if (room == null) {
                return; // createShareRoom 内部已 reject
            }
        } else if (!Objects.equals(room.hostId, hostId)) {
            reject(wsSession, "该共享会话属于另一台主机，无法加入");
            return;
        }
        String user = params.get("user");
        String name = WebSocketSessions.firstNonBlank(user, "访客-" + wsSession.getId().substring(0, Math.min(6, wsSession.getId().length())));
        ShareMember member = new ShareMember(wsSession, name, writable);
        room.members.put(wsSession.getId(), member);
        memberRoom.put(wsSession.getId(), roomId);
        // 加入者先收到房间全量状态，再广播他人「有新人加入」
        sendText(wsSession, room.toRoomStateJson(member));
        broadcastText(room, room.toMemberJoinJson(member), wsSession.getId());
        log.info("[terminal-ssh] 共享成员加入 roomId={} sessionId={} name={} userId={} 人数={}",
                roomId, wsSession.getId(), name, userId, room.members.size());
    }

    /**
     * 创建共享房间：解析凭据、建立 SSH 连接与远端 伪终端。
     *
     * @param wsSession 创建者 WS 会话
     * @param params    握手参数
     * @param roomId    房间 标识
     * @param hostId    目标主机 标识
     * @return 房间上下文，失败时返回 空（已 reject）
     */
    private ShareRoom createShareRoom(WebSocketSession wsSession, Map<String, String> params, String roomId, Long hostId) {
        Long identityId = WebSocketSessions.parseLong(params.get("identityId"));
        int cols = WebSocketSessions.parsePositiveInt(params.get("cols"), DEFAULT_COLS);
        int rows = WebSocketSessions.parsePositiveInt(params.get("rows"), DEFAULT_ROWS);
        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return null;
        }
        MsServerHost host = hostResult.getData();
        // 同直连通道：本机也可能开着 sshd，不按采集方式拦截
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId, PROTOCOL);
        if (!resolved.isOk()) {
            reject(wsSession, resolved.getMsg());
            return null;
        }
        // SSH 端口就是台账里的主机端口（serverHostPort，缺省 22）。
        // 不能用 remotePort：那是远控协议（VNC 5900 / RDP 3389）的端口，
        // 一台机器同时开着 VNC 与 sshd 时会把 SSH 连到 VNC 上去。
        int port = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort()
                : ServerConstants.DEFAULT_SSH_PORT;

        SshClients.Handle handle;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
        } catch (Exception e) {
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-ssh] 共享 SSH 连接失败 hostId={} {}:{} {}", hostId, host.getServerHostAddress(), port, cause, e);
            reject(wsSession, "SSH 连接失败 " + host.getServerHostAddress() + ":" + port + " - " + cause);
            return null;
        }

        ShareRoom room = new ShareRoom(roomId, hostId, wsSession.getId(), handle);

        String userName = WebSocketSessions.firstNonBlank(params.get("user"), resolved.getData().username());
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), PROTOCOL, userName, WebSocketSessions.clientIp(wsSession));
        room.recordId = record == null ? null : record.getId();
        room.controllerId = wsSession.getId();
        try {
            SshClient.TerminalOperation operation = handle.client().terminal()
                    .pty(true)
                    .width(cols)
                    .height(rows)
                    .onOutput(room::broadcast)
                    .onClose(() -> destroyShareRoom(room, "远端会话结束"))
                    .connect();
            room.operation = operation;
            // 并发保护：两账号同时建同一 roomId 时，后来者放弃本次连接、复用先建成的房间
            ShareRoom pre = rooms.putIfAbsent(roomId, room);
            if (pre != null) {
                log.info("[terminal-ssh] 房间已存在，放弃重复连接 roomId={}", roomId);
                if (operation != null) {
                    try {
                        operation.close();
                    } catch (Exception ignored) {
                        // 忽略关闭异常
                    }
                }
                closeHandle(handle, hostId);
                terminalSessionService.closeRecord(room.recordId, "房间已由其他账号创建");
                return pre;
            }
            log.info("[terminal-ssh] 共享房间建立 roomId={} hostId={} {}:{} {}x{} owner={}",
                    roomId, hostId, host.getServerHostAddress(), port, cols, rows, wsSession.getId());
            return room;
        } catch (Exception e) {
            closeHandle(handle, hostId);
            terminalSessionService.closeRecord(room.recordId, "PTY 打开失败");
            String cause = WebSocketSessions.describeCause(e);
            log.warn("[terminal-ssh] 共享 PTY 打开失败 hostId={} {}", hostId, cause, e);
            reject(wsSession, "PTY 打开失败 - " + cause);
            return null;
        }
    }

    /**
     * 成员离开共享房间：从房间摘除；房间空/创建者离开时销毁整个房间。
     *
     * @param wsSession 离开的 WS 会话
     * @param roomId    房间 标识
     * @param reason    原因
     */
    private void leaveSharedRoom(WebSocketSession wsSession, String roomId, String reason) {
        ShareRoom room = rooms.get(roomId);
        if (room == null) {
            memberRoom.remove(wsSession.getId());
            return;
        }
        ShareMember member = room.members.remove(wsSession.getId());
        memberRoom.remove(wsSession.getId());
        if (member == null) {
            return;
        }
        // 创建者（owner）离开即解散房间，其余成员随之断开
        boolean isOwner = room.ownerSessionId.equals(wsSession.getId());
        if (isOwner) {
            destroyShareRoom(room, "创建者离开");
            return;
        }
        // 控制者离开：控制权交给剩余成员中最早加入者
        if (wsSession.getId().equals(room.controllerId)) {
            room.controllerId = room.members.isEmpty() ? null : room.members.keySet().iterator().next();
            broadcastText(room, "{\"c\":\"control\",\"controller\":"
                    + (room.controllerId == null ? "null" : "\"" + room.controllerId + "\"") + "}", null);
        }
        broadcastText(room, room.toMemberLeaveJson(wsSession.getId()), null);
        log.info("[terminal-ssh] 共享成员离开 roomId={} sessionId={} reason={} 剩余={}",
                roomId, wsSession.getId(), reason, room.members.size());
    }

    /**
     * 处理共享成员文本指令：尺寸调整 / 请求控制 / 授权控制 / 释放控制 / 主动离开
     *
     * @param wsSession WS 会话
     * @param roomId    房间 标识
     * @param payload   指令 JSON
     */
    private void handleSharedText(WebSocketSession wsSession, String roomId, String payload) {
        ShareRoom room = rooms.get(roomId);
        if (room == null || room.operation == null) {
            return;
        }
        room.touch();
        if (payload.contains("\"request-control\"") || payload.contains("\"request\"")) {
            // 观察者申请控制：仅提示当前控制者（不做自动授权，防抢）
            if (!wsSession.getId().equals(room.controllerId)) {
                String name = room.members.containsKey(wsSession.getId())
                        ? room.members.get(wsSession.getId()).name : wsSession.getId();
                broadcastText(room, "{\"c\":\"control-request\",\"id\":\"" + wsSession.getId()
                        + "\",\"name\":\"" + WebSocketSessions.escapeJson(name) + "\"}", null);
            }
            return;
        }
        if (payload.contains("\"grant\"")) {
            // 仅当前控制者可授权；to 为目标成员 id
            if (wsSession.getId().equals(room.controllerId)) {
                String to = extractString(payload, "to");
                if (to != null && room.members.containsKey(to)) {
                    room.controllerId = to;
                    broadcastText(room, "{\"c\":\"control\",\"controller\":\"" + to + "\"}", null);
                }
            }
            return;
        }
        if (payload.contains("\"release\"") || payload.contains("\"release-control\"")) {
            if (wsSession.getId().equals(room.controllerId)) {
                room.controllerId = null;
                broadcastText(room, "{\"c\":\"control\",\"controller\":null}", null);
            }
            return;
        }
        if (payload.contains("\"resize\"")) {
            // 尺寸调整：任一可协同操作的成员都能调（PTY 只有一套窗口尺寸，跟随最后操作者）
            ShareMember self = room.members.get(wsSession.getId());
            if (self != null && self.writable) {
                int nextCols = readInt(payload, "cols", room.cols);
                int nextRows = readInt(payload, "rows", room.rows);
                if (nextCols > 0 && nextRows > 0) {
                    room.cols = nextCols;
                    room.rows = nextRows;
                    room.controllerId = wsSession.getId();
                    room.resize(nextCols, nextRows);
                }
            }
            return;
        }
        if (payload.contains("\"leave\"") || payload.contains("\"bye\"")) {
            leaveSharedRoom(wsSession, roomId, "主动离开");
        }
    }

    /**
     * 销毁共享房间：幂等关闭 PTY、SSH 句柄、审计记录，并通知全部成员
     *
     * @param room   房间上下文
     * @param reason 原因
     */
    private void destroyShareRoom(ShareRoom room, String reason) {
        if (room.closed) {
            return;
        }
        room.closed = true;
        rooms.remove(room.roomId);
        if (room.operation != null) {
            try {
                room.operation.close();
            } catch (Exception e) {
                log.debug("[terminal-ssh] 关闭共享 PTY 异常: {}", e.getMessage());
            }
        }
        closeHandle(room.handle, room.hostId);
        terminalSessionService.closeRecord(room.recordId, reason);
        for (ShareMember member : room.members.values()) {
            memberRoom.remove(member.wsSession.getId());
            try {
                member.wsSession.sendMessage(new TextMessage("{\"c\":\"closed\",\"msg\":\""
                        + WebSocketSessions.escapeJson(reason) + "\"}"));
            } catch (IOException ignored) {
                // 发送失败不阻塞关闭
            }
            try {
                member.wsSession.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("[terminal-ssh] 关闭共享成员 WS 失败 sessionId={}", member.wsSession.getId());
            }
        }
        room.members.clear();
        log.info("[terminal-ssh] 共享房间销毁 roomId={} reason={}", room.roomId, reason);
    }

    /**
     * 向单个 WS 会话发送文本帧
     *
     * @param wsSession WS 会话
     * @param json      文本
     */
    private void sendText(WebSocketSession wsSession, String json) {
        try {
            wsSession.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("[terminal-ssh] 文本发送失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
        }
    }

    /**
     * 向房间全部成员广播文本帧（可选排除某个 WS）
     *
     * @param room   房间上下文
     * @param json   文本
     * @param except 排除的 WS 会话 标识，可空
     */
    private void broadcastText(ShareRoom room, String json, String except) {
        for (ShareMember member : room.members.values()) {
            if (except != null && except.equals(member.wsSession.getId())) {
                continue;
            }
            try {
                member.wsSession.sendMessage(new TextMessage(json));
            } catch (IOException | IllegalStateException e) {
                log.debug("[terminal-ssh] 房间广播失败 sessionId={}: {}", member.wsSession.getId(), e.getMessage());
            }
        }
    }

    /**
     * 从 JSON 中提取字符串字段（仅用于短控制指令，无需引入 JSON 解析器）
     *
     * @param json 原始 JSON
     * @param key  字段名
     * @return 字段值，不存在返回 空
     */
    private String extractString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        int end = start + 1;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\' && end + 1 < json.length()) {
                end++;
            }
            end++;
        }
        if (end >= json.length()) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    /**
     * 拒绝连接并发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[terminal-ssh] 拒绝直连 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\""
                    + WebSocketSessions.escapeJson(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭动作
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[terminal-ssh] 关闭被拒连接失败 sessionId={}", wsSession.getId());
        }
    }

    /**
     * 从控制指令 JSON 中取整数字段（仅尺寸两个字段，无需引入 JSON 解析器）
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

    /**
     * 单条直连会话的桥接上下文
     */
    private static final class Bridge {

        /**
         * SSH 句柄
        */
        private final SshClients.Handle handle;

        /**
         * 主机 标识
        */
        private final Long hostId;

        /**
         * 浏览器 WS 会话
        */
        private final WebSocketSession wsSession;

        /**
         * 远端 PTY 操作，可空（握手未完成时）
        */
        private volatile SshClient.TerminalOperation operation;

        /**
         * 审计记录 标识，可空
        */
        private volatile Long recordId;

        /**
         * 最近活跃时间（管理端在线列表与空闲清理依据）
        */
        private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

        /**
         * 当前列数
        */
        private volatile int cols = DEFAULT_COLS;

        /**
         * 当前行数
        */
        private volatile int rows = DEFAULT_ROWS;

        /**
         * 是否已关闭（幂等标记）
        */
        private volatile boolean closed;

        /**
         * 构造桥接上下文
         *
         * @param handle    SSH 句柄
         * @param wsSession 浏览器 WS 会话
         * @param hostId    主机 标识
         */
        private Bridge(SshClients.Handle handle, WebSocketSession wsSession, Long hostId) {
            this.handle = handle;
            this.wsSession = wsSession;
            this.hostId = hostId;
        }

        /**
         * 刷新活跃时间（浏览器有输入即视为活跃）
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

        /**
         * 远端输出回推浏览器（二进制帧，UTF-8 编码）
         *
         * @param data 远端输出
         */
        private void writeToBrowser(String data) {
            if (closed || !wsSession.isOpen()) {
                return;
            }
            touch();
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new BinaryMessage(data.getBytes(StandardCharsets.UTF_8)));
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("[terminal-ssh] 输出回推失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }

        /**
         * 调整远端 伪终端 尺寸
         *
         * @param nextCols 列数
         * @param nextRows 行数
         */
        private void resize(int nextCols, int nextRows) {
            SshClient.TerminalOperation current = operation;
            if (current == null || closed) {
                return;
            }
            try {
                org.apache.sshd.client.channel.ChannelShell channel = current.getChannel();
                if (channel != null && channel.isOpen()) {
                    channel.sendWindowChange(nextCols, nextRows);
                }
            } catch (Exception e) {
                log.debug("[terminal-ssh] 尺寸调整失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }
    }

    /**
     * 共享终端房间上下文：一条 SSH 连接 + 一个远端 伪终端，多个成员共享输出流。
     */
    private static final class ShareRoom {

        /**
         * 房间 标识（前端生成，跨账号加入凭它）
         */
        private final String roomId;

        /**
         * 目标主机 标识（成员加入时校验一致）
         */
        private final Long hostId;

        /**
         * SSH 句柄
         */
        private final SshClients.Handle handle;

        /**
         * 创建者（owner）WS 会话 标识；owner 离开即解散房间
         */
        private final String ownerSessionId;

        /**
         * 房间成员：WS 会话 标识 -> 成员
         */
        private final Map<String, ShareMember> members = new ConcurrentHashMap<>();

        /**
         * 当前控制者 WS 会话 标识（可写）；空 表示无人可写（仅观察）
         */
        private volatile String controllerId;

        /**
         * 远端 PTY 操作
         */
        private volatile SshClient.TerminalOperation operation;

        /**
         * 审计记录 标识，可空
         */
        private volatile Long recordId;

        /**
         * 当前列数（以控制者最后一次调整为基准）
         */
        private volatile int cols = DEFAULT_COLS;

        /**
         * 当前行数
         */
        private volatile int rows = DEFAULT_ROWS;

        /**
         * 最近活跃时间
         */
        private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

        /**
         * 是否已关闭（幂等标记）
         */
        private volatile boolean closed;

        /**
         * 构造房间
         *
         * @param roomId          房间 标识
         * @param hostId          目标主机 标识
         * @param ownerSessionId  创建者（owner）WS 会话 标识
         * @param handle          SSH 句柄
         */
        private ShareRoom(String roomId, Long hostId, String ownerSessionId, SshClients.Handle handle) {
            this.roomId = roomId;
            this.hostId = hostId;
            this.ownerSessionId = ownerSessionId;
            this.handle = handle;
        }

        /**
         * 刷新活跃时间
         */
        private void touch() {
            lastActiveTime = LocalDateTime.now();
        }

        /**
         * 远端输出广播给房间全体成员（二进制帧，UTF-8 编码）
         *
         * @param data 远端输出
         */
        private void broadcast(String data) {
            if (closed || members.isEmpty()) {
                return;
            }
            touch();
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            for (ShareMember member : members.values()) {
                if (!member.wsSession.isOpen()) {
                    continue;
                }
                try {
                    synchronized (member.wsSession) {
                        member.wsSession.sendMessage(new BinaryMessage(bytes));
                    }
                } catch (IOException | IllegalStateException e) {
                    log.debug("[terminal-ssh] 共享输出回推失败 sessionId={}: {}", member.wsSession.getId(), e.getMessage());
                }
            }
        }

        /**
         * 调整远端 伪终端 尺寸（与控制者联动）
         *
         * @param nextCols 列数
         * @param nextRows 行数
         */
        private void resize(int nextCols, int nextRows) {
            SshClient.TerminalOperation current = operation;
            if (current == null || closed) {
                return;
            }
            try {
                org.apache.sshd.client.channel.ChannelShell channel = current.getChannel();
                if (channel != null && channel.isOpen()) {
                    channel.sendWindowChange(nextCols, nextRows);
                }
            } catch (Exception e) {
                log.debug("[terminal-ssh] 共享尺寸调整失败: {}", e.getMessage());
            }
        }

        /**
         * 加入者视角的房间全量状态
         *
         * @param self 新加入成员
         * @return 状态 JSON
         */
        private String toRoomStateJson(ShareMember self) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"c\":\"room\",\"roomId\":\"").append(roomId)
                    .append("\",\"id\":\"").append(self.wsSession.getId())
                    .append("\",\"writable\":").append(self.writable)
                    .append(",\"controller\":")
                    .append(controllerId == null ? "null" : "\"" + controllerId + "\"")
                    .append(",\"members\":[");
            boolean first = true;
            for (ShareMember m : members.values()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"id\":\"").append(m.wsSession.getId())
                        .append("\",\"name\":\"").append(WebSocketSessions.escapeJson(m.name))
                        .append("\",\"owner\":").append(ownerSessionId.equals(m.wsSession.getId()))
                        .append(",\"writable\":").append(m.writable)
                        .append('}');
            }
            sb.append("]}");
            return sb.toString();
        }

        /**
         * 成员加入通知
         *
         * @param member 新成员
         * @return 通知 JSON
         */
        private String toMemberJoinJson(ShareMember member) {
            return "{\"c\":\"member-join\",\"id\":\"" + member.wsSession.getId()
                    + "\",\"name\":\"" + WebSocketSessions.escapeJson(member.name)
                    + "\",\"owner\":" + ownerSessionId.equals(member.wsSession.getId()) + "}";
        }

        /**
         * 成员离开通知
         *
         * @param memberId 离开的 WS 会话 标识
         * @return 通知 JSON
         */
        private String toMemberLeaveJson(String memberId) {
            return "{\"c\":\"member-leave\",\"id\":\"" + memberId + "\"}";
        }
    }

    /**
     * 共享房间成员载体：浏览器 WS 会话 + 显示名 + 写权限。
     */
    private static final class ShareMember {

        /**
         * 浏览器 WS 会话
         */
        private final WebSocketSession wsSession;

        /**
         * 显示名（来自握手 user 参数或兜底）
         */
        private final String name;

        /**
         * 是否可协同操作（写远端 伪终端）。
         *
         * <p>由共享记录判定：公开共享一律可写；私有共享仅发起者与被授权用户可写。
         * 与文件协同同一口径 —— 私有文档未授权者根本看不到，私有终端未授权者
         * 在 {@link #joinSharedRoom} 就被拒绝，进不来。此标记是第二道闸：
         * 即便准入判定漏了，写入也会被丢弃。</p>
         */
        private final boolean writable;

        /**
         * 构造成员
         *
         * @param wsSession WS 会话
         * @param name      显示名
         * @param writable  是否可协同操作
         */
        private ShareMember(WebSocketSession wsSession, String name, boolean writable) {
            this.wsSession = wsSession;
            this.name = name;
            this.writable = writable;
        }
    }
}

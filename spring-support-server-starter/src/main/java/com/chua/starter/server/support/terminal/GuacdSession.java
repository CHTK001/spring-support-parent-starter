package com.chua.starter.server.support.terminal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* 远程终端 guacd 会话
* <p>
* 聚合一次远控连接的三方对象：浏览器 webSocket、guacd Socket、审计记录。
* 生命周期：{@code TerminalWebSocketHandler} 在 WS 握手后创建并注册到
* {@link GuacdSessionRegistry}；读线程将 guacd 指令推送给浏览器，
* 浏览器指令由处理器写入 guacd。任一侧断开即整条会话关闭。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Getter
public class GuacdSession {

    /** 会话唯一标识（同时作为审计关联键的补充信息） */
    private final String tunnelId;

    /** 浏览器侧 webSocket 会话 */
    private final WebSocketSession webSocketSession;

    /** guacd 侧隧道（含握手后的配置） */
    private final GuacdTunnel tunnel;

    /** 主机 标识 */
    private final Long hostId;

    /** 审计会话记录 标识（ms_terminal_会话.标识） */
    private final Long sessionId;

    /** 协议名（小写：ssh/vnc/rdp） */
    private final String protocol;

    /**
    * 读线程（guacd → 浏览器方向泵），在 connectionEstablished 回调中创建后写回
    */
    @Setter
    private Thread readerThread;

    /** 关闭标记，防止重复关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 最近活跃时间（空闲清理依据） */
    @Setter
    private volatile LocalDateTime lastActiveTime = LocalDateTime.now(); // 最后一个活跃时间

    /**
    * 构造会话
    *
    * @param tunnelId          会话唯一标识
    * @param webSocketSession  浏览器 WS 会话
    * @param tunnel            guacd 隧道
    * @param hostId            主机 标识
    * @param sessionId         审计记录 标识
    * @param protocol          协议名
    * @param readerThread      读线程
    */
    public GuacdSession(String tunnelId, WebSocketSession webSocketSession, GuacdTunnel tunnel,
                        Long hostId, Long sessionId, String protocol, Thread readerThread) {
        this.tunnelId = tunnelId;
        this.webSocketSession = webSocketSession;
        this.tunnel = tunnel;
        this.hostId = hostId;
        this.sessionId = sessionId;
        this.protocol = protocol;
        this.readerThread = readerThread;
    }

    /**
    * 向浏览器发送一条 guacd 指令
    * <p>
    * WS 会话不允许并发发送，调用方必须持有 {@link #getSendLock()}。
    * </p>
    *
    * @param instruction guacd 指令文本
    * @throws IOException 发送失败时抛出
    */
    public void sendToBrowser(String instruction) throws IOException {
        synchronized (sendLock) {
            if (webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(instruction));
            }
        }
    }

    /** 发送锁：保证同一会话内指令串行下发 */
    private final Object sendLock = new Object();

    /**
    * 获取发送锁对象
    *
    * @return 锁
    */
    public Object getSendLock() {
        return sendLock;
    }

    /**
    * 标记会话已关闭（幂等）
    *
    * @return true 表示本次调用完成了状态翻转
    */
    public boolean markClosed() {
        return closed.compareAndSet(false, true);
    }
}

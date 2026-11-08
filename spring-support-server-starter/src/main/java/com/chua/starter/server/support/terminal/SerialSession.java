package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.fazecast.jSerialComm.SerialPort;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 串口终端会话
 * <p>
 * 聚合一次串口远控连接的三方对象：浏览器 webSocket、串口设备 {@link SerialPort}、审计记录。
 * 生命周期由会话注册表 {@link SerialSessionRegistry} 在 WS 握手后创建并注册；
 * 读线程将串口输出原样推送给浏览器，浏览器指令由 {@link
 * com.chua.starter.server.support.terminal.handler.SerialWebSocketHandler} 写入串口。
 * 任一侧断开即整条会话关闭。
 * </p>
 *
 * @author CH
 * @since 4.0.0.48
 */
@Getter
public class SerialSession {

    /**
     * 会话唯一标识（同时作为审计关联键的补充信息）
     */
    private final String tunnelId;

    /**
     * 浏览器侧 webSocket 会话
     */
    private final WebSocketSession webSocketSession;

    /**
     * 串口设备（由 {@link SerialConnectionFactory} 创建后写回，只用于关闭与写操作）
     */
    private final SerialPort port;

    /**
     * 串口设备路径（装置名，如 /dev/ttyUSB0、COM3）
     */
    private final String device;

    /**
     * 主机 标识
     */
    private final Long hostId;

    /**
     * 审计会话记录 标识（ms_terminal_会话.标识）
     */
    @Setter
    private Long sessionId;

    /**
     * 协议名（小写：serial / tty）
     */
    private final String protocol;

    /**
     * 当前终端尺寸（掉线重连/管理端控制长度参考）
     */
    @Setter
    private volatile int cols = 120;

    /**
     * 当前终端尺寸（掉线重连/管理端控制长度参考）
     */
    @Setter
    private volatile int rows = 30;

    /**
     * 阅读器线程（串口 → 浏览器方向泵），在连接建立时创建
     */
    @Setter
    private Thread readerThread;

    /**
     * 关闭标记，防止重复关闭
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 最近活跃时间（空闲清理依据）
     */
    @Setter
    private volatile LocalDateTime lastActiveTime = LocalDateTime.now();

    /**
     * 构造会话
     *
     * @param tunnelId          会会唯一标识
     * @param webSocketSession  浏览器 WS 会话
     * @param port              串口设备
     * @param device            设备路径
     * @param hostId            主机 标识
     * @param sessionId         审计记录 标识
     * @param protocol          协议名
     */
    public SerialSession(String tunnelId, WebSocketSession webSocketSession, SerialPort port, String device,
                         Long hostId, Long sessionId, String protocol) {
        this.tunnelId = tunnelId;
        this.webSocketSession = webSocketSession;
        this.port = port;
        this.device = device;
        this.hostId = hostId;
        this.sessionId = sessionId;
        this.protocol = protocol;
    }

    /**
     * 向浏览器发送一块字节数据（终端输出）
     * <p>
     * WS 会话不允许并发发送，调用方必须持有 {@link #getWriteLock()}。
     * </p>
     *
     * @param bytes 串口收到的原始字节
     * @throws IOException 发送失败时抛出
     */
    public void sendToBrowser(byte[] bytes) throws IOException {
        synchronized (getWriteLock()) {
            if (webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new BinaryMessage(bytes));
            }
        }
    }

    /**
     * 向浏览器发送一块文本（调试/非文本串口）
     * <p>
     * WS 会话不允许并发发送，调用方必须持有 {@link #getWriteLock()}。
     * </p>
     *
     * @param text 串口文本
     * @throws IOException 发送失败时抛出
     */
    public void sendToBrowser(String text) throws IOException {
        sendToBrowser(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 发送锁：保证同一会话内输出串行下发
     */
    private final Object writeLock = new Object();

    /**
     * 获取发送锁对象
     *
     * @return 锁
     */
    public Object getWriteLock() {
        return writeLock;
    }

    /**
     * 向串口写入原始字节
     * <p>
     * 调用方负责先取 {@link #getWriteLock()}；写入失败时抛出，由处理器捕获后关闭整条会话。
     * </p>
     *
     * @param bytes 要写入串口的字节
     * @throws IOException 写入失败时抛出
     */
    public void write(byte[] bytes) throws IOException {
        synchronized (getWriteLock()) {
            if (!port.isOpen()) {
                throw new IOException("串口未打开: " + device);
            }
            port.writeBytes(bytes, bytes.length);
            touch();
        }
    }

    /**
     * 向串口写入文本（转为 UTF-8）
     *
     * @param text 要写入串口的文本
     * @throws IOException 写入失败时抛出
     */
    public void write(String text) throws IOException {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 标记会话已关闭（幂等）
     *
     * @return true 表示本次调用完成了状态翻转
     */
    public boolean markClosed() {
        return closed.compareAndSet(false, true);
    }

    /**
     * 刷新活跃时间（浏览器有输入即视为活跃）
     */
    public void touch() {
        lastActiveTime = LocalDateTime.now();
    }

    /**
     * 生成在线会话摘要
     *
     * @return 在线会话视图
     */
    public TerminalOnlineVO toOnlineView() {
        return new TerminalOnlineVO(webSocketSession.getId(), hostId, sessionId,
                "SERIAL", lastActiveTime);
    }

    /**
     * 关闭会话：中断读线程、关闭串口（内部幂等）
     */
    public void close() {
        if (!markClosed()) {
            return;
        }
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        close(port);
    }

    /**
     * 安静关闭串口（脱敏 jSerialComm 异常）
     *
     * @param port 串口
     */
    private static void close(SerialPort port) {
        if (port == null || !port.isOpen()) {
            return;
        }
        try {
            port.closePort();
        } catch (Exception e) {
            // 关闭失败不再出异常，由调用方日志补齐
        }
    }

    /**
     * 随机生成的唯一标识
     *
     * @return UUID 字符串
     */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }
}

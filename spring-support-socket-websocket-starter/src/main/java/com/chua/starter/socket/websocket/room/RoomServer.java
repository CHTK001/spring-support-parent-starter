package com.chua.starter.socket.websocket.room;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.server.AbstractServer;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.websocket.WebSocketProtocol;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P2P 房间信令服务器（服务管理里的 ROOM 节点）。
 *
 * <p>把房间做成 {@link AbstractServer} 的一种，是为了让「服务管理」能像 HTTP / 代理 / SIP
 * 一样按节点落库、启停、查看状态，而不是挂在一个独立菜单或宿主同端口上：</p>
 * <ul>
 *   <li>协议：RFC 6455 WebSocket，握手/帧编解码复用 {@link WebSocketProtocol}；</li>
 *   <li>端口：节点自己的 host/port（默认 18083），不与宿主应用抢端口；</li>
 *   <li>信令：解析与分发复用 {@link RoomSignalProcessor}，与同端口实现行为一致；</li>
 *   <li>配置：{@link RoomProperties} 提供 maxPeers / STUN / TURN / 超时。</li>
 * </ul>
 *
 * <p>不使用 {@code @Spi}：房间节点由 {@code ServerNodeRegistry} 直接构造（与 SIP 同路），
 * 避免注册成通用协议后被其它宿主按名称误实例化。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class RoomServer extends AbstractServer {

    /** 握手请求头上限（防止异常连接无限占用内存） */
    private static final int MAX_HANDSHAKE_BYTES = 16 * 1024;

    /** 连接 ID 前缀 */
    private static final String CONNECTION_ID_PREFIX = "room-";

    /** 房间配置（maxPeers / STUN / TURN / 超时） */
    private final RoomProperties properties;

    /** 活跃连接：连接 ID -> 连接 */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    /** 连接 ID 序列 */
    private final AtomicInteger connectionSequence = new AtomicInteger();

    /** 房间信令引擎（每次启动新建，停止时销毁） */
    private volatile RoomService roomService;

    /** 信令分发器 */
    private volatile RoomSignalProcessor processor;

    /** 监听套接字 */
    private ServerSocket serverSocket;

    /** 连接处理线程池（虚拟线程，一连接一线程） */
    private ExecutorService executor;

    /**
     * 按通用服务器配置构造（房间配置全部取默认值）。
     *
     * @param setting 服务器配置
     */
    public RoomServer(ServerSetting setting) {
        this(setting, new RoomProperties());
    }

    /**
     * 构造器。
     *
     * @param setting    服务器配置（host / port 等）
     * @param properties 房间协议配置（maxPeers / STUN / TURN / 超时）
     */
    public RoomServer(ServerSetting setting, RoomProperties properties) {
        super(setting);
        this.properties = properties == null ? new RoomProperties() : properties;
    }

    @Override
    protected void doStart() {
        try {
            if (roomService == null) {
                roomService = new RoomService(properties, this::send);
                roomService.init();
                processor = new RoomSignalProcessor(roomService, this::send);
            }
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(setting.isSoReuseAddr());
            serverSocket.setReceiveBufferSize(Math.max(setting.getBufferSize(), MIN_BUFFER_SIZE));
            serverSocket.bind(new InetSocketAddress(setting.getHost(), setting.getPort()),
                    setting.resolveBacklog(DEFAULT_BACKLOG));
// [P3C C1 豁免] 场景化创建线程，需文档化
            executor = Executors.newVirtualThreadPerTaskExecutor();
            executor.submit(this::acceptLoop);
            log.info("Room WebSocketServer started on {}:{} (maxPeers={}, virtualThreads=true)",
                    setting.getHost(), setting.getPort(), properties.getMaxPeers());
        } catch (IOException e) {
            throw new RuntimeException("房间信令服务器启动失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doStopAccepting() {
        closeServerSocket();
    }

    @Override
    protected void doStop() {
        closeServerSocket();
        for (Connection connection : connections.values()) {
            closeConnection(connection);
        }
        connections.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        RoomService service = roomService;
        if (service != null) {
            service.shutdown();
            roomService = null;
            processor = null;
        }
        log.info("Room WebSocketServer stopped");
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.WS;
    }

    /**
     * 获取房间信令引擎（未启动时为 null）。
     *
     * @return 房间信令引擎
     */
    public RoomService getRoomService() {
        return roomService;
    }

    /**
     * 向指定连接下发文本消息（供 {@link RoomService} 作为 {@link RoomSender} 使用）。
     *
     * @param connectionId 连接 ID
     * @param message      消息内容
     */
    public void send(String connectionId, String message) {
        Connection connection = connections.get(connectionId);
        if (connection == null) {
            log.debug("[Room] 目标连接不存在: connectionId={}", connectionId);
            return;
        }
        try {
            connection.write(WebSocketProtocol.textFrame(message));
        } catch (IOException e) {
            log.debug("[Room] 下发信令失败，关闭连接: connectionId={}, err={}", connectionId, e.getMessage());
            closeConnection(connection);
        }
    }

    /* ---------- 连接接纳 ---------- */

    /**
     * 接纳循环：把每个连接交给一个虚拟线程处理。
     */
    private void acceptLoop() {
        ServerSocket socket = this.serverSocket;
        while (socket != null && !socket.isClosed() && accepting) {
            try {
                Socket client = socket.accept();
                if (!accepting) {
                    client.close();
                    continue;
                }
                if (!tryAcquireConnection()) {
                    client.close();
                    continue;
                }
                String connectionId = CONNECTION_ID_PREFIX + connectionSequence.incrementAndGet();
                try {
                    client.setTcpNoDelay(setting.isTcpNoDelay());
                    applySocketOptions(client);
                    Connection connection = new Connection(connectionId, client);
                    connections.put(connectionId, connection);
                    executor.submit(() -> handleConnection(connection));
                } catch (Exception e) {
                    client.close();
                    releaseConnection();
                }
            } catch (IOException e) {
                if (socket.isClosed()) {
                    break;
                }
                log.warn("Room WebSocket accept failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 处理一个连接：握手 → 读帧 → 收尾。
     *
     * @param connection 连接
     */
    private void handleConnection(Connection connection) {
        try {
            if (!performHandshake(connection)) {
                return;
            }
            readFrames(connection);
        } catch (IOException e) {
            log.debug("[Room] 连接关闭: connectionId={}, err={}", connection.id, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[Room] 连接处理异常: connectionId={}, err={}", connection.id, e.getMessage());
        } finally {
            closeConnection(connection);
            RoomService service = roomService;
            if (service != null) {
                service.onDisconnect(connection.id);
            }
        }
    }

    /**
     * 幂等关闭连接：关套接字、移出连接表、归还连接预算。
     *
     * @param connection 连接
     */
    private void closeConnection(Connection connection) {
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        connection.close();
        connections.remove(connection.id);
        releaseConnection();
    }

    /**
     * WebSocket 握手（RFC 6455）。
     *
     * <p>请求行可能带 {@code ?path=/ws&x-oauth-token=...}，这里不解析也不鉴权：
     * 节点是独立端口的内网信令面，鉴权由部署侧网关负责。</p>
     *
     * @param connection 连接
     * @return true 表示握手完成
     * @throws IOException 读写失败
     */
    private boolean performHandshake(Connection connection) throws IOException {
        InputStream in = connection.socket.getInputStream();
        ByteArrayOutputStream requestBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        String request = null;
        while (request == null) {
            int read = in.read(buffer);
            if (read <= 0) {
                return false;
            }
            requestBuffer.write(buffer, 0, read);
            String text = requestBuffer.toString(StandardCharsets.UTF_8);
            if (text.contains("\r\n\r\n")) {
                request = text;
            } else if (requestBuffer.size() > MAX_HANDSHAKE_BYTES) {
                return false;
            }
        }
        String key = null;
        for (String line : request.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
                break;
            }
        }
        OutputStream out = connection.socket.getOutputStream();
        if (key == null || key.isEmpty()) {
            out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return false;
        }
        out.write(WebSocketProtocol.handshakeResponse(WebSocketProtocol.computeAccept(key)));
        out.flush();
        connection.out = out;
        return true;
    }

    /**
     * 读取帧并把文本帧交给信令分发器。
     *
     * @param connection 连接
     * @throws IOException 读失败
     */
    private void readFrames(Connection connection) throws IOException {
        InputStream in = connection.socket.getInputStream();
        while (!connection.socket.isClosed() && !Thread.currentThread().isInterrupted()) {
            WebSocketProtocol.Frame frame = WebSocketProtocol.readFrame(in);
            if (frame == null) {
                return;
            }
            int opcode = frame.opcode();
            if (opcode == 0x8) {
                return;
            }
            if (opcode == 0x9 || opcode == 0xA) {
                continue;
            }
            if (opcode == 0x1 || opcode == 0x2) {
                RoomSignalProcessor dispatcher = processor;
                if (dispatcher != null) {
                    dispatcher.onMessage(connection.id, new String(frame.payload(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * 关闭监听套接字（幂等）。
     */
    private void closeServerSocket() {
        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[Room] 关闭监听失败，已忽略: {}", e.getMessage());
            }
        }
    }

    /**
     * 一条 WebSocket 连接。
     */
    private static final class Connection {

        /** 连接 ID（房间引擎的 connectionId） */
        private final String id;

        /** 客户端套接字 */
        private final Socket socket;

        /** 握手完成后可写的输出流 */
        private volatile OutputStream out;

        /** 关闭标志（幂等收尾） */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * 构造器。
         *
         * @param id     连接 ID
         * @param socket 套接字
         */
        private Connection(String id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }

        /**
         * 写出一帧（连接内串行，避免多线程交错写坏帧）。
         *
         * @param frame 完整帧字节
         * @throws IOException 写失败
         */
        private synchronized void write(byte[] frame) throws IOException {
            OutputStream target = this.out;
            if (target == null) {
                return;
            }
            target.write(frame);
            target.flush();
        }

        /**
         * 关闭套接字（幂等）。
         */
        private void close() {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                log.debug("[Room] 关闭连接失败，已忽略: {}", e.getMessage());
            }
        }
    }
}

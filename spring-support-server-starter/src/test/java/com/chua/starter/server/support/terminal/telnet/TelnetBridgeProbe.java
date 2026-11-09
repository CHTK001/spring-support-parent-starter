package com.chua.starter.server.support.terminal.telnet;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Telnet 桥接运行时自检（不依赖 Spring 容器）。
 * <p>
 * 用真实 ServerSocket 充当 Telnet 对端，验证：
 * <ol>
 *     <li>WS 建连后能连上远端 TCP；</li>
 *     <li>对端 IAC 协商被剥离，浏览器只收到可见数据；</li>
 *     <li>协商回写（WONT/DONT）真的回到了对端；</li>
 *     <li>浏览器输入按 NVT 规则回写并被回显；</li>
 *     <li>关闭 WS 会收口远端连接。</li>
 * </ol>
 * 运行：{@code java -cp <classpath> com.chua...telnet.TelnetBridgeProbe}
 *
 * @author CH
 */
public final class TelnetBridgeProbe {

    private static int failed = 0;

    private static int total = 0;

    private TelnetBridgeProbe() {
    }

    public static void main(String[] args) throws Exception {
        int port = startEchoServer();
        FakeSession session = new FakeSession("probe-1");

        MsServerHost host = new MsServerHost();
        host.setId(1L);
        host.setServerHostName("probe-telnet");
        host.setServerHostAddress("127.0.0.1");
        host.setServerHostRemotePort(port);

        ServerHostService hostService = (ServerHostService) Proxy.newProxyInstance(
                TelnetBridgeProbe.class.getClassLoader(),
                new Class<?>[]{ServerHostService.class},
                (proxy, method, methodArgs) -> {
                    if ("get".equals(method.getName())) {
                        return ReturnResult.ok(host);
                    }
                    return null;
                });

        MsTerminalSession record = new MsTerminalSession();
        record.setId(9L);
        TerminalSessionService sessionService = (TerminalSessionService) Proxy.newProxyInstance(
                TelnetBridgeProbe.class.getClassLoader(),
                new Class<?>[]{TerminalSessionService.class},
                (proxy, method, methodArgs) -> {
                    if ("openRecord".equals(method.getName())) {
                        return record;
                    }
                    return null;
                });

        ServerProperties properties = new ServerProperties();
        TelnetWebSocketHandler handler = new TelnetWebSocketHandler(hostService, properties, sessionService);

        handler.afterConnectionEstablished(session);
        check("建立桥接后 WS 处于打开状态", session.isOpen());

        String banner = session.awaitText(3000);
        check("对端 banner 到达浏览器", "READY\r\n".equals(banner), String.valueOf(banner));
        check("banner 中不含 IAC 协商字节", banner != null && banner.indexOf('\u00ff') < 0);

        byte[] negotiated = EchoServer.lastClientBytes(3000);
        check("协商回写到达对端（IAC WONT TTYPE）",
                negotiated.length >= 3
                        && (negotiated[0] & 0xFF) == 0xFF
                        && (negotiated[1] & 0xFF) == 0xFC
                        && (negotiated[2] & 0xFF) == 0x18,
                hex(negotiated));

        session.clearText();
        handler.handleBinaryMessage(session, new BinaryMessage("hello\r\n".getBytes("UTF-8")));
        String echo = session.awaitText(3000);
        check("输入被回显", echo != null && echo.contains("hello"), String.valueOf(echo));
        String wire = EchoServer.lastPayload();
        check("回车按 NVT 展开为 CR LF", wire != null && wire.contains("hello\r\n"), String.valueOf(wire));

        List<TerminalOnlineVO> online = handler.onlineSessions();
        check("在线会话可见", online.size() == 1, String.valueOf(online.size()));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        Thread.sleep(300);
        check("关闭后在线会话清空", handler.onlineSessions().isEmpty());
        check("对端连接已关闭", EchoServer.closedCount() > 0);

        System.out.println("\nTOTAL=" + total + " PASS=" + (total - failed) + " FAILED=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void check(String name, boolean ok) {
        check(name, ok, null);
    }

    private static void check(String name, boolean ok, String detail) {
        total++;
        if (!ok) {
            failed++;
        }
        System.out.println((ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " — " + detail));
    }

    private static String hex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 12); i++) {
            sb.append(String.format("%02X ", bytes[i] & 0xFF));
        }
        return sb.toString().trim();
    }

    private static int startEchoServer() throws IOException {
        return EchoServer.start();
    }

    /** 极简 Telnet 对端：先发 IAC DO TTYPE + banner，之后回显收到的数据 */
    static final class EchoServer implements Runnable {

        private static volatile EchoServer current;

        private final ServerSocket serverSocket;
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile byte[] firstClientBytes = new byte[0];
        private volatile String lastPayload;
        private volatile int closed;

        private EchoServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        static int start() throws IOException {
            ServerSocket socket = new ServerSocket();
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            EchoServer server = new EchoServer(socket);
            current = server;
            Thread thread = new Thread(server, "telnet-echo-probe");
            thread.setDaemon(true);
            thread.start();
            return socket.getLocalPort();
        }

        static byte[] lastClientBytes(long timeoutMs) throws InterruptedException {
            EchoServer server = current;
            return server == null ? new byte[0] : server.firstClientBytes;
        }

        static String lastPayload() {
            EchoServer server = current;
            return server == null ? null : server.lastPayload;
        }

        static int closedCount() {
            EchoServer server = current;
            return server == null ? 0 : server.closed;
        }

        @Override
        public void run() {
            try (Socket client = serverSocket.accept()) {
                ready.countDown();
                OutputStream out = client.getOutputStream();
                out.write(new byte[]{(byte) 0xFF, (byte) 0xFD, 0x18});
                out.write("READY\r\n".getBytes("UTF-8"));
                out.flush();

                InputStream in = client.getInputStream();
                byte[] buffer = new byte[4096];
                while (true) {
                    int read = in.read(buffer);
                    if (read <= 0) {
                        break;
                    }
                    byte[] chunk = java.util.Arrays.copyOf(buffer, read);
                    if (firstClientBytes.length == 0) {
                        firstClientBytes = chunk;
                    }
                    lastPayload = new String(chunk, "UTF-8");
                    out.write(chunk);
                    out.flush();
                }
            } catch (IOException ignored) {
                // 客户端断开属预期
            } finally {
                closed++;
            }
        }
    }

    /** 只实现桥接用到的那部分 WebSocketSession 能力 */
    static final class FakeSession implements WebSocketSession {

        private final String id;
        private final Map<String, Object> attributes = new HashMap<>();
        private final ByteArrayOutputStream text = new ByteArrayOutputStream();
        private volatile boolean open = true;
        private int textLimit = 8192;
        private int binaryLimit = 8192;

        FakeSession(String id) {
            this.id = id;
        }

        String awaitText(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                synchronized (text) {
                    if (text.size() > 0) {
                        String value = text.toString(StandardCharsets.UTF_8);
                        text.reset();
                        return value;
                    }
                }
                Thread.sleep(30L);
            }
            return null;
        }

        void clearText() {
            synchronized (text) {
                text.reset();
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://127.0.0.1/ws/ms/server/terminal/telnet?hostId=1");
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return new HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8082);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 5555);
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            this.textLimit = messageSizeLimit;
        }

        @Override
        public int getTextMessageSizeLimit() {
            return textLimit;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            this.binaryLimit = messageSizeLimit;
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return binaryLimit;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return Collections.emptyList();
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (!open) {
                throw new IOException("session closed");
            }
            if (message instanceof TextMessage) {
                byte[] payload = ((TextMessage) message).getPayload().getBytes("UTF-8");
                synchronized (text) {
                    text.write(payload, 0, payload.length);
                }
            } else if (message instanceof BinaryMessage) {
                ByteBuffer buffer = ((BinaryMessage) message).getPayload();
                byte[] payload = new byte[buffer.remaining()];
                buffer.get(payload);
                synchronized (text) {
                    text.write(payload, 0, payload.length);
                }
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }

        @Override
        public void close(CloseStatus status) throws IOException {
            open = false;
        }
    }
}

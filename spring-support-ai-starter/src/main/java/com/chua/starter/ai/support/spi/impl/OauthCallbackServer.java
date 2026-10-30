package com.chua.starter.ai.support.spi.impl;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 OAuth 回调监听器（进程内）。
 *
 * <p>Trae / Gemini 这类渠道的 {@code redirect_uri} 指向本机回环地址
 * （{@code http://127.0.0.1:18080/authorize}、{@code http://localhost:8845/oauth-callback}）。
 * 由百度的 Electron 客户端自己监听这些端口；服务端版本必须自己监听，
 * 否则用户授权后浏览器跳转到一个没人听的端口，只能靠手工粘贴回调 URL。</p>
 *
 * <p>职责边界：本类只负责「拿到回调 query 串」并交给 SPI 的 {@code pollLogin}，
 * 不解析业务字段、不换 token——解析与换票仍在各渠道实现里。</p>
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>只绑回环地址（{@code 127.0.0.1}），不对局域网暴露；</li>
 *   <li>端口按渠道复用：同一渠道重复发起绑定时复用既有监听，不重复占端口；</li>
 *   <li>首选端口被占用时回退到系统分配端口，并把实际端口写回给渠道实现
 *       （{@code redirect_uri} 必须与实际监听一致，否则回调打偏）；</li>
 *   <li>回给浏览器的页面是纯静态提示，不回显任何凭据。</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
final class OauthCallbackServer {

    /** 渠道 id → 该渠道的监听实例（端口按渠道固定，重复发起时复用）。 */
    private static final Map<String, Binding> BINDINGS = new ConcurrentHashMap<>(4);

    /** 回调页给浏览器的提示（不含任何凭据）。 */
    private static final String DEFAULT_DONE_HTML = "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<title>授权完成</title></head><body style=\"font-family:system-ui;text-align:center;padding-top:15vh\">"
            + "<h2>授权已完成</h2><p>可以关闭此页面，回到控制台继续操作。</p></body></html>";

    private OauthCallbackServer() {
    }

    /**
     * 确保某渠道在指定端口上处于监听状态。
     *
     * <p>已存在同一渠道的监听且端口一致时直接复用；端口不一致则先关旧的再重开。</p>
     *
     * @param channel     渠道标识（监听实例的归属者）
     * @param preferredPort 首选端口
     * @return 绑定结果（实际端口 + 回调路径），失败时 {@link Binding#port()} 为 -1
     */
    static Binding ensure(String channel, int preferredPort) {
        return ensure(channel, preferredPort, true);
    }

    /**
     * 确保某渠道在指定端口上处于监听状态。
     *
     * @param channel       渠道标识（监听实例的归属者）
     * @param preferredPort 首选端口
     * @param allowFallback 端口被占用时是否回退到系统分配端口。
     *                      <b>必须为 false 的场景</b>：上游要求 redirect_uri 与注册值
     *                      完全一致（如 Google 的 {@code localhost:8845/oauth-callback}），
     *                      换端口会被上游拒绝。
     * @return 绑定结果；失败时 {@link Binding#port()} 为 -1
     */
    static Binding ensure(String channel, int preferredPort, boolean allowFallback) {
        Binding existing = BINDINGS.get(channel);
        if (existing != null && existing.port() == preferredPort) {
            return existing;
        }
        if (existing != null) {
            existing.close();
            BINDINGS.remove(channel);
        }
        Binding created = allowFallback
                ? open(channel, preferredPort)
                : tryOpen(channel, preferredPort, preferredPort);
        if (created.port() > 0) {
            BINDINGS.put(channel, created);
        }
        return created;
    }

    /**
     * 取走某渠道最近一次收到的回调 query 串（取走即清空，避免重复消费）。
     *
     * @param channel 渠道标识
     * @return query 串（不含 {@code ?}），没有则 null
     */
    static String take(String channel) {
        Binding binding = BINDINGS.get(channel);
        return binding == null ? null : binding.take();
    }

    /**
     * 打开监听。首选端口占用时回退到系统分配端口。
     *
     * @param channel       渠道标识
     * @param preferredPort 首选端口
     * @return 绑定结果；失败时 port = -1
     */
    private static Binding open(String channel, int preferredPort) {
        Binding binding = tryOpen(channel, preferredPort, preferredPort);
        if (binding.port() > 0) {
            return binding;
        }
        log.info("[ChannelAuth] 回调端口 {} 被占用，回退到随机端口 channel={}", preferredPort, channel);
        binding = tryOpen(channel, 0, preferredPort);
        if (binding.port() <= 0) {
            log.warn("[ChannelAuth] 回调监听启动失败 channel={} preferredPort={}", channel, preferredPort);
        }
        return binding;
    }

    /**
     * 真正建立监听并起接收线程。
     *
     * @param channel       渠道标识
     * @param bindPort      绑定端口（0 = 系统分配）
     * @param preferredPort 首选端口（仅用于日志与回显）
     * @return 绑定结果
     */
    private static Binding tryOpen(String channel, int bindPort, int preferredPort) {
        ServerSocket server;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), bindPort), 16);
        } catch (IOException e) {
            log.debug("[ChannelAuth] 回调端口绑定失败 channel={} port={} cause={}", channel, bindPort, e.getMessage());
            return new Binding(channel, null, -1, preferredPort);
        }
        Binding binding = new Binding(channel, server, server.getLocalPort(), preferredPort);
        Thread acceptor = new Thread(() -> acceptLoop(channel, server, binding),
                "channel-callback-" + channel.toLowerCase(java.util.Locale.ROOT));
        // 守护线程：不阻止 JVM 退出（服务关闭时端口随进程释放）。
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("[ChannelAuth] 回调监听已启动 channel={} port={}", channel, binding.port());
        return binding;
    }

    /**
     * 接收循环：只处理一次有效回调，其它请求（favicon 等）回 404 继续等。
     *
     * @param channel 渠道标识
     * @param server  监听套接字
     * @param binding 归属绑定（用于写入收到的 query）
     */
    private static void acceptLoop(String channel, ServerSocket server, Binding binding) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5_000);
                String target = readRequestTarget(socket.getInputStream());
                if (target == null) {
                    continue;
                }
                int queryIndex = target.indexOf('?');
                if (queryIndex < 0) {
                    respond(socket.getOutputStream(), 404, "Not Found", "等待授权回调…");
                    continue;
                }
                String query = target.substring(queryIndex + 1);
                binding.store(query);
                log.info("[ChannelAuth] 收到浏览器回调 channel={} params={}", channel, summarizeKeys(query));
                respond(socket.getOutputStream(), 200, "OK", DEFAULT_DONE_HTML);
            } catch (IOException e) {
                // 单次连接异常不影响监听（浏览器预连接、客户端提前断开等）。
                log.debug("[ChannelAuth] 回调连接异常 channel={} cause={}", channel, e.getMessage());
            }
        }
    }

    /**
     * 从 HTTP 请求头里取请求目标（形如 {@code /authorize?code=..}）。
     * @param in 请求输入流
     * @return 请求目标；读不到返回 null
     * @throws IOException 异常
    */
    private static String readRequestTarget(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        byte[] buffer = new byte[1024];
        int total = 0;
        while (total < 16_384) {
            int read = in.read(buffer);
            if (read < 0) {
                break;
            }
            head.append(new String(buffer, 0, read, StandardCharsets.ISO_8859_1));
            total += read;
            int end = head.indexOf("\r\n\r\n");
            if (end >= 0 || head.indexOf("\n\n") >= 0) {
                break;
            }
        }
        int lineEnd = head.indexOf("\r\n");
        if (lineEnd < 0) {
            lineEnd = head.indexOf("\n");
        }
        if (lineEnd <= 0) {
            return null;
        }
        String[] parts = head.substring(0, lineEnd).split(" ");
        return parts.length >= 2 ? parts[1] : null;
    }

    /**
     * 回一个极简 HTTP 响应（不需要完整 servlet 栈）。
     *
     * @param out     输出流
     * @param status  HTTP 状态码
     * @param reason  状态短语
     * @param body    响应体
     */
    private static void respond(OutputStream out, int status, String reason, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try {
            out.write(header.getBytes(StandardCharsets.ISO_8859_1));
            out.write(payload);
            out.flush();
        } catch (IOException ignored) {
            // 浏览器已断开，无需处理。
        }
    }

    /**
     * 只记录参数名，避免把 refreshToken 等凭据写进日志。
     *
     * @param query 回调 query 串
     * @return 参数名列表
     */
    private static String summarizeKeys(String query) {
        StringBuilder sb = new StringBuilder("[");
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq > 0 ? pair.substring(0, eq) : pair;
            if (key.isBlank()) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(key);
        }
        return sb.append(']').toString();
    }

    /**
     * 一条渠道的监听绑定。
     *
     * @param channel       渠道标识
     * @param server        监听套接字（null 表示启动失败）
     * @param port          实际监听端口（-1 表示失败）
     * @param preferredPort 首选端口
     */
    record Binding(String channel, ServerSocket server, int port, int preferredPort) {

        /** 最近一次收到的回调 query 串。 */
        private static final Map<String, String> LAST = new ConcurrentHashMap<>(4);

        /**
         * 记录回调 query。
         *
         * @param query query 串
         */
        void store(String query) {
            LAST.put(channel, query);
        }

        /**
         * 取走并清空回调 query。
         *
         * @return query 串或 null
         */
        String take() {
            return LAST.remove(channel);
        }

        /**
         * 是否启动成功。
         *
         * @return true 表示正在监听
         */
        boolean alive() {
            return server != null && !server.isClosed();
        }

        /** 关闭监听并清掉未消费的回调。 */
        void close() {
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException ignored) {
                // 关闭失败无需处理。
            }
            LAST.remove(channel);
        }
    }
}

package com.chua.starter.server.support.sip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIP (Secure Infrastructure Proxy) 隧道系统
 *
 * <p>单文件内网穿透实现，通过 {@code main} 以三种角色独立运行：</p>
 * <ul>
 *   <li>{@code server} —— 公网中继，负责 Provider 注册与访客连接撮合</li>
 *   <li>{@code provider} —— 内网侧 Agent，把本地服务暴露给中继</li>
 *   <li>{@code visit} —— 访客侧客户端，把远端服务映射到本地端口</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SipTunnel {

    private static final Logger log = LoggerFactory.getLogger(SipTunnel.class);

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sip-" + System.identityHashCode(r));
        t.setDaemon(true);
        return t;
    });

    // ==================== SERVER ====================

    /**
    * 启动中继服务（server 角色），阻塞返回后由线程池持续接受控制连接
    *
    * @param controlPort 控制端口
    * @param secret      鉴权密钥
    * @throws IOException 端口占用或监听失败
    */
    public static void startServer(int controlPort, String secret) throws IOException {
        ServerSocket controlSocket = new ServerSocket(controlPort);
        log.info("[SIP-Server] 中继服务启动，控制端口: {}，secret: {}", controlPort, secret);

        Map<String, ProviderInfo> providers = new ConcurrentHashMap<>();
        Map<Integer, PendingTunnel> pendingTunnels = new ConcurrentHashMap<>();
        AtomicInteger channelIdGen = new AtomicInteger(0);

        POOL.submit(() -> {
            while (true) {
                try {
                    Socket client = controlSocket.accept();
                    POOL.submit(() -> handleControl(client, providers, pendingTunnels, channelIdGen, secret));
                } catch (IOException e) {
                    log.error("[SIP-Server] 接受控制连接失败", e);
                }
            }
        });
    }

    /**
    * 处理单条控制连接：解析 REGISTER / CONNECT / DATA 指令
    *
    * @param socket         控制连接（方法结束自动关闭）
    * @param providers      共享的 Provider 注册表
    * @param pendingTunnels 共享的待桥接隧道表
    * @param channelIdGen   通道号生成器
    * @param secret         鉴权密钥
    */
    private static void handleControl(Socket socket, Map<String, ProviderInfo> providers,
                                       Map<Integer, PendingTunnel> pendingTunnels,
                                       AtomicInteger channelIdGen, String secret) {
        try (socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            String line;

            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                String cmd = parts[0];

                if ("REGISTER".equals(cmd)) {
                    String providerId = parts[1];
                    String authSecret = parts[2];
                    String services = parts.length > 3 ? parts[3] : "";

                    if (!secret.equals(authSecret)) {
                        out.println("REGISTER_DENIED");
                        continue;
                    }
                    providers.put(providerId, new ProviderInfo(socket, out, services));
                    out.println("REGISTER_OK");
                    log.info("[SIP-Server] Provider 注册: {} 服务: {}", providerId, services);

                } else if ("CONNECT".equals(cmd)) {
                    String providerId = parts[1];
                    String service = parts[2];
                    String authSecret = parts[3];

                    if (!secret.equals(authSecret)) {
                        out.println("CONNECT_DENIED");
                        continue;
                    }

                    ProviderInfo provider = providers.get(providerId);
                    if (provider == null) {
                        out.println("CONNECT_NO_PROVIDER");
                        continue;
                    }

                    int targetPort = -1;
                    for (String svc : provider.services.split(",")) {
                        String[] kv = svc.split(":");
                        if (kv[0].equals(service)) {
                            targetPort = Integer.parseInt(kv[1]);
                            break;
                        }
                    }
                    if (targetPort < 0) {
                        out.println("CONNECT_NO_SERVICE");
                        continue;
                    }

                    int channelId = channelIdGen.incrementAndGet();
                    ServerSocket dataSocket = new ServerSocket(0);
                    int dataPort = dataSocket.getLocalPort();

                    PendingTunnel tunnel = new PendingTunnel(channelId, dataSocket, out);
                    pendingTunnels.put(channelId, tunnel);

                    provider.out.println("OPEN " + channelId + " 127.0.0.1 " + targetPort);
                    out.println("CONNECT_OK " + dataPort);

                    POOL.submit(() -> {
                        try {
                            Socket visitData = dataSocket.accept();
                            tunnel.visitSocket = visitData;
                            tunnel.tryBridge();
                        } catch (IOException e) {
                            log.error("[SIP-Server] 等待 visit 数据连接失败", e);
                        }
                    });

                } else if ("DATA".equals(cmd)) {
                    int channelId = Integer.parseInt(parts[1]);
                    PendingTunnel tunnel = pendingTunnels.get(channelId);
                    if (tunnel != null) {
                        tunnel.providerSocket = socket;
                        tunnel.tryBridge();
                    }
                }
            }
        } catch (Exception e) {
            log.error("[SIP-Server] 控制连接异常", e);
        }
    }

    // ==================== PROVIDER ====================

    /**
    * 启动 Provider 端（provider 角色），注册到中继并处理 OPEN 指令
    *
    * <p>断线后每 5 秒自动重连，直到进程退出。</p>
    *
    * @param serverHost 中继地址
    * @param serverPort 中继控制端口
    * @param providerId Provider 标识
    * @param secret     鉴权密钥
    * @param services   暴露的服务列表（service:port 逗号分隔）
    * @throws IOException 与中继建立连接失败
    */
    public static void startProvider(String serverHost, int serverPort, String providerId,
                                      String secret, String services) throws IOException {
        log.info("[SIP-Provider] 连接 {}:{} providerId={} services={}", serverHost, serverPort, providerId, services);

        while (true) {
            try {
                Socket control = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(control.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream()));

                out.println("REGISTER " + providerId + " " + secret + " " + services);
                String resp = in.readLine();
                log.info("[SIP-Provider] 注册: {}", resp);

                while ((resp = in.readLine()) != null) {
                    if (resp.startsWith("OPEN ")) {
                        String[] parts = resp.split(" ");
                        int channelId = Integer.parseInt(parts[1]);
                        String host = parts[2];
                        int port = Integer.parseInt(parts[3]);

                        Socket localService = new Socket(host, port);
                        out.println("DATA " + channelId);
                        bridgeStreams(localService, control);
                        localService.close();
                        control.close();
                        break;
                    }
                }
            } catch (IOException e) {
                log.warn("[SIP-Provider] 连接断开，5秒后重连: {}", e.getMessage());
            }
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
    }

    // ==================== VISIT ====================

    /**
    * 启动 Visit 端（visit 角色），把远端服务映射到本地端口
    *
    * <p>向中继发起 CONNECT 后循环接受本地连接，并为每个连接新建数据通道桥接到中继。</p>
    *
    * @param serverHost 中继地址
    * @param serverPort 中继控制端口
    * @param providerId 目标 Provider 标识
    * @param service    目标服务名（如 rdp、ssh）
    * @param localPort  本地监听端口
    * @param secret     鉴权密钥
    * @throws IOException 连接中继或本地端口监听失败
    */
    public static void startVisit(String serverHost, int serverPort, String providerId,
                                   String service, int localPort, String secret) throws IOException {
        log.info("[SIP-Visit] server={}:{} providerId={} service={} localPort={}",
                serverHost, serverPort, providerId, service, localPort);

        Socket control = new Socket(serverHost, serverPort);
        PrintWriter out = new PrintWriter(control.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream()));

        out.println("CONNECT " + providerId + " " + service + " " + localPort + " " + secret);
        String resp = in.readLine();
        log.info("[SIP-Visit] 响应: {}", resp);

        if (resp == null || !resp.startsWith("CONNECT_OK")) {
            log.error("[SIP-Visit] 连接失败: {}", resp);
            return;
        }

        int dataPort = Integer.parseInt(resp.split(" ")[1]);
        log.info("[SIP-Visit] 数据端口: {}, 本地监听: {}", dataPort, localPort);

        ServerSocket localListener = new ServerSocket(localPort);
        log.info("[SIP-Visit] 本地端口 {} 已就绪", localPort);

        while (true) {
            Socket client = localListener.accept();
            Socket dataChannel = new Socket(serverHost, dataPort);
            POOL.submit(() -> bridgeStreams(client, dataChannel));
        }
    }

    // ==================== 工具方法 ====================

    /**
    * 双向桥接两个 Socket 的数据流（各提交一个拷贝任务）
    *
    * @param a 一侧连接
    * @param b 另一侧连接
    */
    private static void bridgeStreams(Socket a, Socket b) {
        try {
            InputStream aIn = a.getInputStream();
            OutputStream aOut = a.getOutputStream();
            InputStream bIn = b.getInputStream();
            OutputStream bOut = b.getOutputStream();

            POOL.submit(() -> { try { copy(aIn, bOut); } catch (IOException ignored) {} });
            POOL.submit(() -> { try { copy(bIn, aOut); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.error("[SIP] 桥接失败", e);
        }
    }

    /**
    * 字节流拷贝，直至输入流结束
    *
    * @param in  输入流
    * @param out 输出流
    * @throws IOException 读写异常
    */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            out.flush();
        }
    }

    // ==================== 内部类 ====================

    /**
    * Provider 注册信息：控制连接、控制输出流、可访问服务列表（service:port 逗号分隔）
    */
    private static class ProviderInfo {
        Socket socket;
        PrintWriter out;
        String services;

        /**
        * 构造 Provider 注册信息
        *
        * @param socket   控制连接
        * @param out      控制连接输出流（自动 flush）
        * @param services 服务列表
        */
        ProviderInfo(Socket socket, PrintWriter out, String services) {
            this.socket = socket;
            this.out = out;
            this.services = services;
        }
    }

    /**
    * 待桥接隧道：visit 与 provider 两端数据连接就绪后开始双向转发
    */
    private static class PendingTunnel {
        int channelId;
        ServerSocket dataSocket;
        PrintWriter visitControl;
        Socket visitSocket;
        Socket providerSocket;

        /**
        * 构造隧道上下文
        *
        * @param channelId    通道号
        * @param dataSocket   数据端口监听
        * @param visitControl 访客控制连接输出流
        */
        PendingTunnel(int channelId, ServerSocket dataSocket, PrintWriter visitControl) {
            this.channelId = channelId;
            this.dataSocket = dataSocket;
            this.visitControl = visitControl;
        }

        /**
        * 两端数据连接均就绪时启动桥接（线程安全）
        */
        synchronized void tryBridge() {
            if (visitSocket != null && providerSocket != null) {
                bridgeStreams(visitSocket, providerSocket);
            }
        }
    }

    // ==================== Main ====================

    /**
    * 命令行入口，按角色分发
    *
    * <p>支持 {@code --config xxx.properties} 方式从外部配置文件读取参数，
    * 否则按 {@code server|provider|visit} 位置参数解析。</p>
    *
    * @param args 命令行参数
    * @throws Exception 启动异常
    */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("SIP Tunnel - 内网穿透隧道");
            System.out.println("用法:");
            System.out.println("  server <controlPort> <secret>");
            System.out.println("  provider <serverHost> <serverPort> <providerId> <secret> <services>");
            System.out.println("  visit <serverHost> <serverPort> <providerId> <service> <localPort> <secret>");
            System.out.println("  --config <config.properties>  从外部配置文件读取参数");
            return;
        }

        // 支持 --config xxx.properties 方式启动
        if ("--config".equals(args[0])) {
            Properties cfg = new Properties();
            try (FileInputStream fis = new FileInputStream(args[1])) {
                cfg.load(fis);
            }
            String role = cfg.getProperty("role", "server");
            switch (role) {
                case "server" -> startServer(
                        Integer.parseInt(cfg.getProperty("control.port", "7822")),
                        cfg.getProperty("secret", ""));
                case "provider" -> startProvider(
                        cfg.getProperty("server.host", "127.0.0.1"),
                        Integer.parseInt(cfg.getProperty("server.port", "7822")),
                        cfg.getProperty("provider.id", "win-test-01"),
                        cfg.getProperty("secret", ""),
                        cfg.getProperty("services", "rdp:3389"));
                case "visit" -> startVisit(
                        cfg.getProperty("server.host", "127.0.0.1"),
                        Integer.parseInt(cfg.getProperty("server.port", "7822")),
                        cfg.getProperty("provider.id", "win-test-01"),
                        cfg.getProperty("service", "rdp"),
                        Integer.parseInt(cfg.getProperty("local.port", "13389")),
                        cfg.getProperty("secret", ""));
                default -> System.out.println("未知 role: " + role);
            }
            return;
        }

        switch (args[0]) {
            case "server" -> startServer(Integer.parseInt(args[1]), args[2]);
            case "provider" -> startProvider(args[1], Integer.parseInt(args[2]), args[3], args[4], args[5]);
            case "visit" -> startVisit(args[1], Integer.parseInt(args[2]), args[3], args[4],
                    Integer.parseInt(args[5]), args[6]);
            default -> System.out.println("未知模式: " + args[0]);
        }
    }
}

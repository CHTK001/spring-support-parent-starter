package com.chua.starter.server.support.sip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIP 内嵌中继服务管理器
 * 由系统配置 sip.enabled 控制启停，端口 sip.control.port
 *
 * <p>控制连接为纯文本协议，每条指令一行：</p>
 * <ul>
 *   <li>{@code REGISTER <providerId> <secret> <services>} —— Provider 注册</li>
 *   <li>{@code CONNECT <providerId> <service> <secret>} —— 访客申请隧道，响应数据端口</li>
 *   <li>{@code DATA <channelId>} —— Provider 接入数据通道</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SipServiceManager {

    private static final Logger log = LoggerFactory.getLogger(SipServiceManager.class);

    /**
     * 服务运行标记
    */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 控制端口监听
    */
    private ServerSocket controlSocket;
    /**
     * 控制连接接收线程
    */
    private Thread acceptThread;

    /**
     * 已注册 Provider：providerId -> 注册信息
    */
    private final Map<String, ProviderInfo> providers = new ConcurrentHashMap<>();
    /**
     * 等待桥接的隧道：channelId -> 隧道上下文
    */
    private final Map<Integer, PendingTunnel> pendingTunnels = new ConcurrentHashMap<>();
    /**
     * 通道号生成器
    */
    private final AtomicInteger channelIdGen = new AtomicInteger(0);
    /**
     * 工作线程池（全部为守护线程）
    */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
        Thread t = new Thread(r, "sip-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动中继服务并开始接收控制连接
     *
     * @param port   控制端口
     * @param secret 鉴权密钥
     * @throws IOException 端口占用或监听失败
     */
    public synchronized void start(int port, String secret) throws IOException {
        if (running.get()) {
            log.warn("[SIP] 已在运行中");
            return;
        }
        controlSocket = new ServerSocket(port);
        running.set(true);
        log.info("[SIP] 中继服务已启动，端口: {}", port);

    // [P3C C1 豁免] I/O 阻塞循环（aio/nio/ssh/ftp/终端），不适合交给通用执行器
    acceptThread = new Thread(() -> {
            while (running.get() && !controlSocket.isClosed()) {
                try {
                    Socket client = controlSocket.accept();
                    pool.submit(() -> handleControl(client, secret));
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("[SIP] 接受连接失败", e);
                    }
                }
            }
        }, "sip-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * 停止中继服务：关闭控制端口并清空 Provider / 隧道状态
     */
    public synchronized void stop() {
        running.set(false);
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
        providers.clear();
        pendingTunnels.clear();
        log.info("[SIP] 中继服务已停止");
    }

    /**
     * 中继服务是否运行中
     *
     * @return true 表示已启动
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 处理单条控制连接：循环读取并解析 REGISTER / CONNECT / DATA 指令
     *
     * @param socket 控制连接（方法结束自动关闭）
     * @param secret 鉴权密钥，与指令中携带的密钥比对
     */
    private void handleControl(Socket socket, String secret) {
        try (socket) {
            var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
            var out = new java.io.PrintWriter(socket.getOutputStream(), true);
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
                    log.info("[SIP] Provider 注册: {} 服务: {}", providerId, services);
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
                    // 在 Provider 声明的服务列表中查找目标端口
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
                    PendingTunnel tunnel = new PendingTunnel(channelId, dataSocket);
                    pendingTunnels.put(channelId, tunnel);
                    provider.out.println("OPEN " + channelId + " 127.0.0.1 " + targetPort);
                    out.println("CONNECT_OK " + dataPort);
                    pool.submit(() -> {
                        try {
                            Socket visitData = dataSocket.accept();
                            tunnel.visitSocket = visitData;
                            tunnel.tryBridge();
                        } catch (IOException e) {
                            log.error("[SIP] 等待 visit 连接失败", e);
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
            log.error("[SIP] 控制连接异常", e);
        }
    }

    /**
     * 双向桥接两个 Socket 的数据流（各提交一个拷贝任务）
     *
     * @param a 一侧连接
     * @param b 另一侧连接
     */
    private void bridgeStreams(java.net.Socket a, java.net.Socket b) {
        try {
            java.io.InputStream aIn = a.getInputStream();
            java.io.OutputStream aOut = a.getOutputStream();
            java.io.InputStream bIn = b.getInputStream();
            java.io.OutputStream bOut = b.getOutputStream();
            pool.submit(() -> {
                try {
                    copy(aIn, bOut);
                } catch (Exception ignored) {
                    // 单侧断开时忽略，等待另一侧自然结束
                }
            });
            pool.submit(() -> {
                try {
                    copy(bIn, aOut);
                } catch (Exception ignored) {
                    // 单侧断开时忽略，等待另一侧自然结束
                }
            });
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
    private void copy(java.io.InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            out.flush();
        }
    }

    /**
     * Provider 注册信息：控制连接、控制输出流、可访问服务列表（service:port 逗号分隔）
     */
    private static class ProviderInfo {
        /**
         * 控制连接
        */
        Socket socket;
        /**
         * 控制连接输出流（自动 flush）
        */
        java.io.PrintWriter out;
        /**
         * 服务列表，形如 rdp:3389,ssh:22
        */
        String services;

        /**
         * 构造 Provider 注册信息
         * @param o 控制连接输出流（自动 flush）
         * @param s 控制连接
         * @param svc 服务列表
         * @return 返回值
        */
        ProviderInfo(Socket s, java.io.PrintWriter o, String svc) {
            socket = s;
            out = o;
            services = svc;
        }
    }

    /**
     * 待桥接隧道：visit 与 provider 两端数据连接就绪后开始双向转发
     */
    private class PendingTunnel {
        /**
         * 通道号
        */
        int channelId;
        /**
         * 数据端口监听
        */
        ServerSocket dataSocket;
        /**
         * 访客侧数据连接
        */
        java.net.Socket visitSocket;
        /**
         * Provider 侧数据连接
        */
        java.net.Socket providerSocket;

        /**
         * 构造隧道上下文
         * @param ds 数据端口监听
         * @param id 通道号
         * @return 返回值
        */
        PendingTunnel(int id, ServerSocket ds) {
            channelId = id;
            dataSocket = ds;
        }

        /**
         * 两端数据连接均就绪时启动桥接（线程安全）
         */
        synchronized void tryBridge() {
            if (visitSocket != null && providerSocket != null) {
                bridgeStreams(visitSocket, providerSocket);
            }
        }
    }}
package com.chua.starter.datasourcehub.support.support;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 数据源探活与会话延迟测量。
 *
 * <p>两个延迟口径：</p>
 * <ul>
 *     <li>连接延迟：建立 TCP 连接（或 JDBC 完整握手）耗时</li>
 *     <li>会话延迟：已建立连接上一次往返耗时。jdbc 走 {@code SELECT 1}、redis 走 RESP
 *     {@code PING}、nginx/nacos/influxdb 走一次 HTTP 往返；neo4j 与 zookeeper 的协议是二进制且
 *     本模块无对应客户端，故退化为「第二次 TCP 建链耗时」作为近似，并在返回值中如实标注。</li>
 * </ul>
 *
 * <p>非 jdbc 类型的连接延迟与连接延迟同源（都只做 TCP 建链），因此两者数值接近属预期。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DatasourceProbeSupport {

    /**
     * 工具类禁止实例化
     */
    private DatasourceProbeSupport() {
    }

    /**
     * HTTP 探活请求模板：只关心「有没有回字节」，不校验状态码。
     */
    private static final String HTTP_PROBE_REQUEST =
            "GET / HTTP/1.1\r\nHost: %s\r\nConnection: close\r\nAccept: */*\r\n\r\n";

    /**
     * RESP PING 请求体。
     */
    private static final String RESP_PING = "*1\r\n$4\r\nPING\r\n";

    /**
     * 单次读超时（毫秒）。取值远小于探活总超时，避免服务端不回数据时长时间挂住。
     */
    private static final int READ_TIMEOUT_MS = 2000;

    /**
     * 服务器运行指标（随探活顺路采集，取不到为 null）。
     *
     * <p>目前只有 MySQL/MariaDB 的 JDBC 探活查询 {@code SHOW GLOBAL STATUS}，
     * 其余方言/协议不支持时为 null —— 前端面板如实显示「暂未采集」，
     * 不编造数值。</p>
     *
     * @param slowQueries         累计慢查询数（MySQL 协议不暴露宿主 CPU，慢查询
     *                            数是「SQL 侧负载」最接近的公开口径）
     * @param threadsRunning      正在执行的线程数（负载的近似指标）
     * @param questions           累计提问次数（MySQL 自身计数）
     * @param uptimeSeconds       服务器已运行秒数
     * @param maxUsedConnections  历史峰值客户端连接数
     * @param memoryBytes         实例内存占用（字节）。来自
     *                            {@code performance_schema.memory_summary_global_by_event_name}
     *                            的 InnoDB 相关事件当前占用合计 —— 是 MySQL 进程
     *                            <b>内部</b>内存（缓冲池 / 字典缓存等），不是宿主机内存；
     *                            权限不足或 performance_schema 关闭时为 null
     */
    public record ServerStats(
            Long slowQueries,
            Long threadsRunning,
            Long questions,
            Long uptimeSeconds,
            Long maxUsedConnections,
            Long memoryBytes
    ) {
    }

    /**
     * 探活结果。
     *
     * @param success            是否连通
     * @param connectLatencyMs   连接延迟（毫秒）
     * @param sessionLatencyMs   会话延迟（毫秒）
     * @param message            结果说明
     * @param connectionCount    采样时刻目标服务上的客户端连接数；取不到为 null
     *                           （不是所有类型都暴露该指标，null 在图上是缺格）
     * @param serverStats        采样时刻服务器运行指标；不支持的类型为 null
     * @param serverVersion      服务端版本；探测不到为 null
     */
    public record ProbeOutcome(
            boolean success,
            Long connectLatencyMs,
            Long sessionLatencyMs,
            String message,
            Long connectionCount,
            ServerStats serverStats,
            String serverVersion
    ) {
        /**
         * 无版本、无服务器指标的六参构造。
         *
         * @param success            是否连通
         * @param connectLatencyMs   连接延迟（毫秒）
         * @param sessionLatencyMs   会话延迟（毫秒）
         * @param message            结果说明
         * @param connectionCount    采样时刻目标服务上的客户端连接数
         * @param serverStats        采样时刻服务器运行指标
         */
        public ProbeOutcome(boolean success, Long connectLatencyMs, Long sessionLatencyMs,
                            String message, Long connectionCount, ServerStats serverStats) {
            this(success, connectLatencyMs, sessionLatencyMs, message, connectionCount, serverStats, null);
        }

        /**
         * 无服务器指标的五参构造：让不支持服务器指标的类型照旧只填前五项，
         * 不需要每个调用点都补 {@code null}。
         *
         * @param success            是否连通
         * @param connectLatencyMs   连接延迟（毫秒）
         * @param sessionLatencyMs   会话延迟（毫秒）
         * @param message            结果说明
         * @param connectionCount    采样时刻目标服务上的客户端连接数
         */
        public ProbeOutcome(boolean success, Long connectLatencyMs, Long sessionLatencyMs,
                            String message, Long connectionCount) {
            this(success, connectLatencyMs, sessionLatencyMs, message, connectionCount, null, null);
        }

        /**
         * 构造一个「没走到探测那一步」的失败结果。
         *
         * <p>用于探测的前置环节失败（隧道打不开、数据源配置缺失等）：
         * 此时没有任何延迟数据可报，填 0 会被画成「延迟 0 毫秒」的漂亮曲线，
         * 与事实完全相反，所以延迟一律留 null。</p>
         *
         * @param message 失败原因
         * @return 失败结果
         */
        public static ProbeOutcome failed(String message) {
            return new ProbeOutcome(false, null, null, message, null);
        }
    }

    /**
     * TCP 建链并测量耗时。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时（毫秒）
     * @return 建链耗时（毫秒）
     * @throws Exception 建链失败
     */
    public static long connectAndMeasure(String host, int port, int timeoutMs) throws Exception {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(READ_TIMEOUT_MS);
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    /**
     * 非 jdbc 类型的完整探活：建链 + 一次会话往返。
     *
     * @param type      数据源类型
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时（毫秒）
     * @return 探活结果
     */
    public static ProbeOutcome probeSocket(String type, String host, int port, int timeoutMs) {
        long connectMs;
        try {
            connectMs = connectAndMeasure(host, port, timeoutMs);
        } catch (Exception e) {
            return new ProbeOutcome(false, null, null, "连接失败: " + e.getMessage(), null);
        }

        String normalized = type == null ? "" : type.trim().toLowerCase();
        try {
            Long connectionCount = null;
            String serverVersion = null;
            long sessionMs;
            switch (normalized) {
                case "redis" -> {
                    // 顺路取客户端连接数与版本：PING 与 INFO 在同一次会话里完成，
                    // 多一次往返也不浪费 —— 反正这条 socket 用完即关。
                    long[] session = new long[1];
                    RedisProbe redis = redisProbe(host, port, timeoutMs, session);
                    connectionCount = redis.connectionCount();
                    serverVersion = redis.version();
                    sessionMs = session[0];
                }
                case "nginx", "nacos", "influxdb", "prometheus" -> sessionMs = httpRoundTrip(host, port, timeoutMs);
                // neo4j(Bolt) / zookeeper(ZooKeeper) 为二进制协议，本模块无对应客户端，
                // 退化为第二次建链作为会话延迟近似值。
                default -> sessionMs = connectAndMeasure(host, port, timeoutMs);
            }
            return new ProbeOutcome(true, connectMs, sessionMs, "连接测试成功", connectionCount, null, serverVersion);
        } catch (Exception e) {
            // 建链已成功，会话探测失败不推翻连通性结论，但会话延迟无值
            log.debug("数据源 {} 会话探测失败: {}", type, e.getMessage());
            return new ProbeOutcome(true, connectMs, null,
                    "连接测试成功(会话探测失败: " + e.getMessage() + ")", null);
        }
    }

    /**
     * Redis 会话探测：发送 RESP {@code PING} 并读到响应，随后 {@code INFO} 解析
     * {@code connected_clients} 与 {@code redis_version}。
     *
     * <p>连接数与版本都是「尽力而为」：未设密码的实例能拿到；设了密码时 INFO 返回 NOAUTH，
     * 这里返回 null 而不是报错 —— 连通性结论已经由 PING 得出，缺一格
     * 比为了它把整次探测判成失败合理得多。</p>
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时（毫秒）
     * @param sessionMs 长度为 1 的出参，带回 PING 往返耗时（毫秒）
     * @return 连接数与版本（都可能为 null）
     * @throws Exception PING 探测失败
     */
    private static RedisProbe redisProbe(String host, int port, int timeoutMs, long[] sessionMs) throws Exception {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(RESP_PING.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            // RESP 简单字符串以 '+' 开头，读到第一个字节即视为有响应
            if (in.read() < 0) {
                throw new IllegalStateException("Redis 未返回 PING 响应");
            }
            sessionMs[0] = (System.nanoTime() - start) / 1_000_000L;

            // INFO：不带 section 取全量，一次往返同时拿到连接数与版本。
            // 原先只发 INFO clients，版本还得再来一次往返 —— 这条 socket 用完即关，
            // 省下的那次往返没有意义，而版本正是「这条数据源到底是哪个 redis」的答案。
            out.write("*2\r\n$4\r\nINFO\r\n$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String body = respBulkRead(in);
            if (body == null) {
                return new RedisProbe(null, null);
            }
            Long connectionCount = null;
            String version = null;
            for (String line : body.split("\r\n")) {
                if (line.startsWith("connected_clients:")) {
                    connectionCount = parseLongOrNull(line.substring("connected_clients:".length()).trim());
                } else if (line.startsWith("redis_version:")) {
                    String value = line.substring("redis_version:".length()).trim();
                    version = value.isEmpty() ? null : value;
                }
            }
            return new RedisProbe(connectionCount, version);
        }
    }

    /**
     * 解析长整型，失败返回 null。
     *
     * @param text 文本
     * @return 数值或 null
     */
    private static Long parseLongOrNull(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Redis 探测顺带取到的两项信息。
     *
     * @param connectionCount 客户端连接数，取不到为 null
     * @param version         {@code redis_version}，取不到为 null
     */
    private record RedisProbe(Long connectionCount, String version) {
    }

    /**
     * 读取一个 RESP bulk string 响应体（行内协议，只够 INFO 用）。
     *
     * @param in socket 输入流
     * @return 响应体；非 bulk 响应（如错误行）返回 null
     * @throws java.io.IOException 读取失败
     */
    private static String respBulkRead(InputStream in) throws java.io.IOException {
        StringBuilder header = new StringBuilder();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                header.append((char) b);
            }
        }
        // bulk string 以 '$' 开头，后跟字节数；错误行以 '-' 开头，直接放弃
        if (!header.toString().startsWith("$")) {
            return null;
        }
        int length;
        try {
            length = Integer.parseInt(header.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (length <= 0) {
            return null;
        }
        byte[] body = in.readNBytes(length);
        if (body.length < length) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * HTTP 类服务会话延迟：发一次 GET，读到响应首字节即止。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时（毫秒）
     * @return 往返耗时（毫秒）
     * @throws Exception 探测失败
     */
    private static long httpRoundTrip(String host, int port, int timeoutMs) throws Exception {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(String.format(HTTP_PROBE_REQUEST, host).getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            if (in.read() < 0) {
                throw new IllegalStateException("HTTP 服务未返回响应");
            }
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }
}

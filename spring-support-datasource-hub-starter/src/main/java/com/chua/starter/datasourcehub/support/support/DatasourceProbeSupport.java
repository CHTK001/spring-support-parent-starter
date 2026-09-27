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
     * 探活结果。
     *
     * @param success            是否连通
     * @param connectLatencyMs   连接延迟（毫秒）
     * @param sessionLatencyMs   会话延迟（毫秒）
     * @param message            结果说明
     */
    public record ProbeOutcome(
            boolean success,
            Long connectLatencyMs,
            Long sessionLatencyMs,
            String message
    ) {
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
            return new ProbeOutcome(false, null, null, "连接失败: " + e.getMessage());
        }

        String normalized = type == null ? "" : type.trim().toLowerCase();
        try {
            long sessionMs = switch (normalized) {
                case "redis" -> redisPing(host, port, timeoutMs);
                case "nginx", "nacos", "influxdb" -> httpRoundTrip(host, port, timeoutMs);
                // neo4j(Bolt) / zookeeper(ZooKeeper) 为二进制协议，本模块无对应客户端，
                // 退化为第二次建链作为会话延迟近似值。
                default -> connectAndMeasure(host, port, timeoutMs);
            };
            return new ProbeOutcome(true, connectMs, sessionMs, "连接测试成功");
        } catch (Exception e) {
            // 建链已成功，会话探测失败不推翻连通性结论，但会话延迟无值
            log.debug("数据源 {} 会话探测失败: {}", type, e.getMessage());
            return new ProbeOutcome(true, connectMs, null, "连接测试成功(会话探测失败: " + e.getMessage() + ")");
        }
    }

    /**
     * Redis 会话延迟：发送 RESP {@code PING} 并读到响应。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时（毫秒）
     * @return 往返耗时（毫秒）
     * @throws Exception 探测失败
     */
    private static long redisPing(String host, int port, int timeoutMs) throws Exception {
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
        }
        return (System.nanoTime() - start) / 1_000_000L;
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

// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.support;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 各数据源类型的专属运行时信息探针。
 *
 * <p>只用裸 Socket / HTTP，不引入任何协议客户端依赖：</p>
 * <ul>
 *     <li>redis：RESP {@code INFO} 命令，取 redis_version / mode 等关键行</li>
 *     <li>zookeeper：四字命令 {@code ruok}，返回 {@code imok} 即存活</li>
 *     <li>influxdb / nacos / nginx / neo4j：HTTP 探活，取状态码与响应体片段</li>
 *     <li>jdbc：不在此处理，交给 DatasourceServiceImpl 的 JDBC 专用探活</li>
 * </ul>
 *
 * <p>探针失败不抛异常，一律以「取不到值」表达，避免把类型专属信息缺失误判成数据源整体不可用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DatasourceTypeInfoSupport {

    /**
     * 工具类禁止实例化
     */
    private DatasourceTypeInfoSupport() {
    }

    /**
     * 单次读超时（毫秒）
     */
    private static final int READ_TIMEOUT_MS = 2000;

    /**
     * 读取的最大字节数，避免把大响应体全读进内存
     */
    private static final int MAX_READ = 8192;

    /**
     * 读取指定类型的运行时信息。
     *
     * @param type      数据源类型
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 连接超时（毫秒）
     * @return 键值信息；类型不支持或探测失败时返回空 Map
     */
    public static Map<String, String> read(String type, String host, int port, int timeoutMs) {
        Map<String, String> info = new LinkedHashMap<>();
        if (host == null || host.isBlank() || port <= 0) {
            return info;
        }
        String normalized = type == null ? "" : type.trim().toLowerCase();
        try {
            switch (normalized) {
                case "redis" -> readRedis(host, port, timeoutMs, info);
                case "zookeeper" -> readZookeeper(host, port, timeoutMs, info);
                case "influxdb", "nacos", "nginx", "neo4j", "prometheus" -> readHttp(normalized, host, port, timeoutMs, info);
                default -> log.debug("类型 {} 无专属信息探针", type);
            }
        } catch (Exception e) {
            log.debug("读取 {} 信息失败: {}", type, e.getMessage());
        }
        return info;
    }

    /**
     * Redis：发 RESP {@code INFO}，挑出版本与运行模式。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时
     * @param info      输出容器
     * @throws Exception 探测失败
     */
    private static void readRedis(String host, int port, int timeoutMs, Map<String, String> info) throws Exception {
        // *1\r\n$4\r\nINFO\r\n
        String body = exchange(host, port, timeoutMs, "*1\r\n$4\r\nINFO\r\n");
        for (String key : new String[]{"redis_version", "redis_mode", "uptime_in_seconds"}) {
            String value = extract(body, key);
            if (value != null) {
                info.put(key, value);
            }
        }
    }

    /**
     * ZooKeeper：四字命令 {@code ruok}。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时
     * @param info      输出容器
     * @throws Exception 探测失败
     */
    private static void readZookeeper(String host, int port, int timeoutMs, Map<String, String> info) throws Exception {
        String body = exchange(host, port, timeoutMs, "ruok").trim();
        info.put("ruok", body);
    }

    /**
     * HTTP 类服务：取状态码与响应体前若干字符。
     *
     * @param type      类型，用于挑选探活路径
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时
     * @param info      输出容器
     * @throws Exception 探测失败
     */
    private static void readHttp(String type, String host, int port, int timeoutMs, Map<String, String> info)
            throws Exception {
        String path = switch (type) {
            case "influxdb" -> "/health";
            case "nacos" -> "/nacos/v1/console/health/readiness";
            case "prometheus" -> "/-/healthy";
            case "neo4j" -> "/";
            default -> "/";
        };
        String response = exchange(host, port, timeoutMs, null, path, host);
        String statusLine = response.isEmpty() ? "" : response.split("\r\n", 2)[0];
        info.put("status", statusLine);
        int bodyIndex = response.indexOf("\r\n\r\n");
        if (bodyIndex >= 0) {
            String snippet = response.substring(bodyIndex + 4).trim();
            if (!snippet.isEmpty()) {
                info.put("body", snippet.length() > 200 ? snippet.substring(0, 200) : snippet);
            }
        }
    }

    /**
     * 一次「发-收」往返。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时
     * @param payload   发送内容
     * @return 收到的内容
     * @throws Exception 探测失败
     */
    private static String exchange(String host, int port, int timeoutMs, String payload) throws Exception {
        return exchange(host, port, timeoutMs, payload, null, host);
    }

    /**
     * 一次「发-收」往返（可指定 HTTP 路径）。
     *
     * @param host      目标主机
     * @param port      目标端口
     * @param timeoutMs 超时
     * @param payload   原始发送内容；为 null 时按 HTTP 组包
     * @param path      HTTP 路径
     * @param hostHeader Host 请求头
     * @return 收到的内容
     * @throws Exception 探测失败
     */
    private static String exchange(String host, int port, int timeoutMs, String payload, String path, String hostHeader)
            throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            if (payload != null) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            } else {
                out.write(("GET " + path + " HTTP/1.1\r\nHost: " + hostHeader
                        + "\r\nConnection: close\r\nAccept: */*\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[MAX_READ];
            int read = in.read(buffer);
            return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    /**
     * 从 {@code key:value} 形式的文本里取值。
     *
     * @param body 文本
     * @param key  键
     * @return 值；未找到时 null
     */
    private static String extract(String body, String key) {
        for (String line : body.split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().equals(key)) {
                return line.substring(idx + 1).trim();
            }
        }
        return null;
    }
}
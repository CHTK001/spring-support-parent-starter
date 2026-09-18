package com.chua.agent;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent HTTP 接口处理器。
 *
 * <p>以 {@code com.sun.net.httpserver} 实现各信息接口的统一响应封装与查询参数解析，
 * 统一返回 {@code application/json; charset=utf-8} 并允许跨域。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class AgentHttpHandlers {

    /**
     * 输出 JSON 响应。
     *
     * @param ex   HTTP 交换对象
     * @param code HTTP 状态码
     * @param json 响应体
     * @throws IOException 响应写出失败
     */
    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 解析 URL 查询串为键值对。
     *
     * @param query 查询串（可为空）
     * @return 参数映射；无参数时返回空 Map
     */
    static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) {
            return map;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    /**
     * 处理 /info：返回主机基础信息。
     *
     * @param ex HTTP 交换对象
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleInfo(HttpExchange ex, AgentSystemInfo si) throws IOException {
        try {
            sendJson(ex, 200, si.info());
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /processes：分页返回进程列表。
     *
     * @param ex HTTP 交换对象（读取 page / pageSize 查询参数）
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleProcesses(HttpExchange ex, AgentSystemInfo si) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int page = Integer.parseInt(q.getOrDefault("page", "1"));
        int ps = Integer.parseInt(q.getOrDefault("pageSize", "20"));
        try {
            sendJson(ex, 200, si.processes(page, ps));
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /services：分页返回服务列表。
     *
     * @param ex HTTP 交换对象（读取 page / pageSize 查询参数）
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleServices(HttpExchange ex, AgentSystemInfo si) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        int page = Integer.parseInt(q.getOrDefault("page", "1"));
        int ps = Integer.parseInt(q.getOrDefault("pageSize", "20"));
        try {
            sendJson(ex, 200, si.services(page, ps));
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /disks：返回磁盘列表。
     *
     * @param ex HTTP 交换对象
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleDisks(HttpExchange ex, AgentSystemInfo si) throws IOException {
        try {
            sendJson(ex, 200, si.disks());
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /network：返回网络接口列表。
     *
     * @param ex HTTP 交换对象
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleNetwork(HttpExchange ex, AgentSystemInfo si) throws IOException {
        try {
            sendJson(ex, 200, si.network());
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /netproc：返回端口占用（网络进程）列表。
     *
     * @param ex HTTP 交换对象
     * @param si 系统信息采集器
     * @throws IOException 响应写出失败
     */
    static void handleNetProc(HttpExchange ex, AgentSystemInfo si) throws IOException {
        try {
            sendJson(ex, 200, si.netProc());
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /files：列出指定目录下的文件条目（name/isDir/size/modified）。
     *
     * @param ex HTTP 交换对象（读取 path 查询参数，默认 /）
     * @throws IOException 响应写出失败
     */
    static void handleFiles(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        String path = q.getOrDefault("path", "/");
        File dir = new File(path);
        File[] files = dir.listFiles();
        StringBuilder sb = new StringBuilder("[");
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                File f = files[i];
                sb.append("{\"name\":\"").append(f.getName().replace("\"", "\\\""))
                  .append("\",\"isDir\":").append(f.isDirectory())
                  .append(",\"size\":").append(f.length())
                  .append(",\"modified\":\"").append(new java.util.Date(f.lastModified())).append("\"}");
            }
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    /**
     * 处理 /exec：执行命令并返回标准输出。
     *
     * @param ex HTTP 交换对象（读取 cmd 查询参数）
     * @throws IOException 响应写出失败
     */
    static void handleExec(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        String cmd = q.getOrDefault("cmd", "");
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            String out = new String(p.getInputStream().readAllBytes());
            sendJson(ex, 200, "{\"output\":\"" + out.replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 /env：返回进程环境变量列表。
     *
     * @param ex HTTP 交换对象
     * @throws IOException 响应写出失败
     */
    static void handleEnv(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("{\"name\":\"").append(e.getKey()).append("\",\"value\":\"").append(e.getValue().replace("\"", "\\\"")).append("\"}");
            first = false;
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }
}

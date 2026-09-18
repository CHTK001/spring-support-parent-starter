package com.chua.agent;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 监控 Agent 启动入口（独立运行于被监控主机）。
 *
 * <p>启动 19876 端口的内置 HTTP 服务对外暴露系统信息接口，
 * 并启动守护线程周期性把指标推送到平台。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class AgentMain {

    /**
     * 启动 Agent：读取 agent.yml 配置 → 注册 HTTP 接口 → 启动指标上报线程。
     *
     * @param args 命令行参数（当前未使用）
     * @throws Exception 配置读取失败或端口占用
     */
    public static void main(String[] args) throws Exception {
        // 优先读取 config/agent.yml，其次读取工作目录下的 agent.yml
        Properties cfg = new Properties();
        Path cfgPath = Path.of("config", "agent.yml");
        if (!Files.exists(cfgPath)) {
            cfgPath = Path.of("agent.yml");
        }
        try (Reader r = Files.newBufferedReader(cfgPath)) {
            // 极简 key: value 解析，忽略空行、注释行与列表行
            for (String line : Files.readAllLines(cfgPath)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) {
                    continue;
                }
                int idx = line.indexOf(':');
                if (idx > 0) {
                    cfg.setProperty(line.substring(0, idx).trim(),
                            line.substring(idx + 1).trim().replace("\"", ""));
                }
            }
        }
        String serverUrl = cfg.getProperty("server.url", "http://localhost:8081");
        String token = cfg.getProperty("agent.token", "");
        int port = Integer.parseInt(cfg.getProperty("agent.port", "19876"));

        System.out.println("[Agent] Starting on port " + port + ", pushing to " + serverUrl);

        AgentSystemInfo sysInfo = new AgentSystemInfo();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/info", ex -> AgentHttpHandlers.handleInfo(ex, sysInfo));
        server.createContext("/processes", ex -> AgentHttpHandlers.handleProcesses(ex, sysInfo));
        server.createContext("/services", ex -> AgentHttpHandlers.handleServices(ex, sysInfo));
        server.createContext("/disks", ex -> AgentHttpHandlers.handleDisks(ex, sysInfo));
        server.createContext("/network", ex -> AgentHttpHandlers.handleNetwork(ex, sysInfo));
        server.createContext("/netproc", ex -> AgentHttpHandlers.handleNetProc(ex, sysInfo));
        server.createContext("/files", ex -> AgentHttpHandlers.handleFiles(ex));
        server.createContext("/exec", ex -> AgentHttpHandlers.handleExec(ex));
        server.createContext("/env", ex -> AgentHttpHandlers.handleEnv(ex));
        server.createContext("/health", ex -> AgentHttpHandlers.sendJson(ex, 200, "{\"status\":\"up\"}"));
        server.setExecutor(null);
        server.start();

        // 启动指标上报守护线程
        MetricsPusher pusher = new MetricsPusher(serverUrl, token, sysInfo);
        Thread pushThread = new Thread(pusher, "agent-metrics-pusher");
        pushThread.setDaemon(true);
        pushThread.start();

        System.out.println("[Agent] Started successfully");
    }
}

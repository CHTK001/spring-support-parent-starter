package com.chua.starter.ai.support.manage.api;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* MCP 真实连接服务。
*
* <p>基于 MCP Java SDK 连接真实的 MCP 服务器，支持
* streamable HTTP 与 SSE 两种传输，连接成功后列举服务器声明的工具。</p>
*
* <p>前端约定的请求体：{@code { name, transport }}，其中 transport 支持
* {@code { type: 'http'|'streamable_http'|'sse', url, headers? }}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Service
@Slf4j
public class McpConnectionService {

    /** 连接超时：初始化 MCP 会话的最长等待时间 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /**
    * 连接 MCP 服务器并列举其工具。
    *
    * @param name      服务器名称（仅用于日志）
    * @param transport transport 配置（类型 + url + 头部）
    * @return 前端约定的连接测试结果：
    *         {@code { success, tools?: [{name, description, input_schema}], error?, code? }}
    */
    public Map<String, Object> testConnection(String name, Map<String, Object> transport) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        String type = transport == null ? "" : String.valueOf(transport.getOrDefault("type", ""));
        String url = transport == null ? "" : String.valueOf(transport.getOrDefault("url", ""));
        boolean isStdio = "stdio".equalsIgnoreCase(type);

        // stdio 用本地命令启动，不要求 URL；HTTP/SSE 必须提供 URL
        if (!isStdio && (url == null || url.isBlank())) {
            result.put("success", false);
            result.put("error", "MCP 服务器 URL 不能为空");
            result.put("code", "BAD_REQUEST");
            return result;
        }
        if (isStdio && (transport == null || transport.get("command") == null
                || String.valueOf(transport.get("command")).isBlank())) {
            result.put("success", false);
            result.put("error", "stdio MCP 服务器 command 不能为空");
            result.put("code", "BAD_REQUEST");
            return result;
        }

        try {
            io.modelcontextprotocol.spec.McpClientTransport clientTransport =
                    /**
                    * 构建transport。
                    * @param transport transport
                    */
                    buildTransport(transport);
            if (clientTransport == null) {
                result.put("success", false);
                result.put("error", "不支持的 MCP 传输类型: " + type);
                result.put("code", "UNSUPPORTED_TRANSPORT");
                return result;
            }

            /**
            * 尝试。
            * @param McpClient.sync(clientTransport mcp客户端.同步(客户端transport
            */
            try (McpSyncClient client = McpClient.sync(clientTransport)
                    .requestTimeout(CONNECT_TIMEOUT)
                    .initializationTimeout(CONNECT_TIMEOUT)
                    .build()) {
                client.initialize();
                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<Map<String, Object>> tools = new ArrayList<>(8); // MCP 工具数量不可预估
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    Map<String, Object> toolMap = new LinkedHashMap<>(4);
                    toolMap.put("name", tool.name());
                    toolMap.put("description", tool.description());
                    toolMap.put("input_schema", tool.inputSchema());
                    tools.add(toolMap);
                }
                result.put("success", true);
                result.put("tools", tools);
                return result;
            }
        } catch (Exception e) {
            log.warn("[McpConnection] 连接 MCP 服务器失败: name={}, type={}, err={}", name, type, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage() == null ? String.valueOf(e) : e.getMessage());
            result.put("code", "CONNECTION_FAILED");
            return result;
        }
    }

    /**
    * 根据传输配置构建 MCP 客户端 transport。
    *
    * @param transport transport 配置（类型 + url/命令/参数/env + 头部）
    * @return 对应 transport，不支持的 类型 返回 空
    */
    private io.modelcontextprotocol.spec.McpClientTransport buildTransport(Map<String, Object> transport) {
        String type = transport == null ? "" : String.valueOf(transport.getOrDefault("type", ""));
        String url = transport == null ? "" : String.valueOf(transport.getOrDefault("url", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> headers =
                transport == null || !(transport.get("headers") instanceof Map<?, ?> h)
                        ? null
                        : (Map<String, Object>) h;

        String normalized = type == null ? "" : type.toLowerCase();
        switch (normalized) {
            case "http":
            case "streamable_http":
            case "streamablehttp":
                return buildStreamableHttpTransport(url, headers);
            case "sse":
                return buildSseTransport(url, headers);
            case "stdio":
                return buildStdioTransport(transport);
            default:
                return null;
        }
    }

    private io.modelcontextprotocol.spec.McpClientTransport buildStreamableHttpTransport(
            String url, Map<String, Object> headers) {
        HttpClientStreamableHttpTransport.Builder builder =
                HttpClientStreamableHttpTransport.builder(url);
        /**
        * apply头部。
        * @param builder 构建器
        * @param headers 头部
        */
        applyHeaders(builder, headers);
        return builder.build();
    }

    private io.modelcontextprotocol.spec.McpClientTransport buildSseTransport(
            String url, Map<String, Object> headers) {
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(url);
        /**
        * apply头部。
        * @param builder 构建器
        * @param headers 头部
        */
        applyHeaders(builder, headers);
        return builder.build();
    }

    /**
    * 构建 stdio 本地进程 transport（命令 + 参数 + env）
    *
    * @param transport transport
    * @return 构建stdiotransport的结果
    */
    private io.modelcontextprotocol.spec.McpClientTransport buildStdioTransport(Map<String, Object> transport) {
        String command = String.valueOf(transport.getOrDefault("command", ""));
        ServerParameters.Builder builder = ServerParameters.builder(command);

        Object rawArgs = transport.get("args");
        if (rawArgs instanceof Iterable<?> iterable) {
            List<String> args = new ArrayList<>(4); // CLI 参数数量不可预估
            for (Object a : iterable) {
                args.add(String.valueOf(a));
            }
            if (!args.isEmpty()) {
                builder.args(args);
            }
        }

        Object rawEnv = transport.get("env");
        if (rawEnv instanceof Map<?, ?> envMap) {
            Map<String, String> env = new HashMap<>(8);
            for (Map.Entry<?, ?> e : envMap.entrySet()) {
                if (e.getValue() != null) {
                    env.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            if (!env.isEmpty()) {
                builder.env(env);
            }
        }

        /**
        * stdio客户端transport。
        * @param builder.build( 构建器.构建(
        * @return stdio客户端transport的结果
        */
        return new StdioClientTransport(builder.build(), McpJsonDefaults.getMapper());
    }

    /** 把自定义 头部 应用到请求构造器（默认每次请求都带上） */
    private void applyHeaders(
            HttpClientStreamableHttpTransport.Builder builder,
            Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<String, String> flat = new HashMap<>(8);
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getValue() != null) {
                flat.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        if (flat.isEmpty()) {
            return;
        }
        builder.customizeRequest(req -> flat.forEach(req::header));
    }

    /** 把自定义 头部 应用到 SSE transport 请求构造器 */
    private void applyHeaders(
            HttpClientSseClientTransport.Builder builder,
            Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<String, String> flat = new HashMap<>(8);
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getValue() != null) {
                flat.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        if (flat.isEmpty()) {
            return;
        }
        builder.customizeRequest(req -> flat.forEach(req::header));
    }
}

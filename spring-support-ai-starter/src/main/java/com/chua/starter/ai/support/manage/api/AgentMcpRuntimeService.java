package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.mcp.DefaultMcpManager;
import com.chua.common.support.ai.mcp.McpClient;
import com.chua.common.support.ai.mcp.McpManager;
import com.chua.common.support.ai.mcp.McpToolCall;
import com.chua.common.support.ai.mcp.McpToolDescriptor;
import com.chua.common.support.ai.mcp.McpToolResult;
import com.chua.starter.ai.support.manage.store.McpServerStore;
import com.chua.starter.ai.support.service.AiPluginCatalogService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent MCP 运行时服务。
 *
 * <p>在管理员角色启用 MCP 能力时，从 {@code sys_ai_mcp_server} 读取启用的服务器，
 * 基于官方 MCP Java SDK 建立实时同步连接，适配为项目通用 {@link McpManager}，
 * 供 AgentScope Harness 把 MCP 工具注册为可调用工具。</p>
 *
 * <p>连接为单次 Agent 运行私有，运行结束必须 {@link McpRuntime#close()}
 * 关闭底层 stdio 进程 / HTTP 连接，避免资源泄漏。</p>
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMcpRuntimeService {

    /**
     * MCP 连接与请求超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * 单次工具调用的硬性墙钟超时（兜底，避免远程服务器异常导致无限挂起）
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(90);

    /**
     * 本地 stdio 工具首次启动（npx 下载依赖）的初始化超时，比远程连接更宽松
     */
    private static final Duration LOCAL_INIT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * MCP 服务器档案存储
     */
    private final McpServerStore mcpServerStore;
    /**
     * 复用其传输构建逻辑
     */
    private final McpConnectionService mcpConnectionService;
    /**
     * 本地插件清单：用于发现已启用的 .mcp.json 本地工具
     */
    private final AiPluginCatalogService pluginCatalogService;

    /**
     * 构建并初始化一次 Agent 运行使用的 MCP 运行时（仅连接启用的服务器）。
     *
     * @return MCP 运行时句柄；无可用服务器时返回空管理器（不返回 null）
     */
    public McpRuntime build() {
        return build(null);
    }

    /**
     * 构建并初始化一次 Agent 运行使用的 MCP 运行时。
     *
     * @param onlyIds null=连接全部启用服务器；非空（含空集合）=仅连接所选ID，
     *                选择即本次启用、忽略全局 enabled
     * @return MCP 运行时句柄；无可用服务器时返回空管理器（不返回 null）
     */
    public McpRuntime build(java.util.Collection<String> onlyIds) {
        DefaultMcpManager manager = new DefaultMcpManager();
        List<McpSyncClient> liveClients = new ArrayList<>();

        List<Map<String, Object>> serverContracts;
        try {
            serverContracts = mcpServerStore.listAll();
        } catch (Exception e) {
            log.warn("[AgentMcp] 读取 MCP 服务器列表失败: {}", e.getMessage());
            serverContracts = List.of();
        }

        java.util.Set<String> availableIds = new java.util.HashSet<>();
        for (Map<String, Object> s : serverContracts) {
            String sid = str(s.get("mcp_server_id"));
            if (sid != null) {
                availableIds.add(sid);
            }
        }
        java.util.Set<String> wanted = onlyIds == null ? null
                : new java.util.LinkedHashSet<>(onlyIds);
        if (wanted != null) {
            for (String w : wanted) {
                if (!availableIds.contains(w)) {
                    log.warn("[AgentMcp] 会话选择的 MCP 服务器不存在，忽略: id={}", w);
                }
            }
        }

        for (Map<String, Object> server : serverContracts) {
            String serverId = str(server.get("mcp_server_id"));
            if (wanted == null) {
                Object enabled = server.get("enabled");
                if (enabled != null && !Boolean.parseBoolean(String.valueOf(enabled))) {
                    continue;
                }
            } else if (serverId == null || !wanted.contains(serverId)) {
                continue;
            }
            String name = str(server.get("name"));
            if (!(server.get("transport") instanceof Map<?, ?> t)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> transport = (Map<String, Object>) t;
            // stdio：先把命令解析为可直接启动的绝对路径（Windows 下 npx -> npx.cmd），
            // 命令在 PATH 中不存在时快速跳过，避免进程 spawn 失败后仍死等初始化超时（约30s）
            if (isStdio(transport)) {
                Map<String, Object> ready = prepareStdioTransport(transport);
                if (ready == null) {
                    log.warn("[AgentMcp] stdio 命令未找到，快速跳过 name={}, command={}",
                            name, transport.get("command"));
                    continue;
                }
                transport = ready;
            }
            try {
                var clientTransport = mcpConnectionService.buildTransport(transport);
                if (clientTransport == null) {
                    log.warn("[AgentMcp] 不支持的 MCP 传输，跳过: name={}", name);
                    continue;
                }
                McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(clientTransport)
                        .requestTimeout(TIMEOUT)
                        .initializationTimeout(TIMEOUT)
                        .build();
                client.initialize();
                String registerName = name != null && !name.isBlank()
                        ? name : "mcp-" + liveClients.size();
                manager.register(registerName, new SdkMcpClient(client, registerName));
                liveClients.add(client);
            } catch (Exception e) {
                log.warn("[AgentMcp] 连接 MCP 服务器失败，跳过: name={}, err={}", name, e.getMessage());
            }
        }

        // 追加已启用的本地 .mcp.json 工具（与目录服务器叠加，独立于会话选择）
        int localConnected = registerLocalTools(manager, liveClients);

        log.info("[AgentMcp] 本次 Agent 已连接 MCP 服务器 {} 个（其中本地工具 {} 个），工具 {} 个",
                liveClients.size(), localConnected, manager.listAllTools().size());
        return new McpRuntime(manager, () -> {
            for (McpSyncClient c : liveClients) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // 关闭单个连接失败不影响其余连接
                }
            }
        });
    }

    /**
     * 发现并连接已启用的本地 MCP 插件（{@code .mcp.json}），结果并入管理器。
     *
     * <p>仅处理清单路径为 {@code .mcp.json} 的条目（如 trae-cn 扫描出的本地 MCP）；
     * 普通编辑器扩展（package.json，如 Cline）不是 MCP，直接跳过。含未解析占位符
     * （如 {@code ${connector.gitee.ACCESS_TOKEN}}）的条目缺少凭据，跳过。</p>
     *
     * @param manager     MCP 管理器
     * @param liveClients 已连接客户端集合（用于统一关闭）
     * @return 成功连接的本地工具数
     */
    private int registerLocalTools(DefaultMcpManager manager, List<McpSyncClient> liveClients) {
        int connected = 0;
        List<Map<String, Object>> plugins;
        try {
            plugins = pluginCatalogService.listLocalPlugins();
        } catch (Exception e) {
            log.warn("[AgentMcp] 读取本地插件清单失败: {}", e.getMessage());
            return 0;
        }
        java.util.Set<String> registeredNames = new java.util.HashSet<>();
        for (Map<String, Object> plugin : plugins) {
            Object enabledObj = plugin.get("enabled");
            if (enabledObj == null || !Boolean.parseBoolean(String.valueOf(enabledObj))) {
                continue;
            }
            String mcpJsonPath = pickMcpJsonPath(plugin);
            if (mcpJsonPath == null) {
                continue;
            }
            Map<String, Object> servers;
            try {
                servers = readMcpServers(mcpJsonPath);
            } catch (Exception e) {
                log.warn("[AgentMcp] 解析本地 .mcp.json 失败 path={}, err={}", mcpJsonPath, e.getMessage());
                continue;
            }
            for (Map.Entry<String, Object> entry : servers.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> server = (Map<String, Object>) raw;
                Map<String, Object> transport = toTransport(server);
                if (transport == null) {
                    log.info("[AgentMcp] 本地工具 {} 含未解析占位符或缺少启动配置，跳过", entry.getKey());
                    continue;
                }
                // stdio：解析命令绝对路径（npx -> npx.cmd）；命令缺失快速跳过，不死等初始化超时
                if (isStdio(transport)) {
                    Map<String, Object> ready = prepareStdioTransport(transport);
                    if (ready == null) {
                        log.warn("[AgentMcp] 本地工具 {} 命令未找到，快速跳过 command={}",
                                entry.getKey(), transport.get("command"));
                        continue;
                    }
                    transport = ready;
                }
                try {
                    var clientTransport = mcpConnectionService.buildTransport(transport);
                    if (clientTransport == null) {
                        log.warn("[AgentMcp] 本地工具 {} 传输类型不支持，跳过", entry.getKey());
                        continue;
                    }
                    McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(clientTransport)
                            .requestTimeout(TIMEOUT)
                            .initializationTimeout(LOCAL_INIT_TIMEOUT)
                            .build();
                    client.initialize();
                    String name = uniqueName(entry.getKey(), registeredNames);
                    manager.register(name, new SdkMcpClient(client, name));
                    liveClients.add(client);
                    connected++;
                    log.info("[AgentMcp] 本地工具已连接: name={} from={}", name, mcpJsonPath);
                } catch (Exception e) {
                    log.warn("[AgentMcp] 连接本地工具失败 name={}, err={}", entry.getKey(), e.getMessage());
                }
            }
        }
        return connected;
    }

    /**
     * 选取插件的 .mcp.json 清单路径。
     *
     * @param plugin 插件条目
     * @return .mcp.json 绝对路径；不存在时返回 null
     */
    private static String pickMcpJsonPath(Map<String, Object> plugin) {
        for (String key : new String[]{"manifestPath", "location"}) {
            Object value = plugin.get(key);
            if (value != null) {
                String path = String.valueOf(value);
                if (path.toLowerCase().endsWith(".mcp.json")) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * 读取并解析 .mcp.json 的 mcpServers 节点。
     *
     * @param path .mcp.json 路径
     * @return 服务器名 → 配置 Map；无 mcpServers 时返回空 Map
     * @throws Exception 读取或解析失败
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMcpServers(String path) throws Exception {
        File file = new File(path);
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            Object parsed = tools.jackson.databind.json.JsonMapper.shared().readValue(in, Object.class);
            if (parsed instanceof Map<?, ?> root && root.get("mcpServers") instanceof Map<?, ?> servers) {
                return (Map<String, Object>) servers;
            }
        }
        return Map.of();
    }

    /**
     * 将 .mcp.json 单服务器条目归一化为 {@code buildTransport} 所需传输 Map。
     *
     * @param server 服务器原始配置
     * @return 传输 Map；含未解析占位符或缺少 command/url 时返回 null
     */
    private static Map<String, Object> toTransport(Map<String, Object> server) {
        if (containsUnresolved(server)) {
            return null;
        }
        if (server.get("command") != null) {
            Map<String, Object> transport = new LinkedHashMap<>();
            transport.put("type", "stdio");
            transport.put("command", String.valueOf(server.get("command")));
            if (server.get("args") != null) {
                transport.put("args", server.get("args"));
            }
            if (server.get("env") != null) {
                transport.put("env", server.get("env"));
            }
            return transport;
        }
        if (server.get("url") != null) {
            Map<String, Object> transport = new LinkedHashMap<>();
            Object type = server.get("type");
            transport.put("type", type != null ? String.valueOf(type) : "http");
            transport.put("url", String.valueOf(server.get("url")));
            if (server.get("headers") != null) {
                transport.put("headers", server.get("headers"));
            }
            return transport;
        }
        return null;
    }

    /**
     * 递归判断节点是否含未解析的 {@code ${...}} 占位符。
     *
     * @param node 任意节点
     * @return 含未解析占位符返回 true
     */
    private static boolean containsUnresolved(Object node) {
        if (node instanceof String s) {
            return s.contains("${");
        }
        if (node instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (containsUnresolved(value)) {
                    return true;
                }
            }
        } else if (node instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (containsUnresolved(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 生成不冲突的注册名（重名时追加序号）。
     *
     * @param base 原始名称
     * @param used 已用名称集合
     * @return 唯一名称
     */
    private static String uniqueName(String base, java.util.Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int suffix = 2;
        while (!used.add(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    /**
     * null 安全转字符串。
     *
     * @param o 原始值
     * @return 字符串，null 返回 null
     */
    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    /**
     * 判断传输是否为 stdio。
     *
     * @param transport 传输配置
     * @return 是 stdio 返回 true
     */
    private static boolean isStdio(Map<String, Object> transport) {
        return transport != null
                && "stdio".equalsIgnoreCase(String.valueOf(transport.get("type")));
    }

    /**
     * 规范化 stdio 传输：把命令解析为可直接启动的绝对路径。
     *
     * <p>Windows 下 {@code npx}/{@code npm} 等是 {@code .cmd} 批处理，Java
     * {@code CreateProcess} 不会自动补扩展名，直接 spawn 字面 {@code npx} 会立即失败，
     * 而 MCP {@code initialize()} 不传播该失败、仍死等初始化超时（约30s）。</p>
     *
     * @param transport 原始传输（不修改）
     * @return 命令解析后的传输副本；命令在 PATH 中不存在时返回 null（调用方应快速跳过）
     */
    private static Map<String, Object> prepareStdioTransport(Map<String, Object> transport) {
        String resolved = resolveStdioCommand(str(transport.get("command")));
        if (resolved == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(transport);
        copy.put("command", resolved);
        return copy;
    }

    /**
     * 把 stdio 命令解析为绝对可执行路径。
     *
     * <p>依次：①命令本身是已存在文件直接返回；②按 PATH（Windows 再叠加 PATHEXT
     * 扩展名）搜索。找不到返回 null。</p>
     *
     * @param command 原始命令
     * @return 绝对路径；不存在返回 null
     */
    private static String resolveStdioCommand(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        File direct = new File(command);
        if (direct.isFile()) {
            return direct.getAbsoluteFile().toString();
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            pathEnv = "";
        }
        String[] pathDirs = pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator));

        List<String> extensions = new ArrayList<>();
        if (windows) {
            String pathext = System.getenv("PATHEXT");
            if (pathext == null || pathext.isBlank()) {
                pathext = ".COM;.EXE;.BAT;.CMD";
            }
            for (String e : pathext.split(";")) {
                if (!e.isBlank()) {
                    extensions.add(e.toLowerCase());
                }
            }
        } else {
            extensions.add("");
        }

        for (String dir : pathDirs) {
            if (dir.isBlank()) {
                continue;
            }
            if (new File(dir, command).isFile()) {
                return new File(dir, command).getAbsolutePath();
            }
            for (String ext : extensions) {
                File candidate = new File(dir, command + ext);
                if (candidate.isFile()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * 一次 Agent 运行的 MCP 句柄。
     */
    public static final class McpRuntime {

        /**
         * MCP 管理器
         */
        private final McpManager manager;
        /**
         * 资源关闭动作
         */
        private final Runnable closeAction;

        McpRuntime(McpManager manager, Runnable closeAction) {
            this.manager = manager;
            this.closeAction = closeAction;
        }

        /**
         * 获取 MCP 管理器。
         *
         * @return MCP 管理器
         */
        public McpManager getManager() {
            return manager;
        }

        /**
         * 关闭运行时持有的全部底层连接。
         */
        public void close() {
            try {
                closeAction.run();
            } catch (Exception ignored) {
                // 关闭动作自身异常可忽略
            }
        }
    }

    /**
     * 将官方 SDK {@link McpSyncClient} 适配为项目通用 {@link McpClient}。
     */
    private static final class SdkMcpClient implements McpClient {

        /**
         * 官方同步客户端
         */
        private final McpSyncClient client;
        /**
         * 所属服务器名称（用于工具描述符）
         */
        private final String serverName;
        /**
         * 是否已初始化
         */
        private volatile boolean initialized;

        SdkMcpClient(McpSyncClient client, String serverName) {
            this.client = client;
            this.serverName = serverName;
            this.initialized = true;
        }

        @Override
        public void init() {
            if (!initialized) {
                try {
                    client.initialize();
                    initialized = true;
                } catch (Exception ignored) {
                    // 初始化失败保持未初始化状态
                }
            }
        }

        @Override
        public List<McpToolDescriptor> listTools() {
            List<McpToolDescriptor> result = new ArrayList<>();
            try {
                McpSchema.ListToolsResult toolsResult = client.listTools();
                if (toolsResult != null && toolsResult.tools() != null) {
                    for (McpSchema.Tool tool : toolsResult.tools()) {
                        result.add(new McpToolDescriptor(tool.name(), serverName,
                                tool.description() != null ? tool.description() : "",
                                toSchemaMap(tool.inputSchema())));
                    }
                }
            } catch (Exception ignored) {
                // 列举失败返回已收集的部分（可能为空）
            }
            return result;
        }

        @Override
        public McpToolResult callTool(McpToolCall toolCall) {
            // 硬性墙钟超时：部分远程（streamable HTTP）服务器在异常时不主动结束响应，
            // SDK 的 requestTimeout 可能不生效，这里在外层再兜底，保证 Agent 不会无限等待
            java.util.concurrent.CompletableFuture<McpToolResult> future =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> doCallTool(toolCall));
            try {
                return future.get(CALL_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                future.cancel(true);
                log.warn("[AgentMcp] 工具调用墙钟超时 server={} tool={}", serverName, toolCall.getToolName());
                return McpToolResult.error("MCP 工具调用超时（" + CALL_TIMEOUT.toSeconds() + "s），已跳过");
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return McpToolResult.error(cause.getMessage());
            }
        }

        /**
         * 实际执行一次阻塞式工具调用（由 {@link #callTool} 放到带超时的 future 中运行）。
         */
        private McpToolResult doCallTool(McpToolCall toolCall) {
            try {
                McpSchema.CallToolResult res = client.callTool(
                        new McpSchema.CallToolRequest(toolCall.getToolName(), toolCall.getArguments()));
                boolean isError = Boolean.TRUE.equals(res.isError());
                StringBuilder sb = new StringBuilder();
                if (res.content() != null) {
                    for (McpSchema.Content content : res.content()) {
                        if (content instanceof McpSchema.TextContent tc && tc.text() != null) {
                            sb.append(tc.text());
                        } else {
                            sb.append(String.valueOf(content));
                        }
                    }
                }
                if (isError) {
                    return McpToolResult.error(sb.length() > 0 ? sb.toString() : "MCP 工具返回错误");
                }
                return McpToolResult.success(sb.toString());
            } catch (Exception e) {
                return McpToolResult.error(e.getMessage());
            }
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }

        @Override
        public void close() {
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭失败可忽略
            }
        }

        /**
         * 把官方 JsonSchema 记录转为 JSON Schema Map。
         *
         * @param js 官方 JsonSchema
         * @return 可被 LLM 读取的 schema Map
         */
        private static Map<String, Object> toSchemaMap(McpSchema.JsonSchema js) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (js == null) {
                map.put("type", "object");
                map.put("properties", Map.of());
                return map;
            }
            map.put("type", js.type() != null ? js.type() : "object");
            if (js.properties() != null) {
                map.put("properties", js.properties());
            }
            if (js.required() != null) {
                map.put("required", js.required());
            }
            if (js.additionalProperties() != null) {
                map.put("additionalProperties", js.additionalProperties());
            }
            if (js.defs() != null) {
                map.put("$defs", js.defs());
            }
            return map;
        }
    }
}

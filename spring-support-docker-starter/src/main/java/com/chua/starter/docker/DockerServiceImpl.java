// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.docker;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.log.MdcWrap;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.docker.model.DockerNetworkRequest;
import com.chua.starter.docker.model.DockerVolumeRequest;
import com.chua.starter.docker.service.RegistryService;
import com.chua.starter.docker.service.TemplateService;
import com.chua.starter.docker.spi.RegistryConfig;
import com.chua.starter.server.support.client.DockerChannel;
import com.chua.starter.server.support.client.DockerEngineClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.connection.WindowsSshShell;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Docker 管理服务实现 —— 全部经 SSH 在受控主机上执行 {@code docker} CLI。
 *
 * <p>与主机工作台共用同一套连接设施（{@link ConnectionManager} 缓存 +
 * {@link ServerHostService#resolveCredential} 解析凭据），但协议与页面结构完全独立：
 * 本类只回答「容器 / 镜像 / 网络 / 卷」的问题，不掺 K8s 与隧道。</p>
 *
 * <p><strong>命令安全</strong>：创建容器时不允许透传任意 shell 片段。镜像名、容器名、
 * 端口、环境变量、卷、网络、重启策略逐项按固定格式校验，命令文本按空白切分后
 * 逐段单引号转义，因此即使表单里填了 {@code ; rm -rf /} 也只会被当成一个普通参数。</p>
 *
 * <p><strong>镜像库</strong>：请求带 {@code registryId} 时先按该库 SPI 拼出
 * {@code pullRef}，再用库凭据在会话内执行 {@code docker login}（密码经 heredoc
 * 从标准输入读入，不进入命令行历史）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerServiceImpl implements DockerService {

    /**
     * 输出截断长度（长文本防撑爆响应）
     */
    private static final int OUTPUT_TRUNCATE = 64 * 1024;

    /**
     * 一次性命令超时（秒）
     */
    private static final int EXEC_TIMEOUT_SECONDS = 30;

    /**
     * SSE 会话存活时长（30 分钟）
     */
    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    /**
     * 镜像名白名单（registry host / 命名空间 / tag / digest 均允许）
     */
    private static final String IMAGE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._/:@-]*";

    /**
     * {@code docker login} 的 heredoc 结束标记（密码经标准输入，不进命令行）
     */
    private static final String LOGIN_EOF = "CHUA_DOCKER_LOGIN_EOF";

    /**
     * API 通道时间格式（与 CMD 路径 {@code toIso} 同构的秒级 UTC 时间）
     */
    private static final DateTimeFormatter API_ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.of("UTC"));

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 主机服务（主机查询 + 凭据解析，统一走 server 模块契约，不直连其持久层）
     */
    private final ServerHostService serverHostService;

    /**
     * 连接缓存管理器
     */
    private final ConnectionManager connectionManager;

    /**
     * 镜像库服务
     */
    private final RegistryService registryService;

    /**
     * 模板服务
     */
    private final TemplateService templateService;

    /**
     * 活跃的容器 exec 会话：sessionId → 会话上下文
     */
    private final Map<String, ExecSession> execSessions = new ConcurrentHashMap<>();

    /* ==================== 能力与列表 ==================== */

    @Override
    public ReturnResult<Map<String, Object>> capability(Long hostId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("installed", false);
        out.put("version", null);
        out.put("compose", false);
        out.put("runtime", "docker");
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                try {
                    String version = DockerChannel.client(host).version();
                    out.put("installed", version != null && !version.isBlank());
                    out.put("version", version);
                } catch (Exception e) {
                    log.warn("[docker] API 通道能力探测失败 hostId={}: {}", hostId, e.getMessage());
                }
                return ReturnResult.ok(out);
            }
            ensurePosix(hostId);
            RemoteExecResult probe = execRemote(hostId, "docker version --format '{{.Server.Version}}' 2>/dev/null");
            String version = probe.exitCode() == 0 ? probe.stdout().trim() : "";
            if (version.isEmpty()) {
                return ReturnResult.ok(out);
            }
            RemoteExecResult compose = execRemote(hostId, "docker compose version >/dev/null 2>&1 && echo yes || echo no");
            out.put("installed", true);
            out.put("version", version);
            out.put("compose", "yes".equalsIgnoreCase(compose.stdout().trim()));
            return ReturnResult.ok(out);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.ok(out);
        } catch (Exception e) {
            log.warn("[docker] 能力探测失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.ok(out);
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> containers(Long hostId, Boolean all) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                return ReturnResult.ok(apiContainers(
                        DockerChannel.client(host).listContainersRaw(!Boolean.FALSE.equals(all))));
            }
            ensurePosix(hostId);
            String flag = Boolean.FALSE.equals(all) ? "" : " -a";
            RemoteExecResult result = execRemote(hostId, "docker ps" + flag + " --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                String id = node.path("ID").asText("");
                item.put("id", id);
                item.put("shortId", id.length() > 12 ? id.substring(0, 12) : id);
                item.put("name", firstName(node.path("Names").asText("")));
                item.put("image", node.path("Image").asText(""));
                item.put("state", node.path("State").asText(""));
                item.put("status", node.path("Status").asText(""));
                item.put("ports", node.path("Ports").asText(""));
                item.put("network", node.path("Networks").asText(""));
                item.put("runningFor", node.path("RunningFor").asText(""));
                item.put("createdAt", toIso(node.path("CreatedAt").asText("")));
                list.add(item);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 容器列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取容器列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> containerDetail(Long hostId, String id) {
        if (isBlank(id)) {
            return ReturnResult.error("容器标识不能为空");
        }
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                // Docker API inspect 结构与 docker inspect 同构，直接复用同一解析器
                return ReturnResult.ok(parseInspect(DockerChannel.client(host).inspectContainerRaw(id)));
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker inspect " + shellSafe(id) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            JsonNode root = objectMapper.readTree(result.stdout());
            JsonNode node = root.isArray() && root.size() > 0 ? root.get(0) : root;
            return ReturnResult.ok(parseInspect(node));
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 容器详情失败 hostId={} {}: {}", hostId, id, e.getMessage());
            return ReturnResult.error("读取容器详情失败: " + e.getMessage());
        }
    }

    /* ==================== 生命周期 ==================== */

    @Override
    public ReturnResult<Boolean> containerAction(Long hostId, String id, String action) {
        try {
            if (!List.of("start", "stop", "restart", "pause", "unpause", "kill").contains(action)) {
                return ReturnResult.error("不支持的操作: " + action);
            }
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                DockerChannel.client(host).containerAction(id, action);
                return ReturnResult.ok(true);
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker " + action + " " + shellSafe(id) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 容器操作失败 hostId={} {} {}: {}", hostId, id, action, e.getMessage());
            return ReturnResult.error("操作失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> removeContainer(Long hostId, String id, Boolean force) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                DockerChannel.client(host).removeContainer(id, Boolean.TRUE.equals(force));
                return ReturnResult.ok(true);
            }
            ensurePosix(hostId);
            String flag = Boolean.TRUE.equals(force) ? " -f" : "";
            RemoteExecResult result = execRemote(hostId, "docker rm" + flag + " " + shellSafe(id) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 删除容器失败 hostId={} {}: {}", hostId, id, e.getMessage());
            return ReturnResult.error("删除容器失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter removeContainerStream(Long hostId, String id, Boolean force) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            if (isBlank(id)) {
                sendEvent(emitter, "error", "容器标识不能为空");
                emitter.complete();
                return;
            }
            try {
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    sendEvent(emitter, "step", "卸载容器 " + id);
                    DockerChannel.client(host).removeContainer(id, Boolean.TRUE.equals(force));
                    sendEvent(emitter, "done", id);
                    emitter.complete();
                    return;
                }
            } catch (Exception e) {
                log.warn("[docker] 删除容器失败 hostId={} {}: {}", hostId, id, e.getMessage());
                sendEvent(emitter, "error", "删除容器失败: " + e.getMessage());
                emitter.complete();
                return;
            }
            String flag = Boolean.TRUE.equals(force) ? " -f" : "";
            execStream(hostId, emitter, "卸载容器 " + id,
                    "docker rm" + flag + " " + shellSafe(id) + " 2>&1", id);
        }));
        return emitter;
    }

    @Override
    public SseEmitter runContainer(Long hostId, DockerCreateRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            try {
                ensurePosix(hostId);
                if (request == null) {
                    sendEvent(emitter, "error", "创建参数不能为空");
                    emitter.complete();
                    return;
                }
                templateService.fill(request);
                if (isBlank(request.getImage())) {
                    sendEvent(emitter, "error", "镜像不能为空");
                    emitter.complete();
                    return;
                }
                String image = request.getImage().trim();
                if (!image.matches(IMAGE_PATTERN)) {
                    sendEvent(emitter, "error", "镜像名不合法: " + image);
                    emitter.complete();
                    return;
                }
                String pullRef = registryService.pullRef(request.getRegistryId(), image);
                String run = buildRunCommand(request, pullRef);
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    // buildRunCommand 已完成全量字段校验（两通道同一套规则），API 通道另拼 JSON spec
                    runContainerViaApi(DockerChannel.client(host), emitter, request, pullRef);
                    return;
                }
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    sendEvent(emitter, "error", resolved.getMsg());
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;

                if (!Boolean.FALSE.equals(request.getPull())) {
                    if (!loginRegistry(handle, emitter, request.getRegistryId())) {
                        emitter.complete();
                        return;
                    }
                    sendEvent(emitter, "step", "拉取镜像 " + pullRef);
                    int pullCode = handle.client().exec().command("docker pull " + shQuote(pullRef) + " 2>&1")
                            .stream(line -> sendEvent(emitter, "log", line)).exitCode();
                    if (pullCode != 0) {
                        sendEvent(emitter, "error", "拉取镜像失败（退出码 " + pullCode + "），请确认镜像名与网络");
                        emitter.complete();
                        return;
                    }
                }

                sendEvent(emitter, "step", "创建并启动容器");
                String[] lastLine = new String[1];
                int runCode = handle.client().exec().command(run + " 2>&1").stream(line -> {
                    lastLine[0] = line;
                    sendEvent(emitter, "log", line);
                }).exitCode();
                if (runCode != 0) {
                    sendEvent(emitter, "error", "创建容器失败（退出码 " + runCode + "）: " + safe(lastLine[0]));
                    emitter.complete();
                    return;
                }
                sendEvent(emitter, "done", safe(lastLine[0]).trim());
                emitter.complete();
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (UnsupportedOperationException e) {
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.warn("[docker] 创建容器失败 hostId={}: {}", hostId, e.getMessage());
                sendEvent(emitter, "error", "创建容器失败: " + e.getMessage());
                emitter.complete();
            } finally {
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        }));
        return emitter;
    }

    /* ==================== 日志 ==================== */

    @Override
    public ReturnResult<String> containerLogs(Long hostId, String id, Integer tail) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                int lines = tail == null || tail < 1 ? 200 : tail;
                return ReturnResult.ok(truncate(DockerChannel.client(host).containerLogs(id, lines)));
            }
            ensurePosix(hostId);
            int lines = tail == null || tail < 1 ? 200 : tail;
            RemoteExecResult result = execRemote(hostId, "docker logs --tail " + lines + " " + shellSafe(id) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return ReturnResult.ok(truncate(result.stdout()));
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 容器日志失败 hostId={} {}: {}", hostId, id, e.getMessage());
            return ReturnResult.error("读取日志失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter streamContainerLogs(Long hostId, String id, Integer tail) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            try {
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    // API 通道：经 Docker Engine API 多路复用日志流跟随（DockerChannel 内自发 session 事件并 complete）
                    DockerChannel.streamLogsViaApi(DockerChannel.client(host), emitter, sessionId, id, tail,
                            msg -> log.warn("[docker] 日志流失败 hostId={} {}: {}", hostId, id, msg));
                    return;
                }
                ensurePosix(hostId);
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    sendEvent(emitter, "error", resolved.getMsg());
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;
                int lines = tail == null || tail < 1 ? 100 : tail;
                String cmd = "docker logs -f --tail " + lines + " " + shellSafe(id) + " 2>&1";

                SshClient.StreamHandle[] streamHandleRef = new SshClient.StreamHandle[1];
                SshClient.StreamHandle streamHandle = handle.client().exec().command(cmd).follow(line -> {
                    if (!sendEvent(emitter, "log", line)) {
                        try {
                            streamHandleRef[0].close();
                        } catch (Exception ignored) {
                            // 客户端已断开，关闭失败无需处理
                        }
                    }
                });
                streamHandleRef[0] = streamHandle;

                Runnable cleanup = () -> {
                    try {
                        streamHandle.close();
                    } catch (Exception ignored) {
                        // 关闭通道失败无需处理
                    }
                    if (handleRef[0] != null) {
                        connectionManager.release(hostId);
                    }
                };
                emitter.onCompletion(cleanup);
                emitter.onTimeout(cleanup);
                emitter.onError(e -> cleanup.run());
                sendEvent(emitter, "session", sessionId);
            } catch (Exception e) {
                log.warn("[docker] 日志流失败 hostId={} {}: {}", hostId, id, e.getMessage());
                sendEvent(emitter, "error", "日志流启动失败: " + e.getMessage());
                emitter.complete();
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        }));
        return emitter;
    }

    /* ==================== 交互式终端 ==================== */

    @Override
    public SseEmitter execContainer(Long hostId, String id, String command) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            SshClient.TerminalOperation[] terminalRef = new SshClient.TerminalOperation[1];
            try {
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    // API 通道不走 SSH，无 PTY（hijack+tty），交互式终端仅 CMD 通道支持
                    sendEvent(emitter, "error", DockerChannel.EXEC_UNSUPPORTED_MESSAGE);
                    emitter.complete();
                    return;
                }
                ensurePosix(hostId);
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    sendEvent(emitter, "error", resolved.getMsg());
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;
                String shell = isBlank(command) ? "/bin/sh" : command.trim();
                String execCmd = "docker exec -it " + shellSafe(id) + " " + shell;

                SshClient.TerminalOperation terminal = handle.client().terminal()
                        .width(120).height(30)
                        .onOutput(data -> {
                            if (!sendEvent(emitter, "output", data)) {
                                SshClient.TerminalOperation t = terminalRef[0];
                                if (t != null) {
                                    t.close();
                                }
                            }
                        })
                        .onClose(() -> {
                            sendEvent(emitter, "closed", "Session closed");
                            emitter.complete();
                            if (handleRef[0] != null) {
                                connectionManager.release(hostId);
                            }
                            execSessions.remove(sessionId);
                        })
                        .connect();
                terminalRef[0] = terminal;
                terminal.send(execCmd);
                execSessions.put(sessionId, new ExecSession(sessionId, terminal, handle, hostId));

                Runnable cleanup = () -> closeExecSession(sessionId);
                emitter.onCompletion(cleanup);
                emitter.onTimeout(cleanup);
                emitter.onError(e -> cleanup.run());
                sendEvent(emitter, "session", sessionId);
            } catch (Exception e) {
                log.warn("[docker] 进入容器失败 hostId={} {}: {}", hostId, id, e.getMessage());
                sendEvent(emitter, "error", "进入容器失败: " + e.getMessage());
                emitter.complete();
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        }));
        return emitter;
    }

    @Override
    public ReturnResult<Void> execInput(Long hostId, String id, String sessionId, String input) {
        ExecSession session = execSessions.get(sessionId);
        if (session == null) {
            return ReturnResult.error("会话不存在或已关闭: " + sessionId);
        }
        try {
            session.terminal.sendRaw(input);
            return ReturnResult.success();
        } catch (Exception e) {
            return ReturnResult.error("发送输入失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> execClose(Long hostId, String id, String sessionId) {
        if (execSessions.get(sessionId) == null) {
            return ReturnResult.error("会话不存在或已关闭: " + sessionId);
        }
        closeExecSession(sessionId);
        return ReturnResult.success();
    }

    /**
     * 关闭交互会话并释放连接
     *
     * @param sessionId 会话标识
     */
    private void closeExecSession(String sessionId) {
        ExecSession session = execSessions.remove(sessionId);
        if (session != null) {
            try {
                session.terminal.close();
            } catch (Exception e) {
                log.debug("[docker] 关闭终端异常: {}", e.getMessage());
            }
            connectionManager.release(session.hostId);
        }
    }

    /* ==================== 镜像 / 网络 / 卷 ==================== */

    @Override
    public ReturnResult<List<Map<String, Object>>> images(Long hostId) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                return ReturnResult.ok(apiImages(DockerChannel.client(host).listImagesRaw()));
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker images --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                String id = node.path("ID").asText("");
                item.put("id", id);
                item.put("shortId", id.length() > 19 ? id.substring(7, 19) : id);
                item.put("repository", node.path("Repository").asText(""));
                item.put("tag", node.path("Tag").asText(""));
                item.put("size", node.path("Size").asText(""));
                item.put("createdAt", toIso(node.path("CreatedAt").asText("")));
                list.add(item);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 镜像列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取镜像列表失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter pullImage(Long hostId, String image, String registryId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            try {
                if (isBlank(image) || !image.trim().matches(IMAGE_PATTERN)) {
                    sendEvent(emitter, "error", "镜像名不合法: " + image);
                    emitter.complete();
                    return;
                }
                String pullRef = registryService.pullRef(registryId, image.trim());
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    try {
                        if (pullImageViaApi(DockerChannel.client(host), emitter, pullRef, registryId)) {
                            sendEvent(emitter, "done", pullRef);
                        }
                    } catch (Exception e) {
                        log.warn("[docker] 拉取镜像失败 hostId={} {}: {}", hostId, image, e.getMessage());
                        sendEvent(emitter, "error", "拉取失败: " + e.getMessage());
                    }
                    emitter.complete();
                    return;
                }
                ensurePosix(hostId);
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    sendEvent(emitter, "error", resolved.getMsg());
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;
                if (!loginRegistry(handle, emitter, registryId)) {
                    emitter.complete();
                    return;
                }
                sendEvent(emitter, "step", "拉取 " + pullRef);
                int code = handle.client().exec().command("docker pull " + shQuote(pullRef) + " 2>&1")
                        .stream(line -> sendEvent(emitter, "log", line)).exitCode();
                if (code == 0) {
                    sendEvent(emitter, "done", pullRef);
                } else {
                    sendEvent(emitter, "error", "拉取失败（退出码 " + code + "）");
                }
                emitter.complete();
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (UnsupportedOperationException e) {
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.warn("[docker] 拉取镜像失败 hostId={} {}: {}", hostId, image, e.getMessage());
                sendEvent(emitter, "error", "拉取镜像失败: " + e.getMessage());
                emitter.complete();
            } finally {
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        }));
        return emitter;
    }

    @Override
    public ReturnResult<Boolean> removeImage(Long hostId, String imageId, Boolean force) {
        try {
            if (isBlank(imageId)) {
                return ReturnResult.error("镜像标识不能为空");
            }
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                DockerChannel.client(host).removeImage(imageId.trim(), Boolean.TRUE.equals(force));
                return ReturnResult.ok(true);
            }
            ensurePosix(hostId);
            String flag = Boolean.TRUE.equals(force) ? " -f" : "";
            RemoteExecResult result = execRemote(hostId, "docker rmi" + flag + " " + shQuote(imageId.trim()) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 删除镜像失败 hostId={} {}: {}", hostId, imageId, e.getMessage());
            return ReturnResult.error("删除镜像失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter removeImageStream(Long hostId, String imageId, Boolean force) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(MdcWrap.wrap(() -> {
            if (isBlank(imageId)) {
                sendEvent(emitter, "error", "镜像标识不能为空");
                emitter.complete();
                return;
            }
            try {
                MsServerHost host = requireHost(hostId);
                if (DockerChannel.isApiMode(host)) {
                    sendEvent(emitter, "step", "卸载镜像 " + imageId);
                    DockerChannel.client(host).removeImage(imageId.trim(), Boolean.TRUE.equals(force));
                    sendEvent(emitter, "done", imageId);
                    emitter.complete();
                    return;
                }
            } catch (Exception e) {
                log.warn("[docker] 删除镜像失败 hostId={} {}: {}", hostId, imageId, e.getMessage());
                sendEvent(emitter, "error", "删除镜像失败: " + e.getMessage());
                emitter.complete();
                return;
            }
            String flag = Boolean.TRUE.equals(force) ? " -f" : "";
            execStream(hostId, emitter, "卸载镜像 " + imageId,
                    "docker rmi" + flag + " " + shQuote(imageId.trim()) + " 2>&1", imageId);
        }));
        return emitter;
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> networks(Long hostId) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                return ReturnResult.ok(apiNetworks(DockerChannel.client(host).listNetworksRaw()));
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker network ls --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", node.path("ID").asText(""));
                item.put("name", node.path("Name").asText(""));
                item.put("driver", node.path("Driver").asText(""));
                item.put("scope", node.path("Scope").asText(""));
                list.add(item);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 网络列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取网络列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> volumes(Long hostId) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                return ReturnResult.ok(apiVolumes(DockerChannel.client(host).listVolumesRaw()));
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker volume ls --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", node.path("Name").asText(""));
                item.put("driver", node.path("Driver").asText(""));
                item.put("mountpoint", node.path("Mountpoint").asText(""));
                list.add(item);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 卷列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取卷列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> prune(Long hostId) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                return ReturnResult.ok(DockerChannel.client(host).prune());
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker system prune -af --volumes 2>&1");
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("output", truncate(result.stdout()));
            return ReturnResult.ok(out);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 清理失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 创建网络（{@code docker network create}）。
     *
     * @param hostId  主机标识
     * @param request 网络创建请求
     * @return 网络视图
     */
    @Override
    public ReturnResult<Map<String, Object>> createNetwork(Long hostId, DockerNetworkRequest request) {
        try {
            String name = request == null || isBlank(request.getName()) ? "" : request.getName().trim();
            if (isBlank(name)) {
                return ReturnResult.error("请填写网络名称");
            }
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                return ReturnResult.error("网络名只允许字母、数字、下划线、点与连字符: " + name);
            }
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                String driver = request.getDriver() == null ? "" : request.getDriver().trim();
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("Name", name);
                spec.put("Driver", driver.isEmpty() ? "bridge" : driver);
                Map<String, Object> options = optionMap(request.getOptions());
                if (!options.isEmpty()) {
                    spec.put("Options", options);
                }
                DockerChannel.client(host).createNetwork(spec);
                return apiNetworkInspect(DockerChannel.client(host), name);
            }
            ensurePosix(hostId);
            StringBuilder cmd = new StringBuilder("docker network create --driver bridge ")
                    .append(shQuote(name));
            String driver = request.getDriver() == null ? "" : request.getDriver().trim();
            if (!driver.isEmpty()) {
                cmd.append(" --driver ").append(driver);
            }
            if (request.getOptions() != null) {
                for (String option : request.getOptions()) {
                    cmd.append(" --opt ").append(shQuote(option));
                }
            }
            RemoteExecResult result = execRemote(hostId, cmd.toString());
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return parseNetworkInspect(hostId, name);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 创建网络失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("创建网络失败: " + e.getMessage());
        }
    }

    /**
     * 删除网络（{@code docker network rm}），id 或名称均可识别。
     *
     * @param hostId 主机标识
     * @param id     网络标识
     * @return 是否成功
     */
    @Override
    public ReturnResult<Boolean> removeNetwork(Long hostId, String id) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                DockerChannel.client(host).removeNetwork(id);
                return ReturnResult.ok(true);
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker network rm " + shellSafe(id));
            return result.exitCode() == 0 ? ReturnResult.ok(true) : dockerFail(result);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 删除网络失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("删除网络失败: " + e.getMessage());
        }
    }

    /**
     * 创建卷（{@code docker volume create}）。
     *
     * @param hostId  主机标识
     * @param request 卷创建请求
     * @return 卷视图
     */
    @Override
    public ReturnResult<Map<String, Object>> createVolume(Long hostId, DockerVolumeRequest request) {
        try {
            String name = request == null || isBlank(request.getName()) ? "" : request.getName().trim();
            if (isBlank(name)) {
                return ReturnResult.error("请填写卷名称");
            }
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                return ReturnResult.error("卷名只允许字母、数字、下划线、点与连字符: " + name);
            }
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                String driver = request.getDriver() == null ? "" : request.getDriver().trim();
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("Name", name);
                spec.put("Driver", driver.isEmpty() ? "local" : driver);
                Map<String, Object> options = optionMap(request.getOptions());
                if (!options.isEmpty()) {
                    spec.put("Options", options);
                }
                DockerChannel.client(host).createVolume(spec);
                return apiVolumeInspect(DockerChannel.client(host), name);
            }
            ensurePosix(hostId);
            StringBuilder cmd = new StringBuilder("docker volume create --driver local --name ")
                    .append(shQuote(name));
            String driver = request.getDriver() == null ? "" : request.getDriver().trim();
            if (!driver.isEmpty()) {
                cmd.append(" --driver ").append(driver);
            }
            if (request.getOptions() != null) {
                for (String option : request.getOptions()) {
                    cmd.append(" --opt ").append(shQuote(option));
                }
            }
            RemoteExecResult result = execRemote(hostId, cmd.toString());
            if (result.exitCode() != 0) {
                return dockerFail(result);
            }
            return parseVolumeInspect(hostId, name);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 创建卷失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("创建卷失败: " + e.getMessage());
        }
    }

    /**
     * 删除卷（{@code docker volume rm}）。
     *
     * @param hostId 主机标识
     * @param name   卷名称
     * @return 是否成功
     */
    @Override
    public ReturnResult<Boolean> removeVolume(Long hostId, String name) {
        try {
            MsServerHost host = requireHost(hostId);
            if (DockerChannel.isApiMode(host)) {
                DockerChannel.client(host).removeVolume(name);
                return ReturnResult.ok(true);
            }
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker volume rm " + shellSafe(name));
            return result.exitCode() == 0 ? ReturnResult.ok(true) : dockerFail(result);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[docker] 删除卷失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("删除卷失败: " + e.getMessage());
        }
    }

    /* ==================== Docker API 通道辅助 ==================== */

    /**
     * 容器列表：Docker API {@code /containers/json} → {@code docker ps --format json} 同构视图。
     *
     * @param root API 原始响应
     * @return 容器视图列表
     */
    private List<Map<String, Object>> apiContainers(JsonNode root) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return list;
        }
        for (JsonNode node : root) {
            Map<String, Object> item = new LinkedHashMap<>();
            String id = node.path("Id").asText("");
            item.put("id", id);
            item.put("shortId", id.length() > 12 ? id.substring(0, 12) : id);
            JsonNode names = node.path("Names");
            String name = names.isArray() && names.size() > 0 ? names.get(0).asText("") : "";
            item.put("name", stripSlash(name));
            item.put("image", node.path("Image").asText(""));
            item.put("state", node.path("State").asText(""));
            item.put("status", node.path("Status").asText(""));
            item.put("ports", apiPortSummary(node.path("Ports")));
            item.put("network", String.join(",", fieldNames(node.path("NetworkSettings").path("Networks"))));
            long created = node.path("Created").asLong(0L);
            item.put("runningFor", relativeTime(created));
            item.put("createdAt", epochToIso(created));
            list.add(item);
        }
        return list;
    }

    /**
     * 镜像列表：Docker API {@code /images/json} → {@code docker images --format json} 同构视图。
     *
     * @param root API 原始响应
     * @return 镜像视图列表
     */
    private List<Map<String, Object>> apiImages(JsonNode root) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return list;
        }
        for (JsonNode node : root) {
            Map<String, Object> item = new LinkedHashMap<>();
            String id = node.path("Id").asText("");
            item.put("id", id);
            item.put("shortId", id.length() > 19 ? id.substring(7, 19) : id);
            String repository = "";
            String tag = "";
            JsonNode repoTags = node.path("RepoTags");
            if (repoTags.isArray() && repoTags.size() > 0) {
                String repoTag = repoTags.get(0).asText("");
                int idx = repoTag.lastIndexOf(':');
                if (idx > 0) {
                    repository = repoTag.substring(0, idx);
                    tag = repoTag.substring(idx + 1);
                } else {
                    repository = repoTag;
                }
            }
            item.put("repository", repository);
            item.put("tag", tag);
            item.put("size", humanSize(node.path("Size").asLong(0L)));
            item.put("createdAt", epochToIso(node.path("Created").asLong(0L)));
            list.add(item);
        }
        return list;
    }

    /**
     * 网络列表：Docker API {@code /networks} → {@code docker network ls --format json} 同构视图。
     *
     * @param root API 原始响应
     * @return 网络视图列表
     */
    private List<Map<String, Object>> apiNetworks(JsonNode root) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return list;
        }
        for (JsonNode node : root) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.path("Id").asText(""));
            item.put("name", node.path("Name").asText(""));
            item.put("driver", node.path("Driver").asText(""));
            item.put("scope", node.path("Scope").asText(""));
            list.add(item);
        }
        return list;
    }

    /**
     * 卷列表：Docker API {@code /volumes} → {@code docker volume ls --format json} 同构视图。
     *
     * @param root API 原始响应
     * @return 卷视图列表
     */
    private List<Map<String, Object>> apiVolumes(JsonNode root) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return list;
        }
        for (JsonNode node : root.path("Volumes")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", node.path("Name").asText(""));
            item.put("driver", node.path("Driver").asText(""));
            item.put("mountpoint", node.path("Mountpoint").asText(""));
            list.add(item);
        }
        return list;
    }

    /**
     * 网络创建结果查询（API 通道，对应 CMD 路径的 {@link #parseNetworkInspect}）。
     *
     * @param client Docker API 客户端
     * @param name   网络名
     * @return 网络视图
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private ReturnResult<Map<String, Object>> apiNetworkInspect(DockerEngineClient client, String name)
            throws IOException, InterruptedException {
        JsonNode node = client.inspectNetworkRaw(name);
        if (node == null || !node.isObject()) {
            return ReturnResult.error("网络创建结果无法解析");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", node.path("Id").asText(""));
        out.put("name", node.path("Name").asText(""));
        out.put("driver", node.path("Driver").asText(""));
        out.put("scope", node.path("Scope").asText(""));
        out.put("labels", toLabelMap(node.path("Labels")));
        return ReturnResult.ok(out);
    }

    /**
     * 卷创建结果查询（API 通道，对应 CMD 路径的 {@link #parseVolumeInspect}）。
     *
     * @param client Docker API 客户端
     * @param name   卷名
     * @return 卷视图
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private ReturnResult<Map<String, Object>> apiVolumeInspect(DockerEngineClient client, String name)
            throws IOException, InterruptedException {
        JsonNode node = client.inspectVolumeRaw(name);
        if (node == null || !node.isObject()) {
            return ReturnResult.error("卷创建结果无法解析");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", node.path("Name").asText(""));
        out.put("driver", node.path("Driver").asText(""));
        out.put("mountpoint", node.path("Mountpoint").asText(""));
        out.put("labels", toLabelMap(node.path("Labels")));
        return ReturnResult.ok(out);
    }

    /**
     * API 通道创建并启动容器：pull（可选）→ create → start → done(容器 ID)。
     * 事件协议与 CMD 路径一致（step / log / error / done）。
     *
     * @param client  Docker API 客户端
     * @param emitter SSE 发射器
     * @param request 创建请求（已经 {@code buildRunCommand} 全量校验）
     * @param pullRef 已解析的镜像引用
     */
    private void runContainerViaApi(DockerEngineClient client, SseEmitter emitter,
                                    DockerCreateRequest request, String pullRef) {
        try {
            if (!Boolean.FALSE.equals(request.getPull())) {
                sendEvent(emitter, "step", "拉取镜像 " + pullRef);
                if (!pullImageViaApi(client, emitter, pullRef, request.getRegistryId())) {
                    emitter.complete();
                    return;
                }
            }
            sendEvent(emitter, "step", "创建并启动容器");
            String containerId = client.createContainer(safe(request.getName()).trim(), buildRunSpec(request, pullRef));
            client.startContainer(containerId);
            sendEvent(emitter, "done", containerId);
        } catch (Exception e) {
            log.warn("[docker] 创建容器失败(API): {}", e.getMessage());
            sendEvent(emitter, "error", "创建容器失败: " + e.getMessage());
        }
        emitter.complete();
    }

    /**
     * API 通道拉取镜像：逐行消费 {@code POST /images/create} 的 JSON 进度流。
     * 事件协议与 CMD 路径一致（log / error），成功返回 true。
     *
     * @param client     Docker API 客户端
     * @param emitter    SSE 发射器
     * @param pullRef    镜像引用（可含 tag / registry host）
     * @param registryId 镜像库标识，可空
     * @return 是否拉取成功
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private boolean pullImageViaApi(DockerEngineClient client, SseEmitter emitter, String pullRef, String registryId)
            throws IOException, InterruptedException {
        String fromImage = pullRef;
        String tag = null;
        if (!pullRef.contains("@")) {
            int idx = pullRef.lastIndexOf(':');
            if (idx > 0 && !pullRef.substring(idx + 1).contains("/")) {
                fromImage = pullRef.substring(0, idx);
                tag = pullRef.substring(idx + 1);
            } else {
                tag = "latest";
            }
        }
        try (InputStream stream = client.pullImageStream(fromImage, tag, registryAuthHeader(registryId))) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(line);
                } catch (Exception e) {
                    continue;
                }
                JsonNode errorDetail = node.path("errorDetail");
                if (errorDetail.isObject() && !errorDetail.path("message").asText("").isEmpty()) {
                    sendEvent(emitter, "error", "拉取失败: " + errorDetail.path("message").asText(""));
                    return false;
                }
                if (!node.path("error").asText("").isEmpty()) {
                    sendEvent(emitter, "error", "拉取失败: " + node.path("error").asText(""));
                    return false;
                }
                String status = node.path("status").asText("");
                String progress = node.path("progress").asText("");
                String text = status.isEmpty() ? progress : (progress.isEmpty() ? status : status + " " + progress);
                if (!text.isBlank()) {
                    sendEvent(emitter, "log", text);
                }
            }
        }
        return true;
    }

    /**
     * 组装 {@code X-Registry-Auth} 头（base64 凭据 JSON），未配置账号密码时返回 null。
     *
     * @param registryId 镜像库标识
     * @return base64 凭据，或 null
     */
    private String registryAuthHeader(String registryId) {
        if (isBlank(registryId)) {
            return null;
        }
        try {
            RegistryConfig config = registryService.config(registryId);
            if (isBlank(config.getUsername()) || isBlank(config.getPassword())) {
                return null;
            }
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("username", config.getUsername().trim());
            auth.put("password", config.getPassword());
            auth.put("serveraddress", registryService.loginHost(config));
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(auth));
        } catch (Exception e) {
            log.warn("[docker] 组装镜像库凭据失败 id={}: {}", registryId, e.getMessage());
            return null;
        }
    }

    /**
     * 把创建表单拼成 Docker API ContainerCreate 请求体
     * （字段口径与 {@code buildRunCommand} 一一对应；格式校验已由前者完成）。
     *
     * @param request 创建请求
     * @param image   已解析的镜像引用
     * @return ContainerCreate 请求体
     */
    private Map<String, Object> buildRunSpec(DockerCreateRequest request, String image) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("Image", image);
        if (!isBlank(request.getCommand())) {
            List<String> cmd = new ArrayList<>();
            for (String part : request.getCommand().trim().split("\\s+")) {
                cmd.add(part);
            }
            spec.put("Cmd", cmd);
        }
        if (request.getEnv() != null) {
            List<String> env = new ArrayList<>();
            for (String item : request.getEnv()) {
                if (!isBlank(item)) {
                    env.add(item.trim());
                }
            }
            if (!env.isEmpty()) {
                spec.put("Env", env);
            }
        }
        Map<String, Object> hostConfig = new LinkedHashMap<>();
        if (request.getVolumes() != null) {
            List<String> binds = new ArrayList<>();
            for (String item : request.getVolumes()) {
                if (!isBlank(item)) {
                    binds.add(item.trim());
                }
            }
            if (!binds.isEmpty()) {
                hostConfig.put("Binds", binds);
            }
        }
        if (!isBlank(request.getNetwork())) {
            hostConfig.put("NetworkMode", request.getNetwork().trim());
        }
        if (!isBlank(request.getRestartPolicy())) {
            Map<String, Object> restartPolicy = new LinkedHashMap<>();
            restartPolicy.put("Name", request.getRestartPolicy().trim());
            hostConfig.put("RestartPolicy", restartPolicy);
        }
        if (request.getPorts() != null) {
            Map<String, Object> exposed = new LinkedHashMap<>();
            Map<String, Object> bindings = new LinkedHashMap<>();
            for (String item : request.getPorts()) {
                if (isBlank(item)) {
                    continue;
                }
                String value = item.trim();
                String proto = "tcp";
                int slash = value.indexOf('/');
                if (slash > 0) {
                    proto = value.substring(slash + 1);
                    value = value.substring(0, slash);
                }
                String[] pair = value.split(":");
                if (pair.length != 2) {
                    continue;
                }
                String key = pair[1] + "/" + proto;
                exposed.put(key, new LinkedHashMap<>());
                Map<String, Object> binding = new LinkedHashMap<>();
                binding.put("HostIp", "");
                binding.put("HostPort", pair[0]);
                List<Map<String, Object>> list = new ArrayList<>();
                list.add(binding);
                bindings.put(key, list);
            }
            if (!exposed.isEmpty()) {
                spec.put("ExposedPorts", exposed);
                hostConfig.put("PortBindings", bindings);
            }
        }
        if (!hostConfig.isEmpty()) {
            spec.put("HostConfig", hostConfig);
        }
        return spec;
    }

    /**
     * 把 {@code --opt key=value} 参数列表转成 API 的 Options 映射。
     *
     * @param options 参数列表
     * @return 选项映射（无 '=' 的项跳过）
     */
    private Map<String, Object> optionMap(List<String> options) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (options == null) {
            return out;
        }
        for (String option : options) {
            if (isBlank(option)) {
                continue;
            }
            int idx = option.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            out.put(option.substring(0, idx).trim(), option.substring(idx + 1).trim());
        }
        return out;
    }

    /**
     * 拼接容器端口摘要（形如 {@code 0.0.0.0:8080->80/tcp}，与 docker ps 输出同构）。
     *
     * @param ports API Ports 数组
     * @return 摘要串
     */
    private String apiPortSummary(JsonNode ports) {
        if (ports == null || !ports.isArray() || ports.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode port : ports) {
            String ip = port.path("IP").asText("");
            long publicPort = port.path("PublicPort").asLong(0L);
            long privatePort = port.path("PrivatePort").asLong(0L);
            String type = port.path("Type").asText("tcp");
            if (publicPort > 0) {
                parts.add(ip + ":" + publicPort + "->" + privatePort + "/" + type);
            } else {
                parts.add(privatePort + "/" + type);
            }
        }
        return String.join(", ", parts);
    }

    /**
     * 取 JSON 对象的全部键名（保持原顺序）。
     *
     * @param objectNode 对象节点
     * @return 键名列表
     */
    private List<String> fieldNames(JsonNode objectNode) {
        List<String> names = new ArrayList<>();
        if (objectNode != null && objectNode.isObject()) {
            objectNode.fields().forEachRemaining(entry -> names.add(entry.getKey()));
        }
        return names;
    }

    /**
     * epoch 秒 → docker 风格相对时间（如 {@code 3 hours ago}）。
     *
     * @param epochSeconds epoch 秒
     * @return 相对时间
     */
    private String relativeTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "";
        }
        long seconds = Math.max(0, Instant.now().getEpochSecond() - epochSeconds);
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second ago" : " seconds ago");
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    /**
     * epoch 秒 → 与 CMD 路径 {@code toIso} 同构的秒级 UTC 时间（{@code yyyy-MM-dd'T'HH:mm:ss}）。
     *
     * @param epochSeconds epoch 秒
     * @return ISO 时间
     */
    private String epochToIso(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "";
        }
        return API_ISO_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    /**
     * 字节数 → docker 风格人类可读大小（如 {@code 45.2MB}）。
     *
     * @param bytes 字节数
     * @return 大小串
     */
    private String humanSize(long bytes) {
        if (bytes <= 0) {
            return "0B";
        }
        String[] units = {"B", "kB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        if (unit == 0) {
            return (long) value + units[0];
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, units[unit]);
    }

    /**
     * 解析 `docker inspect --format '{{json .}}'` 返回的对象（fragment），
     * 同时允许多元素的列表（此时取第一个）。
     *
     * @param stdout inspect 输出
     * @param what 实体名（用于错误信息）
     * @return 实体节点，无法解析时返回 MissingNode
     * @throws Exception 读到非法 JSON
     */
    private JsonNode parseSingleObject(String stdout, String what) throws Exception {
        JsonNode node = objectMapper.readTree(stdout);
        if (node.isArray() && node.size() > 0) {
            return node.get(0);
        }
        if (node.isObject()) {
            return node;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    /**
     * 从 `docker network inspect` 解析网络视图。
     *
     * @param hostId 主机标识
     * @param name   网络名称
     * @return 网络视图
     * @throws Exception 执行或解析失败
     */
    private ReturnResult<Map<String, Object>> parseNetworkInspect(Long hostId, String name) throws Exception {
        RemoteExecResult result = execRemote(hostId, "docker network inspect " + shQuote(name)
                + " --format '{{json .}}'");
        if (result.exitCode() != 0) {
            return ReturnResult.error("查询网络创建结果失败: " + truncate(result.stdout() + result.stderr()));
        }
        JsonNode node = parseSingleObject(result.stdout(), "网络");
        if (node.isMissingNode()) {
            return ReturnResult.error("网络创建结果无法解析");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", node.path("Id").asText(""));
        out.put("name", node.path("Name").asText(""));
        out.put("driver", node.path("Driver").asText(""));
        out.put("scope", node.path("Scope").asText(""));
        out.put("labels", toLabelMap(node.path("Labels")));
        return ReturnResult.ok(out);
    }

    /**
     * 从 `docker volume inspect` 解析卷视图。
     *
     * @param hostId 主机标识
     * @param name   卷名称
     * @return 卷视图
     * @throws Exception 执行或解析失败
     */
    private ReturnResult<Map<String, Object>> parseVolumeInspect(Long hostId, String name) throws Exception {
        RemoteExecResult result = execRemote(hostId, "docker volume inspect " + shQuote(name)
                + " --format '{{json .}}'");
        if (result.exitCode() != 0) {
            return ReturnResult.error("查询卷创建结果失败: " + truncate(result.stdout() + result.stderr()));
        }
        JsonNode node = parseSingleObject(result.stdout(), "卷");
        if (node.isMissingNode()) {
            return ReturnResult.error("卷创建结果无法解析");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", node.path("Name").asText(""));
        out.put("driver", node.path("Driver").asText(""));
        out.put("mountpoint", node.path("Mountpoint").asText(""));
        out.put("labels", toLabelMap(node.path("Labels")));
        return ReturnResult.ok(out);
    }

    /**
     * 把 inspect 返回的 label 对象转成文本映射
     *
     * @param node 可以是 missing node
     * @return 标签映射
     */
    private Map<String, Object> toLabelMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return out;
        }
        node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
        return out;
    }

    /* ==================== 私有工具 ==================== */

    /**
     * 在已 borrow 的会话里执行镜像库登录（仅当库配置了账号与密码）。
     *
     * <p>密码经 heredoc 从标准输入传给 {@code docker login --password-stdin}，
     * 不出现在命令行参数里，也不会进入远端 shell 历史。</p>
     *
     * @param handle     已借出的 SSH 句柄（复用调用方连接，避免二次占用连接池）
     * @param emitter    SSE 发射器
     * @param registryId 镜像库标识，可空
     * @return true 表示可继续（未配置凭据或登录成功）
     */
    private boolean loginRegistry(SshClients.Handle handle, SseEmitter emitter, String registryId) {
        if (isBlank(registryId)) {
            return true;
        }
        RegistryConfig config = registryService.config(registryId);
        if (isBlank(config.getUsername()) || isBlank(config.getPassword())) {
            return true;
        }
        String host = registryService.loginHost(config);
        sendEvent(emitter, "step", "登录镜像库 " + safe(config.getName()));
        try {
            int code = handle.client().exec().command(buildLoginCommand(config, host) + " 2>&1")
                    .stream(line -> sendEvent(emitter, "log", line)).exitCode();
            if (code != 0) {
                sendEvent(emitter, "error", "登录镜像库失败（退出码 " + code + "）: " + host);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[docker] 登录镜像库失败 id={}: {}", registryId, e.getMessage());
            sendEvent(emitter, "error", "登录镜像库失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 拼 {@code docker login} 命令（密码走 heredoc 标准输入）。
     *
     * @param config 镜像库配置
     * @param host   登录主机
     * @return 受控命令
     */
    private String buildLoginCommand(RegistryConfig config, String host) {
        String password = safe(config.getPassword()).replace("\r", "").replace("\n", "");
        return "docker login -u " + shQuote(config.getUsername().trim())
                + " --password-stdin " + shQuote(host)
                + " <<'" + LOGIN_EOF + "'\n" + password + "\n" + LOGIN_EOF;
    }

    /**
     * 通用 SSE 命令执行（卸载容器 / 卸载镜像）：step → log… → done|error。
     *
     * @param hostId  主机标识
     * @param emitter SSE 发射器
     * @param step    阶段说明
     * @param command 受控命令
     * @param done    成功事件数据
     */
    private void execStream(Long hostId, SseEmitter emitter, String step, String command, String done) {
        SshClients.Handle[] handleRef = new SshClients.Handle[1];
        try {
            ensurePosix(hostId);
            MsServerHost host = requireHost(hostId);
            ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
            if (!resolved.isOk()) {
                sendEvent(emitter, "error", resolved.getMsg());
                emitter.complete();
                return;
            }
            SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
            handleRef[0] = handle;
            sendEvent(emitter, "step", step);
            String[] lastLine = new String[1];
            int code = handle.client().exec().command(command).stream(line -> {
                lastLine[0] = line;
                sendEvent(emitter, "log", line);
            }).exitCode();
            if (code == 0) {
                sendEvent(emitter, "done", done);
            } else {
                sendEvent(emitter, "error", "执行失败（退出码 " + code + "）: " + safe(lastLine[0]));
            }
            emitter.complete();
        } catch (UnsupportedOperationException e) {
            sendEvent(emitter, "error", e.getMessage());
            emitter.complete();
        } catch (Exception e) {
            log.warn("[docker] 流式命令失败 hostId={} {}: {}", hostId, step, e.getMessage());
            sendEvent(emitter, "error", "执行失败: " + e.getMessage());
            emitter.complete();
        } finally {
            if (handleRef[0] != null) {
                connectionManager.release(hostId);
            }
        }
    }

    /**
     * 把创建表单拼成 {@code docker run -d ...} 命令。
     *
     * @param request 创建请求
     * @param image   已解析的镜像引用（registry host + 命名空间 + 镜像）
     * @return 受控命令行
     * @throws IllegalArgumentException 字段格式不合法
     */
    private String buildRunCommand(DockerCreateRequest request, String image) {
        StringBuilder sb = new StringBuilder("docker run -d");
        if (!isBlank(request.getName())) {
            String name = request.getName().trim();
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("容器名只允许字母、数字、下划线、点与连字符: " + name);
            }
            sb.append(" --name ").append(shQuote(name));
        }
        if (!isBlank(request.getRestartPolicy())) {
            String policy = request.getRestartPolicy().trim();
            if (!List.of("no", "always", "unless-stopped", "on-failure").contains(policy)) {
                throw new IllegalArgumentException("重启策略不合法: " + policy);
            }
            sb.append(" --restart ").append(policy);
        }
        if (!isBlank(request.getNetwork())) {
            String network = request.getNetwork().trim();
            if (!network.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("网络名不合法: " + network);
            }
            sb.append(" --network ").append(shQuote(network));
        }
        if (request.getPorts() != null) {
            for (String port : request.getPorts()) {
                if (isBlank(port)) {
                    continue;
                }
                String value = port.trim();
                if (!value.matches("\\d{1,5}:\\d{1,5}(/(tcp|udp))?")) {
                    throw new IllegalArgumentException("端口映射格式应为 宿主端口:容器端口[/tcp|udp]: " + value);
                }
                sb.append(" -p ").append(value);
            }
        }
        if (request.getEnv() != null) {
            for (String env : request.getEnv()) {
                if (isBlank(env)) {
                    continue;
                }
                String value = env.trim();
                if (!value.matches("[A-Za-z_][A-Za-z0-9_]*=.*")) {
                    throw new IllegalArgumentException("环境变量格式应为 KEY=VALUE: " + value);
                }
                sb.append(" -e ").append(shQuote(value));
            }
        }
        if (request.getVolumes() != null) {
            for (String volume : request.getVolumes()) {
                if (isBlank(volume)) {
                    continue;
                }
                String value = volume.trim();
                if (!value.matches("[^:\\s]+:[^:\\s]+")) {
                    throw new IllegalArgumentException("卷映射格式应为 宿主路径:容器路径: " + value);
                }
                sb.append(" -v ").append(shQuote(value));
            }
        }
        sb.append(' ').append(shQuote(image));
        if (!isBlank(request.getCommand())) {
            for (String part : request.getCommand().trim().split("\\s+")) {
                sb.append(' ').append(shQuote(part));
            }
        }
        return sb.toString();
    }

    /**
     * {@code docker inspect} 节点裁剪为页面需要的结构。
     *
     * @param node inspect 数组的首元素
     * @return 详情视图
     */
    private Map<String, Object> parseInspect(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", node.path("Id").asText(""));
        out.put("name", stripSlash(node.path("Name").asText("")));
        out.put("created", toIso(node.path("Created").asText("")));
        out.put("platform", node.path("Platform").asText(""));
        out.put("image", node.path("Config").path("Image").asText(""));
        out.put("imageId", node.path("Image").asText(""));
        out.put("restartCount", node.path("RestartCount").asInt(0));
        out.put("logPath", node.path("LogPath").asText(""));

        JsonNode state = node.path("State");
        Map<String, Object> stateView = new LinkedHashMap<>();
        stateView.put("status", state.path("Status").asText(""));
        stateView.put("running", state.path("Running").asBoolean(false));
        stateView.put("paused", state.path("Paused").asBoolean(false));
        stateView.put("exitCode", state.path("ExitCode").asInt(0));
        stateView.put("startedAt", toIso(state.path("StartedAt").asText("")));
        stateView.put("finishedAt", toIso(state.path("FinishedAt").asText("")));
        stateView.put("pid", state.path("Pid").asInt(0));
        String health = "";
        if (state.path("Health").isObject()) {
            health = state.path("Health").path("Status").asText("");
        }
        stateView.put("health", health);
        out.put("state", stateView);

        JsonNode config = node.path("Config");
        out.put("cmd", toTextList(config.path("Cmd")));
        out.put("entrypoint", toTextList(config.path("Entrypoint")));
        out.put("workingDir", config.path("WorkingDir").asText(""));
        out.put("env", toTextList(config.path("Env")));
        out.put("labels", toTextList(config.path("Labels")));

        JsonNode hostConfig = node.path("HostConfig");
        out.put("networkMode", hostConfig.path("NetworkMode").asText(""));
        out.put("restartPolicy", hostConfig.path("RestartPolicy").path("Name").asText(""));
        out.put("autoRemove", hostConfig.path("AutoRemove").asBoolean(false));

        List<Map<String, Object>> ports = new ArrayList<>();
        JsonNode portNode = node.path("NetworkSettings").path("Ports");
        if (portNode.isObject()) {
            portNode.fields().forEachRemaining(entry -> {
                String containerPort = entry.getKey();
                JsonNode bindings = entry.getValue();
                if (bindings == null || !bindings.isArray() || bindings.isEmpty()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("container", containerPort);
                    item.put("host", "");
                    item.put("hostIp", "");
                    ports.add(item);
                    return;
                }
                for (JsonNode binding : bindings) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("container", containerPort);
                    item.put("host", binding.path("HostPort").asText(""));
                    item.put("hostIp", binding.path("HostIp").asText(""));
                    ports.add(item);
                }
            });
        }
        out.put("ports", ports);

        List<Map<String, Object>> mounts = new ArrayList<>();
        for (JsonNode mount : node.path("Mounts")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", mount.path("Type").asText(""));
            item.put("source", mount.path("Source").asText(""));
            item.put("destination", mount.path("Destination").asText(""));
            item.put("mode", mount.path("Mode").asText(""));
            item.put("rw", mount.path("RW").asBoolean(true));
            mounts.add(item);
        }
        out.put("mounts", mounts);

        List<Map<String, Object>> networks = new ArrayList<>();
        JsonNode networkNode = node.path("NetworkSettings").path("Networks");
        if (networkNode.isObject()) {
            networkNode.fields().forEachRemaining(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getKey());
                item.put("ip", entry.getValue().path("IPAddress").asText(""));
                item.put("gateway", entry.getValue().path("Gateway").asText(""));
                item.put("mac", entry.getValue().path("MacAddress").asText(""));
                networks.add(item);
            });
        }
        out.put("networks", networks);
        return out;
    }

    /**
     * 按标识取主机（经 server 模块契约 {@link ServerHostService#get(Long)}，不直连其持久层）
     *
     * @param hostId 主机标识
     * @return 主机实体
     * @throws IllegalStateException 主机不存在或凭据查询失败
     */
    private MsServerHost requireHost(Long hostId) {
        ReturnResult<MsServerHost> result = serverHostService.get(hostId);
        if (result == null || !result.isOk() || result.getData() == null) {
            String msg = result == null ? "主机不存在" : result.getMsg();
            throw new IllegalStateException(msg + ": " + hostId);
        }
        return result.getData();
    }

    /**
     * 校验主机为 POSIX 平台（Windows 无 POSIX shell）
     *
     * @param hostId 主机标识
     */
    private void ensurePosix(Long hostId) {
        MsServerHost host = requireHost(hostId);
        if (WindowsSshShell.isWindows(host)) {
            throw new UnsupportedOperationException("该主机为 Windows，暂不支持 Docker 管理");
        }
    }

    /**
     * 执行远程命令并返回结构化结果
     *
     * @param hostId  主机标识
     * @param command 命令
     * @return 退出码 + 标准输出 + 错误输出
     * @throws Exception 连接或执行失败
     */
    private RemoteExecResult execRemote(Long hostId, String command) throws Exception {
        MsServerHost host = requireHost(hostId);
        if (host == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            throw new IllegalStateException(resolved.getMsg());
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
            var result = handle.client().exec().command(command).execute();
            return new RemoteExecResult(result.exitCode(), result.stdout(), result.stderr());
        } finally {
            if (handle != null) {
                connectionManager.release(hostId);
            }
        }
    }

    /**
     * Docker 命令失败时的统一错误（区分未安装与执行错误）
     *
     * @param result 远程结果
     * @return 错误响应
     */
    private <T> ReturnResult<T> dockerFail(RemoteExecResult result) {
        String err = result.stderr() == null || result.stderr().isBlank()
                ? result.stdout() : result.stderr();
        err = err == null ? "" : err.trim();
        String low = err.toLowerCase();
        if (low.contains("command not found") || low.contains("no such file")
                || low.contains("not found:dockerd") || low.contains("cannot connect to the docker daemon")) {
            return ReturnResult.error("主机未安装或未启动 Docker" + (err.isBlank() ? "" : ": " + truncate(err)));
        }
        return ReturnResult.error("Docker 执行失败: " + (err.isBlank() ? "退出码 " + result.exitCode() : truncate(err)));
    }

    /**
     * 单引号转义（POSIX shell 安全）
     *
     * @param value 原始值
     * @return 可直接拼进命令的片段
     */
    private String shQuote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }

    /**
     * 标识类参数白名单（容器 / 镜像 ID、名称）
     *
     * @param value 原始值
     * @return 安全值
     */
    private String shellSafe(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9._:-]", "");
    }

    /**
     * 发送 SSE 事件
     *
     * @param emitter 发射器
     * @param name    事件名
     * @param data    数据（null 按空串发送）
     * @return true 表示发送成功，false 表示客户端已断开
     */
    private boolean sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data == null ? "" : data, MediaType.TEXT_PLAIN));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    /**
     * 文本数组转 List（null 安全）
     *
     * @param node JSON 节点
     * @return 字符串列表
     */
    private List<String> toTextList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText(""));
            }
            return list;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    list.add(entry.getKey() + "=" + entry.getValue().asText("")));
            return list;
        }
        return list;
    }

    /**
     * 拆分标准输出为行（过滤空行）
     *
     * @param stdout 标准输出
     * @return 行列表
     */
    private List<String> splitLines(String stdout) {
        List<String> lines = new ArrayList<>();
        if (stdout == null) {
            return lines;
        }
        for (String line : stdout.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    /**
     * 容器名去前导斜杠并取首个
     *
     * @param names 名称串
     * @return 名称
     */
    private String firstName(String names) {
        if (names == null || names.isBlank()) {
            return "";
        }
        return stripSlash(names.split(",")[0].trim());
    }

    /**
     * 去掉前导斜杠
     *
     * @param name 名称
     * @return 名称
     */
    private String stripSlash(String name) {
        if (name == null) {
            return "";
        }
        return name.startsWith("/") ? name.substring(1) : name;
    }

    /**
     * Docker 时间串（2026-09-13T08:12:00.123456789Z）截为秒级
     *
     * @param raw 原始时间
     * @return ISO 时间
     */
    private String toIso(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String body = raw;
        int dot = body.indexOf('.');
        if (dot > 0) {
            body = body.substring(0, dot);
        }
        if (body.length() > 19) {
            body = body.substring(0, 19);
        }
        return body.replace(' ', 'T');
    }

    /**
     * 截断超长文本
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > OUTPUT_TRUNCATE ? text.substring(0, OUTPUT_TRUNCATE) + "…" : text;
    }

    /**
     * null 安全取串
     *
     * @param value 原值
     * @return 非 null 字符串
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空白判断
     *
     * @param value 原值
     * @return 是否空白
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 容器 exec 会话上下文
     */
    private static final class ExecSession {
        /**
         * 会话标识
         */
        private final String sessionId;
        /**
         * PTY 终端
         */
        private final SshClient.TerminalOperation terminal;
        /**
         * SSH 句柄
         */
        private final SshClients.Handle handle;
        /**
         * 主机标识
         */
        private final Long hostId;

        /**
         * 构造会话
         *
         * @param sessionId 会话标识
         * @param terminal  PTY 终端
         * @param handle    SSH 句柄
         * @param hostId    主机标识
         */
        private ExecSession(String sessionId, SshClient.TerminalOperation terminal,
                            SshClients.Handle handle, Long hostId) {
            this.sessionId = sessionId;
            this.terminal = terminal;
            this.handle = handle;
            this.hostId = hostId;
        }
    }
}
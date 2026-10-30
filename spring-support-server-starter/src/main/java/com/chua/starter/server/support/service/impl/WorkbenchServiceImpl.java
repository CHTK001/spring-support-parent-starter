package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.connection.WindowsSshShell;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.WorkbenchDockerCapabilityVO;
import com.chua.starter.server.support.model.vo.WorkbenchK8sCapabilityVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.WorkbenchService;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 主机工作台服务实现 —— Docker / Kubernetes 经 SSH 执行 CLI 落地
 *
 * <p>受控主机凭据来自 {@link ServerHostService#resolveCredential}，连接复用
 * {@link ConnectionManager} 缓存；命令经 SSH exec 通道执行，输出解析为契约
 * {@code workbench-api-contract.md} 定义的结构。全部操作对 Windows 主机返回
 * 「暂不支持」提示（Windows 无 POSIX shell，docker/kubectl 场景罕见）。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchServiceImpl implements WorkbenchService {

    /**
     * 输出截断长度（日志类长文本防撑爆响应）
     */
    private static final int OUTPUT_TRUNCATE = 64 * 1024;

    /**
     * 命令执行超时（秒）
     */
    private static final int EXEC_TIMEOUT_SECONDS = 30;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 主机 映射器
     */
    private final MsServerHostMapper hostMapper;

    /**
     * 主机服务（凭据解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 连接缓存管理器
     */
    private final ConnectionManager connectionManager;

    /**
     * 活跃的容器 exec 会话映射：sessionId → 会话上下文
     */
    private final Map<String, ExecSession> execSessions = new ConcurrentHashMap<>();

    /* ==================== Docker ==================== */

    @Override
    public ReturnResult<WorkbenchDockerCapabilityVO> dockerCapability(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult probe = execRemote(hostId, "command -v docker >/dev/null 2>&1 && "
                    + "docker version --format '{{.Server.Version}}' 2>/dev/null && echo __OK__ || echo __NO_DOCKER__");
            String stdout = probe.stdout().trim();
            if (!stdout.contains("__OK__")) {
                return ReturnResult.ok(WorkbenchDockerCapabilityVO.notInstalled("docker"));
            }
            String version = firstNonEmptyLine(stdout);
            RemoteExecResult compose = execRemote(hostId, "docker compose version >/dev/null 2>&1 && echo yes || echo no");
            boolean hasCompose = "yes".equalsIgnoreCase(compose.stdout().trim());
            return ReturnResult.ok(WorkbenchDockerCapabilityVO.installed(version, hasCompose));
        } catch (UnsupportedOperationException e) {
            return ReturnResult.ok(WorkbenchDockerCapabilityVO.notInstalled("docker"));
        } catch (Exception e) {
            log.warn("[workbench] Docker 能力探测失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.ok(WorkbenchDockerCapabilityVO.notInstalled("docker"));
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> listDockerContainers(Long hostId, Boolean all) {
        try {
            ensurePosix(hostId);
            String flag = Boolean.FALSE.equals(all) ? "" : " -a";
            RemoteExecResult result = execRemote(hostId, "docker ps" + flag + " --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                String id = node.path("ID").asText("");
                item.put("id", id.length() > 12 ? id.substring(0, 12) : id);
                item.put("name", firstName(node.path("Names").asText("")));
                item.put("image", node.path("Image").asText(""));
                item.put("state", node.path("State").asText(""));
                item.put("status", node.path("Status").asText(""));
                item.put("ports", node.path("Ports").asText(""));
                item.put("network", node.path("Networks").asText(""));
                item.put("createdAt", toIso(node.path("CreatedAt").asText("")));
                list.add(item);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 容器列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取容器列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> dockerContainerAction(Long hostId, String containerId, String action) {
        try {
            ensurePosix(hostId);
            if (!List.of("start", "stop", "restart").contains(action)) {
                return ReturnResult.error("不支持的操作: " + action);
            }
            RemoteExecResult result = execRemote(hostId, "docker " + action + " " + shellSafe(containerId));
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 容器操作失败 hostId={} {} {}: {}", hostId, containerId, action, e.getMessage());
            return ReturnResult.error("操作失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<String> dockerContainerLogs(Long hostId, String containerId, Integer tail) {
        try {
            ensurePosix(hostId);
            int lines = tail == null || tail < 1 ? 200 : tail;
            RemoteExecResult result = execRemote(hostId, "docker logs --tail " + lines + " " + shellSafe(containerId) + " 2>&1");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
            }
            return ReturnResult.ok(truncate(result.stdout()));
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 容器日志失败 hostId={} {}: {}", hostId, containerId, e.getMessage());
            return ReturnResult.error("读取日志失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter streamContainerLogs(Long hostId, String containerId, Integer tail) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        CompletableFuture.runAsync(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            try {
                ensurePosix(hostId);
                MsServerHost host = hostMapper.selectById(hostId);
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    emitter.send(SseEmitter.event().name("error").data(resolved.getMsg(), MediaType.TEXT_PLAIN));
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;
                int lines = tail == null || tail < 1 ? 100 : tail;
                String cmd = "docker logs -f --tail " + lines + " " + shellSafe(containerId) + " 2>&1";

                SshClient.StreamHandle[] streamHandleRef = new SshClient.StreamHandle[1];
                SshClient.StreamHandle streamHandle = handle.client().exec().command(cmd).follow(line -> {
                    try {
                        emitter.send(SseEmitter.event().name("log").data(line, MediaType.TEXT_PLAIN));
                    } catch (IOException e) {
                        // 客户端断开，停止跟随
                        try { streamHandleRef[0].close(); } catch (Exception ignored) {}
                    }
                });
                streamHandleRef[0] = streamHandle;

                // 客户端断开时清理
                Runnable cleanup = () -> {
                    try { streamHandle.close(); } catch (Exception ignored) {}
                    if (handleRef[0] != null) {
                        connectionManager.release(hostId);
                    }
                };
                emitter.onCompletion(cleanup);
                emitter.onTimeout(cleanup);
                emitter.onError(e -> cleanup.run());

                // 发送初始会话标识
                emitter.send(SseEmitter.event().name("session").data(sessionId, MediaType.TEXT_PLAIN));

            } catch (Exception e) {
                log.warn("[workbench] 容器日志流失败 hostId={} {}: {}", hostId, containerId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("日志流启动失败: " + e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (Exception ignored) {}
                emitter.complete();
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        });

        return emitter;
    }

    @Override
    public SseEmitter dockerContainerExec(Long hostId, String containerId, String command) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        CompletableFuture.runAsync(() -> {
            SshClients.Handle[] handleRef = new SshClients.Handle[1];
            SshClient.TerminalOperation[] terminalRef = new SshClient.TerminalOperation[1];
            try {
                ensurePosix(hostId);
                MsServerHost host = hostMapper.selectById(hostId);
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    emitter.send(SseEmitter.event().name("error").data(resolved.getMsg(), MediaType.TEXT_PLAIN));
                    emitter.complete();
                    return;
                }
                SshClients.Handle handle = connectionManager.borrow(host, resolved.getData());
                handleRef[0] = handle;
                String execCmd = "docker exec -it " + shellSafe(containerId) + " " + command;

                SshClient.TerminalOperation terminal = handle.client().terminal()
                        .width(120).height(30)
                        .onOutput(data -> {
                            try {
                                emitter.send(SseEmitter.event().name("output").data(data, MediaType.TEXT_PLAIN));
                            } catch (IOException e) {
                                // 客户端断开
                                SshClient.TerminalOperation t = terminalRef[0];
                                if (t != null) {
                                    t.close();
                                }
                            }
                        })
                        .onClose(() -> {
                            try {
                                emitter.send(SseEmitter.event().name("closed").data("Session closed", MediaType.TEXT_PLAIN));
                            } catch (Exception ignored) {}
                            emitter.complete();
                            if (handleRef[0] != null) {
                                connectionManager.release(hostId);
                            }
                            execSessions.remove(sessionId);
                        })
                        .connect();
                terminalRef[0] = terminal;

                // 发送 docker exec 命令
                terminal.send(execCmd);

                // 存入会话表
                execSessions.put(sessionId, new ExecSession(sessionId, terminal, handle, hostId));

                // 发送会话标识给前端
                emitter.send(SseEmitter.event().name("session").data(sessionId, MediaType.TEXT_PLAIN));

                // 客户端断开时清理
                Runnable cleanup = () -> closeExecSession(sessionId);
                emitter.onCompletion(cleanup);
                emitter.onTimeout(cleanup);
                emitter.onError(e -> cleanup.run());

            } catch (Exception e) {
                log.warn("[workbench] 容器exec失败 hostId={} {}: {}", hostId, containerId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("进入容器失败: " + e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (Exception ignored) {}
                emitter.complete();
                if (handleRef[0] != null) {
                    connectionManager.release(hostId);
                }
            }
        });

        return emitter;
    }

    @Override
    public ReturnResult<Void> dockerContainerExecInput(Long hostId, String containerId, String sessionId, String input) {
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
    public ReturnResult<Void> dockerContainerExecClose(Long hostId, String containerId, String sessionId) {
        ExecSession session = execSessions.get(sessionId);
        if (session == null) {
            return ReturnResult.error("会话不存在或已关闭: " + sessionId);
        }
        closeExecSession(sessionId);
        return ReturnResult.success();
    }

    /**
     * 关闭容器 exec 会话并释放资源
     *
     * @param sessionId 会话标识
     */
    private void closeExecSession(String sessionId) {
        ExecSession session = execSessions.remove(sessionId);
        if (session != null) {
            try {
                session.terminal.close();
            } catch (Exception e) {
                log.debug("[workbench] 关闭终端异常: {}", e.getMessage());
            }
            connectionManager.release(session.hostId);
        }
    }

    /**
     * 容器 exec 会话上下文
     */
    private static class ExecSession {
        /**
         * 会话标识
         */
        final String sessionId;
        /**
         * PTY 终端
         */
        final SshClient.TerminalOperation terminal;
        /**
         * SSH 句柄
         */
        final SshClients.Handle handle;
        /**
         * 主机标识
         */
        final Long hostId;

        ExecSession(String sessionId, SshClient.TerminalOperation terminal, SshClients.Handle handle, Long hostId) {
            this.sessionId = sessionId;
            this.terminal = terminal;
            this.handle = handle;
            this.hostId = hostId;
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> listDockerImages(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker images --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String line : splitLines(result.stdout())) {
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> item = new LinkedHashMap<>();
                String id = node.path("ID").asText("");
                item.put("id", id.length() > 12 ? id.substring(0, 12) : id);
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
            log.warn("[workbench] 镜像列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取镜像列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> listDockerNetworks(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker network ls --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
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
            log.warn("[workbench] 网络列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取网络列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> listDockerVolumes(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker volume ls --format '{{json .}}'");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
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
            log.warn("[workbench] 卷列表失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取卷列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> dockerPrune(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "docker system prune -af --volumes 2>&1");
            if (result.exitCode() != 0) {
                return dockerUnavailable(result);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("output", truncate(result.stdout()));
            return ReturnResult.ok(out);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 清理失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("清理失败: " + e.getMessage());
        }
    }

    /* ==================== Kubernetes ==================== */

    @Override
    public ReturnResult<WorkbenchK8sCapabilityVO> k8sCapability(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult probe = execRemote(hostId, "command -v kubectl >/dev/null 2>&1 && kubectl version -o json 2>/dev/null");
            if (probe.exitCode() != 0 || probe.stdout().isBlank()) {
                return ReturnResult.ok(WorkbenchK8sCapabilityVO.notInstalled());
            }
            JsonNode root = objectMapper.readTree(probe.stdout());
            JsonNode server = root.path("serverVersion");
            String gitVersion = server.path("gitVersion").asText("");
            if (gitVersion.isBlank()) {
                // kubectl 存在但集群不可达（无 serverVersion）
                return ReturnResult.ok(WorkbenchK8sCapabilityVO.notInstalled());
            }
            String apiVersion = root.path("apiVersion").asText("");
            int total = nodeCount(hostId, "kubectl get nodes --no-headers 2>/dev/null | wc -l");
            int ready = nodeCount(hostId, "kubectl get nodes --no-headers 2>/dev/null | awk '$2==\"Ready\"{n++}END{print n+0}'");
            return ReturnResult.ok(WorkbenchK8sCapabilityVO.installed(apiVersion, gitVersion, total, ready));
        } catch (UnsupportedOperationException e) {
            return ReturnResult.ok(WorkbenchK8sCapabilityVO.notInstalled());
        } catch (Exception e) {
            log.warn("[workbench] K8s 能力探测失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.ok(WorkbenchK8sCapabilityVO.notInstalled());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> k8sCluster(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "kubectl get nodes -o json 2>&1");
            if (result.exitCode() != 0) {
                return k8sUnavailable(result);
            }
            JsonNode root = objectMapper.readTree(result.stdout());
            String version = root.path("serverVersion").path("gitVersion").asText("");
            List<Map<String, Object>> nodes = new ArrayList<>();
            int readyTotal = 0;
            for (JsonNode item : root.path("items")) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", item.path("metadata").path("name").asText(""));
                node.put("role", nodeRole(item));
                node.put("version", item.path("status").path("nodeInfo").path("kubeletVersion").asText(""));
                boolean ready = nodeReady(item);
                node.put("ready", ready);
                if (ready) {
                    readyTotal++;
                }
                String cpu = item.path("status").path("capacity").path("cpu").asText("");
                String mem = item.path("status").path("capacity").path("memory").asText("");
                node.put("cpu", cpu);
                node.put("mem", mem);
                nodes.add(node);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("version", version);
            out.put("ready", readyTotal);
            out.put("total", nodes.size());
            out.put("nodes", nodes);
            return ReturnResult.ok(out);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 集群节点失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取集群节点失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> k8sWorkloads(Long hostId, String namespace) {
        try {
            ensurePosix(hostId);
            String ns = namespace == null || namespace.isBlank() ? "-A" : "-n " + shellSafe(namespace.trim());
            RemoteExecResult result = execRemote(hostId, "kubectl get deploy,statefulset,daemonset,job " + ns + " -o json 2>&1");
            if (result.exitCode() != 0) {
                return k8sUnavailable(result);
            }
            JsonNode root = objectMapper.readTree(result.stdout());
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                Map<String, Object> wl = new LinkedHashMap<>();
                String kind = item.path("kind").asText("");
                wl.put("kind", kind);
                wl.put("name", item.path("metadata").path("name").asText(""));
                wl.put("namespace", item.path("metadata").path("namespace").asText(""));
                wl.put("ready", workloadReady(item, kind));
                wl.put("image", firstImage(item));
                wl.put("restarts", workloadRestarts(item));
                wl.put("age", ageOf(item.path("metadata").path("creationTimestamp").asText("")));
                list.add(wl);
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 工作负载失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取工作负载失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<String>> k8sNamespaces(Long hostId) {
        try {
            ensurePosix(hostId);
            RemoteExecResult result = execRemote(hostId, "kubectl get ns -o json 2>&1");
            if (result.exitCode() != 0) {
                return k8sUnavailable(result);
            }
            JsonNode root = objectMapper.readTree(result.stdout());
            List<String> list = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                list.add(item.path("metadata").path("name").asText(""));
            }
            return ReturnResult.ok(list);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 命名空间失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("读取命名空间失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> k8sScale(Long hostId, String name, String kind, String ns, Integer replicas) {
        try {
            ensurePosix(hostId);
            if (replicas == null || replicas < 0) {
                return ReturnResult.error("副本数不合法: " + replicas);
            }
            String k = normalizeKind(kind);
            RemoteExecResult result = execRemote(hostId, "kubectl scale " + k + "/" + shellSafe(name)
                    + " --replicas=" + replicas + " -n " + shellSafe(ns) + " 2>&1");
            if (result.exitCode() != 0) {
                return k8sUnavailable(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 扩缩容失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("扩缩容失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> k8sRestart(Long hostId, String name, String kind, String ns) {
        try {
            ensurePosix(hostId);
            String k = normalizeKind(kind);
            RemoteExecResult result = execRemote(hostId, "kubectl rollout restart " + k + "/" + shellSafe(name)
                    + " -n " + shellSafe(ns) + " 2>&1");
            if (result.exitCode() != 0) {
                return k8sUnavailable(result);
            }
            return ReturnResult.ok(true);
        } catch (UnsupportedOperationException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[workbench] 滚动重启失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("滚动重启失败: " + e.getMessage());
        }
    }

    /* ==================== 私有工具 ==================== */

    /**
     * 校验主机为 POSIX 平台（Windows 无 POSIX shell，暂不支持工作台）
     *
     * @param hostId 主机 标识
     * @throws UnsupportedOperationException Windows 主机时抛出
     */
    private void ensurePosix(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
        if (WindowsSshShell.isWindows(host)) {
            throw new UnsupportedOperationException("该主机为 Windows，暂不支持 Docker/K8s 工作台");
        }
    }

    /**
     * 执行远程命令并返回结构化结果
     *
     * @param hostId  主机 标识
     * @param command 命令
     * @return 退出码 + 标准输出 + 错误输出
     * @throws Exception 连接或执行失败时抛出
     */
    private RemoteExecResult execRemote(Long hostId, String command) throws Exception {
        MsServerHost host = hostMapper.selectById(hostId);
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
    private <T> ReturnResult<T> dockerUnavailable(RemoteExecResult result) {
        String err = result.stderr() == null || result.stderr().isBlank()
                ? "退出码非 0: " + result.exitCode() : result.stderr().trim();
        if (err.toLowerCase().contains("command not found") || err.toLowerCase().contains("no such file")) {
            return ReturnResult.error("主机未安装Docker");
        }
        return ReturnResult.error("Docker 执行失败: " + truncate(err));
    }

    /**
     * K8s 命令失败时的统一错误
     *
     * @param result 远程结果
     * @return 错误响应
     */
    private <T> ReturnResult<T> k8sUnavailable(RemoteExecResult result) {
        String err = result.stderr() == null || result.stderr().isBlank()
                ? "退出码非 0: " + result.exitCode() : result.stderr().trim();
        if (err.toLowerCase().contains("the server") && err.toLowerCase().contains("could not find the server")) {
            return ReturnResult.error("未检测到Kubernetes集群");
        }
        if (err.toLowerCase().contains("command not found") || err.toLowerCase().contains("no such file")) {
            return ReturnResult.error("未检测到Kubernetes集群");
        }
        return ReturnResult.error("Kubernetes 执行失败: " + truncate(err));
    }

    /**
     * 节点计数（capability 用，容错：任何异常按 0 处理）
     *
     * @param hostId  主机 标识
     * @param command 计数命令
     * @return 计数值
     */
    private int nodeCount(Long hostId, String command) {
        try {
            RemoteExecResult result = execRemote(hostId, command);
            if (result.exitCode() != 0) {
                return 0;
            }
            return Integer.parseInt(result.stdout().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 节点角色：从 label 前缀 node-role.kubernetes.io/ 提取
     *
     * @param node 节点 JsonNode
     * @return 角色名，未知为 worker
     */
    private String nodeRole(JsonNode node) {
        JsonNode labels = node.path("metadata").path("labels");
        if (labels.isObject()) {
            var fields = labels.fields();
            while (fields.hasNext()) {
                var me = fields.next();
                if (me.getKey().startsWith("node-role.kubernetes.io/")) {
                    return me.getValue().asText("worker");
                }
            }
        }
        return "worker";
    }

    /**
     * 节点是否就绪：conditions 中 Type=Ready 且 Status=True
     *
     * @param node 节点 JsonNode
     * @return 是否就绪
     */
    private boolean nodeReady(JsonNode node) {
        for (JsonNode cond : node.path("status").path("conditions")) {
            if ("Ready".equals(cond.path("type").asText())) {
                return "True".equals(cond.path("status").asText());
            }
        }
        return false;
    }

    /**
     * 工作负载就绪状态串（如 3/3）
     *
     * @param item 工作负载 JsonNode
     * @param kind 类型
     * @return 就绪串
     */
    private String workloadReady(JsonNode item, String kind) {
        JsonNode status = item.path("status");
        switch (kind) {
            case "Deployment":
            case "StatefulSet":
                int ready = status.path("readyReplicas").asInt(0);
                int replicas = status.path("replicas").asInt(ready);
                return ready + "/" + replicas;
            case "DaemonSet":
                int numberReady = status.path("numberReady").asInt(0);
                int desired = status.path("desiredNumberScheduled").asInt(numberReady);
                return numberReady + "/" + desired;
            case "Job":
                int succeeded = status.path("succeeded").asInt(0);
                int completions = status.path("succeeded").asInt(succeeded);
                return succeeded + "/" + completions;
            default:
                return "0/0";
        }
    }

    /**
     * 首个容器镜像
     *
     * @param item 工作负载 JsonNode
     * @return 镜像名，空 为 unknown
     */
    private String firstImage(JsonNode item) {
        JsonNode containers = item.path("spec").path("template").path("spec").path("containers");
        if (containers.isArray() && containers.size() > 0) {
            String image = containers.get(0).path("image").asText("");
            return image.isBlank() ? "unknown" : image;
        }
        return "unknown";
    }

    /**
     * 重启次数：取所有容器 restartCount 之和
     *
     * @param item 工作负载 JsonNode
     * @return 重启次数
     */
    private int workloadRestarts(JsonNode item) {
        int total = 0;
        JsonNode status = item.path("status");
        JsonNode rs = status.path("restartCount");
        if (rs.isInt()) {
            return rs.asInt(0);
        }
        for (JsonNode container : item.path("spec").path("template").path("spec").path("containers")) {
            total += container.path("restartCount").asInt(0);
        }
        return total;
    }

    /**
     * 创建时长（如 12d / 3h）
     *
     * @param creationTimestamp ISO 时间串
     * @return 可读时长
     */
    private String ageOf(String creationTimestamp) {
        try {
            Instant created = Instant.parse(creationTimestamp);
            long days = Duration.between(created, Instant.now()).toDays();
            if (days > 0) {
                return days + "d";
            }
            long hours = Duration.between(created, Instant.now()).toHours();
            return hours > 0 ? hours + "h" : "new";
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    /**
     * 标准化工作负载类型（前端可能传 Deployment / deployment）
     *
     * @param kind 类型
     * @return 小写复数类型
     */
    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "deployment";
        }
        String lower = kind.trim().toLowerCase();
        return switch (lower) {
            case "statefulset", "sts" -> "statefulset";
            case "daemonset", "ds" -> "daemonset";
            case "job" -> "job";
            default -> "deployment";
        };
    }

    /**
     * 首行非空内容（capability 版本号解析用）
     *
     * @param stdout 标准输出
     * @return 首行，空 为 unknown
     */
    private String firstNonEmptyLine(String stdout) {
        for (String line : stdout.split("\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "unknown";
    }

    /**
     * 拆分标准输出为行（过滤空行）
     *
     * @param stdout 标准输出
     * @return 行列表
     */
    private List<String> splitLines(String stdout) {
        List<String> lines = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    /**
     * 容器名取第一个（docker 多 name 逗号分隔），并去掉前导斜杠
     *
     * @param names 名称串
     * @return 首个名称
     */
    private String firstName(String names) {
        if (names == null || names.isBlank()) {
            return "";
        }
        String first = names.split(",")[0].trim();
        return first.startsWith("/") ? first.substring(1) : first;
    }

    /**
     * docker CreatedAt（如 2026-09-13 08:12:00 +0800 CST）转 ISO 片段
     *
     * @param raw 原始时间串
     * @return ISO 时间（取前 19 位，空格换 T）
     */
    private String toIso(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String body = raw.length() > 19 ? raw.substring(0, 19) : raw;
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
     * 命令参数转义：仅允许安全字符（docker id / kubectl name 均为 [a-z0-9-_.]）
     *
     * @param value 原始值
     * @return 安全值
     */
    private String shellSafe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9._:-]", "");
    }
}
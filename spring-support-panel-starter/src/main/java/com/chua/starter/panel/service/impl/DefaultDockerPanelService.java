package com.chua.starter.panel.service.impl;

import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.docker.support.client.DockerProtocolClient;
import com.chua.starter.panel.service.DockerPanelService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 默认 Docker 面板服务。
 * 使用 DockerProtocolClient（docker-java SDK）实现，支持 TCP/HTTP/HTTPS/Unix Socket 连接。
 * 连接创建使用 {@link ClientSetting} builder 模式（provider 优先），不直接操作底层 docker-java 配置。
 *
 * @author CH
 */
@SuppressWarnings("unused")
public class DefaultDockerPanelService implements DockerPanelService {

    private record DockerConnection(DockerProtocolClient client, String host, int port, String protocol) {
    }

    private final Map<String, DockerConnection> connectionMap = new ConcurrentHashMap<>();

    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;

    // ─── 连接管理 ──────────────────────────────────────────────────────────

    @Override
    public String open(String host, int port, String protocol) {
        return open(host, port, protocol, null);
    }

    @Override
    public String open(String host, int port, String protocol, String socketPath) {
        String dockerUrl = resolveDockerUrl(host, port, protocol, socketPath);
        ClientSetting setting = ClientSetting.builder()
                .url(dockerUrl)
                .connectTimeoutMillis(DEFAULT_TIMEOUT_MILLIS)
                .readTimeoutMillis(60000)
                .writeTimeoutMillis(60000)
                .build();
        DockerProtocolClient client = DockerProtocolClient.of(setting);
        try {
            client.connect();
            if (!client.isConnected()) {
                throw new RuntimeException("Docker ping 失败");
            }
        } catch (Exception e) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Docker 连接失败 (" + dockerUrl + "): " + e.getMessage(), e);
        }
        String connectionId = UUID.randomUUID().toString();
        connectionMap.put(connectionId, new DockerConnection(client, host, port, protocol));
        return connectionId;
    }

    private String resolveDockerUrl(String host, int port, String protocol, String socketPath) {
        String p = (protocol == null ? "tcp" : protocol).toLowerCase();
        return switch (p) {
            case "unix", "socket" -> {
                String sp = (socketPath == null || socketPath.isBlank())
                        ? "/var/run/docker.sock"
                        : socketPath.trim();
                if (!sp.startsWith("unix://")) {
                    if (sp.startsWith("/")) {
                        sp = "unix://" + sp;
                    } else {
                        sp = "unix:///" + sp;
                    }
                }
                yield sp;
            }
            default -> p + "://" + host + ":" + port;
        };
    }

    @Override
    public void close(String connectionId) {
        DockerConnection conn = connectionMap.remove(connectionId);
        if (conn != null) {
            try {
                conn.client().close();
            } catch (Exception ignored) {
            }
        }
    }

    // ─── 系统管理 ──────────────────────────────────────────────────────────

    @Override
    public String ping(String connectionId) {
        try {
            return requireClient(connectionId).pingDocker() ? "pong" : "error";
        } catch (Exception e) {
            throw new RuntimeException("Docker ping 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> info(String connectionId) {
        DockerProtocolClient client = requireClient(connectionId);
        try {
            Info dockerInfo = client.getDockerInfo();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", dockerInfo.getName());
            result.put("serverVersion", dockerInfo.getServerVersion());
            result.put("operatingSystem", dockerInfo.getOperatingSystem());
            result.put("architecture", dockerInfo.getArchitecture());
            result.put("kernelVersion", dockerInfo.getKernelVersion());
            result.put("nCPU", dockerInfo.getNCPU());
            result.put("memTotal", dockerInfo.getMemTotal());
            result.put("containers", dockerInfo.getContainers());
            result.put("containersRunning", dockerInfo.getContainersRunning());
            result.put("containersPaused", dockerInfo.getContainersPaused());
            result.put("containersStopped", dockerInfo.getContainersStopped());
            result.put("images", dockerInfo.getImages());
            result.put("driver", dockerInfo.getDriver());
            result.put("dockerRootDir", dockerInfo.getDockerRootDir());
            result.put("storageDriver", dockerInfo.getDriverStatuses() != null
                    ? dockerInfo.getDriverStatuses().stream()
                    .filter(s -> "Driver Name".equals(s.get(0)) || "Storage Driver".equals(s.get(0)))
                    .map(s -> s.get(1)).findFirst().orElse(null)
                    : null);
            result.put("cgroupDriver", dockerInfo.getCGroupDriver());
            result.put("securityOptions", dockerInfo.getSecurityOptions());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 信息查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> version(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            Version v = dc.versionCmd().exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("version", v.getVersion());
            result.put("apiVersion", v.getApiVersion());
            result.put("minAPIVersion", v.getMinAPIVersion());
            result.put("gitCommit", v.getGitCommit());
            result.put("goVersion", v.getGoVersion());
            result.put("os", v.getOperatingSystem());
            result.put("arch", v.getArch());
            result.put("kernelVersion", v.getKernelVersion());
            result.put("buildTime", v.getBuildTime());
            result.put("platform", v.getPlatform() != null ? v.getPlatform().getName() : null);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 版本查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> diskUsage(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            // docker-java 3.5.1 没有 DfCmd，改用独立 API 获取统计
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("layersSize", 0L);
            result.put("containersSize", 0L);
            result.put("imagesSize", 0L);
            result.put("volumesSize", 0L);
            result.put("cacheSize", 0L);

            Info info = null;
            try { info = dc.infoCmd().exec(); } catch (Exception ignored) { }
            List<Container> containers = dc.listContainersCmd().withShowAll(true).exec();
            result.put("containerCount", containers != null ? containers.size() : 0);
            result.put("imageCount", info != null && info.getImages() != null ? info.getImages() : 0);

            try {
                ListVolumesResponse lvr = dc.listVolumesCmd().exec();
                result.put("volumeCount", lvr.getVolumes() != null ? lvr.getVolumes().size() : 0);
            } catch (Exception ignored) {
                result.put("volumeCount", 0);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 磁盘使用查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> events(String connectionId, long since, long until, String type) {
        DockerClient dc = getDc(connectionId);
        try {
            EventsCmd cmd = dc.eventsCmd();
            if (since > 0) cmd.withSince(String.valueOf(since));
            if (until > 0) cmd.withUntil(String.valueOf(until));
            if (type != null && !type.isBlank()) {
                cmd.withEventFilter(type);
            }
            List<Map<String, Object>> result = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(1);
            cmd.exec(new ResultCallback<Event>() {
                @Override
                public void onStart(Closeable closeable) { }

                @Override
                public void onNext(Event event) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", event.getType() != null ? event.getType().name() : "");
                    item.put("action", event.getAction());
                    item.put("id", event.getId());
                    item.put("from", event.getFrom());
                    item.put("time", event.getTime());
                    item.put("timeNano", event.getTimeNano());
                    item.put("status", event.getStatus());
                    Map<String, Object> actor = new LinkedHashMap<>();
                    if (event.getActor() != null) {
                        actor.put("id", event.getActor().getId());
                        actor.put("attributes", event.getActor().getAttributes());
                    }
                    item.put("actor", actor);
                    result.add(item);
                }

                @Override
                public void onError(Throwable throwable) { latch.countDown(); }

                @Override
                public void onComplete() { latch.countDown(); }

                @Override
                public void close() { latch.countDown(); }
            });
            // 等待最多 5 秒收集事件
            latch.await(5, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 事件查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> auth(String connectionId, String username, String password, String serverAddress) {
        DockerClient dc = getDc(connectionId);
        try {
            AuthConfig authConfig = new AuthConfig()
                    .withUsername(username)
                    .withPassword(password);
            if (serverAddress != null && !serverAddress.isBlank()) {
                authConfig.withRegistryAddress(serverAddress);
            }
            AuthResponse response = dc.authCmd().withAuthConfig(authConfig).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.getStatus());
            result.put("identityToken", response.getIdentityToken());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 认证失败: " + e.getMessage(), e);
        }
    }

    // ─── 容器管理 ──────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listContainers(String connectionId, boolean showAll) {
        DockerClient dc = getDc(connectionId);
        try {
            List<Container> containers = dc.listContainersCmd().withShowAll(showAll).exec();
            return containers.stream().map(c -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("names", c.getNames() != null ? List.of(c.getNames()) : List.of());
                item.put("image", c.getImage());
                item.put("imageId", c.getImageId());
                item.put("command", c.getCommand());
                item.put("created", c.getCreated());
                item.put("state", c.getState());
                item.put("status", c.getStatus());
                item.put("ports", c.getPorts() != null ? Arrays.stream(c.getPorts())
                        .map(p -> Map.of(
                                "ip", p.getIp() == null ? "" : p.getIp(),
                                "privatePort", p.getPrivatePort(),
                                "publicPort", p.getPublicPort() == null ? 0 : p.getPublicPort(),
                                "type", p.getType() == null ? "" : p.getType()
                        )).toList() : List.of());
                item.put("labels", c.getLabels());
                item.put("mounts", c.getMounts() != null ? c.getMounts().stream()
                        .map(m -> Map.of(
                                "source", m.getSource() != null ? m.getSource() : "",
                                "destination", m.getDestination() != null ? m.getDestination() : "",
                                "mode", m.getMode() != null ? m.getMode() : "",
                                "rw", m.getRw() != null && m.getRw()
                        )).toList() : List.of());
                // 计算 CPU 和内存（需要额外查询，此处使用占位）
                item.put("sizeRw", c.getSizeRw());
                item.put("sizeRootFs", c.getSizeRootFs());
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> inspectContainer(String connectionId, String containerId) {
        DockerClient dc = getDc(connectionId);
        try {
            InspectContainerResponse resp = dc.inspectContainerCmd(containerId).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", resp.getId());
            result.put("name", resp.getName() != null ? resp.getName().replaceFirst("^/", "") : "");
            result.put("image", resp.getConfig() != null ? resp.getConfig().getImage() : "");
            result.put("state", resp.getState() != null ? safeGet(resp.getState()::getStatus) : "");
            result.put("created", resp.getCreated());
            result.put("platform", resp.getPlatform());
            result.put("path", resp.getPath());
            result.put("args", resp.getArgs() != null ? List.of(resp.getArgs()) : List.of());
            // 状态详情
            if (resp.getState() != null) {
                Map<String, Object> stateMap = new LinkedHashMap<>();
                stateMap.put("running", resp.getState().getRunning());
                stateMap.put("paused", resp.getState().getPaused());
                stateMap.put("restarting", resp.getState().getRestarting());
                stateMap.put("exitCode", resp.getState().getExitCode());
                stateMap.put("startedAt", resp.getState().getStartedAt());
                stateMap.put("finishedAt", resp.getState().getFinishedAt());
                result.put("stateDetail", stateMap);
            }
            // 网络
            if (resp.getNetworkSettings() != null && resp.getNetworkSettings().getNetworks() != null) {
                result.put("networks", resp.getNetworkSettings().getNetworks().entrySet().stream()
                        .map(e -> Map.of("name", e.getKey(), "ipAddress", e.getValue().getIpAddress()))
                        .toList());
            }
            // 挂载
            if (resp.getMounts() != null) {
                result.put("mounts", resp.getMounts().stream()
                        .map(m -> Map.of(
                                "source", m.getSource() != null ? m.getSource() : "",
                                "destination", m.getDestination() != null ? m.getDestination() : "",
                                "mode", m.getMode() != null ? m.getMode() : "",
                                "rw", m.getRW()
                        )).toList());
            }
            // 端口映射
            if (resp.getNetworkSettings() != null && resp.getNetworkSettings().getPorts() != null) {
                Map<String, List<Map<String, Object>>> portMap = new LinkedHashMap<>();
                resp.getNetworkSettings().getPorts().getBindings().forEach((exposedPort, bindings) -> {
                    if (bindings != null) {
                        portMap.put(exposedPort.toString(), Arrays.stream(bindings)
                                .map(b -> java.util.Map.<String, Object>of(
                                        "hostIp", b.getHostIp() != null ? b.getHostIp() : "",
                                        "hostPort", b.getHostPortSpec() != null ? b.getHostPortSpec() : ""))
                                .toList());
                    }
                });
                if (!portMap.isEmpty()) {
                    result.put("portBindings", portMap);
                }
            }
            // 端口映射回退：从 HostConfig 读取（容器退出后 NetworkSettings.Ports 为空）
            if (!result.containsKey("portBindings") && resp.getHostConfig() != null) {
                try {
                    com.github.dockerjava.api.model.Ports hostPorts = resp.getHostConfig().getPortBindings();
                    if (hostPorts != null && hostPorts.getBindings() != null) {
                        Map<String, List<Map<String, Object>>> portMap = new LinkedHashMap<>();
                        hostPorts.getBindings().forEach((exposedPort, bindings) -> {
                            if (bindings != null) {
                                portMap.put(exposedPort.toString(), Arrays.stream(bindings)
                                        .map(b -> java.util.Map.<String, Object>of(
                                                "hostIp", b.getHostIp() != null ? b.getHostIp() : "",
                                                "hostPort", b.getHostPortSpec() != null ? b.getHostPortSpec() : ""))
                                        .toList());
                            }
                        });
                        result.put("portBindings", portMap);
                    }
                } catch (Exception ignored) {
                    // HostConfig port bindings not available
                }
            }
            // 环境变量
            if (resp.getConfig() != null && resp.getConfig().getEnv() != null) {
                result.put("env", List.of(resp.getConfig().getEnv()));
            }
            if (resp.getConfig() != null) {
                result.put("hostname", resp.getConfig().getHostName());
                result.put("cmd", resp.getConfig().getCmd() != null ? List.of(resp.getConfig().getCmd()) : List.of());
                result.put("entrypoint", resp.getConfig().getEntrypoint() != null ? List.of(resp.getConfig().getEntrypoint()) : List.of());
                result.put("labels", resp.getConfig().getLabels());
                result.put("workingDir", resp.getConfig().getWorkingDir());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String createContainer(String connectionId, String imageName, String containerName,
                                  Map<String, String> env, Map<String, String> ports,
                                  Map<String, String> volumes) {
        DockerClient dc = getDc(connectionId);
        try {
            CreateContainerCmd cmd = dc.createContainerCmd(imageName);
            if (containerName != null && !containerName.isBlank()) {
                cmd.withName(containerName);
            }
            if (env != null && !env.isEmpty()) {
                cmd.withEnv(env.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
            }
            if (ports != null && !ports.isEmpty()) {
                List<PortBinding> portBindingsList = new ArrayList<>();
                List<ExposedPort> exposedPortsList = new ArrayList<>();
                for (Map.Entry<String, String> e : ports.entrySet()) {
                    int containerPort = Integer.parseInt(e.getKey());
                    ExposedPort exposed = ExposedPort.tcp(containerPort);
                    exposedPortsList.add(exposed);
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        String[] parts = e.getValue().split(":");
                        int hostPortNum = Integer.parseInt(parts[parts.length - 1]);
                        String hostIp = parts.length > 1 ? parts[0] : "0.0.0.0";
                        Ports.Binding binding = new Ports.Binding(hostIp, String.valueOf(hostPortNum));
                        portBindingsList.add(new PortBinding(binding, exposed));
                    }
                }
                cmd.withExposedPorts(exposedPortsList);
                cmd.withPortBindings(portBindingsList);
            }
            if (volumes != null && !volumes.isEmpty()) {
                List<Mount> mountList = new ArrayList<>();
                for (Map.Entry<String, String> e : volumes.entrySet()) {
                    mountList.add(new Mount()
                            .withSource(e.getKey())
                            .withTarget(e.getValue())
                            .withType(MountType.BIND));
                }
                HostConfig hc = cmd.getHostConfig();
                if (hc == null) {
                    hc = new HostConfig();
                    cmd.withHostConfig(hc);
                }
                hc.withMounts(mountList);
            }
            CreateContainerResponse response = cmd.exec();
            return response.getId();
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void startContainer(String connectionId, String containerId) {
        requireClient(connectionId).startContainer(containerId);
    }

    @Override
    public void stopContainer(String connectionId, String containerId) {
        requireClient(connectionId).stopContainer(containerId);
    }

    @Override
    public void restartContainer(String connectionId, String containerId) {
        requireClient(connectionId).restartContainer(containerId);
    }

    @Override
    public void killContainer(String connectionId, String containerId, String signal) {
        DockerClient dc = getDc(connectionId);
        try {
            KillContainerCmd cmd = dc.killContainerCmd(containerId);
            if (signal != null && !signal.isBlank()) {
                cmd.withSignal(signal);
            }
            cmd.exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器终止失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void pauseContainer(String connectionId, String containerId) {
        requireClient(connectionId).pauseContainer(containerId);
    }

    @Override
    public void unpauseContainer(String connectionId, String containerId) {
        requireClient(connectionId).unpauseContainer(containerId);
    }

    @Override
    public void renameContainer(String connectionId, String containerId, String newName) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.renameContainerCmd(containerId).withName(newName).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器重命名失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeContainer(String connectionId, String containerId, boolean force) {
        requireClient(connectionId).removeContainerById(containerId, force);
    }

    @Override
    public Map<String, Object> updateContainer(String connectionId, String containerId,
                                                 String cpuCpus, Long memoryBytes,
                                                 String restartPolicy, Integer maxRetries) {
        DockerClient dc = getDc(connectionId);
        UpdateContainerCmd cmd = dc.updateContainerCmd(containerId);
        if (cpuCpus != null && !cpuCpus.isEmpty()) {
            cmd.withCpusetCpus(cpuCpus);
        }
        if (memoryBytes != null) {
            cmd.withMemory(memoryBytes);
        }
        if (restartPolicy != null) {
            switch (restartPolicy.toLowerCase()) {
                case "no":
                    cmd.withRestartPolicy(RestartPolicy.noRestart());
                    break;
                case "always":
                    cmd.withRestartPolicy(RestartPolicy.alwaysRestart());
                    break;
                case "unless-stopped":
                    cmd.withRestartPolicy(RestartPolicy.unlessStoppedRestart());
                    break;
                case "on-failure":
                    if (maxRetries != null) {
                        cmd.withRestartPolicy(RestartPolicy.onFailureRestart(maxRetries));
                    } else {
                        cmd.withRestartPolicy(RestartPolicy.onFailureRestart(0));
                    }
                    break;
                default:
                    break;
            }
        }
        cmd.exec();
        // Return updated container info
        return inspectContainer(connectionId, containerId);
    }

    @Override
    public String containerLogs(String connectionId, String containerId, int tail) {
        return requireClient(connectionId).getContainerLogs(containerId, tail);
    }

    @Override
    public Map<String, Object> getContainerStats(String connectionId, String containerId) {
        DockerClient dc = getDc(connectionId);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            var statsCmd = dc.statsCmd(containerId);
            statsCmd.withNoStream(true);
            Map<String, Object> result = new LinkedHashMap<>();
            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) { }

                @Override
                public void onNext(Statistics stats) {
                    // Memory
                    if (stats.getMemoryStats() != null) {
                        result.put("memoryUsage", stats.getMemoryStats().getUsage() != null ? stats.getMemoryStats().getUsage() : 0);
                        result.put("memoryLimit", stats.getMemoryStats().getLimit() != null ? stats.getMemoryStats().getLimit() : 0);
                    }
                    // Network
                    if (stats.getNetworks() != null) {
                        long rx = 0, tx = 0;
                        for (var entry : stats.getNetworks().entrySet()) {
                            rx += entry.getValue().getRxBytes() != null ? entry.getValue().getRxBytes() : 0;
                            tx += entry.getValue().getTxBytes() != null ? entry.getValue().getTxBytes() : 0;
                        }
                        result.put("networkRx", rx);
                        result.put("networkTx", tx);
                    }
                    // CPU
                    if (stats.getCpuStats() != null) {
                        long cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage()
                                - (stats.getPreCpuStats() != null ? stats.getPreCpuStats().getCpuUsage().getTotalUsage() : 0);
                        long systemDelta = stats.getCpuStats().getSystemCpuUsage()
                                - (stats.getPreCpuStats() != null ? stats.getPreCpuStats().getSystemCpuUsage() : 0);
                        int onlineCpus = stats.getCpuStats().getOnlineCpus() != null ? Math.toIntExact(stats.getCpuStats().getOnlineCpus()) : 1;
                        if (systemDelta > 0 && cpuDelta > 0) {
                            result.put("cpuPercent", (double) cpuDelta / systemDelta * onlineCpus * 100);
                        } else {
                            result.put("cpuPercent", 0.0);
                        }
                    }
                    result.put("timestamp", Instant.now().toString());
                    result.put("containerName", containerId);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) { latch.countDown(); }

                @Override
                public void onComplete() { latch.countDown(); }

                @Override
                public void close() { latch.countDown(); }
            });
            latch.await(5, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器统计查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getContainerProcesses(String connectionId, String containerId, String psArgs) {
        DockerClient dc = getDc(connectionId);
        try {
            var topCmd = dc.topContainerCmd(containerId);
            if (psArgs != null && !psArgs.isBlank()) {
                topCmd.withPsArgs(psArgs);
            }
            TopContainerResponse top = topCmd.exec();
            List<Map<String, Object>> result = new ArrayList<>();
            String[] titlesArr = top.getTitles();
            if (top.getProcesses() != null) {
                for (String[] process : top.getProcesses()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (int i = 0; i < titlesArr.length && i < process.length; i++) {
                        item.put(titlesArr[i], process[i]);
                    }
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器进程查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String execInContainer(String connectionId, String containerId, String[] command) {
        requireClient(connectionId);
        try {
            DockerClient dc = getDc(connectionId);
            ExecCreateCmdResponse execCreate = dc.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .exec();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);
            dc.execStartCmd(execCreate.getId())
                    .exec(new ResultCallback<Frame>() {
                        @Override
                        public void onStart(Closeable closeable) { }
                        @Override
                        public void onNext(Frame frame) {
                            try { output.write(frame.getPayload()); } catch (Exception ignored) { }
                        }
                        @Override
                        public void onError(Throwable throwable) { latch.countDown(); }
                        @Override
                        public void onComplete() { latch.countDown(); }
                        @Override
                        public void close() { latch.countDown(); }
                    });
            latch.await(30, TimeUnit.SECONDS);
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器命令执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getContainerDiff(String connectionId, String containerId) {
        DockerClient dc = getDc(connectionId);
        try {
            List<ChangeLog> changes = dc.containerDiffCmd(containerId).exec();
            return changes.stream().map(c -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("path", c.getPath());
                item.put("kind", c.getKind());
                String kindStr = switch (c.getKind()) {
                    case 0 -> "修改";
                    case 1 -> "新增";
                    case 2 -> "删除";
                    default -> String.valueOf(c.getKind());
                };
                item.put("kindText", kindStr);
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器差异查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String commitContainer(String connectionId, String containerId, String repository, String tag) {
        DockerClient dc = getDc(connectionId);
        try {
            var commitCmd = dc.commitCmd(containerId);
            if (repository != null && !repository.isBlank()) {
                commitCmd.withRepository(repository);
            }
            if (tag != null && !tag.isBlank()) {
                commitCmd.withTag(tag);
            }
            return commitCmd.exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> pruneContainers(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            PruneResponse pr = dc.pruneCmd(PruneType.CONTAINERS).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("spaceReclaimed", pr.getSpaceReclaimed());
            result.put("containersDeleted", List.of());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 容器清理失败: " + e.getMessage(), e);
        }
    }

    // ─── 镜像管理 ──────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listImages(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            List<Image> images = dc.listImagesCmd().withShowAll(true).exec();
            return images.stream().map(img -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", img.getId());
                item.put("repotags", img.getRepoTags() != null ? List.of(img.getRepoTags()) : List.of());
                item.put("repodigests", img.getRepoDigests() != null ? List.of(img.getRepoDigests()) : List.of());
                item.put("parentId", img.getParentId());
                item.put("created", img.getCreated());
                item.put("size", img.getSize());
                item.put("virtualSize", img.getVirtualSize());
                item.put("labels", img.getLabels());
                item.put("containers", img.getContainers());
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void pullImage(String connectionId, String imageName, Map<String, String> registryAuth) {
        DockerClient dc = getDc(connectionId);
        try {
            PullImageCmd cmd = dc.pullImageCmd(imageName);
            if (registryAuth != null && !registryAuth.isEmpty()) {
                AuthConfig auth = new AuthConfig();
                if (registryAuth.containsKey("username")) auth.withUsername(registryAuth.get("username"));
                if (registryAuth.containsKey("password")) auth.withPassword(registryAuth.get("password"));
                if (registryAuth.containsKey("serverAddress")) auth.withRegistryAddress(registryAuth.get("serverAddress"));
                cmd.withAuthConfig(auth);
            }
            CountDownLatch latch = new CountDownLatch(1);
            cmd.exec(new ResultCallback<PullResponseItem>() {
                @Override public void onStart(Closeable closeable) { }
                @Override public void onNext(PullResponseItem item) { }
                @Override public void onError(Throwable t) { latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
                @Override public void close() { latch.countDown(); }
            });
            latch.await(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像拉取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void pushImage(String connectionId, String imageName, Map<String, String> registryAuth) {
        DockerClient dc = getDc(connectionId);
        try {
            PushImageCmd cmd = dc.pushImageCmd(imageName);
            if (registryAuth != null && !registryAuth.isEmpty()) {
                AuthConfig auth = new AuthConfig();
                if (registryAuth.containsKey("username")) auth.withUsername(registryAuth.get("username"));
                if (registryAuth.containsKey("password")) auth.withPassword(registryAuth.get("password"));
                if (registryAuth.containsKey("serverAddress")) auth.withRegistryAddress(registryAuth.get("serverAddress"));
                cmd.withAuthConfig(auth);
            }
            CountDownLatch latch = new CountDownLatch(1);
            cmd.exec(new ResultCallback<PushResponseItem>() {
                @Override public void onStart(Closeable closeable) { }
                @Override public void onNext(PushResponseItem item) { }
                @Override public void onError(Throwable t) { latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
                @Override public void close() { latch.countDown(); }
            });
            latch.await(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像推送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void tagImage(String connectionId, String imageId, String repository, String tag) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.tagImageCmd(imageId, repository, tag).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像标记失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeImage(String connectionId, String imageId, boolean force) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.removeImageCmd(imageId).withForce(force).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> inspectImage(String connectionId, String imageId) {
        DockerClient dc = getDc(connectionId);
        try {
            InspectImageResponse resp = dc.inspectImageCmd(imageId).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", resp.getId());
            result.put("parent", resp.getParent());
            result.put("comment", resp.getComment());
            result.put("created", resp.getCreated());
            result.put("container", resp.getContainer());
            result.put("dockerVersion", resp.getDockerVersion());
            result.put("author", resp.getAuthor());
            result.put("architecture", resp.getArch());
            result.put("os", resp.getOs());
            result.put("size", resp.getSize());
            result.put("virtualSize", resp.getVirtualSize());
            result.put("repoTags", resp.getRepoTags() != null ? List.of(resp.getRepoTags()) : List.of());
            result.put("repoDigests", resp.getRepoDigests() != null ? List.of(resp.getRepoDigests()) : List.of());
            result.put("containerConfig", resp.getContainerConfig() != null
                    ? resp.getContainerConfig().getLabels() : null);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchImages(String connectionId, String term) {
        DockerClient dc = getDc(connectionId);
        try {
            List<SearchItem> items = dc.searchImagesCmd(term).exec();
            return items.stream().map(s -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", s.getName());
                item.put("description", s.getDescription());
                item.put("official", s.isOfficial() != null && s.isOfficial());
                item.put("stars", s.getStarCount() != null ? s.getStarCount() : 0);
                item.put("isOfficial", s.isOfficial());
                item.put("isAutomated", s.isTrusted());
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> buildImage(String connectionId, String dockerfile, List<String> tags) {
        DockerClient dc = getDc(connectionId);
        try {
            // 将 Dockerfile 内容写入临时文件
            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "docker-build-" + UUID.randomUUID());
            tmpDir.mkdirs();
            File dockerfileFile = new File(tmpDir, "Dockerfile");
            java.nio.file.Files.writeString(dockerfileFile.toPath(), dockerfile, StandardCharsets.UTF_8);

            BuildImageCmd cmd = dc.buildImageCmd(tmpDir);
            if (tags != null && !tags.isEmpty()) {
                cmd.withTags(new HashSet<>(tags));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder buildLog = new StringBuilder();
            cmd.exec(new ResultCallback<BuildResponseItem>() {
                @Override public void onStart(Closeable closeable) { }
                @Override public void onNext(BuildResponseItem item) {
                    if (item.getStream() != null) buildLog.append(item.getStream());
                    if (item.isErrorIndicated()) buildLog.append("[ERROR] ").append(item.getError());
                    if (item.getImageId() != null) result.put("imageId", item.getImageId());
                }
                @Override public void onError(Throwable t) { latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
                @Override public void close() { latch.countDown(); }
            });
            latch.await(300, TimeUnit.SECONDS);
            result.put("buildLog", buildLog.toString());
            // 清理临时文件
            try { deleteDirectory(tmpDir); } catch (Exception ignored) { }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像构建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> pruneImages(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            PruneResponse pr = dc.pruneCmd(PruneType.IMAGES).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("spaceReclaimed", pr.getSpaceReclaimed());
            result.put("imagesDeleted", List.of());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 镜像清理失败: " + e.getMessage(), e);
        }
    }

    // ─── 网络管理 ──────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listNetworks(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            List<Network> networks = dc.listNetworksCmd().exec();
            return networks.stream().map(n -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", n.getId());
                item.put("name", n.getName());
                item.put("driver", n.getDriver());
                item.put("scope", n.getScope());
                item.put("internal", n.getInternal() != null && n.getInternal());
                item.put("enableIpv6", n.getEnableIPv6() != null && n.getEnableIPv6());
                item.put("ipam", n.getIpam() != null ? n.getIpam().getConfig() : List.of());
                item.put("containers", n.getContainers() != null ? n.getContainers().keySet() : Set.of());
                item.put("labels", n.getLabels());
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String createNetwork(String connectionId, String networkName, String driver) {
        DockerClient dc = getDc(connectionId);
        try {
            CreateNetworkCmd cmd = dc.createNetworkCmd().withName(networkName);
            if (driver != null && !driver.isBlank()) {
                cmd.withDriver(driver);
            }
            return cmd.exec().getId();
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeNetwork(String connectionId, String networkId) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.removeNetworkCmd(networkId).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> inspectNetwork(String connectionId, String networkId) {
        DockerClient dc = getDc(connectionId);
        try {
            Network network = dc.inspectNetworkCmd().withNetworkId(networkId).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", network.getId());
            result.put("name", network.getName());
            result.put("driver", network.getDriver());
            result.put("scope", network.getScope());
            result.put("internal", network.getInternal());
            result.put("enableIpv6", network.getEnableIPv6());
            result.put("ipam", network.getIpam() != null ? network.getIpam().getConfig() : List.of());
            // 连接的容器
            if (network.getContainers() != null) {
                Map<String, Object> containers = new LinkedHashMap<>();
                network.getContainers().forEach((id, c) -> {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("name", c.getName() != null ? c.getName() : "");
                    ci.put("ipv4Address", c.getIpv4Address() != null ? c.getIpv4Address() : "");
                    ci.put("ipv6Address", "");
                    ci.put("macAddress", c.getMacAddress() != null ? c.getMacAddress() : "");
                    containers.put(id, ci);
                });
                result.put("containers", containers);
            }
            result.put("labels", network.getLabels());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void connectContainerToNetwork(String connectionId, String networkId, String containerId) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.connectToNetworkCmd().withNetworkId(networkId).withContainerId(containerId).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnectContainerFromNetwork(String connectionId, String networkId, String containerId) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.disconnectFromNetworkCmd().withNetworkId(networkId).withContainerId(containerId).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络断开失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> pruneNetworks(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            PruneResponse pr = dc.pruneCmd(PruneType.NETWORKS).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("networksDeleted", List.of());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 网络清理失败: " + e.getMessage(), e);
        }
    }

    // ─── 卷管理 ────────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listVolumes(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            ListVolumesResponse lvr = dc.listVolumesCmd().exec();
            List<InspectVolumeResponse> volumes = lvr.getVolumes();
            List<Map<String, Object>> result = new ArrayList<>();
            if (volumes != null) {
                for (InspectVolumeResponse v : volumes) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", v.getName());
                    item.put("driver", v.getDriver());
                    item.put("mountpoint", v.getMountpoint());
                    item.put("labels", v.getLabels());
                    item.put("options", v.getOptions());
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 卷列表查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> createVolume(String connectionId, String volumeName, String driver,
                                            Map<String, String> driverOpts) {
        DockerClient dc = getDc(connectionId);
        try {
            CreateVolumeCmd cmd = dc.createVolumeCmd();
            if (volumeName != null && !volumeName.isBlank()) {
                cmd.withName(volumeName);
            }
            if (driver != null && !driver.isBlank()) {
                cmd.withDriver(driver);
            }
            if (driverOpts != null && !driverOpts.isEmpty()) {
                cmd.withDriverOpts(driverOpts);
            }
            com.github.dockerjava.api.command.CreateVolumeResponse resp = cmd.exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", resp.getName());
            result.put("driver", resp.getDriver());
            result.put("mountpoint", resp.getMountpoint());
            result.put("labels", resp.getLabels());
            result.put("options", Map.of());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 卷创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeVolume(String connectionId, String volumeName, boolean force) {
        DockerClient dc = getDc(connectionId);
        try {
            dc.removeVolumeCmd(volumeName).exec();
        } catch (Exception e) {
            throw new RuntimeException("Docker 卷删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> inspectVolume(String connectionId, String volumeName) {
        DockerClient dc = getDc(connectionId);
        try {
            InspectVolumeResponse resp = dc.inspectVolumeCmd(volumeName).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", resp.getName());
            result.put("driver", resp.getDriver());
            result.put("mountpoint", resp.getMountpoint());
            result.put("labels", resp.getLabels());
            result.put("options", resp.getOptions());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 卷详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> pruneVolumes(String connectionId) {
        DockerClient dc = getDc(connectionId);
        try {
            PruneResponse pr = dc.pruneCmd(PruneType.VOLUMES).exec();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("spaceReclaimed", pr.getSpaceReclaimed());
            result.put("volumesDeleted", List.of());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Docker 卷清理失败: " + e.getMessage(), e);
        }
    }

    // ─── 辅助方法 ──────────────────────────────────────────────────────────

    @Override
    public DockerClient getDockerClient(String connectionId) {
        return getDc(connectionId);
    }

    private DockerProtocolClient requireClient(String connectionId) {
        DockerConnection conn = connectionMap.get(connectionId);
        if (conn == null) {
            throw new IllegalStateException("Docker 连接不存在: " + connectionId);
        }
        return conn.client();
    }

    private DockerClient getDc(String connectionId) {
        return requireClient(connectionId).getDockerClient();
    }

    @FunctionalInterface
    private interface SafeSupplier<T> {
        T get();
    }

    private static <T> T safeGet(SafeSupplier<T> supplier) {
        try { return supplier.get(); } catch (Exception e) { return null; }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}

package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.DockerPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Docker 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/docker")
public class PanelDockerController {

    private final DockerPanelService dockerPanelService;

    // ─── 连接管理 ──────────────────────────────────────────────────────────

    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 2375)));
        String protocol = String.valueOf(body.getOrDefault("protocol", "tcp"));
        String socketPath = body.containsKey("socketPath") ? String.valueOf(body.get("socketPath")) : null;
        try {
            if (socketPath != null && !socketPath.isBlank() && !"null".equals(socketPath)) {
                return ReturnResult.ok(dockerPanelService.open(host, port, protocol, socketPath));
            }
            return ReturnResult.ok(dockerPanelService.open(host, port, protocol));
        } catch (Exception e) {
            return ReturnResult.error("Docker 连接失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        dockerPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    // ─── 系统管理 ──────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/ping")
    public ReturnResult<String> ping(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.ping(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker Ping 失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/info")
    public ReturnResult<Map<String, Object>> info(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.info(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 信息查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/version")
    public ReturnResult<Map<String, Object>> version(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.version(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 版本查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/disk-usage")
    public ReturnResult<Map<String, Object>> diskUsage(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.diskUsage(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 磁盘使用查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/events")
    public ReturnResult<List<Map<String, Object>>> events(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "0") long until,
            @RequestParam(defaultValue = "") String type) {
        try {
            return ReturnResult.ok(dockerPanelService.events(connectionId, since, until, type));
        } catch (Exception e) {
            return ReturnResult.error("Docker 事件查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/auth")
    public ReturnResult<Map<String, Object>> auth(
            @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(dockerPanelService.auth(connectionId,
                    body.get("username"), body.get("password"), body.get("serverAddress")));
        } catch (Exception e) {
            return ReturnResult.error("Docker 认证失败: " + e.getMessage());
        }
    }

    // ─── 容器管理 ──────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/containers")
    public ReturnResult<List<Map<String, Object>>> listContainers(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "false") boolean showAll) {
        try {
            return ReturnResult.ok(dockerPanelService.listContainers(connectionId, showAll));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器列表查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/containers/{containerId}")
    public ReturnResult<Map<String, Object>> inspectContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            return ReturnResult.ok(dockerPanelService.inspectContainer(connectionId, containerId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器详情查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers")
    public ReturnResult<String> createContainer(@RequestBody Map<String, Object> body,
                                                 @PathVariable String connectionId) {
        try {
            String imageName = String.valueOf(body.get("imageName"));
            String containerName = body.containsKey("containerName")
                    ? String.valueOf(body.get("containerName")) : null;
            @SuppressWarnings("unchecked")
            Map<String, String> env = body.containsKey("env")
                    ? (Map<String, String>) body.get("env") : null;
            @SuppressWarnings("unchecked")
            Map<String, String> ports = body.containsKey("ports")
                    ? (Map<String, String>) body.get("ports") : null;
            @SuppressWarnings("unchecked")
            Map<String, String> volumes = body.containsKey("volumes")
                    ? (Map<String, String>) body.get("volumes") : null;
            return ReturnResult.ok(dockerPanelService.createContainer(
                    connectionId, imageName, containerName, env, ports, volumes));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/start")
    public ReturnResult<Void> startContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            dockerPanelService.startContainer(connectionId, containerId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器启动失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/stop")
    public ReturnResult<Void> stopContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            dockerPanelService.stopContainer(connectionId, containerId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器停止失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/restart")
    public ReturnResult<Void> restartContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            dockerPanelService.restartContainer(connectionId, containerId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器重启失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/kill")
    public ReturnResult<Void> killContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestParam(defaultValue = "") String signal) {
        try {
            dockerPanelService.killContainer(connectionId, containerId,
                    signal.isBlank() ? null : signal);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器终止失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/pause")
    public ReturnResult<Void> pauseContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            dockerPanelService.pauseContainer(connectionId, containerId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器暂停失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/unpause")
    public ReturnResult<Void> unpauseContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            dockerPanelService.unpauseContainer(connectionId, containerId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器恢复失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/rename")
    public ReturnResult<Void> renameContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestBody Map<String, String> body) {
        try {
            dockerPanelService.renameContainer(connectionId, containerId, body.get("name"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器重命名失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/containers/{containerId}")
    public ReturnResult<Void> removeContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestParam(defaultValue = "false") boolean force) {
        try {
            dockerPanelService.removeContainer(connectionId, containerId, force);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/containers/{containerId}/logs")
    public ReturnResult<String> containerLogs(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestParam(defaultValue = "100") int tail) {
        try {
            return ReturnResult.ok(dockerPanelService.containerLogs(connectionId, containerId, tail));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器日志获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/containers/{containerId}/stats")
    public ReturnResult<Map<String, Object>> containerStats(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            return ReturnResult.ok(dockerPanelService.getContainerStats(connectionId, containerId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器统计查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/containers/{containerId}/top")
    public ReturnResult<List<Map<String, Object>>> containerProcesses(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestParam(defaultValue = "") String psArgs) {
        try {
            return ReturnResult.ok(dockerPanelService.getContainerProcesses(
                    connectionId, containerId, psArgs.isBlank() ? null : psArgs));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器进程查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/exec")
    public ReturnResult<String> execContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> cmdList = (List<String>) body.get("command");
            String[] command = cmdList != null ? cmdList.toArray(new String[0]) : new String[0];
            return ReturnResult.ok(dockerPanelService.execInContainer(connectionId, containerId, command));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器命令执行失败: " + e.getMessage());
        }
    }

    @PutMapping("/{connectionId}/containers/{containerId}/update")
    public ReturnResult<Map<String, Object>> updateContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestBody Map<String, Object> body) {
        try {
            String cpuCpus = (String) body.get("cpuCpus");
            Long memoryBytes = body.get("memoryBytes") != null
                    ? ((Number) body.get("memoryBytes")).longValue() : null;
            String restartPolicy = (String) body.get("restartPolicy");
            Integer maxRetries = body.get("maxRetries") != null
                    ? ((Number) body.get("maxRetries")).intValue() : null;
            return ReturnResult.ok(dockerPanelService.updateContainer(
                    connectionId, containerId, cpuCpus, memoryBytes, restartPolicy, maxRetries));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/containers/{containerId}/diff")
    public ReturnResult<List<Map<String, Object>>> containerDiff(
            @PathVariable String connectionId,
            @PathVariable String containerId) {
        try {
            return ReturnResult.ok(dockerPanelService.getContainerDiff(connectionId, containerId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器差异查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/{containerId}/commit")
    public ReturnResult<String> commitContainer(
            @PathVariable String connectionId,
            @PathVariable String containerId,
            @RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(dockerPanelService.commitContainer(connectionId, containerId,
                    body.get("repository"), body.get("tag")));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器提交失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/containers/prune")
    public ReturnResult<Map<String, Object>> pruneContainers(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.pruneContainers(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 容器清理失败: " + e.getMessage());
        }
    }

    // ─── 镜像管理 ──────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/images")
    public ReturnResult<List<Map<String, Object>>> listImages(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.listImages(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像列表查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/images/{imageId}")
    public ReturnResult<Map<String, Object>> inspectImage(
            @PathVariable String connectionId,
            @PathVariable String imageId) {
        try {
            return ReturnResult.ok(dockerPanelService.inspectImage(connectionId, imageId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像详情查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/images/pull")
    public ReturnResult<Void> pullImage(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        try {
            String imageName = String.valueOf(body.get("imageName"));
            @SuppressWarnings("unchecked")
            Map<String, String> registryAuth = body.containsKey("registryAuth")
                    ? (Map<String, String>) body.get("registryAuth") : null;
            dockerPanelService.pullImage(connectionId, imageName, registryAuth);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像拉取失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/images/{imageId}/push")
    public ReturnResult<Void> pushImage(
            @PathVariable String connectionId,
            @PathVariable String imageId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> registryAuth = body != null && body.containsKey("registryAuth")
                    ? (Map<String, String>) body.get("registryAuth") : null;
            dockerPanelService.pushImage(connectionId, imageId, registryAuth);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像推送失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/images/{imageId}/tag")
    public ReturnResult<Void> tagImage(
            @PathVariable String connectionId,
            @PathVariable String imageId,
            @RequestBody Map<String, String> body) {
        try {
            dockerPanelService.tagImage(connectionId, imageId,
                    body.get("repository"), body.get("tag"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像标记失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/images/{imageId}")
    public ReturnResult<Void> removeImage(
            @PathVariable String connectionId,
            @PathVariable String imageId,
            @RequestParam(defaultValue = "false") boolean force) {
        try {
            dockerPanelService.removeImage(connectionId, imageId, force);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/images/search")
    public ReturnResult<List<Map<String, Object>>> searchImages(
            @PathVariable String connectionId,
            @RequestParam String term) {
        try {
            return ReturnResult.ok(dockerPanelService.searchImages(connectionId, term));
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像搜索失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/images/build")
    public ReturnResult<Map<String, Object>> buildImage(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        try {
            String dockerfile = String.valueOf(body.get("dockerfile"));
            @SuppressWarnings("unchecked")
            List<String> tags = body.containsKey("tags")
                    ? (List<String>) body.get("tags") : null;
            return ReturnResult.ok(dockerPanelService.buildImage(connectionId, dockerfile, tags));
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像构建失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/images/prune")
    public ReturnResult<Map<String, Object>> pruneImages(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.pruneImages(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 镜像清理失败: " + e.getMessage());
        }
    }

    // ─── 网络管理 ──────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/networks")
    public ReturnResult<List<Map<String, Object>>> listNetworks(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.listNetworks(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络列表查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/networks")
    public ReturnResult<String> createNetwork(
            @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(dockerPanelService.createNetwork(connectionId,
                    body.get("name"), body.get("driver")));
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/networks/{networkId}")
    public ReturnResult<Void> removeNetwork(
            @PathVariable String connectionId,
            @PathVariable String networkId) {
        try {
            dockerPanelService.removeNetwork(connectionId, networkId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/networks/{networkId}")
    public ReturnResult<Map<String, Object>> inspectNetwork(
            @PathVariable String connectionId,
            @PathVariable String networkId) {
        try {
            return ReturnResult.ok(dockerPanelService.inspectNetwork(connectionId, networkId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络详情查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/networks/{networkId}/connect")
    public ReturnResult<Void> connectContainerToNetwork(
            @PathVariable String connectionId,
            @PathVariable String networkId,
            @RequestBody Map<String, String> body) {
        try {
            dockerPanelService.connectContainerToNetwork(connectionId, networkId, body.get("containerId"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/networks/{networkId}/disconnect")
    public ReturnResult<Void> disconnectContainerFromNetwork(
            @PathVariable String connectionId,
            @PathVariable String networkId,
            @RequestBody Map<String, String> body) {
        try {
            dockerPanelService.disconnectContainerFromNetwork(connectionId, networkId, body.get("containerId"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络断开失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/networks/prune")
    public ReturnResult<Map<String, Object>> pruneNetworks(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.pruneNetworks(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 网络清理失败: " + e.getMessage());
        }
    }

    // ─── 卷管理 ────────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/volumes")
    public ReturnResult<List<Map<String, Object>>> listVolumes(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.listVolumes(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 卷列表查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/volumes")
    public ReturnResult<Map<String, Object>> createVolume(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        try {
            String volumeName = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String driver = body.containsKey("driver") ? String.valueOf(body.get("driver")) : null;
            @SuppressWarnings("unchecked")
            Map<String, String> driverOpts = body.containsKey("driverOpts")
                    ? (Map<String, String>) body.get("driverOpts") : null;
            return ReturnResult.ok(dockerPanelService.createVolume(connectionId, volumeName, driver, driverOpts));
        } catch (Exception e) {
            return ReturnResult.error("Docker 卷创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/volumes/{volumeName}")
    public ReturnResult<Void> removeVolume(
            @PathVariable String connectionId,
            @PathVariable String volumeName,
            @RequestParam(defaultValue = "false") boolean force) {
        try {
            dockerPanelService.removeVolume(connectionId, volumeName, force);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("Docker 卷删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/volumes/{volumeName}")
    public ReturnResult<Map<String, Object>> inspectVolume(
            @PathVariable String connectionId,
            @PathVariable String volumeName) {
        try {
            return ReturnResult.ok(dockerPanelService.inspectVolume(connectionId, volumeName));
        } catch (Exception e) {
            return ReturnResult.error("Docker 卷详情查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/volumes/prune")
    public ReturnResult<Map<String, Object>> pruneVolumes(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(dockerPanelService.pruneVolumes(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Docker 卷清理失败: " + e.getMessage());
        }
    }
}

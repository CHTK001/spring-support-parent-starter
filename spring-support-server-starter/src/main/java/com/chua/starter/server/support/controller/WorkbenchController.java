package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.model.vo.WorkbenchDockerCapabilityVO;
import com.chua.starter.server.support.model.vo.WorkbenchK8sCapabilityVO;
import com.chua.starter.server.support.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 主机工作台 —— Docker / Kubernetes 真实接口
 *
 * <p>端点与前端 {@code pages/server/src/workbench-api-contract.md} 严格对齐：
 * 前缀 {@code /ms/server/host/{hostId}/docker|kubernetes}，响应统一
 * {@code ReturnResult<T>}。能力探测返回 {@code installed=false} 时前端隐藏入口，
 * 报错（未安装等）返回 {@code code=1, msg=...} 供前端显示「未检测到服务」空态。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Tag(name = "监控-主机工作台", description = "Docker / Kubernetes 工作台（真实接口，SSH 执行 CLI）")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/{hostId}")
@RequiredArgsConstructor
public class WorkbenchController {

    /**
     * 工作台服务
     */
    private final WorkbenchService workbenchService;

    /* ==================== Docker ==================== */

    /**
     * 能力探测
     *
     * @param hostId 主机 标识
     * @return 能力结果
     */
    @Operation(summary = "Docker 能力探测")
    @GetMapping("/docker/capability")
    public ReturnResult<WorkbenchDockerCapabilityVO> dockerCapability(@PathVariable Long hostId) {
        return workbenchService.dockerCapability(hostId);
    }

    /**
     * 容器列表
     *
     * @param hostId 主机 标识
     * @param all    是否包含已停止容器
     * @return 容器列表
     */
    @Operation(summary = "Docker 容器列表")
    @GetMapping("/docker/containers")
    public ReturnResult<List<Map<String, Object>>> listDockerContainers(
            @PathVariable Long hostId,
            @RequestParam(required = false) Boolean all) {
        return workbenchService.listDockerContainers(hostId, all);
    }

    /**
     * 启动容器
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @return 操作结果
     */
    @Operation(summary = "启动容器")
    @PostMapping("/docker/containers/{containerId}/start")
    public ReturnResult<Boolean> startDockerContainer(@PathVariable Long hostId,
                                                      @PathVariable String containerId) {
        return workbenchService.dockerContainerAction(hostId, containerId, "start");
    }

    /**
     * 停止容器
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @return 操作结果
     */
    @Operation(summary = "停止容器")
    @PostMapping("/docker/containers/{containerId}/stop")
    public ReturnResult<Boolean> stopDockerContainer(@PathVariable Long hostId,
                                                     @PathVariable String containerId) {
        return workbenchService.dockerContainerAction(hostId, containerId, "stop");
    }

    /**
     * 重启容器
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @return 操作结果
     */
    @Operation(summary = "重启容器")
    @PostMapping("/docker/containers/{containerId}/restart")
    public ReturnResult<Boolean> restartDockerContainer(@PathVariable Long hostId,
                                                        @PathVariable String containerId) {
        return workbenchService.dockerContainerAction(hostId, containerId, "restart");
    }

    /**
     * 容器最近日志（一次性返回，非流式）
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @param tail        尾部行数
     * @return 日志文本
     */
    @Operation(summary = "容器日志（一次性）")
    @PostMapping("/docker/containers/{containerId}/logs")
    public ReturnResult<String> dockerContainerLogs(@PathVariable Long hostId,
                                                    @PathVariable String containerId,
                                                    @RequestParam(required = false) Integer tail) {
        return workbenchService.dockerContainerLogs(hostId, containerId, tail);
    }

    /**
     * 容器日志实时流（SSE 异步推送）
     * <p>使用 docker logs -f 持续跟随容器日志，每行作为一条 SSE 事件推送。
     * 客户端断开连接时自动停止跟随并释放 SSH 连接。</p>
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @param tail        起始尾部行数（默认 100）
     * @return SSE 发射器
     */
    @Operation(summary = "容器日志实时流（SSE）")
    @GetMapping(value = "/docker/containers/{containerId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamContainerLogs(@PathVariable Long hostId,
                                           @PathVariable String containerId,
                                           @RequestParam(required = false) Integer tail) {
        return workbenchService.streamContainerLogs(hostId, containerId, tail);
    }

    /**
     * 进入容器（SSE 异步流式交互）
     * <p>通过 SSH PTY 建立交互式会话，执行 docker exec -it 进入容器 shell。
     * 容器输出经 SSE 推送，客户端输入经 /exec/input 端点发送。</p>
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @param body        请求体：command（容器内命令，默认 /bin/bash）
     * @return SSE 发射器
     */
    @Operation(summary = "进入容器（SSE）")
    @PostMapping(value = "/docker/containers/{containerId}/exec", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter dockerContainerExec(@PathVariable Long hostId,
                                           @PathVariable String containerId,
                                           @RequestBody(required = false) Map<String, String> body) {
        String command = body != null && body.get("command") != null ? body.get("command") : "/bin/bash";
        return workbenchService.dockerContainerExec(hostId, containerId, command);
    }

    /**
     * 发送交互输入到容器会话
     * <p>向活跃的 docker exec 会话发送输入（按键/命令文本）。</p>
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @param body        请求体：sessionId（会话标识）、input（输入文本）
     * @return 操作结果
     */
    @Operation(summary = "容器交互输入")
    @PostMapping("/docker/containers/{containerId}/exec/input")
    public ReturnResult<Void> dockerContainerExecInput(@PathVariable Long hostId,
                                                        @PathVariable String containerId,
                                                        @RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String input = body.get("input");
        return workbenchService.dockerContainerExecInput(hostId, containerId, sessionId, input);
    }

    /**
     * 关闭容器交互会话
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID
     * @param body        请求体：sessionId（会话标识）
     * @return 操作结果
     */
    @Operation(summary = "关闭容器交互会话")
    @PostMapping("/docker/containers/{containerId}/exec/close")
    public ReturnResult<Void> dockerContainerExecClose(@PathVariable Long hostId,
                                                        @PathVariable String containerId,
                                                        @RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        return workbenchService.dockerContainerExecClose(hostId, containerId, sessionId);
    }

    /**
     * 镜像列表
     *
     * @param hostId 主机 标识
     * @return 镜像列表
     */
    @Operation(summary = "Docker 镜像列表")
    @GetMapping("/docker/images")
    public ReturnResult<List<Map<String, Object>>> listDockerImages(@PathVariable Long hostId) {
        return workbenchService.listDockerImages(hostId);
    }

    /**
     * 网络列表
     *
     * @param hostId 主机 标识
     * @return 网络列表
     */
    @Operation(summary = "Docker 网络列表")
    @GetMapping("/docker/networks")
    public ReturnResult<List<Map<String, Object>>> listDockerNetworks(@PathVariable Long hostId) {
        return workbenchService.listDockerNetworks(hostId);
    }

    /**
     * 卷列表
     *
     * @param hostId 主机 标识
     * @return 卷列表
     */
    @Operation(summary = "Docker 卷列表")
    @GetMapping("/docker/volumes")
    public ReturnResult<List<Map<String, Object>>> listDockerVolumes(@PathVariable Long hostId) {
        return workbenchService.listDockerVolumes(hostId);
    }

    /**
     * 清理悬空镜像与停用卷
     *
     * @param hostId 主机 标识
     * @return 清理结果
     */
    @Operation(summary = "Docker 清理")
    @PostMapping("/docker/prune")
    public ReturnResult<Map<String, Object>> dockerPrune(@PathVariable Long hostId) {
        return workbenchService.dockerPrune(hostId);
    }

    /* ==================== Kubernetes ==================== */

    /**
     * 能力探测
     *
     * @param hostId 主机 标识
     * @return 能力结果
     */
    @Operation(summary = "Kubernetes 能力探测")
    @GetMapping("/kubernetes/capability")
    public ReturnResult<WorkbenchK8sCapabilityVO> k8sCapability(@PathVariable Long hostId) {
        return workbenchService.k8sCapability(hostId);
    }

    /**
     * 集群节点
     *
     * @param hostId 主机 标识
     * @return 集群视图
     */
    @Operation(summary = "Kubernetes 集群节点")
    @GetMapping("/kubernetes/cluster")
    public ReturnResult<Map<String, Object>> k8sCluster(@PathVariable Long hostId) {
        return workbenchService.k8sCluster(hostId);
    }

    /**
     * 工作负载列表
     *
     * @param hostId    主机 标识
     * @param namespace 命名空间过滤
     * @return 工作负载列表
     */
    @Operation(summary = "Kubernetes 工作负载")
    @GetMapping("/kubernetes/workloads")
    public ReturnResult<List<Map<String, Object>>> k8sWorkloads(@PathVariable Long hostId,
                                                                @RequestParam(required = false) String namespace) {
        return workbenchService.k8sWorkloads(hostId, namespace);
    }

    /**
     * 命名空间列表
     *
     * @param hostId 主机 标识
     * @return 命名空间列表
     */
    @Operation(summary = "Kubernetes 命名空间")
    @GetMapping("/kubernetes/namespaces")
    public ReturnResult<List<String>> k8sNamespaces(@PathVariable Long hostId) {
        return workbenchService.k8sNamespaces(hostId);
    }

    /**
     * 工作负载扩缩容
     *
     * @param hostId   主机 标识
     * @param name     工作负载名称
     * @param kind     工作负载类型
     * @param ns       命名空间
     * @param replicas 目标副本数
     * @return 操作结果
     */
    @Operation(summary = "工作负载扩缩容")
    @PostMapping("/kubernetes/workloads/{name}/scale")
    public ReturnResult<Boolean> k8sScale(@PathVariable Long hostId,
                                          @PathVariable String name,
                                          @RequestParam(required = false) String kind,
                                          @RequestParam(required = false) String ns,
                                          @RequestParam Integer replicas) {
        return workbenchService.k8sScale(hostId, name, kind, ns, replicas);
    }

    /**
     * 工作负载滚动重启
     *
     * @param hostId 主机 标识
     * @param name   工作负载名称
     * @param kind   工作负载类型
     * @param ns     命名空间
     * @return 操作结果
     */
    @Operation(summary = "工作负载滚动重启")
    @PostMapping("/kubernetes/workloads/{name}/restart")
    public ReturnResult<Boolean> k8sRestart(@PathVariable Long hostId,
                                            @PathVariable String name,
                                            @RequestParam(required = false) String kind,
                                            @RequestParam(required = false) String ns) {
        return workbenchService.k8sRestart(hostId, name, kind, ns);
    }
}
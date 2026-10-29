package com.chua.starter.docker;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.docker.model.DockerNetworkRequest;
import com.chua.starter.docker.model.DockerVolumeRequest;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Docker 管理控制器 —— 独立模块的主机维度 REST 入口。
 *
 * <p>路径前缀 {@code /ms/server/docker/{hostId}}，与主机工作台的
 * {@code /ms/server/host/{hostId}/docker} 明确分开：前者是完整的 Docker 管理台
 * （列表 / 详情 / 创建 / 删除 / 拉取 / 日志 / 终端 / 镜像 / 网络 / 卷），
 * 后者只保留旧页面用到的那几个端点，互不影响。</p>
 *
 * <p>SSE 端点（创建容器、卸载容器/镜像、拉取镜像、日志流、进入容器）统一用
 * {@code text/event-stream} 输出，事件名见各方法说明。</p>
 *
 * <p>镜像库与模板不带 hostId，见 {@link com.chua.starter.docker.controller.RegistryController}
 * 与 {@link com.chua.starter.docker.controller.TemplateController}。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Tag(name = "Docker 管理", description = "容器 / 镜像 / 网络 / 卷（SSH 执行 docker CLI）")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/docker/{hostId}")
@RequiredArgsConstructor
public class DockerController {

    /**
     * Docker 服务
     */
    private final DockerService dockerService;

    /**
     * 能力探测
     *
     * @param hostId 主机标识
     * @return {installed, version, compose, runtime}
     */
    @Operation(summary = "Docker 能力探测")
    @GetMapping("/capability")
    public ReturnResult<Map<String, Object>> capability(@PathVariable Long hostId) {
        return dockerService.capability(hostId);
    }

    /**
     * 容器列表
     *
     * @param hostId 主机标识
     * @param all    是否包含已停止容器
     * @return 容器列表
     */
    @Operation(summary = "容器列表")
    @GetMapping("/containers")
    public ReturnResult<List<Map<String, Object>>> containers(@PathVariable Long hostId,
                                                              @RequestParam(required = false) Boolean all) {
        return dockerService.containers(hostId, all);
    }

    /**
     * 容器详情
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @return 详情
     */
    @Operation(summary = "容器详情")
    @GetMapping("/containers/{id}")
    public ReturnResult<Map<String, Object>> containerDetail(@PathVariable Long hostId,
                                                             @PathVariable String id) {
        return dockerService.containerDetail(hostId, id);
    }

    /**
     * 启动 / 停止 / 重启 / 暂停 / 恢复 / 强杀容器
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param action 动作
     * @return 是否成功
     */
    @Operation(summary = "容器生命周期操作")
    @PostMapping("/containers/{id}/{action}")
    public ReturnResult<Boolean> containerAction(@PathVariable Long hostId,
                                                 @PathVariable String id,
                                                 @PathVariable String action) {
        return dockerService.containerAction(hostId, id, action);
    }

    /**
     * 删除容器（同步版，保留给程序调用）
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param force  是否强制删除
     * @return 是否成功
     */
    @Operation(summary = "删除容器")
    @DeleteMapping("/containers/{id}")
    public ReturnResult<Boolean> removeContainer(@PathVariable Long hostId,
                                                 @PathVariable String id,
                                                 @RequestParam(required = false) Boolean force) {
        return dockerService.removeContainer(hostId, id, force);
    }

    /**
     * 卸载容器（SSE 实时回显）
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param force  是否强制删除
     * @return SSE 发射器（事件 step|log|error|done）
     */
    @Operation(summary = "卸载容器（SSE 实时回显）")
    @PostMapping(value = "/containers/{id}/remove-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter removeContainerStream(@PathVariable Long hostId,
                                            @PathVariable String id,
                                            @RequestParam(required = false) Boolean force) {
        return dockerService.removeContainerStream(hostId, id, force);
    }

    /**
     * 创建并启动容器（SSE 实时进度）
     *
     * @param hostId  主机标识
     * @param request 创建请求（可带 registryId / templateId）
     * @return SSE 发射器
     */
    @Operation(summary = "创建并启动容器（SSE 实时进度）")
    @PostMapping(value = "/containers/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runContainer(@PathVariable Long hostId,
                                   @RequestBody DockerCreateRequest request) {
        return dockerService.runContainer(hostId, request);
    }

    /**
     * 容器最近日志（一次性）
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param tail   尾部行数
     * @return 日志文本
     */
    @Operation(summary = "容器日志（一次性）")
    @GetMapping("/containers/{id}/logs")
    public ReturnResult<String> containerLogs(@PathVariable Long hostId,
                                              @PathVariable String id,
                                              @RequestParam(required = false) Integer tail) {
        return dockerService.containerLogs(hostId, id, tail);
    }

    /**
     * 容器日志实时流（SSE）
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param tail   起始尾部行数
     * @return SSE 发射器
     */
    @Operation(summary = "容器日志实时流（SSE）")
    @GetMapping(value = "/containers/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamContainerLogs(@PathVariable Long hostId,
                                          @PathVariable String id,
                                          @RequestParam(required = false) Integer tail) {
        return dockerService.streamContainerLogs(hostId, id, tail);
    }

    /**
     * 进入容器（SSE 交互式 PTY）
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param body   {command}
     * @return SSE 发射器
     */
    @Operation(summary = "进入容器（SSE）")
    @PostMapping(value = "/containers/{id}/exec", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter execContainer(@PathVariable Long hostId,
                                    @PathVariable String id,
                                    @RequestBody(required = false) Map<String, String> body) {
        String command = body != null && body.get("command") != null ? body.get("command") : "/bin/sh";
        return dockerService.execContainer(hostId, id, command);
    }

    /**
     * 容器交互输入
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param body   {sessionId, input}
     * @return 操作结果
     */
    @Operation(summary = "容器交互输入")
    @PostMapping("/containers/{id}/exec/input")
    public ReturnResult<Void> execInput(@PathVariable Long hostId,
                                        @PathVariable String id,
                                        @RequestBody Map<String, String> body) {
        return dockerService.execInput(hostId, id, body.get("sessionId"), body.get("input"));
    }

    /**
     * 关闭容器交互会话
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param body   {sessionId}
     * @return 操作结果
     */
    @Operation(summary = "关闭容器交互会话")
    @PostMapping("/containers/{id}/exec/close")
    public ReturnResult<Void> execClose(@PathVariable Long hostId,
                                        @PathVariable String id,
                                        @RequestBody Map<String, String> body) {
        return dockerService.execClose(hostId, id, body.get("sessionId"));
    }

    /**
     * 镜像列表
     *
     * @param hostId 主机标识
     * @return 镜像列表
     */
    @Operation(summary = "镜像列表")
    @GetMapping("/images")
    public ReturnResult<List<Map<String, Object>>> images(@PathVariable Long hostId) {
        return dockerService.images(hostId);
    }

    /**
     * 拉取镜像（SSE 实时进度）
     *
     * @param hostId     主机标识
     * @param image      镜像名
     * @param registryId 镜像库标识，可空
     * @return SSE 发射器
     */
    @Operation(summary = "拉取镜像（SSE 实时进度）")
    @PostMapping(value = "/images/pull", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pullImage(@PathVariable Long hostId,
                                @RequestParam String image,
                                @RequestParam(required = false) String registryId) {
        return dockerService.pullImage(hostId, image, registryId);
    }

    /**
     * 删除镜像（同步版，保留给程序调用）
     *
     * @param hostId  主机标识
     * @param imageId 镜像 ID 或 repository:tag
     * @param force   是否强制删除
     * @return 是否成功
     */
    @Operation(summary = "删除镜像")
    @DeleteMapping("/images/{imageId}")
    public ReturnResult<Boolean> removeImage(@PathVariable Long hostId,
                                             @PathVariable String imageId,
                                             @RequestParam(required = false) Boolean force) {
        return dockerService.removeImage(hostId, imageId, force);
    }

    /**
     * 卸载镜像（SSE 实时回显）
     *
     * @param hostId  主机标识
     * @param imageId 镜像 ID 或 repository:tag
     * @param force   是否强制删除
     * @return SSE 发射器（事件 step|log|error|done）
     */
    @Operation(summary = "卸载镜像（SSE 实时回显）")
    @PostMapping(value = "/images/{imageId}/remove-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter removeImageStream(@PathVariable Long hostId,
                                        @PathVariable String imageId,
                                        @RequestParam(required = false) Boolean force) {
        return dockerService.removeImageStream(hostId, imageId, force);
    }

    /**
     * 网络列表
     *
     * @param hostId 主机标识
     * @return 网络列表
     */
    @Operation(summary = "网络列表")
    @GetMapping("/networks")
    public ReturnResult<List<Map<String, Object>>> networks(@PathVariable Long hostId) {
        return dockerService.networks(hostId);
    }

    /**
     * 卷列表
     *
     * @param hostId 主机标识
     * @return 卷列表
     */
    @Operation(summary = "卷列表")
    @GetMapping("/volumes")
    public ReturnResult<List<Map<String, Object>>> volumes(@PathVariable Long hostId) {
        return dockerService.volumes(hostId);
    }

    /**
     * 清理悬空资源
     *
     * @param hostId 主机标识
     * @return 清理输出
     */
    @Operation(summary = "清理悬空资源")
    @PostMapping("/prune")
    public ReturnResult<Map<String, Object>> prune(@PathVariable Long hostId) {
        return dockerService.prune(hostId);
    }

    /**
     * 创建网络（{@code docker network create}）
     *
     * @param hostId  主机标识
     * @param request 网络创建请求（name / driver / options）
     * @return 网络视图
     */
    @Operation(summary = "创建网络")
    @PostMapping("/networks")
    public ReturnResult<Map<String, Object>> createNetwork(@PathVariable Long hostId,
                                                           @RequestBody DockerNetworkRequest request) {
        return dockerService.createNetwork(hostId, request);
    }

    /**
     * 删除网络（{@code docker network rm}）
     *
     * @param hostId 主机标识
     * @param id     网络标识（id 或名称）
     * @return 是否成功
     */
    @Operation(summary = "删除网络")
    @DeleteMapping("/networks/{id}")
    public ReturnResult<Boolean> removeNetwork(@PathVariable Long hostId,
                                               @PathVariable String id) {
        return dockerService.removeNetwork(hostId, id);
    }

    /**
     * 创建卷（{@code docker volume create}）
     *
     * @param hostId  主机标识
     * @param request 卷创建请求（name / driver / options）
     * @return 卷视图
     */
    @Operation(summary = "创建卷")
    @PostMapping("/volumes")
    public ReturnResult<Map<String, Object>> createVolume(@PathVariable Long hostId,
                                                          @RequestBody DockerVolumeRequest request) {
        return dockerService.createVolume(hostId, request);
    }

    /**
     * 删除卷（{@code docker volume rm}）
     *
     * @param hostId 主机标识
     * @param name   卷名称
     * @return 是否成功
     */
    @Operation(summary = "删除卷")
    @DeleteMapping("/volumes/{name}")
    public ReturnResult<Boolean> removeVolume(@PathVariable Long hostId,
                                              @PathVariable String name) {
        return dockerService.removeVolume(hostId, name);
    }
}

package com.chua.starter.docker;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.docker.model.DockerNetworkRequest;
import com.chua.starter.docker.model.DockerVolumeRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Docker 管理服务 —— 独立模块的对外契约。
 *
 * <p>本模块与主机工作台（分屏 / K8s / 隧道）解耦：容器生命周期、镜像、网络、
 * 卷、日志与交互式终端全部在本模块内完成，前端也有独立的 Docker 页面与 API
 * 文件，工作台不再承载 Docker 的页面结构。</p>
 *
 * <p>所有能力经 SSH 在受控主机上执行 {@code docker} CLI 落地，不要求额外暴露
 * Docker TCP 端口。命令失败时区分「未安装 Docker」与「执行失败」两类，前端据此
 * 显示空态或错误原因。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public interface DockerService {

    /**
     * 能力探测：{@code docker version} 可解析出版本号即视为已安装。
     *
     * @param hostId 主机标识
     * @return 能力结果（未安装也返回 ok，installed=false）
     */
    ReturnResult<Map<String, Object>> capability(Long hostId);

    /**
     * 容器列表。
     *
     * @param hostId 主机标识
     * @param all    是否包含已停止容器，默认 true
     * @return 容器视图列表
     */
    ReturnResult<List<Map<String, Object>>> containers(Long hostId, Boolean all);

    /**
     * 容器详情（{@code docker inspect} 的裁剪投影）。
     *
     * <p>只保留页面真正会展示的字段：状态、镜像、端口、挂载、环境变量、网络、
     * 重启策略、健康检查。原始 JSON 可达数百 KB，整包透传会让详情页首帧卡住。</p>
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @return 详情
     */
    ReturnResult<Map<String, Object>> containerDetail(Long hostId, String id);

    /**
     * 容器生命周期操作。
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param action start / stop / restart / pause / unpause / kill
     * @return 是否成功
     */
    ReturnResult<Boolean> containerAction(Long hostId, String id, String action);

    /**
     * 删除容器。
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param force  是否强制删除（运行中容器需 force=true）
     * @return 是否成功
     */
    ReturnResult<Boolean> removeContainer(Long hostId, String id, Boolean force);

    /**
     * 删除容器，进度以 SSE 实时推送。
     *
     * <p>事件：{@code step}（阶段说明）、{@code log}（命令行输出增量）、
     * {@code done}（成功）、{@code error}（失败原因）。</p>
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param force  是否强制删除
     * @return SSE 发射器
     */
    SseEmitter removeContainerStream(Long hostId, String id, Boolean force);

    /**
     * 创建并启动容器，进度以 SSE 实时推送。
     *
     * <p>事件：{@code step}（阶段说明）、{@code log}（命令行输出增量）、
     * {@code done}（成功，data 为容器 ID）、{@code error}（失败原因）。</p>
     *
     * @param hostId  主机标识
     * @param request 创建请求
     * @return SSE 发射器
     */
    SseEmitter runContainer(Long hostId, DockerCreateRequest request);

    /**
     * 容器最近日志（一次性）。
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param tail   尾部行数，默认 200
     * @return 日志文本
     */
    ReturnResult<String> containerLogs(Long hostId, String id, Integer tail);

    /**
     * 容器日志实时流（{@code docker logs -f}）。
     *
     * @param hostId 主机标识
     * @param id     容器 ID 或名称
     * @param tail   起始尾部行数，默认 100
     * @return SSE 发射器
     */
    SseEmitter streamContainerLogs(Long hostId, String id, Integer tail);

    /**
     * 进入容器（交互式 PTY）。
     *
     * @param hostId  主机标识
     * @param id      容器 ID 或名称
     * @param command 容器内命令，默认 /bin/sh
     * @return SSE 发射器
     */
    SseEmitter execContainer(Long hostId, String id, String command);

    /**
     * 向容器交互会话发送输入。
     *
     * @param hostId    主机标识
     * @param id        容器 ID 或名称
     * @param sessionId 会话标识
     * @param input     输入文本
     * @return 操作结果
     */
    ReturnResult<Void> execInput(Long hostId, String id, String sessionId, String input);

    /**
     * 关闭容器交互会话。
     *
     * @param hostId    主机标识
     * @param id        容器 ID 或名称
     * @param sessionId 会话标识
     * @return 操作结果
     */
    ReturnResult<Void> execClose(Long hostId, String id, String sessionId);

    /**
     * 镜像列表。
     *
     * @param hostId 主机标识
     * @return 镜像视图列表
     */
    ReturnResult<List<Map<String, Object>>> images(Long hostId);

    /**
     * 拉取镜像，进度以 SSE 实时推送（{@code docker pull} 的逐行输出）。
     *
     * @param hostId 主机标识
     * @param image  镜像名（含 tag）
     * @return SSE 发射器
     */
    default SseEmitter pullImage(Long hostId, String image) {
        return pullImage(hostId, image, null);
    }

    /**
     * 从指定镜像库拉取镜像，进度以 SSE 实时推送。
     *
     * <p>传了 {@code registryId} 时先按镜像库凭据在会话内 {@code docker login}，
     * 再用该库的 {@code pullRef}（registry host + 命名空间 + 镜像）作为拉取目标。</p>
     *
     * @param hostId     主机标识
     * @param image      镜像名（含 tag）
     * @param registryId 镜像库标识，可空（空则直接用镜像名）
     * @return SSE 发射器
     */
    SseEmitter pullImage(Long hostId, String image, String registryId);

    /**
     * 删除镜像。
     *
     * @param hostId  主机标识
     * @param imageId 镜像 ID 或 repository:tag
     * @param force   是否强制删除
     * @return 是否成功
     */
    ReturnResult<Boolean> removeImage(Long hostId, String imageId, Boolean force);

    /**
     * 删除镜像，进度以 SSE 实时推送。
     *
     * @param hostId  主机标识
     * @param imageId 镜像 ID 或 repository:tag
     * @param force   是否强制删除
     * @return SSE 发射器
     */
    SseEmitter removeImageStream(Long hostId, String imageId, Boolean force);

    /**
     * 网络列表。
     *
     * @param hostId 主机标识
     * @return 网络视图列表
     */
    ReturnResult<List<Map<String, Object>>> networks(Long hostId);

    /**
     * 卷列表。
     *
     * @param hostId 主机标识
     * @return 卷视图列表
     */
    ReturnResult<List<Map<String, Object>>> volumes(Long hostId);

    /**
     * 创建网络（{@code docker network create}）。
     *
     * @param hostId  主机标识
     * @param request 网络创建请求（name / driver / options）
     * @return 网络视图
     */
    ReturnResult<Map<String, Object>> createNetwork(Long hostId, DockerNetworkRequest request);

    /**
     * 删除网络（{@code docker network rm}）。
     *
     * @param hostId 主机标识
     * @param id     网络标识（id 或名称）
     * @return 是否成功
     */
    ReturnResult<Boolean> removeNetwork(Long hostId, String id);

    /**
     * 创建卷（{@code docker volume create}）。
     *
     * @param hostId  主机标识
     * @param request 卷创建请求（name / driver / options）
     * @return 卷视图
     */
    ReturnResult<Map<String, Object>> createVolume(Long hostId, DockerVolumeRequest request);

    /**
     * 删除卷（{@code docker volume rm}）。
     *
     * @param hostId 主机标识
     * @param name   卷名称
     * @return 是否成功
     */
    ReturnResult<Boolean> removeVolume(Long hostId, String name);

    /**
     * 清理悬空镜像、停止的容器与未使用卷。
     *
     * @param hostId 主机标识
     * @return 清理输出
     */
    ReturnResult<Map<String, Object>> prune(Long hostId);
}

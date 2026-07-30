package com.chua.starter.panel.service;

import com.github.dockerjava.api.DockerClient;

import java.util.List;
import java.util.Map;

/**
 * Docker 面板服务。
 * 使用 DockerProtocolClient（docker-java SDK）实现非终端 Docker 管理，
 * 支持 TCP/HTTP/HTTPS/Unix Socket 连接方式。
 *
 * @author CH
 */
@SuppressWarnings("unused")
public interface DockerPanelService {

    // ─── 连接管理 ──────────────────────────────────────────────────────────

    /**
     * 打开 Docker 连接（TCP/HTTP/HTTPS）。
     *
     * @param host     主机地址
     * @param port     TCP 端口（默认 2375/2376）
     * @param protocol 协议（tcp/http/https）
     * @return 连接标识
     */
    String open(String host, int port, String protocol);

    /**
     * 打开 Docker 连接（Unix Socket）。
     *
     * @param host      主机地址（可传 localhost）
     * @param port      端口（Socket 方式传 0）
     * @param protocol  协议（unix/socket）
     * @param socketPath Socket 路径（如 /var/run/docker.sock）
     * @return 连接标识
     */
    String open(String host, int port, String protocol, String socketPath);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    // ─── 系统管理 ──────────────────────────────────────────────────────────

    /**
     * Ping Docker 守护进程。
     *
     * @param connectionId 连接标识
     * @return pong 结果
     */
    String ping(String connectionId);

    /**
     * 查询 Docker 系统信息。
     *
     * @param connectionId 连接标识
     * @return 系统信息
     */
    Map<String, Object> info(String connectionId);

    /**
     * 查询 Docker 版本信息。
     *
     * @param connectionId 连接标识
     * @return 版本信息
     */
    Map<String, Object> version(String connectionId);

    /**
     * 查询磁盘使用情况（Docker System DF）。
     *
     * @param connectionId 连接标识
     * @return 磁盘使用信息
     */
    Map<String, Object> diskUsage(String connectionId);

    /**
     * 查询 Docker 事件。
     *
     * @param connectionId 连接标识
     * @param since  起始时间戳（秒，0 不限制）
     * @param until  截止时间戳（秒，0 不限制）
     * @param type   过滤类型（container/image/volume/network/daemon，空返回全部）
     * @return 事件列表
     */
    List<Map<String, Object>> events(String connectionId, long since, long until, String type);

    /**
     * Registry 认证。
     *
     * @param connectionId 连接标识
     * @param username 用户名
     * @param password 密码
     * @param serverAddress 服务器地址
     * @return 认证结果
     */
    Map<String, Object> auth(String connectionId, String username, String password, String serverAddress);

    // ─── 容器管理 ──────────────────────────────────────────────────────────

    /**
     * 查询容器列表。
     *
     * @param connectionId 连接标识
     * @param showAll      是否显示所有（包括已停止）
     * @return 容器列表
     */
    List<Map<String, Object>> listContainers(String connectionId, boolean showAll);

    /**
     * 查看容器详情。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @return 容器详情
     */
    Map<String, Object> inspectContainer(String connectionId, String containerId);

    /**
     * 创建容器。
     *
     * @param connectionId 连接标识
     * @param imageName    镜像名
     * @param containerName 容器名（可选）
     * @param env          环境变量（可选）
     * @param ports        端口映射（可选）
     * @param volumes      挂载卷（可选）
     * @return 容器 ID
     */
    String createContainer(String connectionId, String imageName, String containerName,
                           Map<String, String> env, Map<String, String> ports,
                           Map<String, String> volumes);

    /**
     * 启动容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     */
    void startContainer(String connectionId, String containerId);

    /**
     * 停止容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     */
    void stopContainer(String connectionId, String containerId);

    /**
     * 重启容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     */
    void restartContainer(String connectionId, String containerId);

    /**
     * 强制终止容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param signal       信号（如 SIGTERM, SIGKILL，空则默认 SIGKILL）
     */
    void killContainer(String connectionId, String containerId, String signal);

    /**
     * 暂停容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     */
    void pauseContainer(String connectionId, String containerId);

    /**
     * 恢复暂停的容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     */
    void unpauseContainer(String connectionId, String containerId);

    /**
     * 重命名容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param newName      新名称
     */
    void renameContainer(String connectionId, String containerId, String newName);

    /**
     * 删除容器。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param force        是否强制删除
     */
    void removeContainer(String connectionId, String containerId, boolean force);

    /**
     * 更新容器资源配置（docker update）。
     * 支持在线修改 CPU、内存、重启策略等。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param cpuCpus      CPU 数量（如 "1.5"，null 不修改）
     * @param memoryBytes  内存限制（字节，null 不修改）
     * @param restartPolicy 重启策略（如 "always"、"no"、"on-failure"、"unless-stopped"，null 不修改）
     * @param maxRetries   最大重试次数（仅 restartPolicy=on-failure 时有效，null 不修改）
     * @return 更新后的容器详情
     */
    Map<String, Object> updateContainer(String connectionId, String containerId,
                                         String cpuCpus, Long memoryBytes,
                                         String restartPolicy, Integer maxRetries);

    /**
     * 获取容器日志。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param tail         返回最后 N 行
     * @return 日志文本
     */
    String containerLogs(String connectionId, String containerId, int tail);

    /**
     * 获取容器资源统计。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @return 统计信息（CPU/内存/网络/IO）
     */
    Map<String, Object> getContainerStats(String connectionId, String containerId);

    /**
     * 获取容器内进程列表。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param psArgs       ps 参数（可选，如 "aux"）
     * @return 进程列表
     */
    List<Map<String, Object>> getContainerProcesses(String connectionId, String containerId, String psArgs);

    /**
     * 在容器内执行命令。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param command      命令及参数
     * @return 执行结果
     */
    String execInContainer(String connectionId, String containerId, String[] command);

    /**
     * 查看容器文件系统变更。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @return 变更记录
     */
    List<Map<String, Object>> getContainerDiff(String connectionId, String containerId);

    /**
     * 提交容器为镜像。
     *
     * @param connectionId 连接标识
     * @param containerId  容器 ID
     * @param repository   仓库名
     * @param tag          标签
     * @return 镜像 ID
     */
    String commitContainer(String connectionId, String containerId, String repository, String tag);

    /**
     * 清理已停止的容器。
     *
     * @param connectionId 连接标识
     * @return 清理结果
     */
    Map<String, Object> pruneContainers(String connectionId);

    // ─── 镜像管理 ──────────────────────────────────────────────────────────

    /**
     * 查询镜像列表。
     *
     * @param connectionId 连接标识
     * @return 镜像列表
     */
    List<Map<String, Object>> listImages(String connectionId);

    /**
     * 拉取镜像。
     *
     * @param connectionId 连接标识
     * @param imageName    镜像名（如 nginx:latest）
     * @param registryAuth 仓库认证信息（可选）
     */
    void pullImage(String connectionId, String imageName, Map<String, String> registryAuth);

    /**
     * 推送镜像。
     *
     * @param connectionId 连接标识
     * @param imageName    镜像名
     * @param registryAuth 仓库认证信息（可选）
     */
    void pushImage(String connectionId, String imageName, Map<String, String> registryAuth);

    /**
     * 标记镜像。
     *
     * @param connectionId 连接标识
     * @param imageId      镜像 ID
     * @param repository   仓库名
     * @param tag          标签
     */
    void tagImage(String connectionId, String imageId, String repository, String tag);

    /**
     * 删除镜像。
     *
     * @param connectionId 连接标识
     * @param imageId      镜像 ID
     * @param force        是否强制删除
     */
    void removeImage(String connectionId, String imageId, boolean force);

    /**
     * 查看镜像详情。
     *
     * @param connectionId 连接标识
     * @param imageId      镜像 ID
     * @return 镜像详情
     */
    Map<String, Object> inspectImage(String connectionId, String imageId);

    /**
     * 搜索 Docker Hub 镜像。
     *
     * @param connectionId 连接标识
     * @param term         搜索关键词
     * @return 搜索结果
     */
    List<Map<String, Object>> searchImages(String connectionId, String term);

    /**
     * 从 Dockerfile 构建镜像。
     *
     * @param connectionId 连接标识
     * @param dockerfile   Dockerfile 内容
     * @param tags         标签列表
     * @return 构建结果
     */
    Map<String, Object> buildImage(String connectionId, String dockerfile, List<String> tags);

    /**
     * 清理未使用的镜像。
     *
     * @param connectionId 连接标识
     * @return 清理结果
     */
    Map<String, Object> pruneImages(String connectionId);

    // ─── 网络管理 ──────────────────────────────────────────────────────────

    /**
     * 查询网络列表。
     *
     * @param connectionId 连接标识
     * @return 网络列表
     */
    List<Map<String, Object>> listNetworks(String connectionId);

    /**
     * 创建网络。
     *
     * @param connectionId 连接标识
     * @param networkName  网络名
     * @param driver       驱动（bridge/overlay/host/none，可选）
     * @return 网络 ID
     */
    String createNetwork(String connectionId, String networkName, String driver);

    /**
     * 删除网络。
     *
     * @param connectionId 连接标识
     * @param networkId    网络 ID
     */
    void removeNetwork(String connectionId, String networkId);

    /**
     * 查看网络详情。
     *
     * @param connectionId 连接标识
     * @param networkId    网络 ID
     * @return 网络详情
     */
    Map<String, Object> inspectNetwork(String connectionId, String networkId);

    /**
     * 将容器连接到网络。
     *
     * @param connectionId 连接标识
     * @param networkId    网络 ID
     * @param containerId  容器 ID
     */
    void connectContainerToNetwork(String connectionId, String networkId, String containerId);

    /**
     * 将容器从网络断开。
     *
     * @param connectionId 连接标识
     * @param networkId    网络 ID
     * @param containerId  容器 ID
     */
    void disconnectContainerFromNetwork(String connectionId, String networkId, String containerId);

    /**
     * 清理未使用的网络。
     *
     * @param connectionId 连接标识
     * @return 清理结果
     */
    Map<String, Object> pruneNetworks(String connectionId);

    // ─── 卷管理 ────────────────────────────────────────────────────────────

    /**
     * 查询卷列表。
     *
     * @param connectionId 连接标识
     * @return 卷列表
     */
    List<Map<String, Object>> listVolumes(String connectionId);

    /**
     * 创建卷。
     *
     * @param connectionId 连接标识
     * @param volumeName   卷名
     * @param driver       驱动（local 等，可选）
     * @param driverOpts   驱动选项（可选）
     * @return 卷详情
     */
    Map<String, Object> createVolume(String connectionId, String volumeName, String driver,
                                     Map<String, String> driverOpts);

    /**
     * 删除卷。
     *
     * @param connectionId 连接标识
     * @param volumeName   卷名
     * @param force        是否强制删除
     */
    void removeVolume(String connectionId, String volumeName, boolean force);

    /**
     * 查看卷详情。
     *
     * @param connectionId 连接标识
     * @param volumeName   卷名
     * @return 卷详情
     */
    Map<String, Object> inspectVolume(String connectionId, String volumeName);

    /**
     * 清理未使用的卷。
     *
     * @param connectionId 连接标识
     * @return 清理结果
     */
    Map<String, Object> pruneVolumes(String connectionId);

    // ─── 辅助方法 ────────────────────────────────────────────────────────────

    /**
     * 获取底层 docker-java DockerClient。
     *
     * @param connectionId 连接标识
     * @return DockerClient 实例
     */
    DockerClient getDockerClient(String connectionId);
}

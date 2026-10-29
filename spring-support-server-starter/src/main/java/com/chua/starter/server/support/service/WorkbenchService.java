package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.vo.WorkbenchDockerCapabilityVO;
import com.chua.starter.server.support.model.vo.WorkbenchK8sCapabilityVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 主机工作台服务 —— Docker / Kubernetes 真实接口
 *
 * <p>三个能力域均由「SSH 执行受控主机上的 docker / kubectl CLI」实现：
 * 受控机本就是 SSH 纳管（凭据来自 {@link ServerHostService#resolveCredential}），
 * 无需额外暴露 Docker TCP 端口或 kubeconfig 下载，与「服务器没装服务就不显示功能」
 * 的能力门控（执行过程即探活）天然一致。输出解析为契约文档
 * {@code workbench-api-contract.md} 定义的 JSON 形态。</p>
 *
 * <p>全部操作返回 {@code ReturnResult}：成功 code=0；能力不存在时
 * {@code code=1, msg="主机未安装Docker" / "未检测到Kubernetes集群"}，
 * 前端据 msg 显示「未检测到服务」空态并隐藏入口。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public interface WorkbenchService {

    /* ==================== Docker ==================== */

    /**
     * Docker 能力探测：docker 命令可执行即 installed=true
     *
     * @param hostId 主机 标识
     * @return 能力结果（未安装也返回 ok，installed=false）
     */
    ReturnResult<WorkbenchDockerCapabilityVO> dockerCapability(Long hostId);

    /**
     * 容器列表（含已停止）
     *
     * @param hostId 主机 标识
     * @param all    是否包含已停止容器，默认 true
     * @return 容器视图列表
     */
    ReturnResult<List<Map<String, Object>>> listDockerContainers(Long hostId, Boolean all);

    /**
     * 启动 / 停止 / 重启容器
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param action      start / stop / restart
     * @return 操作结果
     */
    ReturnResult<Boolean> dockerContainerAction(Long hostId, String containerId, String action);

    /**
     * 容器最近日志（一次性返回，非流式）
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param tail        尾部行数，默认 200
     * @return 日志文本
     */
    ReturnResult<String> dockerContainerLogs(Long hostId, String containerId, Integer tail);

    /**
     * 容器日志实时流（SSE 异步推送）
     * <p>使用 docker logs -f 持续跟随容器日志，每行作为一条 SSE 事件推送。
     * 客户端断开连接时自动停止跟随并释放 SSH 连接。</p>
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param tail        起始尾部行数，默认 100
     * @return SSE 发射器
     */
    SseEmitter streamContainerLogs(Long hostId, String containerId, Integer tail);

    /**
     * 进入容器（SSE 异步流式交互）
     * <p>通过 SSH PTY 建立交互式会话，执行 docker exec -it 进入容器 shell。
     * 容器输出经 SSE 推送，客户端输入经 {@link #dockerContainerExecInput} 发送。</p>
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param command     容器内命令，默认 /bin/bash
     * @return SSE 发射器
     */
    SseEmitter dockerContainerExec(Long hostId, String containerId, String command);

    /**
     * 发送交互输入到容器会话
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param sessionId   会话标识（dockerContainerExec 返回的 sessionId）
     * @param input       输入文本
     * @return 操作结果
     */
    ReturnResult<Void> dockerContainerExecInput(Long hostId, String containerId, String sessionId, String input);

    /**
     * 关闭容器交互会话
     *
     * @param hostId      主机 标识
     * @param containerId 容器 ID 或名称
     * @param sessionId   会话标识
     * @return 操作结果
     */
    ReturnResult<Void> dockerContainerExecClose(Long hostId, String containerId, String sessionId);

    /**
     * 镜像列表
     *
     * @param hostId 主机 标识
     * @return 镜像视图列表
     */
    ReturnResult<List<Map<String, Object>>> listDockerImages(Long hostId);

    /**
     * 网络列表
     *
     * @param hostId 主机 标识
     * @return 网络视图列表
     */
    ReturnResult<List<Map<String, Object>>> listDockerNetworks(Long hostId);

    /**
     * 卷列表
     *
     * @param hostId 主机 标识
     * @return 卷视图列表
     */
    ReturnResult<List<Map<String, Object>>> listDockerVolumes(Long hostId);

    /**
     * 清理悬空镜像与停用卷
     *
     * @param hostId 主机 标识
     * @return 清理结果
     */
    ReturnResult<Map<String, Object>> dockerPrune(Long hostId);

    /* ==================== Kubernetes ==================== */

    /**
     * Kubernetes 能力探测：kubectl 可执行且集群可达即 installed=true
     *
     * @param hostId 主机 标识
     * @return 能力结果（未安装也返回 ok，installed=false）
     */
    ReturnResult<WorkbenchK8sCapabilityVO> k8sCapability(Long hostId);

    /**
     * 集群节点列表 + 版本 + 就绪
     *
     * @param hostId 主机 标识
     * @return 集群视图
     */
    ReturnResult<Map<String, Object>> k8sCluster(Long hostId);

    /**
     * 工作负载列表（Deployment / StatefulSet / DaemonSet / Job）
     *
     * @param hostId    主机 标识
     * @param namespace 命名空间过滤，空 表示全部
     * @return 工作负载视图列表
     */
    ReturnResult<List<Map<String, Object>>> k8sWorkloads(Long hostId, String namespace);

    /**
     * 命名空间列表
     *
     * @param hostId 主机 标识
     * @return 命名空间名称列表
     */
    ReturnResult<List<String>> k8sNamespaces(Long hostId);

    /**
     * 工作负载扩缩容
     *
     * @param hostId 主机 标识
     * @param name   工作负载名称
     * @param kind   工作负载类型（deployment / statefulset / daemonset / job）
     * @param ns     命名空间
     * @param replicas 目标副本数
     * @return 操作结果
     */
    ReturnResult<Boolean> k8sScale(Long hostId, String name, String kind, String ns, Integer replicas);

    /**
     * 工作负载滚动重启
     *
     * @param hostId 主机 标识
     * @param name   工作负载名称
     * @param kind   工作负载类型（deployment / statefulset / daemonset）
     * @param ns     命名空间
     * @return 操作结果
     */
    ReturnResult<Boolean> k8sRestart(Long hostId, String name, String kind, String ns);
}
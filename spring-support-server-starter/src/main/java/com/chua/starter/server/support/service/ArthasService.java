package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;
import java.util.Map;

/**
 * Arthas 诊断工具管理服务
 *
 * <p>支持两种方式注入 Arthas：</p>
 * <ul>
 *   <li><b>启动前 javaagent</b>：在项目 jvmArgs 中挂载 {@code -javaagent:arthas-agent.jar}，
 *       与 SkyWalking / 平台 APM 同一口径（见 ProjectsView.vue APM 注入 tab）。</li>
 *   <li><b>运行时 attach</b>：通过 SSH 在目标主机上执行 {@code java -jar arthas-boot.jar <pid>}
 *       动态挂载到运行中的 JVM，无需重启项目。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ArthasService {

    /**
     * 列出发布下所有 Java 项目的 Arthas 注入状态。
     *
     * @param releaseId 发布标识
     * @return 项目列表，每项含 projectId/projectName/projectType/jvmArgs/arthasInjected/arthasPid
     */
    ReturnResult<List<Map<String, Object>>> listArthasProjects(Long releaseId);

    /**
     * 查询单个项目的 Arthas 状态（是否已注入、PID、telnet 端口等）。
     *
     * @param projectId 项目标识
     * @return 状态 Map
     */
    ReturnResult<Map<String, Object>> getArthasStatus(Long projectId);

    /**
     * 运行时挂载 Arthas 到指定项目的 JVM。
     *
     * <p>通过 SSH 在目标主机执行 {@code java -jar arthas-boot.jar <pid> -c "..."}，
     * Arthas 启动 telnet 服务（默认 3658）供后续交互。</p>
     *
     * @param projectId 项目标识
     * @param arthasJarPath arthas-boot.jar 在目标主机的绝对路径
     * @param telnetPort telnet 端口（默认 3658）
     * @return taskId（异步执行，进度经 socket 推送）
     */
    ReturnResult<String> attachArthas(Long projectId, String arthasJarPath, Integer telnetPort);

    /**
     * 从 JVM 卸载 Arthas。
     *
     * <p>通过 SSH 执行 Arthas {@code shutdown} 命令关闭诊断通道。</p>
     *
     * @param projectId 项目标识
     * @return taskId
     */
    ReturnResult<String> detachArthas(Long projectId);

    /**
     * 在 Arthas 终端执行命令。
     *
     * @param projectId 项目标识
     * @param command Arthas 命令（如 dashboard / thread / jvm / sc *Service*）
     * @return 命令输出
     */
    ReturnResult<String> execArthasCommand(Long projectId, String command);

    /**
     * 获取 Arthas 终端输出（最近若干行）。
     *
     * @param projectId 项目标识
     * @param lines 行数
     * @return 终端输出
     */
    ReturnResult<String> getArthasConsole(Long projectId, int lines);
}

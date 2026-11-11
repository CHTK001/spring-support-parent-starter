package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.Map;

/**
 * Agent 构件投放服务。
 *
 * <p>解决「目标主机上还没有 arthas / skywalking / 平台 APM 的 agent 包」这一前置条件：
 * 平台可以</p>
 * <ul>
 *   <li><b>远程下载</b>：给出 http/https 地址（jar 或 zip / tar.gz 发行包），平台自己下载并缓存；</li>
 *   <li><b>本机路径</b>：给出平台所在机器上的 jar 或解压目录；</li>
 * </ul>
 * <p>然后把构件经 SFTP 投放到项目目标目录，返回可以直接写进 jvmArgs / Arthas attach 的远端绝对路径。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface AgentArtifactService {

    /**
     * 准备 Agent 构件并投放到目标主机。
     *
     * @param projectId 项目标识（决定目标主机与默认目标目录）
     * @param kind      构件类型：{@code arthas} / {@code skywalking} / {@code platform}
     * @param source    来源：{@code url} 远程下载、{@code local} 平台本机路径
     * @param value     来源对应的地址或路径
     * @param targetDir 目标目录（空则按类型取 {@code <项目路径>/lib/<类型>}）
     * @param fileName  远端文件名（空则沿用源文件名；目录型构件忽略）
     * @param force     远程下载时是否强制重新下载（忽略本地缓存）
     * @return 投放结果：remotePath / 文件数 / 字节数 / 本地缓存路径等
     */
    ReturnResult<Map<String, Object>> prepare(Long projectId, String kind, String source, String value,
                                              String targetDir, String fileName, boolean force);
}

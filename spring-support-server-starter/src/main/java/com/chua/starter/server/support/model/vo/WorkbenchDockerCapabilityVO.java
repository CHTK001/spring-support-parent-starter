package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Docker 能力探测结果（工作台能力门控的数据源）
 *
 * <p>「服务器没有安装对应服务就不显示功能」由本组数据驱动：
 * {@code installed=false} 时前端隐藏 Docker 域入口。cmd 通道走 SSH 执行
 * {@code docker version}，安装即能解析出版本号；compose 单独探测
 * {@code docker compose version}。api 通道走 Docker Engine HTTP API
 * {@code GET /version}。</p>
 *
 * @param installed Docker 是否已安装/可达
 * @param version   Docker 服务端版本，如 27.3.1；未安装时为空
 * @param compose   docker compose 插件是否可用（api 通道恒为 false）
 * @param runtime   容器运行时标识：docker / containerd / podman
 * @param mode      当前操作通道：CMD = SSH + docker CLI / API = Docker Engine HTTP API
 * @author CH
 * @since 4.0.0.44
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkbenchDockerCapabilityVO(
        boolean installed,
        String version,
        boolean compose,
        String runtime,
        String mode
) {

    /**
     * 便捷构造：未安装
     *
     * @param runtime 探测到的运行时（未知传 docker）
     * @param mode    当前操作通道
     * @return 未安装能力对象
     */
    public static WorkbenchDockerCapabilityVO notInstalled(String runtime, String mode) {
        return new WorkbenchDockerCapabilityVO(false, null, false, runtime, mode);
    }

    /**
     * 便捷构造：已安装
     *
     * @param version Docker 版本
     * @param compose compose 是否可用
     * @param mode    当前操作通道
     * @return 已安装能力对象
     */
    public static WorkbenchDockerCapabilityVO installed(String version, boolean compose, String mode) {
        return new WorkbenchDockerCapabilityVO(true, version, compose, "docker", mode);
    }
}
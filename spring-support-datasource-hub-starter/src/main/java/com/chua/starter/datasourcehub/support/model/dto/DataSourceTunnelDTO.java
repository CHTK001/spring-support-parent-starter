package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 数据源 SSH 隧道配置请求。
 *
 * @param name         隧道名称
 * @param type         隧道类型 - LOCAL:本地转发(-L), REMOTE:反向转发(-R), DYNAMIC:动态SOCKS5
 * @param sshHost      SSH 服务器地址
 * @param sshPort      SSH 端口，空则 22
 * @param sshUsername  SSH 用户名
 * @param sshAuthType  认证方式 - PASSWORD:口令, KEY:私钥
 * @param sshPassword  SSH 口令
 * @param sshPrivateKey SSH 私钥内容
 * @param sshPassphrase 私钥口令
 * @param localPort    本地端口，空则自动分配
 * @param remoteHost   远端目标主机（DYNAMIC 时可空）
 * @param remotePort   远端目标端口（DYNAMIC 时可空）
 * @param bindAddress  本地绑定地址
 * @param status       状态，0 停用、1 启用；为空按停用处理
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataSourceTunnelDTO(
        String name,
        String type,
        String sshHost,
        Integer sshPort,
        String sshUsername,
        String sshAuthType,
        String sshPassword,
        String sshPrivateKey,
        String sshPassphrase,
        Integer localPort,
        String remoteHost,
        Integer remotePort,
        String bindAddress,
        Integer status
) {
}

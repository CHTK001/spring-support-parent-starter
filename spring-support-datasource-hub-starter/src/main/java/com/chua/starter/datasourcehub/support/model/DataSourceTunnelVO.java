package com.chua.starter.datasourcehub.support.model;

/**
 * 数据源 SSH 隧道配置视图。
 *
 * <p>口令与私钥不在此回显：仅回显认证方式，避免配置查询接口变成凭据泄露面。</p>
 *
 * @param id            主键
 * @param datasourceId  数据源 ID
 * @param name          隧道名称
 * @param type          隧道类型 - LOCAL/REMOTE/DYNAMIC
 * @param sshHost       SSH 服务器地址
 * @param sshPort       SSH 端口
 * @param sshUsername   SSH 用户名
 * @param sshAuthType   认证方式 - PASSWORD/KEY
 * @param sshPrivateKey SSH 私钥（PEM 文本或文件路径，取决于用户填写方式）
 * @param localPort     本地端口，空则自动分配
 * @param remoteHost    远端目标主机
 * @param remotePort    远端目标端口
 * @param bindAddress   本地绑定地址
 * @param status        状态，0 停用、1 启用
 * @param createTime    创建时间（ISO-8601 文本）
 * @param updateTime    更新时间（ISO-8601 文本）
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceTunnelVO(
        Long id,
        Long datasourceId,
        String name,
        String type,
        String sshHost,
        Integer sshPort,
        String sshUsername,
        String sshAuthType,
        String sshPrivateKey,
        Integer localPort,
        String remoteHost,
        Integer remotePort,
        String bindAddress,
        Integer status,
        String createTime,
        String updateTime
) {
}

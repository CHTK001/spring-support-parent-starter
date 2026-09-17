package com.chua.starter.monitor.support.controller;

/**
 * 服务器主机简表（资产表单联动下拉专用）。
 *
 * <p>仅暴露下拉与展示所需的 5 个字段，避免把账户/密码/私钥等敏感字段经台账模块接口返回前端。
 * 由 {@link MonitorController#listServerHosts()} 将 {@link com.chua.starter.server.support.entity.MsServerHost} 投影为本记录。</p>
 *
 * @param id 服务器主机 ID
 * @param serverHostName 主机名称
 * @param serverHostAddress 主机地址
 * @param serverHostPort 主机端口
 * @param serverHostOsType 操作系统类型
 * @since 2026/8/4
 * @author CH
 */
public record ServerHostBrief(
        Long id,
        String serverHostName,
        String serverHostAddress,
        Integer serverHostPort,
        String serverHostOsType) {
}

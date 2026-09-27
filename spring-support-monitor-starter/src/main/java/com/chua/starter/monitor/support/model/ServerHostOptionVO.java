package com.chua.starter.monitor.support.model;

/**
 * 服务器主机下拉项 —— 监控表单联动用的主机简表。
 *
 * @param id                主机标识
 * @param serverHostName    主机名称
 * @param serverHostAddress 主机地址
 * @param serverHostPort    SSH 端口
 * @param serverHostOsType  操作系统类型
 * @author CH
 * @since 4.0.0.42
 */
public record ServerHostOptionVO(
        Long id,
        String serverHostName,
        String serverHostAddress,
        Integer serverHostPort,
        String serverHostOsType
) {
}

package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 数据源配置请求。
 *
 * @param name     数据源名称
 * @param type     数据源类型（jdbc/redis/influxdb/neo4j/nacos/zookeeper/nginx）
 * @param host     主机地址
 * @param port     端口
 * @param database 库名
 * @param username 用户名
 * @param password 密码
 * @param remark   备注
 * @param status   状态，0 停用、1 启用；为空按启用处理
 * @param driverClassName JDBC 驱动类名，非 MySQL 系可显式指定
 * @param controlMode    进程控制方式：{@code systemd} / {@code local}；为空按自动探测。
 *                       仅 nginx 类型使用，其余类型忽略
 * @param controlTarget  控制方式为 {@code systemd} 时的托管单元名，如 {@code nginx.service}。
 *                       为空时仍由自动探测决定；{@code local} 方式不使用本字段
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataSourceDTO(
        String name,
        String type,
        String host,
        Integer port,
        String database,
        String username,
        String password,
        String remark,
        Integer status,
        String driverClassName,
        Long driverJarId,
        String controlMode,
        String controlTarget
) {
}
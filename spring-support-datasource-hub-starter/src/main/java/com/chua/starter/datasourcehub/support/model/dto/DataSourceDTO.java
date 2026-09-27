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
        String driverClassName
) {
}
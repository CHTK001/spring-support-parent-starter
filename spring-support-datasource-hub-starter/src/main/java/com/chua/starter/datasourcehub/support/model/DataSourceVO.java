package com.chua.starter.datasourcehub.support.model;

/**
 * 数据源配置视图。
 *
 * @param id         数据源 ID
 * @param name       数据源名称
 * @param type       数据源类型（jdbc/redis/influxdb/neo4j/nacos/zookeeper/nginx）
 * @param host       主机地址
 * @param port       端口
 * @param database   库名
 * @param username   用户名
 * @param password   密码
 * @param remark     备注
 * @param status     状态，0 停用、1 启用
 * @param driverClassName JDBC 驱动类名
 * @param createTime 创建时间（ISO-8601 文本）
 * @param updateTime 更新时间（ISO-8601 文本）
 * @param connectLatencyMs 连接延迟（毫秒），从未测试过时为 null
 * @param sessionLatencyMs 会话延迟（毫秒），从未测试过时为 null
 * @param lastTestTime 最近一次测试时间（ISO-8601 文本）
 * @param lastTestOk 最近一次测试是否连通，null 表示从未测试
 * @param controlMode 进程控制方式：{@code systemd} / {@code local}；null 表示自动探测
 * @param controlTarget systemd 方式的托管单元名；null 或空表示自动探测
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceVO(
        Long id,
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
        String createTime,
        String updateTime,
        Long connectLatencyMs,
        Long sessionLatencyMs,
        String lastTestTime,
        Integer lastTestOk,
        String controlMode,
        String controlTarget
) {
}
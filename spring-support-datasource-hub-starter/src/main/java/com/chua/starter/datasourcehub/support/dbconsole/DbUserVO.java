package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 数据库用户行（JDBC 控制台「用户管理」）。
 *
 * <p>对齐 MySQL Workbench 用户管理视图的列：用户名/主机、SSL 类型、
 * 资源限制（每小时查询/更新/连接、最大用户连接）、超级用户标记。
 * PostgreSQL 等其余内核不支持用户管理时由端点返回 {@code supported=false}，
 * 前端据此不显示「用户管理」右键入口。</p>
 *
 * @param name               用户名
 * @param host               允许登录的主机（{@code %}=任意）
 * @param sslType            SSL 类型（MySQL：NONE/ANY/X509/SPECIFIED），取不到为 null
 * @param maxQuestionsPerHour 每小时最大查询数，-1/0 表示不限
 * @param maxUpdatesPerHour   每小时最大更新数，-1/0 表示不限
 * @param maxConnectionsPerHour 每小时最大连接数，-1/0 表示不限
 * @param maxUserConnections  最大用户连接数，-1/0 表示不限
 * @param isSuper             是否超级用户（Super_priv = Y）
 * @author CH
 * @since 4.0.0.42
 */
public record DbUserVO(
        String name,
        String host,
        String sslType,
        Long maxQuestionsPerHour,
        Long maxUpdatesPerHour,
        Long maxConnectionsPerHour,
        Long maxUserConnections,
        Boolean isSuper
) {
}
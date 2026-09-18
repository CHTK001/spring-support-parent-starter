package com.chua.starter.monitor.support.controller;

/**
 * 受限 SQL 执行请求体（数据库 Web 管理器）。
 *
 * <p>由 {@link MonitorController#executeDbSql} 接收，转发给 {@code JdbcAdminService#executeSql}。
 * SQL 仅允许单条语句，首关键词在 {@code JdbcAdminService} 白名单内（SELECT/SHOW/DESC/EXPLAIN 等）。</p>
 *
 * @param sql 单条 SQL 语句，必填，不能为 null 或空白
 * @param datasourceId 数据源 ID，可为 null（null 或 0 表示默认主数据源）
 * @since 2026/8/4
 * @author CH
 * @return 结果值
 */
public record SqlExecuteRequest(String sql, Long datasourceId) {
}

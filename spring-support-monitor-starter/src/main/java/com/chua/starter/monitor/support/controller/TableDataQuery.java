package com.chua.starter.monitor.support.controller;

/**
 * 数据库 Web 管理器"获取表数据"分页查询参数。
 *
 * <p>绑定 REST 查询参数。由 {@link MonitorController#getTableData} 接收，
 * 转发给 {@code JdbcAdminService#getTableData} 做白名单与分页处理。</p>
 *
 * @param tableName 表名，仅允许字母数字下划线，不能为 null
 * @param page 页码，从 1 起；小于 1 时由服务端按 1 处理
 * @param pageSize 每页行数；小于 1 时按 50 处理，超过 1000 时按 1000 处理
 * @param orderBy 排序列（逗号分隔），白名单校验，可为 null
 * @param orderDir 排序方向，仅允许 ASC/DESC（大小写不敏感），可为 null
 * @param datasourceId 数据源 ID，可为 null（null 或 0 表示默认主数据源）
 * @param database 数据库名，可为 null
 * @since 2026/8/4
 * @author CH
 */
public record TableDataQuery(
        String tableName,
        int page,
        int pageSize,
        String orderBy,
        String orderDir,
        Long datasourceId,
        String database) {
}

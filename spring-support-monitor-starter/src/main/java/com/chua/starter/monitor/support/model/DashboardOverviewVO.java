package com.chua.starter.monitor.support.model;

/**
 * 监控全局概览统计
 *
 * @param total      监控资产总数
 * @param enabled    已启用资产数
 * @param disabled   已停用资产数
 * @param serverCount 服务器类资产数
 * @param redisCount  Redis 类资产数
 * @param dbCount     数据库类资产数
 * @author CH
 * @since 4.0.0.42
 */
public record DashboardOverviewVO(
        int total,
        int enabled,
        int disabled,
        int serverCount,
        int redisCount,
        int dbCount
) {
}

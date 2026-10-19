package com.chua.common.support.data.materialized;

/**
 * 物化视图路由定义
 *
 * @param threshold  阈值
 * @param dataSource 数据来源
 * @author CH
 * @since 4.0.0
 */
public record MaterializedRouteDefinition(long threshold, String dataSource) {
}

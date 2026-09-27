package com.chua.starter.datasource.support.graph;

/**
 * 数据库触发器元数据
 *
 * @param name 触发器名
 * @param tableName 所属表
 * @param timing 触发时机：之前 / 之后 / INSTEAD 的
 * @param event 触发事件：插入 / 更新 / 删除
 *              <p>多个事件用逗号分隔</p>
 * @param status 触发器状态：已启用 / 已禁用
 * @param granularity 触发器类型（数据库方言相关，ROW /
 *                    对账单）
 *
 * @since 2026/8/5
 * @author CH
 */
public record TriggerInfo(
        String name,
        String tableName,
        String timing,
        String event,
        String status,
        String granularity
) {
}

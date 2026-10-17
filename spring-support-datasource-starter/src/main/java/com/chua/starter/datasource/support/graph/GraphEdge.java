package com.chua.starter.datasource.support.graph;

/**
 * relation-图计算 边：单向外键关系
 *
 * @param id         边标识（从 + 转为 + 列名 哈希）
 * @param from       源表标识（持有外键的表）
 * @param to         目标表标识（被引用的表）
 * @param text       边文本：当前表外键列 → 目标表主键列
 * @param fkName     外键名
 * @param updateRule 更新时动作
 * @param deleteRule 删除时动作
 * @since 2026/8/5
 * @author CH
 */
public record GraphEdge(
        String id,
        String from,
        String to,
        String text,
        String fkName,
        String updateRule,
        String deleteRule) {
}

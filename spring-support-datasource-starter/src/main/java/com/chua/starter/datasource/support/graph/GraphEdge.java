package com.chua.starter.datasource.support.graph;

import lombok.Data;

/**
 * relation-graph 边：单向外键关系
 *
 * @author CH
 * @since 2026/8/5
 */
@Data
public class GraphEdge {

    /**
     * 边 id（from + to + 列名 hash）
     */
    private String id;

    /**
     * 源表 id（持有外键的表）
     */
    private String from;

    /**
     * 目标表 id（被引用的表）
     */
    private String to;

    /**
     * 边文本：当前表外键列 → 目标表主键列
     */
    private String text;

    /**
     * 外键名
     */
    private String fkName;

    /**
     * 更新时动作
     */
    private String updateRule;

    /**
     * 删除时动作
     */
    private String deleteRule;
}

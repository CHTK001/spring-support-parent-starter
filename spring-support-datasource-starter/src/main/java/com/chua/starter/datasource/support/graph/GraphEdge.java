package com.chua.starter.datasource.support.graph;

import lombok.Data;

/**
* relation-图计算 边：单向外键关系
*
* @since 2026/8/5
* @author CH
 */
@Data
public class GraphEdge {

    /**
    * 边 标识（从 + 转为 + 列名 哈希）
     */
    private String id;

    /**
    * 源表 标识（持有外键的表）
     */
    private String from;

    /**
    * 目标表 标识（被引用的表）
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

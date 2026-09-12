package com.chua.starter.datasource.support.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* 表节点：聚合表结构、列、触发器、关联存储过程、外键关系
* <p>作为 relation-graph 节点 {@code data} 字段的载体。</p>
*
* @since 2026/8/5
* @author CH
 */
@Data
public class TableNodeInfo {

    /**
    * 表名（节点 标识 同名）
     */
    private String name;

    /**
    * 模式 / Catalog
     */
    private String schema;

    /**
    * 表注释
     */
    private String remark;

    /**
    * 表类型（TABLE / VIEW）
     */
    private String type;

    /**
    * 列数
     */
    private int columnCount;

    /**
    * 列名清单（按 ORDINAL_立场 排序）
     */
    private List<String> columnNames = new ArrayList<>();

    /**
    * 主键列名
     */
    private List<String> primaryKeys = new ArrayList<>();

    /**
    * 是否包含外键（作为出边表）
     */
    private boolean hasForeignKey;

    /**
    * 出边数量（当前表引用别人的 FK）
     */
    private int outRefCount;

    /**
    * 入边数量（被别人引用的 FK）
     */
    private int inRefCount;

    /**
    * 触发器
     */
    private List<TriggerInfo> triggers = new ArrayList<>();

    /**
    * 关联存储过程（按表名启发式匹配，非严格绑定）
     */
    private List<ProcedureInfo> procedures = new ArrayList<>();
}

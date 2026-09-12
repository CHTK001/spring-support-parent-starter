package com.chua.starter.datasource.support.graph;

import lombok.Data;

/**
* 数据库触发器元数据
*
* @since 2026/8/5
* @author CH
 */
@Data
public class TriggerInfo {

    /**
    * 触发器名
     */
    private String name;

    /**
    * 所属表
     */
    private String tableName;

    /**
    * 触发时机：之前 / 之后 / INSTEAD 的
     */
    private String timing;

    /**
    * 触发事件：插入 / 更新 / 删除
    * <p>多个事件用逗号分隔</p>
     */
    private String event;

    /**
    * 触发器状态：已启用 / 已禁用
     */
    private String status;

    /**
    * 触发器类型（数据库方言相关，ROW / 对账单）
     */
    private String granularity;
}

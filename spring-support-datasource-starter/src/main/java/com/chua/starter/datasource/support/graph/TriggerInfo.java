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
     * 触发时机：BEFORE / AFTER / INSTEAD OF
     */
    private String timing;

    /**
     * 触发事件：INSERT / UPDATE / DELETE
     * <p>多个事件用逗号分隔</p>
     */
    private String event;

    /**
     * 触发器状态：ENABLED / DISABLED
     */
    private String status;

    /**
     * 触发器类型（数据库方言相关，ROW / STATEMENT）
     */
    private String granularity;
}

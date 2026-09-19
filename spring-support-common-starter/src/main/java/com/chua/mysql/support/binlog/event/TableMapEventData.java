package com.chua.mysql.support.binlog.event;

/**
 * 表映射事件数据
 *
 * @author CH
 * @since 4.0.0
 */
public class TableMapEventData implements EventData {

    /**
     * 表
    */
    private final String table;

    /**
     * 创建表映射事件数据
     *
     * @param table 表名
     */
    public TableMapEventData(String table) {
        this.table = table;
    }

    /**
     * 获取表名
     *
     * @return 表名
     */
    public String getTable() {
        return table;
    }
}

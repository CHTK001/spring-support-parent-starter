package com.chua.mysql.support.binlog.event;

import java.io.Serializable;
import java.util.List;

/**
* 写入行事件数据
*
* @author CH
* @since 4.0.0
 */
public class WriteRowsEventData implements EventData {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** Rows */
    private final List<Serializable[]> rows;

    /**
    * 创建写入行事件数据
    *
    * @param rows 写入的行数据
     */
    public WriteRowsEventData(List<Serializable[]> rows) {
        this.rows = rows;
    }

    /**
    * 获取写入的行数据
    *
    * @return 写入行数据列表
     */
    public List<Serializable[]> getRows() {
        return rows;
    }
}

package com.chua.mysql.support.binlog.event;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
* 更新行事件数据
*
* @author CH
* @since 4.0.0
 */
public class UpdateRowsEventData implements EventData {
    private static final long serialVersionUID = 1L; // 串行版本uid

    private final List<Map.Entry<Serializable[], Serializable[]>> rows; // rows

    /**
    * 创建更新行事件数据
    *
    * @param rows 更新的行数据，键为旧值，值为新值
    */
    public UpdateRowsEventData(List<Map.Entry<Serializable[], Serializable[]>> rows) {
        this.rows = rows;
    }

    /**
    * 获取更新的行数据
    *
    * @return 更新行数据列表
    */
    public List<Map.Entry<Serializable[], Serializable[]>> getRows() {
        return rows;
    }
}

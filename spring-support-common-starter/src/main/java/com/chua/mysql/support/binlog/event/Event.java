package com.chua.mysql.support.binlog.event;

/**
 * Binary Log 事件
 *
 * @author CH
 */
public class Event {

    /** 数据 */
    private final EventData data;

    /**
     * 创建事件
     *
     * @param data 事件数据
     */
    public Event(EventData data) {
        this.data = data;
    }

    /**
     * 获取事件数据
     *
     * @return 事件数据
     */
    public EventData getData() {
        return data;
    }
}

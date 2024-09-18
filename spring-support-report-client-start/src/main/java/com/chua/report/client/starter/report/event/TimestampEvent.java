package com.chua.report.client.starter.report.event;

import lombok.Data;

/**
 * 时间戳事件
 * @author CH
 * @since 2024/9/18
 */
@Data
public class TimestampEvent {

    /**
     * 时间戳，记录内存信息的采集时间
     */
    private long timestamp = System.currentTimeMillis();
}

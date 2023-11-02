package com.chua.starter.device.support.request;

import lombok.Data;

/**
 * 事件请求
 *
 * @author CH
 * @since 2023/10/29
 */
@Data
public class EventRequest {
    String filter;
    String deviceDataEventInOrOut;
    EventType eventType;
    Integer page = 1;
    Integer pageSize = 10;
}

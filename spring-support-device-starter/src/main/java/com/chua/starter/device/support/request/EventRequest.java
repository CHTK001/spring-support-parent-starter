package com.chua.starter.device.support.request;

import lombok.Data;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;

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
    Integer pageNum = 1;
    Integer pageSize = 10;
}

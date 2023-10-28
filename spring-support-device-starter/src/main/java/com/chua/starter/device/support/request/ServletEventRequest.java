package com.chua.starter.device.support.request;

import com.chua.common.support.lang.date.DateTime;
import lombok.Data;

import java.util.Date;

/**
 * 访问事件请求
 *
 * @author CH
 * @since 2023/10/27
 */
@Data
public class ServletEventRequest {

    private Date startTime = DateTime.now().minusHours(1).toDate();
    private Date endTime = new Date();;

    private Integer deviceConnectorId;

    private EventType eventType = EventType.ACCESS;

}

package com.chua.report.client.starter.report.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网卡上下行
 * @author CH
 * @since 2024/12/23
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IoEvent extends TimestampEvent {

    /**
     * 接收字节
     */
    private long receiveBytes;
    /**
     * 发送字节
     */
    private long transmitBytes;
}

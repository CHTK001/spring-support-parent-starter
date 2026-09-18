package com.chua.starter.device.spi.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
* 设备事件模型
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class DeviceEvent {

    /**
    * 设备 标识
    */
    private String deviceId;

    /**
    * 通道号
    */
    private Integer channel;

    /**
    * 事件类型
    */
    private EventType type;

    /**
    * 事件详情
    */
    private String detail;

    /**
    * 发生时间
    */
    private LocalDateTime occurredAt;

    /**
    * 原始数据
    */
    private Object rawData;
}

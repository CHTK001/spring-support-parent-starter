package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 设备事件模型
 *
 * @param deviceId   设备标识
 * @param channel    通道号
 * @param type       事件类型
 * @param detail     事件详情
 * @param occurredAt 发生时间
 * @param rawData    原始数据
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceEvent(
        String deviceId,
        Integer channel,
        EventType type,
        String detail,
        LocalDateTime occurredAt,
        Object rawData) {
}

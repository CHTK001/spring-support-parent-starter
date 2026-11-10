package com.chua.starter.device.spi.model;

/**
 * 门禁门点位。
 *
 * @param doorId    门的唯一标识（平台侧）
 * @param name      门名称
 * @param deviceId  所属设备
 * @param status    门状态：open 开 / closed 关 / offline 离线 / unknown
 * @param channelNo 门在设备上的通道号
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceDoorInfo(
        String doorId,
        String name,
        String deviceId,
        String status,
        Integer channelNo) {
}

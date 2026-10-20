package com.chua.starter.device.support.realtime;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备在线状态变更事件（含最新经纬度，供大屏/地图实时移动标记）。
 *
 * @param deviceId      设备 ID
 * @param deviceName    设备名称
 * @param vendor        厂商
 * @param online        是否在线
 * @param latitude      纬度（WGS84，可为空）
 * @param longitude     经度（WGS84，可为空）
 * @param lastOnlineAt  最后在线时间
 * @param lastOfflineAt 最后离线时间
 * @param occurredAt    事件发生时间
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceStatusEvent(
        String deviceId,
        String deviceName,
        String vendor,
        boolean online,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime lastOnlineAt,
        LocalDateTime lastOfflineAt,
        LocalDateTime occurredAt) {
}

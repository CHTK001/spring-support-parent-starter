package com.chua.starter.device.support.realtime;

import java.time.LocalDateTime;

/**
 * 新告警入库事件（实时通道）。
 *
 * <p>webhook 推送与平台自检两条入库路径都会发布；前端
 * {@code socketService.subscribe("device", "alarm", handler)} 接收后刷新
 * 告警 KPI 与列表。字段保持精简，只带红点与列表首行需要的消息。</p>
 *
 * @param alarmId    告警 ID
 * @param deviceId   设备 ID
 * @param type       告警类型
 * @param level      级别：critical / major / minor / info
 * @param title      告警标题
 * @param status     处理状态（新告警恒为 unhandled）
 * @param occurredAt 发生时间
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceAlarmEvent(
        String alarmId,
        String deviceId,
        String type,
        String level,
        String title,
        String status,
        LocalDateTime occurredAt) {
}

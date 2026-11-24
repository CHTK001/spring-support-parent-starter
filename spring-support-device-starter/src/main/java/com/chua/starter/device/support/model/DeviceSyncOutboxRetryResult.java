package com.chua.starter.device.support.model;

/**
 * 推送队列（outbox）单条重试结果。
 *
 * @param outboxId 队列记录 ID
 * @param deviceId 设备 ID（队列记录不存在时为 null）
 * @param status   重试后状态：sent（已推送）/ pending（按退避推迟）/ dead（不可重试）；重试调用本身失败时为 failed
 * @param message  结果说明（失败原因 / 推迟说明）
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceSyncOutboxRetryResult(String outboxId, String deviceId, String status, String message) {
}

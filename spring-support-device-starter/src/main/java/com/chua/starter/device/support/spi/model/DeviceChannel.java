package com.chua.starter.device.spi.model;

/**
 * 视频通道（设备下的一个摄像机点位）。
 *
 * <p>各平台的通道模型差异较大：海康用 channelNo，大华用 channelId，
 * 萤石用 deviceSerial + channelNo 组合。统一成本模型后，业务侧只认
 * {@code channelId}（设备内唯一）与 {@code channelNo}（厂商原始通道号）。</p>
 *
 * @param channelId   通道唯一标识（跨设备唯一，通常为 设备序列号_通道号）
 * @param channelNo   厂商通道号（云台/取流接口一般要求回传这个）
 * @param name        通道名称
 * @param deviceSerial 所属设备序列号 / 设备 ID
 * @param online      是否在线
 * @param model       通道型号（平台未提供时为空）
 * @param streamCount 可用的码流数（1 单码流，2 双子码流）
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceChannel(
        String channelId,
        Integer channelNo,
        String name,
        String deviceSerial,
        Boolean online,
        String model,
        Integer streamCount) {
}

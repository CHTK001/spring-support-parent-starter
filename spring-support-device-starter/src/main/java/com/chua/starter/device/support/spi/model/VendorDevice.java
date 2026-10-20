package com.chua.starter.device.spi.model;

import java.math.BigDecimal;

/**
 * 厂商平台返回的设备目录条目（设备同步的传输模型）。
 *
 * <p>各厂商在 {@code fetchDevices()} 中把平台字段映射成本模型，由设备模块统一
 * 按 {@code vendor + externalId} 做幂等 upsert，避免同步重复入库。</p>
 *
 * @param externalId   厂商侧设备唯一标识（序列号/设备 ID）
 * @param name         设备名称
 * @param model        型号
 * @param category     设备分类（camera / accessControl / alarm / intercom）
 * @param serial       设备序列号（平台接入时必填，可与 externalId 相同）
 * @param ip           设备 IP（SDK 直连时可选）
 * @param port         设备端口（SDK 直连时可选）
 * @param address      安装地址
 * @param latitude     纬度（WGS84）
 * @param longitude    经度（WGS84）
 * @param channelCount 通道数
 * @param online       是否在线
 * @since 4.0.0.42
 * @author CH
 */
public record VendorDevice(
        String externalId,
        String name,
        String model,
        String category,
        String serial,
        String ip,
        Integer port,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer channelCount,
        Boolean online) {
}

package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 设备环境指标快照。
 *
 * <p>气象站、温湿度传感器等设备一次会上报多个节点读数，例如温度、湿度、风速、
 * 雨量。{@code key} 是节点在厂商侧的稳定标识，用于把最新值覆盖到同一行；
 * 没有稳定标识时由会话按“名称 + 单位”生成。</p>
 *
 * @param key      节点稳定标识
 * @param name     节点名称，例如“温度”
 * @param value    读数；保留字符串以兼容风向、天气现象等非数值指标
 * @param unit     单位，例如 ℃、%RH
 * @param dataTime 厂商侧数据时间；厂商未返回时为空
 * @since 4.0.0.54
 * @author CH
 */
public record DeviceMetric(
        String key,
        String name,
        String value,
        String unit,
        LocalDateTime dataTime) {
}

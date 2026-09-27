package com.chua.starter.device.spi.model;

/**
 * 预置位信息
 *
 * @param presetId 预置位编号
 * @param name 预置位名称
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PresetInfo(
        int presetId,
        String name
) {
}

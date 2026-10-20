package com.chua.starter.device.spi.model;

/**
 * 接入方式描述
 *
 * @param code        方式编码：PLATFORM / SDK
 * @param name        展示名称
 * @param description 说明
 * @since 4.0.0.42
 * @author CH
 */
public record AuthTypeDescriptor(
        String code,
        String name,
        String description) {
}

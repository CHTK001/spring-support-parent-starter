package com.chua.payment.support.vo;

import java.io.Serializable;

/**
 * 提供者SPI选项
 *
 * @param channelType   渠道类型
 * @param extensionName 扩展名称
 * @param defaultOption 默认授权
 * @param description   描述
 * @author CH
 * @since 4.0.0
 */
public record ProviderSpiOptionVO(
        String channelType,
        String extensionName,
        Boolean defaultOption,
        String description) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}

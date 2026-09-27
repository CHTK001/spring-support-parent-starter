package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 提供者SPI选项
 * @author CH
 * @since 4.0.0
 */
@Data
public class ProviderSpiOptionVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 渠道类型
     */
    private String channelType;

    /**
     * 扩展名称
     */
    private String extensionName;

    /**
     * 默认授权
     */
    private Boolean defaultOption;

    /**
     * 描述
     */
    private String description;
}

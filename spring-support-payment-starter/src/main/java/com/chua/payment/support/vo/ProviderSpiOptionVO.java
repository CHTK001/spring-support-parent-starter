package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * Provider SPI 选项
 */
@Data
public class ProviderSpiOptionVO implements Serializable {

    private String channelType;

    private String extensionName;

    private Boolean defaultOption;

    private String description;
}

package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * Provider SPI 选项
 * @author CH
 */
@Data
public class ProviderSpiOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 通道类型 */
    private String channelType;

    /** Extension名称 */
    private String extensionName;

    /** 默认option */
    private Boolean defaultOption;

    /** 描述 */
    private String description;
}

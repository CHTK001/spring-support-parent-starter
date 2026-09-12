package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;

/**
   * 提供者 SPI 选项
 * @author CH
 * @since 4.0.0
 */
@Data
public class ProviderSpiOptionVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 通道类型 */
    private String channelType;

    /** 延伸名称 */
    private String extensionName;

    /** 默认期权 */
    private Boolean defaultOption;

    /** 描述 */
    private String description;
}

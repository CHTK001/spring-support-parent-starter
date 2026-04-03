package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 旧版图像配置。
 *
 * @author CH
 * @since 2026/04/03
 */
@Data
public class AiLegacyImageProperties {

    /**
     * 是否启用旧版图像配置。
     */
    private boolean enabled = true;

    /**
     * 提供商名称。
     */
    private String provider = "default";

    /**
     * 图像特征维度。
     */
    private int featureDimension = 512;
}

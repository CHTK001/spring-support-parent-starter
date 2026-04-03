package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 旧版文本配置。
 *
 * @author CH
 * @since 2026/04/03
 */
@Data
public class AiLegacyTextProperties {

    /**
     * 是否启用旧版文本配置。
     */
    private boolean enabled = true;

    /**
     * 提供商名称。
     */
    private String provider = "default";

    /**
     * 最大序列长度。
     */
    private int maxSequenceLength = 512;
}

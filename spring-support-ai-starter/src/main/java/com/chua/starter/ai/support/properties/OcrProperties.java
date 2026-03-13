package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * OCR 配置属性
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
public class OcrProperties {
    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 语言
     */
    private String language = "chi_sim";

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;
}

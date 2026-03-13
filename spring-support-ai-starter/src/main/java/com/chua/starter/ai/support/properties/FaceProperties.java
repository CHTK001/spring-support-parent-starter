package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 人脸识别配置属性
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
public class FaceProperties {
    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 置信度阈值
     */
    private Float confidenceThreshold = 0.5f;

    /**
     * NMS 阈值
     */
    private Float nmsThreshold = 0.4f;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;
}

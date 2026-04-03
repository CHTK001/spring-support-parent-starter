package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 旧版人脸检测配置。
 *
 * @deprecated 使用 {@link FaceProperties} 替代
 * @author CH
 * @since 2026/04/03
 */
@Deprecated
@Data
public class AiLegacyFaceDetectionProperties {

    /**
     * 是否启用旧版人脸检测配置。
     */
    private boolean enabled = true;

    /**
     * 提供商名称。
     */
    private String provider = "default";

    /**
     * 人脸置信度阈值。
     */
    private float confidenceThreshold = 0.5f;

    /**
     * NMS 阈值。
     */
    private float nmsThreshold = 0.4f;
}

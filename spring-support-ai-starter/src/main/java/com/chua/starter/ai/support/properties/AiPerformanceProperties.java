package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * 网站性能分析配置。
 *
 * @author CH
 * @since 2026/04/14
 */
@Data
public class AiPerformanceProperties {

    /**
     * 是否启用性能分析能力。
     */
    private boolean enabled = true;

    /**
     * 是否启用 REST 接口。
     */
    private boolean apiEnabled = true;

    /**
     * 默认采样次数。
     */
    private Integer sampleCount = 3;

    /**
     * 采样间隔（毫秒）。
     */
    private Integer sampleIntervalMs = 200;

    /**
     * 连接超时（毫秒）。
     */
    private Integer connectTimeoutMs = 5000;

    /**
     * 单次请求超时（毫秒）。
     */
    private Integer requestTimeoutMs = 30000;

    /**
     * 是否默认启用 AI 建议。
     */
    private boolean aiAdviceEnabled = true;

    /**
     * 预览文本最大长度。
     */
    private Integer snapshotPreviewLength = 600;
}

package com.chua.starter.ai.support.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 网站性能分析请求。
 *
 * @author CH
 * @since 2026/04/14
 */
@Data
public class WebPerformanceAnalyzeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待分析地址。
     */
    private String url;

    /**
     * 采样次数，默认取配置值。
     */
    private Integer sampleCount;

    /**
     * 采样间隔（毫秒），默认取配置值。
     */
    private Integer sampleIntervalMs;

    /**
     * 连接超时（毫秒）。
     */
    private Integer connectTimeoutMs;

    /**
     * 请求超时（毫秒）。
     */
    private Integer requestTimeoutMs;

    /**
     * 是否包含快照预览。
     */
    private Boolean includeSnapshot;

    /**
     * 是否启用 AI 建议。
     */
    private Boolean aiAdviceEnabled;

    /**
     * 可选 User-Agent。
     */
    private String userAgent;
}

package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 网站性能分析结果。
 *
 * @author CH
 * @since 2026/04/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebPerformanceAnalyzeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分析目标地址。
     */
    private String url;

    /**
     * 采集器标识。
     */
    private String collector;

    /**
     * 采集模式说明。
     */
    private String mode;

    /**
     * 开始时间戳（毫秒）。
     */
    private long startTime;

    /**
     * 结束时间戳（毫秒）。
     */
    private long endTime;

    /**
     * 总耗时（毫秒）。
     */
    private long costTime;

    /**
     * 采样明细。
     */
    private List<SampleMetric> samples;

    /**
     * 汇总指标。
     */
    private SummaryMetric summary;

    /**
     * Trace 概览。
     */
    private TraceOverview trace;

    /**
     * 页面快照概览。
     */
    private SnapshotOverview snapshot;

    /**
     * 规则建议。
     */
    private List<String> recommendations;

    /**
     * AI 建议文本。
     */
    private String aiAdvice;

    /**
     * 是否已启用 AI 增强建议。
     */
    private boolean aiEnhanced;

    /**
     * 附加元数据。
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleMetric implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer index;
        private Integer statusCode;
        private String contentType;
        private long responseBytes;
        private long headerLatencyMs;
        private long ttfbMs;
        private long downloadMs;
        private long totalCostMs;
        private boolean success;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryMetric implements Serializable {

        private static final long serialVersionUID = 1L;

        private int sampleCount;
        private int successCount;
        private double successRate;
        private long minCostMs;
        private long maxCostMs;
        private double avgCostMs;
        private double p95CostMs;
        private double avgTtfbMs;
        private long avgPayloadBytes;
        private Integer lastStatusCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceOverview implements Serializable {

        private static final long serialVersionUID = 1L;

        private String traceType;
        private List<TraceStage> stages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceStage implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;
        private long durationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotOverview implements Serializable {

        private static final long serialVersionUID = 1L;

        private String title;
        private String contentType;
        private long contentLength;
        private String preview;
    }
}

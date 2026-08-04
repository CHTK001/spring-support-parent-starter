package com.chua.starter.monitor.support.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个指标点
 * <p>
 * Provider 解析一次采集输出得到的最小数据单元, 用于入库或聚合。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricPoint {

    /**
     * 指标名(如 cpu_usage / mem_total)
     */
    private String metricName;

    /**
     * 指标值
     */
    private Double value;

    /**
     * 单位
     */
    private String unit;

    /**
     * 标签维度(如 core=0, mount=/)
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    /**
     * 目标子表(可选): ms_monitor_metric_cpu / ms_monitor_metric_memory / ...
     * <p>为空时写入通用 ms_monitor_metric</p>
     */
    private String targetTable;
}
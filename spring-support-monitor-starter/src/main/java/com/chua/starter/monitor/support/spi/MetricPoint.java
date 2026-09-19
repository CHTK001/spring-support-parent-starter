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
 * 提供者 解析一次采集输出得到的最小数据单元, 用于入库或聚合。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
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
     * 标签维度(如 核心=0, mount=/)
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>(); // 标签

    /**
     * 目标子表(可选): ms_监控_指标_cpu / ms_监控_指标_内存 / ...
     * <p>为空时写入通用 ms_monitor_metric</p>
     */
    private String targetTable;
}

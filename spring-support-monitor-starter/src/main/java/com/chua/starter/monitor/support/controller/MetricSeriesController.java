package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.spi.impl.MetricTimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标时序曲线查询接口（数据源按配置选择：influxdb / Redis / 无）
 * <p>
 * 通过 {@link MetricTimeSeriesWriter} 统一门面路由到可用引擎；
 * 未配置时序存储时返回空数组，前端自动降级到 MySQL 历史。
 * 返回结构对齐前端 echarts 折线图：{@code [[时间戳ms, 值], ...]} 升序。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/ms/monitor/metric")
@RequiredArgsConstructor
public class MetricSeriesController {

    /**
     * 默认查询窗口
    */
    private static final String DEFAULT_WINDOW = "1m";

    /**
     * 默认查询时长（小时）
    */
    private static final int DEFAULT_HOURS = 1;

    /**
     * 时序存储门面（按配置选择实现）
    */
    private final MetricTimeSeriesWriter timeSeriesWriter;

    /**
     * 列出某目标下已写入的指标名。
     *
     * <p>曲线查询要求「一条曲线对应一个指标」。同一 target 下并存多个指标时，
     * 必须先调本接口拿到候选指标名再显式指定，否则后端会拒绝合并查询。</p>
     *
     * @param target    目标表(测量)，如 ms_monitor_metric_cpu
     * @param monitorId 监控目标标识
     * @return data 为指标名升序数组
     */
    @GetMapping("/metrics")
    public ReturnResult<List<String>> metrics(
            @RequestParam(defaultValue = "ms_monitor_metric") String target,
            @RequestParam Long monitorId) {
        if (!timeSeriesWriter.isAvailable()) {
            return ReturnResult.ok(List.of());
        }
        try {
            return ReturnResult.ok(timeSeriesWriter.metrics(target, monitorId));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[monitor] metrics 查询失败 target={} monitor={}: {}", target, monitorId, e.getMessage());
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 指标均值曲线。
     *
     * <p>一条曲线只能对应一个指标：{@code metricName} 缺省时由引擎按唯一性规则推断，
     * 该 target 下存在多个指标会直接返回错误并列出候选指标名，
     * 而不会把多个指标混进同一条曲线——那样折线图的纵轴会失去意义且不报错。</p>
     *
     * @param target     目标表(测量)，如 ms_monitor_metric_cpu
     * @param monitorId  监控目标标识
     * @param metricName 指标名，如 cpu_usage；缺省时要求该目标下只有一个指标
     * @param hours      回溯小时数，默认 1，必须大于 0
     * @param window     聚合窗口，如 1m/5m/1h，默认 1m
     * @return data 为 [[ts,值],...]
     */
    @GetMapping("/series")
    public ReturnResult<List<List<Object>>> series(
            @RequestParam(defaultValue = "ms_monitor_metric") String target,
            @RequestParam Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(defaultValue = DEFAULT_WINDOW) String window) {
        if (!timeSeriesWriter.isAvailable()) {
            return ReturnResult.ok(List.of());
        }
        try {
            return ReturnResult.ok(timeSeriesWriter.series(target, monitorId, metricName, hours, window));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[monitor] series 查询失败 target={} monitor={}: {}", target, monitorId, e.getMessage());
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }
}

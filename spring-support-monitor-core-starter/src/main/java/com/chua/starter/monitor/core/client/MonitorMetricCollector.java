package com.chua.starter.monitor.core.client;

import com.chua.starter.monitor.core.spi.MetricPoint;

import java.util.List;

/**
 * 客户端指标采集器 SPI。
 *
 * <p>agent 模式下由推送引擎按 {@code plugin.monitor.client.push-interval-seconds}
 * 周期调用，产出的指标点经 {@code POST /ms/monitor/agent/metrics} 上报。
 * 默认实现为 {@link OshiMetricCollector}（OSHI 本机采集）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface MonitorMetricCollector {

    /**
     * 采集一轮本机指标点。
     *
     * <p>实现必须自行兜底：任何异常都不允许抛出，采集失败返回空列表即可，
     * 由推送引擎跳过本轮，不影响心跳。</p>
     *
     * @return 指标点列表，永不返回 null
     */
    List<MetricPoint> collect();
}

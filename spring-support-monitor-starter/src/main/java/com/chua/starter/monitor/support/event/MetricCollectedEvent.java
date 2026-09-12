package com.chua.starter.monitor.support.event;

import com.chua.starter.monitor.support.spi.CollectResult;

/**
 * 指标采集完成事件
 * <p>
 * 采集任务执行成功后发布，用于解耦采集主链路与下游消费方（告警评估、实时推送等）。
 * 下游以 {@link org.springframework.context.event.EventListener} 监听，任一端点失败
 * 不影响采集与入库主链路。
 * </p>
 *
 * @param result 采集结果
 * @author CH
 * @since 4.0.0.42
 * @return 指标collected事件的结果
 */
public record MetricCollectedEvent(CollectResult result) {
}
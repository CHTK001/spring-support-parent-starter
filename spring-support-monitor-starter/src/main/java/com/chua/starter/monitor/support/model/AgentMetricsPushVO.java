package com.chua.starter.monitor.support.model;

import com.chua.starter.monitor.support.spi.MetricPoint;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent 指标推送负载
 * <p>
 * 请求体形如 {@code {"points":[{"metricName":"cpu_usage","value":35.2,"unit":"%",
 * "tags":{"model":"x86"},"targetTable":"ms_monitor_metric_cpu"}]}}；
 * {@code targetTable} 缺省时由服务端落通用表。
 * </p>
 *
 * @param points 指标点列表
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentMetricsPushVO(List<MetricPoint> points) {

    /**
     * 规范构造器：对指标点列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本记录由请求体反序列化而来，
     * {@code points} 可整体缺省为 null，列表元素也允许为 null（上报端归一化时逐项跳过），
     * 因此改用可容纳 null 的不可变包装，不加 requireNonNull。元素自身仍是可变对象，
     * 归一化阶段还要补默认落库表，故只拷贝列表、不深拷贝元素。</p>
     *
     * @param points 指标点列表，可为 null
     */
    public AgentMetricsPushVO {
        points = points == null ? null : Collections.unmodifiableList(new ArrayList<>(points));
    }
}

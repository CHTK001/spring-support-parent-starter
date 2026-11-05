package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 指标时序。
 *
 * @param name   指标名
 * @param scope  作用域
 * @param label  实体展示名（服务名 / 实例名 / 端点名）
 * @param unit   单位
 * @param points 数据点（时间戳 + 值）
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingMetricSeriesVO(String name,
                                       String scope,
                                       String label,
                                       String unit,
                                       List<MetricPointVO> points) {

    /**
     * 单个数据点
     *
     * @param time  毫秒时间戳
     * @param value 值
     */
    public record MetricPointVO(long time, double value) {
    }
}

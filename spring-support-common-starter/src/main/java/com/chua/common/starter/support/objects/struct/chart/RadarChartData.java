package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.List;

/**
 * 雷达图数据
 *
 * @param indicators 指标名称
 * @param values     数值集合
 * @author CH
 * @since 4.0.0
 */
public record RadarChartData(
        List<String> indicators,
        List<Number> values) implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;
}

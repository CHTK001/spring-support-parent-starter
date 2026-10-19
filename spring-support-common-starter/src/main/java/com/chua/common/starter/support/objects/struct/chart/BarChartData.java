package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.List;

/**
 * 柱状图数据
 *
 * @param categories 分类名称
 * @param values    数值集合
 * @author CH
 * @since 4.0.0
 */
public record BarChartData(
        List<String> categories,
        List<Number> values) implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;
}

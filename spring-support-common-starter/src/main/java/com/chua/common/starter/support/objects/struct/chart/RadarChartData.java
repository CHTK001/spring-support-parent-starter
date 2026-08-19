package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 雷达图数据
 *
 * @author CH
 */
public class RadarChartData implements Serializable {

    /** 指标名称 */
    /** Indicators */
    private List<String> indicators = new ArrayList<>();

    /** 数值集合 */
    /** Values */
    private List<Number> values = new ArrayList<>();

    public List<String> getIndicators() {
        return indicators;
    }

    public void setIndicators(List<String> indicators) {
        this.indicators = indicators;
    }

    public List<Number> getValues() {
        return values;
    }

    public void setValues(List<Number> values) {
        this.values = values;
    }
}

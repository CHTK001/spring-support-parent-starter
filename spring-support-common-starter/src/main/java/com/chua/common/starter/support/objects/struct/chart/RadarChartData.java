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
    private List<String> indicators = new ArrayList<>();

    /** 数值集合 */
    private List<Number> values = new ArrayList<>();

    /** 获取Indicators */
    public List<String> getIndicators() {
        return indicators;
    }

    /** 设置Indicators */
    public void setIndicators(List<String> indicators) {
        this.indicators = indicators;
    }

    /** 获取Values */
    public List<Number> getValues() {
        return values;
    }

    /** 设置Values */
    public void setValues(List<Number> values) {
        this.values = values;
    }
}

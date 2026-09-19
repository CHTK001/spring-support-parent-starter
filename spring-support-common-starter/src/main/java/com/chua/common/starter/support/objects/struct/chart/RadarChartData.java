package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 雷达图数据
 *
 * @author CH
 * @since 4.0.0
 */
public class RadarChartData implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 指标名称
    */
    private List<String> indicators = new ArrayList<>();

    /**
     * 数值集合
    */
    private List<Number> values = new ArrayList<>();

    /**
     * 获取indicators。
     * @return 获取indicators的结果
     */
    public List<String> getIndicators() {
        return indicators;
    }

    /**
     * 设置indicators。
     * @param indicators indicators
     */
    public void setIndicators(List<String> indicators) {
        this.indicators = indicators;
    }

    /**
     * 获取值。
     * @return 获取值的结果
     */
    public List<Number> getValues() {
        return values;
    }

    /**
     * 设置值。
     * @param values 值
     */
    public void setValues(List<Number> values) {
        this.values = values;
    }
}

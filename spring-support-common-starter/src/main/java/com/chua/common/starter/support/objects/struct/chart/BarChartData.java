package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 柱状图数据
 *
 * @author CH
 */
public class BarChartData implements Serializable {

    /** 分类名称 */
    private List<String> categories = new ArrayList<>();

    /** 数值集合 */
    private List<Number> values = new ArrayList<>();

    /** 获取Categories */
    public List<String> getCategories() {
        return categories;
    }

    /** 设置Categories */
    public void setCategories(List<String> categories) {
        this.categories = categories;
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

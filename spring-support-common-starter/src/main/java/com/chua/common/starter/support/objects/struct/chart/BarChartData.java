package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 柱状图数据
 *
 * @author CH
 * @since 4.0.0
 */
public class BarChartData implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 分类名称 */
    /** Categories */
    private List<String> categories = new ArrayList<>();

    /** 数值集合 */
    /** 值 */
    private List<Number> values = new ArrayList<>();

    /**
     * 获取分类。
     * @return 获取分类的结果
     */
    public List<String> getCategories() {
        return categories;
    }

    /**
     * 设置分类。
     * @param categories 分类
     */
    public void setCategories(List<String> categories) {
        this.categories = categories;
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

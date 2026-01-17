package com.chua.starter.redis.support.indicator;

import lombok.Data;

/**
 * 时间指标数据类
 * <p>
 * 用于表示时间序列数据点，包含时间戳和对应的值。
 * </p>
 *
 * @author CH
 * @since 2024/7/4
 */
@Data
public class TimeIndicator {

    /**
     * 指标名称
     */
    private String indicator;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 指标值
     */
    private double value;

    /**
     * 构造函数
     *
     * @param indicator 指标名称
     */
    public TimeIndicator(String indicator) {
        this.indicator = indicator;
    }

    /**
     * 无参构造函数
     */
    public TimeIndicator() {
    }
}


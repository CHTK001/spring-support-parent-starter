package com.chua.starter.redis.support.indicator;

import lombok.Data;

/**
 * 数据指标类
 * <p>
 * 用于表示通用数据指标，包含时间戳、值和持久化标志。
 * </p>
 *
 * @author CH
 * @since 2024/7/4
 */
@Data
public class DataIndicator {

    /**
     * 指标名称
     */
    private String indicator;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 指标值（字符串形式）
     */
    private String value;

    /**
     * 是否持久化
     */
    private boolean persistence;

    /**
     * 构造函数
     *
     * @param indicator 指标名称
     */
    public DataIndicator(String indicator) {
        this.indicator = indicator;
    }

    /**
     * 无参构造函数
     */
    public DataIndicator() {
    }
}


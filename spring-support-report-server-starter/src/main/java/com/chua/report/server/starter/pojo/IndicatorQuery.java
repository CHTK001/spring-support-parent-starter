package com.chua.report.server.starter.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指标
 * @author CH
 * @since 2024/7/5
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IndicatorQuery extends IdQuery {
    /**
     * TIME, SIMPLE
     */
    private String type;
    /**
     * 指标名称
     */
    private String name;
    /**
     * 开始时间
     */
    private long fromTimestamp;

    /**
     * 截止时间
     */
    private long toTimestamp;

    /**
     * 数量
     */
    private int count = 1000;
}

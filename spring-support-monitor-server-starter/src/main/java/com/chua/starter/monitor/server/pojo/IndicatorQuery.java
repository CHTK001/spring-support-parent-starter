package com.chua.starter.monitor.server.pojo;

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
     * 指标类型，默认为"cpu-io"，用于标识指标的类别。
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
}

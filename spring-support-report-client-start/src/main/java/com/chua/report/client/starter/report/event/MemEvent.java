package com.chua.report.client.starter.report.event;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 内存信息
 * 该类用于封装某个时间点的内存使用情况，包括总内存、已用内存和可用内存
 * @author CH
 * @since 2024/9/18
 */
@Data
public class MemEvent {
    /**
     * 时间戳，记录内存信息的采集时间
     */
    private long timestamp;
    /**
     * 总内存，单位为字节
     */
    private double total;
    /**
     * 已用内存，单位为字节
     */
    private double used;

    /**
     * 已用内存百分比，范围在0到1之间
     */
    private BigDecimal usedPercent;

    /**
     * 可用内存百分比，范围在0到1之间
     */
    private BigDecimal freePercent;
    /**
     * 可用内存，单位为字节
     */
    private double free;
}


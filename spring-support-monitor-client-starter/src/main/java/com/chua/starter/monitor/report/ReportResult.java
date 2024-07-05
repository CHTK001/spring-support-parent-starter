package com.chua.starter.monitor.report;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 结果
 * @author CH
 * @since 2024/7/5
 */
@Data
@AllArgsConstructor
public class ReportResult {

    /**
     * 数据
     */
    private Object data;

    /**
     * 值
     */
    private double value;
}

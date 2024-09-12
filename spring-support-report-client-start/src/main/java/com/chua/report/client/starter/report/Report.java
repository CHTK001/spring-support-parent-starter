package com.chua.report.client.starter.report;

import com.chua.report.client.starter.entity.ReportValue;

/**
 * 上报信息
 * @author CH
 * @since 2024/9/12
 */
public interface Report<T extends ReportValue<T>> {
    /**
     * 上报
     * @return 上报数据
     */
    T report();
}

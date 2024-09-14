package com.chua.report.client.starter.report;

import com.chua.report.client.starter.report.event.ReportEvent;

/**
 * 上报信息
 * @author CH
 * @since 2024/9/12
 */
public interface Report<T extends ReportEvent<T>> {
    /**
     * 上报
     * @return 上报数据
     */
    T report();
}

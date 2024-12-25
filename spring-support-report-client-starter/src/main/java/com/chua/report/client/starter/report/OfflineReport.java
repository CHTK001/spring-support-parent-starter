package com.chua.report.client.starter.report;

import com.chua.report.client.starter.report.event.OfflineEvent;
import com.chua.report.client.starter.report.event.OnlineEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

/**
 * 下线
 * @author CH
 * @since 2024/12/25
 */
public class OfflineReport implements Report<OfflineEvent>{
    @Override
    public ReportEvent<OfflineEvent> report() {
        ReportEvent<OfflineEvent> reportEvent = new ReportEvent<>();
        reportEvent.setReportType(ReportEvent.ReportType.OFFLINE);
        reportEvent.setReportData(new OfflineEvent());
        return reportEvent;
    }
}

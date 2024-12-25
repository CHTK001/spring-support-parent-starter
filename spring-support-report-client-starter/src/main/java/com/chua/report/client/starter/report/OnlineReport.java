package com.chua.report.client.starter.report;

import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.OnlineEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

/**
 * 上线
 * @author CH
 * @since 2024/12/25
 */
public class OnlineReport implements Report<OnlineEvent>{
    @Override
    public ReportEvent<OnlineEvent> report() {
        ReportEvent<OnlineEvent> reportEvent = new ReportEvent<>();
        reportEvent.setReportType(ReportEvent.ReportType.ONLINE);
        reportEvent.setReportData(new OnlineEvent());
        return reportEvent;
    }
}

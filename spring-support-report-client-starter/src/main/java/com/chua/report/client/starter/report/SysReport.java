package com.chua.report.client.starter.report;

import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Sys;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.SysEvent;

/**
 * 系统信息
 * @author CH
 * @since 2024/9/18
 */
public class SysReport implements Report<SysEvent>{
    @Override
    public ReportEvent<SysEvent> report() {
        Sys sys = Oshi.newSys();
        SysEvent sysEvent = new SysEvent();
        sysEvent.setHostName(sys.getComputerName());
        sysEvent.setIfconfig(sys.getIfconfig());
        sysEvent.setOsName(sys.getOsName());
        sysEvent.setOsArch(sys.getOsArch());
        sysEvent.setPublicAddress(sys.getPublicAddress());

        ReportEvent<SysEvent> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(sysEvent);
        objectReportEvent.setReportType(ReportEvent.ReportType.SYS);
        return objectReportEvent;
    }


}
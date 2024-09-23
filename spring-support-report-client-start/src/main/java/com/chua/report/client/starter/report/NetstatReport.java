package com.chua.report.client.starter.report;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.lang.page.Page;
import com.chua.oshi.support.Netstat;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Process;
import com.chua.report.client.starter.report.event.ProcessEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.StateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Disk信息
 * @author CH
 * @since 2024/9/18
 */
@Slf4j
public class NetstatReport implements Report<List<StateEvent>>{
    @Override
    public ReportEvent<List<StateEvent>> report() {
        List<Netstat> netstats = Oshi.newNetstat();
        ReportEvent<List<StateEvent>> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(netstats.stream().map(it -> {
            StateEvent event = new StateEvent();
            event.setForeignAddress(it.getForeignAddress());
            event.setState(it.getState());
            event.setLocalAddress(it.getLocalAddress());
            return event;
        }).filter(Objects::nonNull).toList());
        objectReportEvent.setReportType(ReportEvent.ReportType.NETSTAT);
        return objectReportEvent;
    }


}

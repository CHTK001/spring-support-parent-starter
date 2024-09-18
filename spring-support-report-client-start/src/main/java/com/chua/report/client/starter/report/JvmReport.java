package com.chua.report.client.starter.report;

import com.chua.oshi.support.Jvm;
import com.chua.oshi.support.Oshi;
import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import lombok.Data;

/**
 * jvm信息
 * @author CH
 * @since 2024/9/18
 */
public class JvmReport implements Report<JvmEvent>{
    @Override
    public ReportEvent<JvmEvent> report() {
        Jvm jvm = Oshi.newJvm();
        JvmEvent jvmEvent = new JvmEvent();
        jvmEvent.setClassLoadedCount(jvm.getClassLoadedCount());
        jvmEvent.setThreadCount(jvm.getThreadCount());
        jvmEvent.setFreeMemory(jvmEvent.getFreeMemory());
        jvmEvent.setMaxMemory(jvm.getMaxMemory());
        jvmEvent.setElapsedTime(jvm.getElapsedTime());
        ReportEvent<JvmEvent> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(jvmEvent);
        objectReportEvent.setReportType(ReportEvent.ReportType.JVM);
        return objectReportEvent;
    }


}

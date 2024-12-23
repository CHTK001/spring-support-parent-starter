package com.chua.report.client.starter.report;

import com.chua.common.support.utils.NumberUtils;
import com.chua.oshi.support.Jvm;
import com.chua.oshi.support.Mem;
import com.chua.oshi.support.Oshi;
import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.MemEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

import java.math.BigDecimal;

/**
 * 内存信息
 * @author CH
 * @since 2024/9/18
 */
public class MemReport implements Report<MemEvent>{
    @Override
    public ReportEvent<MemEvent> report() {
        Mem mem = Oshi.newMem();
        MemEvent memEvent = new MemEvent();
        memEvent.setFree(mem.getFree());
        memEvent.setTotal(mem.getTotal());
        memEvent.setUsed(mem.getUsed());
        memEvent.setUsedPercent(mem.getUsedPercent());
        memEvent.setTimestamp(memEvent.getTimestamp());

        ReportEvent<MemEvent> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(memEvent);
        objectReportEvent.setReportType(ReportEvent.ReportType.MEM);
        return objectReportEvent;
    }


}
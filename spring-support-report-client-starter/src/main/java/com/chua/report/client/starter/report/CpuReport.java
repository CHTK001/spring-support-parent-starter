package com.chua.report.client.starter.report;

import com.chua.oshi.support.Cpu;
import com.chua.oshi.support.Jvm;
import com.chua.oshi.support.Oshi;
import com.chua.report.client.starter.report.event.CpuEvent;
import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

/**
 * cpu信息
 * @author CH
 * @since 2024/9/18
 */
public class CpuReport implements Report<CpuEvent>{
    @Override
    public ReportEvent<CpuEvent> report() {
        Cpu cpu = Oshi.newCpu(1000);
        ReportEvent<CpuEvent> objectReportEvent = new ReportEvent<>();
        CpuEvent cpuEvent = new CpuEvent();
        cpuEvent.setCpuModel(cpu.getCpuModel());
        cpuEvent.setCpuNum(cpu.getCpuNum());
        cpuEvent.setUser(cpu.getUser());
        cpuEvent.setSys(cpu.getSys());
        cpuEvent.setWait(cpu.getWait());
        cpuEvent.setFree(cpu.getFree());
        cpuEvent.setToTal(cpu.getToTal());

        objectReportEvent.setReportData(cpuEvent);
        objectReportEvent.setReportType(ReportEvent.ReportType.CPU);
        return objectReportEvent;
    }


}

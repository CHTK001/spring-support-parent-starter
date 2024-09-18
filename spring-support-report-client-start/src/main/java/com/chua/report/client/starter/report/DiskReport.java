package com.chua.report.client.starter.report;

import com.chua.common.support.utils.NumberUtils;
import com.chua.oshi.support.Disk;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.SysFile;
import com.chua.report.client.starter.report.event.DiskEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

import java.util.List;

/**
 * Disk信息
 * @author CH
 * @since 2024/9/18
 */
public class DiskReport implements Report<List<DiskEvent>>{
    @Override
    public ReportEvent<List<DiskEvent>> report() {
        List<SysFile> sysFiles = Oshi.newSysFile();

        ReportEvent<List<DiskEvent>> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(sysFiles.stream().map(it -> {
            DiskEvent diskEvent = new DiskEvent();
            diskEvent.setDirName(it.getDirName());
            diskEvent.setTypeName(it.getTypeName());
            diskEvent.setTotal(it.getTotal());
            diskEvent.setFree(it.getFree());
            diskEvent.setUsed(it.getUsed());
            diskEvent.setUsedPercent(it.getUsedPercent());

            diskEvent.setTotalText(it.getTotalText());
            diskEvent.setFreeText(it.getFreeText());
            diskEvent.setUsedText(it.getUsedText());
            diskEvent.setSysTypeName(it.getSysTypeName());
            return diskEvent;
        }).toList());
        objectReportEvent.setReportType(ReportEvent.ReportType.DISK);
        return objectReportEvent;
    }


}

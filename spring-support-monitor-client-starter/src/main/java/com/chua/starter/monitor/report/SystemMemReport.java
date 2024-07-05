package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.oshi.support.Mem;
import com.chua.oshi.support.Oshi;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("memory")
public class SystemMemReport implements Report{
    @Override
    public ReportResult report() {
        Mem mem = Oshi.newMem();
        return new ReportResult(mem, mem.getFree() / mem.getTotal() * 100D);
    }
}

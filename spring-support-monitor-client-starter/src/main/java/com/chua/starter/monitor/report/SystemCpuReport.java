package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.oshi.support.Cpu;
import com.chua.oshi.support.Oshi;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("cpu")
public class SystemCpuReport implements Report{
    @Override
    public ReportResult report() {
        Cpu cpu = Oshi.newCpu(1000);
        return new ReportResult(cpu, 100 - cpu.getToTal());
    }
}

package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.oshi.support.JvmMem;
import com.chua.oshi.support.Oshi;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("memory")
public class JvmMemReport implements Report{
    @Override
    public ReportResult report() {
        JvmMem mem = Oshi.newJvmMem();
        return new ReportResult(mem, mem.getProcess());
    }
}

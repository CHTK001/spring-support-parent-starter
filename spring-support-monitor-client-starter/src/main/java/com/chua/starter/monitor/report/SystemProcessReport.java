package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.page.Page;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Process;

import java.util.Collections;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("process")
public class SystemProcessReport implements Report{
    @Override
    public ReportResult report() {
        Page<Process> processPage = Oshi.newProcess(Collections.emptyMap());
        return new ReportResult(processPage, 0);
    }
}

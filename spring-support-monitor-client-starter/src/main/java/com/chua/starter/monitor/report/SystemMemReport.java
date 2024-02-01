package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.oshi.support.Oshi;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("mem")
public class SystemMemReport implements Report{
    @Override
    public Object report() {
        return Oshi.newMem();
    }
}

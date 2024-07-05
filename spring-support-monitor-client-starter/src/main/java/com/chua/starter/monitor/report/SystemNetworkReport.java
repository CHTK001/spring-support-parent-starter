package com.chua.starter.monitor.report;

import com.chua.common.support.annotations.Spi;
import com.chua.oshi.support.Network;
import com.chua.oshi.support.Oshi;

import java.util.List;

/**
 * 系统报告
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("network")
public class SystemNetworkReport implements Report{
    @Override
    public ReportResult report() {
        List<Network> networks = Oshi.newNetwork();
        return new ReportResult(networks, 0);
    }
}

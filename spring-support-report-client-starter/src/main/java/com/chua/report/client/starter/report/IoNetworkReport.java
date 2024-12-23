package com.chua.report.client.starter.report;

import com.chua.oshi.support.Network;
import com.chua.oshi.support.Oshi;
import com.chua.report.client.starter.report.event.IoEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

import java.util.List;

/**
 * 网卡上下行
 * @author CH
 * @since 2024/12/23
 */
public class IoNetworkReport implements Report<IoEvent> {
    @Override
    public ReportEvent<IoEvent> report() {
        IoEvent ioEvent = new IoEvent();
        long receiveBytes = 0, sendBytes = 0;
        List<Network> networks = Oshi.newNetwork();
        for (Network network : networks) {
            receiveBytes += network.getReceiveBytes();
            sendBytes += network.getTransmitBytes();
        }
        ioEvent.setReceiveBytes(receiveBytes);
        ioEvent.setTransmitBytes(sendBytes);
        ReportEvent<IoEvent> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(ioEvent);
        objectReportEvent.setReportType(ReportEvent.ReportType.IO_NETWORK);
        return objectReportEvent;
    }
}

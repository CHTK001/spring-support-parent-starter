package com.chua.report.client.starter.report;

import com.chua.oshi.support.Network;
import com.chua.oshi.support.Oshi;
import com.chua.report.client.starter.report.event.NetworkEvent;
import com.chua.report.client.starter.report.event.ReportEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Disk信息
 * @author CH
 * @since 2024/9/18
 */
public class NetworkReport implements Report<List<NetworkEvent>>{
    @Override
    public ReportEvent<List<NetworkEvent>> report() {
        List<Network> networks = Oshi.newNetwork();

        List<String> less = new ArrayList<>(networks.size());
        ReportEvent<List<NetworkEvent>> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(networks.stream().map(it -> {
            String mac = it.getMac();
            if(less.contains(mac)) {
                return null;
            }
            less.add(mac);
            NetworkEvent networkEvent = new NetworkEvent();
            networkEvent.setName(it.getName());
            networkEvent.setDisplayName(it.getDisplayName());
            networkEvent.setReadBytes(it.getReceiveBytes());
            networkEvent.setWriteBytes(it.getTransmitBytes());
            return networkEvent;
        }).filter(Objects::nonNull).toList());
        objectReportEvent.setReportType(ReportEvent.ReportType.NETWORK);
        return objectReportEvent;
    }


}

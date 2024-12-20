package com.chua.report.client.starter.report;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.SysFile;
import com.chua.oshi.support.UsbDevice;
import com.chua.report.client.starter.report.event.DiskEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.UsbDeviceEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Disk信息
 * @author CH
 * @since 2024/9/18
 */
@Slf4j
public class UsbReport implements Report<List<UsbDeviceEvent>>{
    @Override
    public ReportEvent<List<UsbDeviceEvent>> report() {
        List<UsbDevice> usbDevices = Oshi.newUsb();
        ReportEvent<List<UsbDeviceEvent>> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(usbDevices.stream().map(it -> {
            UsbDeviceEvent usbDeviceEvent = new UsbDeviceEvent();
            usbDeviceEvent.setName(it.getName());
            usbDeviceEvent.setVendor(it.getVendor());
            usbDeviceEvent.setProductId(it.getProductId());
            usbDeviceEvent.setVendorId(it.getVendorId());
            usbDeviceEvent.setSerialNumber(it.getSerialNumber());
            usbDeviceEvent.setUniqueDeviceId(it.getUniqueDeviceId());
            return usbDeviceEvent;
        }).toList());
        if(log.isDebugEnabled()) {
            log.debug("当前上报设备数量: {}", CollectionUtils.size(objectReportEvent.getReportData()));
        }
        objectReportEvent.setReportType(ReportEvent.ReportType.USB);
        return objectReportEvent;
    }


}

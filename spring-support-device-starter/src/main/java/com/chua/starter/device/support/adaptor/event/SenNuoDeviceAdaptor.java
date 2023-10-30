package com.chua.starter.device.support.adaptor.event;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.SenNuoAdaptor;
import com.chua.starter.device.support.adaptor.client.SnNuoClient;
import com.chua.starter.device.support.adaptor.device.DeviceDownloadAdaptor;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.Collections;
import java.util.List;

/**
 * 森诺设备适配器
 *
 * @author CH
 * @since 2023/10/30
 */
@Spi("sen_nuo")
@Group(value = "access_xxz_event", desc = "气象站数据同步", group = "device")
public class SenNuoDeviceAdaptor extends SenNuoAdaptor implements AccessEventAdaptor {

    public SenNuoDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<? extends DeviceDataEvent> getEvent(AccessEventRequest request) {
        List<String> deviceSerials = request.getDeviceSerials();
        if(deviceSerials.isEmpty()) {
            return Collections.emptyList();
        }
        String event = snNuoClient.getEvent(deviceSerials.get(0));
        return null;
    }
}

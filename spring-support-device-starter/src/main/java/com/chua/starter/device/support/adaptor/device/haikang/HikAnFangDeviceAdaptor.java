package com.chua.starter.device.support.adaptor.device.haikang;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.client.pojo.HikAnFangDeviceListResult;
import com.chua.starter.device.support.adaptor.device.DeviceAdaptor;
import com.chua.starter.device.support.adaptor.device.DeviceDownloadAdaptor;
import com.chua.starter.device.support.adaptor.device.DeviceUploadAdaptor;
import com.chua.starter.device.support.adaptor.factory.haikang.HikAnFangAdaptor;
import com.chua.starter.device.support.adaptor.pojo.UploadResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.Collections;
import java.util.List;

/**
 * 海康安防
 * @author CH
 */
//@Group(value = "device", desc = "同步设备到云服务器", group = "device")
@Group(value = "device", desc = "设备同步", group = "service")
@Spi("hai_kang_an_fang")
public class HikAnFangDeviceAdaptor extends HikAnFangAdaptor implements DeviceAdaptor, DeviceUploadAdaptor, DeviceDownloadAdaptor {
    public HikAnFangDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceInfo> downloadFromCloud(int page, int pageSize) {
        HikAnFangDeviceListResult hikAnFangDeviceListResult = hikAnFangClient.devicePage(page, pageSize);
        return Collections.emptyList();
    }

    @Override
    public UploadResult uploadToCloud(DeviceInfo deviceInfo) {
        return null;
    }
}

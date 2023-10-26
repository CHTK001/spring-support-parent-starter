package com.chua.starter.device.support.adaptor.device;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.YunYaoAdaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.pojo.UploadResult;

import java.util.List;

/**
 * @author CH
 */
@Group(value = "device", desc = "同步设备到云服务器", group = "device")
@Group(value = "device", desc = "设备同步", group = "service")
@Spi("hai_kang_yun_yao")
public class HikYunYaoDeviceAdaptor extends
        YunYaoAdaptor implements DeviceAdaptor, DeviceUploadAdaptor, DeviceDownloadAdaptor {


    public HikYunYaoDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceInfo> downloadFromCloud(int page, int pageSize) {
        return null;
    }

    @Override
    public UploadResult uploadToCloud(DeviceInfo deviceInfo) {
        return null;
    }
}

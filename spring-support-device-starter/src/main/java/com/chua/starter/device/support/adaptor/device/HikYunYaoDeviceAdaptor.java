package com.chua.starter.device.support.adaptor.device;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.YunYaoAdaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.List;

/**
 * @author CH
 */
@Spi("hk_yun_yao")
public class HikYunYaoDeviceAdaptor extends YunYaoAdaptor implements DeviceAdaptor {


    public HikYunYaoDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceInfo> query(int page, int pageSize) {
        return null;
    }
}

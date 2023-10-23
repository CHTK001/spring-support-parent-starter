package com.chua.starter.device.support.adaptor.org;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.YunYaoAdaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceOrg;

/**
 * @author CH
 */
@Spi("hai_kang_yun_yao")
public class HikYunYaoOrgAdaptor extends YunYaoAdaptor implements OrgAdaptor {

    public HikYunYaoOrgAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public DeviceOrg orgList(int pageNo, int pageSize) {
//        String json = yunYaoClient.execute("POST /api/resource/v2/org/advance/orgList", deviceCloudPlatformConnector.getDeviceConnectorTimeout());
        return null;
    }
}

package com.chua.starter.device.support.adaptor.org;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.adaptor.YunYaoAdaptor;
import com.chua.starter.device.support.adaptor.client.pojo.OrgListResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceOrg;

import java.util.List;

/**
 * @author CH
 */
@Group(value = "org", desc = "同步组织结构", group = "service")
@Spi("hai_kang_yun_yao")
public class HikYunYaoOrgAdaptor extends YunYaoAdaptor implements OrgAdaptor, OrgDownloadAdaptor {

    public HikYunYaoOrgAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceOrg> downloadFromCloud(int pageNo, int pageSize) {
        OrgListResult orgListResult = hikYunYaoClient.orgList(pageNo, pageSize);
        List<OrgListResult.ListDTO> list = orgListResult.getList();

        return null;
    }
}

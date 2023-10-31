package com.chua.starter.device.support.adaptor.org.haikang;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.client.pojo.HikAnFangOrgListResult;
import com.chua.starter.device.support.adaptor.factory.haikang.HikAnFangAdaptor;
import com.chua.starter.device.support.adaptor.org.OrgAdaptor;
import com.chua.starter.device.support.adaptor.org.OrgDownloadAdaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceOrg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author CH
 */
@Group(value = "org", desc = "组织机构同步", group = "service")
@Spi("hai_kang_an_fang")
public class HikAnFangOrgAdaptor extends HikAnFangAdaptor implements OrgDownloadAdaptor, OrgAdaptor {
    public HikAnFangOrgAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceOrg> downloadFromCloud(int pageNo, int pageSize) {
        HikAnFangOrgListResult hikAnFangOrgListResult = hikAnFangClient.orgList(pageNo, pageSize);
        if(null == hikAnFangOrgListResult) {
            return Collections.emptyList();
        }
        List<HikAnFangOrgListResult.ListDTO> list = hikAnFangOrgListResult.getList();
        List<DeviceOrg> deviceOrgs = new ArrayList<>(list.size());

        String deviceConnectorId = deviceCloudPlatformConnector.getDeviceConnectorId() + "";
        for (HikAnFangOrgListResult.ListDTO dto : list) {
            DeviceOrg item = new DeviceOrg();
            item.setDeviceOrgTreeId(dto.getOrgIndexCode());
            item.setDeviceOrgPid(dto.getParentOrgIndexCode());
            if("-1".equals(dto.getParentOrgIndexCode())) {
                item.setDeviceOrgPid(deviceConnectorId);
            }
            item.setDeviceConnectorId(deviceCloudPlatformConnector.getDeviceConnectorId() +  "");
            item.setDeviceOrgPath(deviceConnectorId + StringUtils.endWithMove(dto.getOrgPath().replace("@", ","), ","));
            item.setDeviceOrgName(dto.getOrgName());
            item.setDeviceSort(dto.getSort());
            if(StringUtils.isNotBlank(dto.getCreateTime())) {
                item.setCreateTime(DateTime.of(dto.getCreateTime()).toDate());
            }

            deviceOrgs.add(item);
        }

        return deviceOrgs;
    }
}

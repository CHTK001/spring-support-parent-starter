package com.chua.starter.device.support.adaptor.org;

import com.chua.starter.device.support.entity.DeviceOrg;

/**
 * @author CH
 */
public interface OrgAdaptor {
    /**
     * 查询组织列表v2
     * @param pageNo     页码(1)
     * @param pageSize 分页数量 (1000)
     * @return 组织机构
     */
    DeviceOrg orgList(int pageNo, int pageSize);
}

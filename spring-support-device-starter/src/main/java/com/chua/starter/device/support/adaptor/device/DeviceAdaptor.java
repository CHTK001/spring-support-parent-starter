package com.chua.starter.device.support.adaptor.device;

import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.List;

/**
 * @author CH
 */
public interface DeviceAdaptor {

    /**
     * 查询
     *
     * @param page     分页
     * @param pageSize 分页大小
     * @return {@link List}<{@link DeviceInfo}>
     */
    List<DeviceInfo> query(int page, int pageSize);
}

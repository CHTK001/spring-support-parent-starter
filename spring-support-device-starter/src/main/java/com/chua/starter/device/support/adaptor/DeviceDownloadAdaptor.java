package com.chua.starter.device.support.adaptor;

import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.List;

/**
 * 下载本地
 * @author CH
 */
public interface DeviceDownloadAdaptor {

    /**
     * 从云端下载
     *
     * @param page     分页
     * @param pageSize 分页大小
     * @return {@link List}<{@link DeviceInfo}>
     */
    List<DeviceInfo> downloadFromCloud(int page, int pageSize);
}

package com.chua.starter.device.support.adaptor.device;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.pojo.UploadResult;

/**
 * 上传到云服务
 * @author CH
 */
public interface DeviceUploadAdaptor {

    /**
     * 上传到云
     *
     * @param deviceInfo 设备信息
     * @return {@link UploadResult}
     */
    UploadResult uploadToCloud(DeviceInfo deviceInfo);
}

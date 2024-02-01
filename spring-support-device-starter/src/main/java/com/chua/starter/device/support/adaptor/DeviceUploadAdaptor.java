package com.chua.starter.device.support.adaptor;

import com.chua.starter.device.support.adaptor.pojo.UploadResult;
import com.chua.starter.device.support.entity.DeviceInfo;

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

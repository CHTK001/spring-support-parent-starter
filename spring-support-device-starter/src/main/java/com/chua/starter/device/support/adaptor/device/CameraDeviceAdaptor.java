package com.chua.starter.device.support.adaptor.device;

import com.chua.starter.device.support.adaptor.pojo.LiveResult;

/**
 * 摄像头
 * @author CH
 */
public interface CameraDeviceAdaptor {

    /**
     * 获取实时地址
     *
     * @param deviceImsi 设备imsi
     * @return {@link String}
     */
    LiveResult getLiveAddress(String deviceImsi);
}

package com.chua.starter.device.support.adaptor;

import com.chua.starter.device.support.adaptor.pojo.LiveResult;

/**
 * 摄像头
 * @author CH
 */
public interface CameraDeviceAdaptor {

    /**
     * 获取实时地址
     *
     * @param deviceImsi    设备imsi
     * @param deviceChannel 设备通道
     * @return {@link LiveResult}
     */
    LiveResult getLiveAddress(String deviceImsi, String deviceChannel);
}

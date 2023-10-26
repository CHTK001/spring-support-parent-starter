package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.List;

/**
 * @author CH
 */
public interface DeviceInfoService extends IService<DeviceInfo> {


    /**
     * 注册装置
     *
     * @param deviceInfos            设备信息
     * @param cloudPlatformConnector 云平台连接器
     */
    void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector);
}

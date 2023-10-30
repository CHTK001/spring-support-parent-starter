package com.chua.starter.device.support.service;

import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;

import java.util.List;

/**
 * 设备气象站服务
 *
 * @author CH
 * @since 2023/10/30
 */
public interface DeviceMeteorologicalStationService {


    /**
     * 注册事件
     *
     * @param event             事件
     * @param platformConnector 平台连接器
     * @param result            后果
     */
    void registerEvent(List<DeviceMeteorologicalStationEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result);
}

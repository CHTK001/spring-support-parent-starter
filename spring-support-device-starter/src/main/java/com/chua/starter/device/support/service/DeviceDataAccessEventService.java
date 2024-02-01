package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;

import java.util.List;

public interface DeviceDataAccessEventService extends IService<DeviceDataAccessEvent>{


    /**
     * 注册事件
     *
     * @param event             事件
     * @param platformConnector 平台连接器
     * @param result            后果
     */
    void registerEvent(List<DeviceDataAccessEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result);
}

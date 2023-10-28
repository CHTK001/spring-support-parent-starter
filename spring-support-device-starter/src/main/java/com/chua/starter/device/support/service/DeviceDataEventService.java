package com.chua.starter.device.support.service;

import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.request.ServletEventRequest;

import java.util.List;

public interface DeviceDataEventService extends IService<DeviceDataEvent>{
    /**
     * 注册事件
     *
     * @param event              事件
     * @param platformConnector  平台连接器
     * @param result             后果
     * @param accessEventRequest
     */
    void registerEvent(List<? extends DeviceDataEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result, ServletEventRequest accessEventRequest);

}

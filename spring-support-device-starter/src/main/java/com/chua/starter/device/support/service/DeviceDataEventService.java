package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.request.EventRequest;
import com.chua.starter.device.support.request.ServletEventRequest;

import java.util.List;

public interface DeviceDataEventService extends IService<DeviceDataEvent>{
    /**
     * 注册事件
     *
     * @param event              事件
     * @param platformConnector  平台连接器
     * @param result             后果
     * @param accessEventRequest 访问事件请求
     */
    void registerEvent(List<? extends DeviceDataEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result, ServletEventRequest accessEventRequest);


    /**
     * 页
     *
     * @param request  事件类型
     * @param deviceInfo 设备信息
     * @return {@link ReturnPageResult}<{@link DeviceDataEvent}>
     */
    ReturnPageResult<? extends DeviceDataEvent> page(EventRequest request, DeviceInfo deviceInfo);
}

package com.chua.starter.device.support.service;

import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.request.EventType;
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
     * @param eventType  事件类型
     * @param pageNum    书籍页码
     * @param pageSize   页面大小
     * @param deviceInfo 设备信息
     * @return {@link ReturnPageResult}<{@link DeviceDataEvent}>
     */
    ReturnPageResult<? extends DeviceDataEvent> page(EventType eventType, Integer pageNum, Integer pageSize, DeviceInfo deviceInfo);
}

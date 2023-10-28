package com.chua.starter.device.support.service.impl;

import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import com.chua.starter.device.support.request.EventType;
import com.chua.starter.device.support.request.ServletEventRequest;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import org.springframework.stereotype.Service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.device.support.mapper.DeviceDataEventMapper;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.chua.starter.device.support.service.DeviceDataEventService;

import javax.annotation.Resource;

@Service
public class DeviceDataEventServiceImpl extends ServiceImpl<DeviceDataEventMapper, DeviceDataEvent> implements DeviceDataEventService{

    @Resource
    private DeviceDataAccessEventService deviceDataAccessEventService;

    @Override
    @SuppressWarnings("ALL")
    public void registerEvent(List<? extends DeviceDataEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result, ServletEventRequest accessEventRequest) {
        EventType eventType = accessEventRequest.getEventType();
        if(eventType == EventType.ACCESS) {
            deviceDataAccessEventService.registerEvent((List<DeviceDataAccessEvent>) event, platformConnector, result);
            return;
        }
    }
}

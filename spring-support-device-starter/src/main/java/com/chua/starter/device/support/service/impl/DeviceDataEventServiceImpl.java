package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.request.EventType;
import com.chua.starter.device.support.request.ServletEventRequest;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.device.support.mapper.DeviceDataEventMapper;
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

    @Override
    public ReturnPageResult<? extends DeviceDataEvent> page(EventType eventType, Integer pageNum, Integer pageSize, DeviceInfo deviceInfo) {
        if(eventType == EventType.ACCESS) {
            Page<DeviceDataAccessEvent> deviceChannelPage = new Page<>(pageNum, pageSize);
            return PageResultUtils.ok(deviceDataAccessEventService.page(deviceChannelPage, Wrappers.<DeviceDataAccessEvent>lambdaQuery()
                    .eq(StringUtils.isNotEmpty(deviceInfo.getDeviceImsi()), DeviceDataAccessEvent::getDeviceIsmi, deviceInfo.getDeviceImsi())));
        }
        return null;
    }

}

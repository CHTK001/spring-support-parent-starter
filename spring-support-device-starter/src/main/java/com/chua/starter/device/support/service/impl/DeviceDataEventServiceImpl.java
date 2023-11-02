package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.request.DataFilter;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.mapper.DeviceDataEventMapper;
import com.chua.starter.device.support.request.EventRequest;
import com.chua.starter.device.support.request.EventType;
import com.chua.starter.device.support.request.ServletEventRequest;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.device.support.service.DeviceDataEventService;
import com.chua.starter.device.support.service.DeviceMeteorologicalStationService;
import com.chua.starter.mybatis.utils.EntityWrapper;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class DeviceDataEventServiceImpl extends ServiceImpl<DeviceDataEventMapper, DeviceDataEvent> implements DeviceDataEventService{

    @Resource
    private DeviceDataAccessEventService deviceDataAccessEventService;

    @Resource
    private DeviceMeteorologicalStationService deviceMeteorologicalStationService;

    @Override
    @SuppressWarnings("ALL")
    public void registerEvent(List<? extends DeviceDataEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result, ServletEventRequest accessEventRequest) {
        EventType eventType = accessEventRequest.getEventType();
        if(eventType == EventType.ACCESS) {
            deviceDataAccessEventService.registerEvent((List<DeviceDataAccessEvent>) event, platformConnector, result);
            return;
        }

        if(eventType == EventType.QI_XIANG_ZHAN) {
            deviceMeteorologicalStationService.registerEvent((List<DeviceMeteorologicalStationEvent>) event, platformConnector, result);
            return;
        }
    }

    @Override
    public ReturnPageResult<? extends DeviceDataEvent> page(EventRequest request, DeviceInfo deviceInfo) {
        Integer pageSize = request.getPageSize();
        Integer pageNum = request.getPageNum();
        EventType eventType = request.getEventType();
        DataFilter dataFilter = DataFilter.of(request.getFilter());
        if(eventType == EventType.ACCESS) {
            Page<DeviceDataAccessEvent> deviceChannelPage = new Page<>(pageNum, pageSize);
            QueryWrapper<DeviceDataAccessEvent> wrapper = EntityWrapper.of(DeviceDataAccessEvent.class, dataFilter).getWrapper();
            return PageResultUtils.ok(deviceDataAccessEventService.page(deviceChannelPage, wrapper.lambda()
                    .eq(StringUtils.isNotEmpty(request.getDeviceDataEventInOrOut()), DeviceDataAccessEvent::getDeviceDataEventInOrOut, request.getDeviceDataEventInOrOut())
                    .orderByDesc(DeviceDataAccessEvent::getDeviceDataEventTime)
            ));
        }

        if(eventType == EventType.QI_XIANG_ZHAN) {
            Page<DeviceMeteorologicalStationEvent> deviceChannelPage = new Page<>(pageNum, pageSize);
            return PageResultUtils.ok(deviceMeteorologicalStationService.page(deviceChannelPage, dataFilter));
        }
        return null;
    }

}

package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.request.DataFilter;
import com.chua.common.support.request.ItemFilter;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.request.EventRequest;
import com.chua.starter.device.support.request.EventType;
import com.chua.starter.device.support.request.ServletEventRequest;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.mybatis.utils.EntityWrapper;
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
    public ReturnPageResult<? extends DeviceDataEvent> page(EventRequest request, DeviceInfo deviceInfo) {
        Integer pageSize = request.getPageSize();
        Integer pageNum = request.getPageNum();
        EventType eventType = request.getEventType();
        DataFilter dataFilter = DataFilter.of(request.getFilter());
        if(eventType == EventType.ACCESS) {
            Page<DeviceDataAccessEvent> deviceChannelPage = new Page<>(pageNum, pageSize);
            Wrapper<DeviceDataAccessEvent> wrapper = EntityWrapper.of(DeviceDataAccessEvent.class, dataFilter).getWrapper();
            return PageResultUtils.ok(deviceDataAccessEventService.page(deviceChannelPage, wrapper
            ));
//                    Wrappers.<DeviceDataAccessEvent>lambdaQuery()
//                        .eq(StringUtils.isNotEmpty(request.getDeviceImsi()) , DeviceDataAccessEvent::getDeviceIsmi, request.getDeviceImsi())
//                        .eq(StringUtils.isNotEmpty(request.getDeviceDataEventInOrOut()), DeviceDataAccessEvent::getDeviceDataEventInOrOut, request.getDeviceDataEventInOrOut())
//                        .ge(null != request.getStartTime(), DeviceDataAccessEvent::getDeviceDataEventTime, request.getStartTime())
//                        .le(null != request.getEndTime(), DeviceDataAccessEvent::getDeviceDataEventTime, request.getEndTime())
//                        .like(StringUtils.isNotEmpty(keyword), DeviceDataAccessEvent::getDeviceDataPersonNum, keyword)
//                        .or().like(StringUtils.isNotEmpty(keyword), DeviceDataAccessEvent::getDeviceDataPersonNum, keyword)
//                        .orderByDesc(DeviceDataAccessEvent::getDeviceDataEventTime)
        }
        return null;
    }

}

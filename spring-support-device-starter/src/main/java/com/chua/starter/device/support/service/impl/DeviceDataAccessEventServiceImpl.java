package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.mapper.DeviceDataAccessEventMapper;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.device.support.service.DeviceLogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
@Service
public class DeviceDataAccessEventServiceImpl extends ServiceImpl<DeviceDataAccessEventMapper, DeviceDataAccessEvent> implements DeviceDataAccessEventService{
    @Resource
    private DeviceLogService deviceLogService;
    @Override
    public void registerEvent(List<DeviceDataAccessEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result) {
        if(CollectionUtils.isEmpty(event)) {
            return;
        }
        String connectorId = platformConnector.getDeviceConnectorId() + "";
        result.addTotal(event.size());
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceLogFrom("同步门禁事件接口(页面)");
        deviceLog.setCreateTime(new Date());
        deviceLog.setDeviceLogType("SYNC("+ connectorId +")");
        for (DeviceDataAccessEvent deviceDataAccessEvent : event) {
            try {
                saveOrUpdate(deviceDataAccessEvent, Wrappers.<DeviceDataAccessEvent>lambdaUpdate()
                        .eq(DeviceDataAccessEvent::getDeviceDataDataId, deviceDataAccessEvent.getDeviceDataDataId())
                        .eq(DeviceDataAccessEvent::getDeviceDataEventTime, deviceDataAccessEvent.getDeviceDataEventTime())
                        .eq(DeviceDataAccessEvent::getDeviceIsmi, deviceDataAccessEvent.getDeviceIsmi())
                );
                result.addSuccessTotal(1);
            } catch (Exception e) {
                result.addFailureTotal(1);
                deviceLog.setDeviceLogError(e.getLocalizedMessage());
            }
        }
        deviceLogService.save(deviceLog);
    }
}

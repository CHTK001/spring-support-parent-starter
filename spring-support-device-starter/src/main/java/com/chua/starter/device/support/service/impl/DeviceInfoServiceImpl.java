package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.device.support.service.DeviceLogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

import static com.chua.starter.device.support.service.impl.DeviceOrgServiceImpl.STATIC_EXECUTOR_SERVICE;

/**
 *    
 * @author CH
 */     
@Service
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements DeviceInfoService{


    @Resource
    private DeviceLogService deviceLogService;

    @Override
    public void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector) {
        if(CollectionUtils.isEmpty(deviceInfos)) {
            return;
        }

        STATIC_EXECUTOR_SERVICE.execute(() -> {
            for (DeviceInfo deviceInfo : deviceInfos) {
                deviceInfo.setDeviceConnectorId(cloudPlatformConnector.getDevicePlatformId());

                DeviceLog deviceLog = new DeviceLog();
                deviceLog.setDeviceLogFrom("同步设备接口(页面)");
                deviceLog.setCreateTime(new Date());
                deviceLog.setDeviceLogType("SYNC("+ cloudPlatformConnector.getDeviceConnectorId() +")");
                try {
                    super.saveOrUpdate(deviceInfo, Wrappers.<DeviceInfo>lambdaUpdate()
                            .eq(DeviceInfo::getDeviceImsi, deviceInfo.getDeviceImsi())
                            .eq(DeviceInfo::getDeviceConnectorId, cloudPlatformConnector.getDeviceConnectorId())
                    );
                } catch (Exception e) {
                    deviceLog.setDeviceLogError(e.getLocalizedMessage());
                }
                deviceLog.setDeviceLogImsi(deviceInfo.getDeviceImsi());
                deviceLogService.save(deviceLog);
            }
        });
    }
}

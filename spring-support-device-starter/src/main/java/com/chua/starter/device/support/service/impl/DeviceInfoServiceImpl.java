package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceType;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceTypeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.chua.starter.device.support.service.impl.DeviceOrgServiceImpl.STATIC_EXECUTOR_SERVICE;

/**
 *    
 * @author CH
 */     
@Service
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements DeviceInfoService{


    @Resource
    private DeviceLogService deviceLogService;

    @Resource
    private DeviceTypeService deviceTypeService;

    @Override
    public void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector) {
        if(CollectionUtils.isEmpty(deviceInfos)) {
            return;
        }

        STATIC_EXECUTOR_SERVICE.execute(() -> {
            List<DeviceType> list = deviceTypeService.list();
            Map<String, String> typeIds = new HashMap<>();
            for (DeviceType deviceType : list) {
                typeIds.put(deviceType.getDeviceTypeCode(), deviceType.getDeviceTypeId() + "");
            }
            for (DeviceInfo deviceInfo : deviceInfos) {
                deviceInfo.setDeviceConnectorId(cloudPlatformConnector.getDeviceConnectorId() + "");
                String deviceTypeCode = deviceInfo.getDeviceTypeCode();
                if(!StringUtils.isEmpty(deviceTypeCode) && !typeIds.containsKey(deviceTypeCode)) {
                    try {
                        DeviceType deviceType = new DeviceType();
                        deviceType.setDeviceTypeCode(deviceTypeCode);
                        deviceType.setDeviceTypeName(deviceInfo.getDeviceTypeName());
                        deviceType.setDeviceTypePath("0");
                        deviceType.setDeviceTypeParent("0");
                        deviceType.setDeviceTypeSystem("1");
                        deviceType.setCreateTime(deviceInfo.getCreateTime());

                        typeIds.put(deviceTypeCode, deviceType.getDeviceTypeId() + "");
                        deviceTypeService.saveOrUpdate(deviceType, Wrappers.<DeviceType>lambdaUpdate()
                                .eq(DeviceType::getDeviceTypeCode, deviceType.getDeviceTypeCode())
                        );
                    } catch (Exception ignored) {
                    }
                }

                DeviceLog deviceLog = new DeviceLog();
                deviceLog.setDeviceLogFrom("同步设备接口(页面)");
                deviceLog.setCreateTime(new Date());
                deviceLog.setDeviceLogType("SYNC("+ cloudPlatformConnector.getDeviceConnectorId() +")");

                deviceInfo.setDeviceTypeId(StringUtils.ifValid(typeIds.get(deviceTypeCode), null));
                try {
                    super.saveOrUpdate(deviceInfo, new LambdaUpdateWrapper<DeviceInfo>()
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

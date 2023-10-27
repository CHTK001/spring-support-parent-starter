package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceType;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.pojo.StaticResult;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceTypeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector, StaticResult result) {
        if(CollectionUtils.isEmpty(deviceInfos)) {
            return;
        }

        result.addTotal(deviceInfos.size());
        Map<String, String> typeIds = findType();
        registerDevice(deviceInfos,cloudPlatformConnector, result, typeIds);
    }

    /**
     * regsiter设备
     *
     * @param deviceInfos            设备信息
     * @param cloudPlatformConnector 云平台连接器
     * @param result                 后果
     * @param typeIds                类型id
     */
    private void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector, StaticResult result, Map<String, String> typeIds) {
        for (DeviceInfo deviceInfo : deviceInfos) {
            deviceInfo.setDeviceConnectorId(cloudPlatformConnector.getDeviceConnectorId() + "");
            String deviceTypeCode = deviceInfo.getDeviceTypeCode();
            registerDeviceType(deviceInfo, typeIds, deviceTypeCode);

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
                result.addSuccessTotal(1);
            } catch (Exception e) {
                result.addFailureTotal(1);
                deviceLog.setDeviceLogError(e.getLocalizedMessage());
            }
            deviceLog.setDeviceLogImsi(deviceInfo.getDeviceImsi());
            deviceLogService.save(deviceLog);
        }
    }

    /**
     * 注册装置类型
     *
     * @param typeIds        类型id
     * @param deviceTypeCode 设备类型代码
     * @param deviceInfo     设备信息
     */
    private void registerDeviceType(DeviceInfo deviceInfo, Map<String, String> typeIds, String deviceTypeCode) {
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
    }

    /**
     * 查找类型
     *
     * @return {@link Map}<{@link String}, {@link String}>
     */
    private Map<String, String> findType() {
        Map<String, String> typeIds = new LinkedHashMap<>();
        List<DeviceType> list = deviceTypeService.list();
        for (DeviceType deviceType : list) {
            typeIds.put(deviceType.getDeviceTypeCode(), deviceType.getDeviceTypeId() + "");
        }
        return typeIds;
    }
}

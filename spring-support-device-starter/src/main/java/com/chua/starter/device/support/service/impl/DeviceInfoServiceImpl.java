package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceChannelService;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceTypeService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

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

    @Resource
    private DeviceChannelService deviceChannelService;

    @Override
    public void registerDevice(List<DeviceInfo> deviceInfos, DeviceCloudPlatformConnector cloudPlatformConnector, StaticResult result) {
        if(CollectionUtils.isEmpty(deviceInfos)) {
            return;
        }

        result.addTotal(deviceInfos.size());
        Map<String, String> typeIds = findType();
        registerDevice(deviceInfos,cloudPlatformConnector, result, typeIds);
    }

    @Override
    @SuppressWarnings("ALL")
    public Page<DeviceInfo> page(Integer pageNum, Integer pageSize, String keyword, String deviceType) {
        Page<DeviceInfo> page = page(new Page<DeviceInfo>(pageNum, pageSize),
                new MPJLambdaWrapper<DeviceInfo>()
                        .selectAll(DeviceInfo.class)
                        .selectAs(DeviceType::getDeviceTypeName, "deviceTypeName")
                        .selectAs(DeviceType::getDeviceTypeCode, "deviceTypeCode")
                        .selectAs(DeviceOrg::getDeviceOrgName, "deviceOrgName")
                        .selectAs(DeviceCloudPlatformConnector::getDeviceConnectorName, "deviceServiceName")
                        .selectAs(DeviceCloudPlatform::getDevicePlatformCode, "devicePlatformCode")
                        .leftJoin(DeviceType.class, DeviceType::getDeviceTypeId, DeviceInfo::getDeviceTypeId)
                        .leftJoin(DeviceCloudPlatformConnector.class, DeviceCloudPlatformConnector::getDeviceConnectorId, DeviceInfo::getDeviceConnectorId)
                        .leftJoin(DeviceCloudPlatform.class, DeviceCloudPlatform::getDevicePlatformId, DeviceCloudPlatformConnector::getDevicePlatformId)
                        .leftJoin(DeviceOrg.class, DeviceOrg::getDeviceOrgTreeId, DeviceInfo::getDeviceOrgCode)
                        .eq(StringUtils.isNotBlank(deviceType), DeviceType::getDeviceTypeCode, deviceType)
                        .like(com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotEmpty(keyword), DeviceInfo::getDeviceName, keyword)
                        .or(com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotEmpty(keyword))
                        .like(com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotEmpty(keyword), DeviceType::getDeviceTypeName, keyword)
                        .or(com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotEmpty(keyword))
                        .like(com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotEmpty(keyword), DeviceInfo::getDeviceImsi, keyword)
                        .orderByDesc(DeviceInfo::getCreateTime, DeviceInfo::getDeviceTypeId)
        );

        List<Integer> deviceIds = new LinkedList<>();
        for (DeviceInfo record : page.getRecords()) {
            String devicePlatformCode = record.getDevicePlatformCode();
            if(com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(devicePlatformCode)) {
                continue;
            }
            deviceIds.add(record.getDeviceId());
//            record.setGroup(ServiceProvider.of(Adaptor.class).group(devicePlatformCode).getGroupInfo("device"));
        }

        List<DeviceChannel> list = deviceChannelService.list(Wrappers.<DeviceChannel>lambdaQuery().in(DeviceChannel::getDeviceId, deviceIds));
        Map<Integer, List<DeviceChannel>> tpl = new HashMap<>(list.size());
        for (DeviceChannel channel : list) {
            tpl.computeIfAbsent(channel.getDeviceId(), it -> new LinkedList<>()).add(channel);
        }
        for (DeviceInfo record : page.getRecords()) {
            record.setChannels(tpl.get(record.getDeviceId()));
        }

        return page;
    }

    @Override
    public DeviceInfo getDeviceInfo(String deviceId, String deviceIsmi) {
        if(StringUtils.isNotEmpty(deviceId) || StringUtils.isNotBlank(deviceIsmi)) {
            DeviceInfo serviceById = null;
            if(StringUtils.isNotBlank(deviceId))  {
                serviceById = getById(deviceId);
            } else {
                serviceById = getOne(Wrappers.<DeviceInfo>lambdaQuery().eq(DeviceInfo::getDeviceImsi, deviceIsmi));
            }
            if(null == serviceById) {
                throw new RuntimeException("设备不存在");
            }

            return serviceById;
        }
        return null;
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

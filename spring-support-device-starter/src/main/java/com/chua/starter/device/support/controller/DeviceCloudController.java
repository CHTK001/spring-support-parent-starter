package com.chua.starter.device.support.controller;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.adaptor.device.DeviceDownloadAdaptor;
import com.chua.starter.device.support.adaptor.org.OrgDownloadAdaptor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrg;
import com.chua.starter.device.support.service.DeviceCloudPlatformConnectorService;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.device.support.service.DeviceOrgService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 厂家信息控制器
 *
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/cloud")
public class DeviceCloudController {


    private final DeviceCloudPlatformConnectorService deviceCloudPlatformConnectorService;

    private final DeviceInfoService deviceInfoService;
    private final DeviceOrgService deviceOrgService;

    /**
     * 同步设备
     *
     * @param deviceCloudPlatformConnector 设备连接器id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/syncDevice")
    public ReturnResult<Boolean> syncDevice(@RequestBody DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        Integer deviceConnectorId = deviceCloudPlatformConnector.getDeviceConnectorId();
        if(null == deviceConnectorId) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceCloudPlatformConnector platformConnector = deviceCloudPlatformConnectorService.getById(deviceConnectorId);
        if(null == platformConnector) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceDownloadAdaptor downloadAdaptor = ServiceProvider.of(DeviceDownloadAdaptor.class).getNewExtension(platformConnector.getDevicePlatformCode(), platformConnector);
        if(null == downloadAdaptor) {
            return ReturnResult.illegal(StringUtils.format("暂不支持从{}同步数据", platformConnector.getDeviceConnectorName()));
        }

        int page = 1;
        List<DeviceInfo> deviceInfos = downloadAdaptor.downloadFromCloud(page, 1000);
        while (CollectionUtils.isNotEmpty(deviceInfos)) {
            deviceInfoService.registerDevice(deviceInfos, platformConnector);
            deviceInfos = downloadAdaptor.downloadFromCloud(++ page, 1000);
        }

        return ReturnResult.ok(true);
    }
    /**
     * 同步组织
     *
     * @param deviceCloudPlatformConnector 设备连接器id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/syncOrg")
    public ReturnResult<Boolean> syncOrg(@RequestBody DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        Integer deviceConnectorId = deviceCloudPlatformConnector.getDeviceConnectorId();
        if(null == deviceConnectorId) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceCloudPlatformConnector platformConnector = deviceCloudPlatformConnectorService.getById(deviceConnectorId);
        if(null == platformConnector) {
            return ReturnResult.illegal("服务不存在");
        }

        OrgDownloadAdaptor downloadAdaptor = ServiceProvider.of(OrgDownloadAdaptor.class).getNewExtension(platformConnector.getDevicePlatformCode(), platformConnector);
        if(null == downloadAdaptor) {
            return ReturnResult.illegal(StringUtils.format("暂不支持从{}同步数据", platformConnector.getDeviceConnectorName()));
        }

        int page = 1;
        List<DeviceOrg> deviceInfos = downloadAdaptor.downloadFromCloud(page, 1000);
        while (CollectionUtils.isNotEmpty(deviceInfos)) {
            deviceOrgService.registerOrg(deviceInfos, platformConnector);
            deviceInfos = downloadAdaptor.downloadFromCloud(++ page, 1000);
        }

        return ReturnResult.ok(true);
    }

}

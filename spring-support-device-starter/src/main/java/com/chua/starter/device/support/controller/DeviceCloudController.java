package com.chua.starter.device.support.controller;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.adaptor.device.CameraDeviceAdaptor;
import com.chua.starter.device.support.adaptor.device.DeviceDownloadAdaptor;
import com.chua.starter.device.support.adaptor.event.AccessEventAdaptor;
import com.chua.starter.device.support.adaptor.org.OrgDownloadAdaptor;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.adaptor.pojo.LiveResult;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.request.ServletEventRequest;
import com.chua.starter.device.support.service.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
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

    private final DeviceDataAccessEventService deviceDataAccessEventService;
    private final DeviceDataEventService deviceDataEventService;

    /**
     * 同步查询设备数据
     *
     * @param accessEventRequest 设备连接器
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/event")
    public ReturnResult<StaticResult> accessEvent(@RequestBody ServletEventRequest accessEventRequest) {
        Integer deviceConnectorId = accessEventRequest.getDeviceConnectorId();
        if(null == deviceConnectorId) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceCloudPlatformConnector platformConnector = deviceCloudPlatformConnectorService.getById(deviceConnectorId);
        if(null == platformConnector) {
            return ReturnResult.illegal("服务不存在");
        }

        AccessEventAdaptor accessEventAdaptor = ServiceProvider.of(AccessEventAdaptor.class).getNewExtension(platformConnector.getDevicePlatformCode(), platformConnector);
        if(null == accessEventAdaptor) {
            return ReturnResult.illegal(StringUtils.format("暂不支持从{}同步数据", platformConnector.getDeviceConnectorName()));
        }

        int page = 1;
        StaticResult result = new StaticResult();
        List<?extends DeviceDataEvent> event = accessEventAdaptor.getEvent(AccessEventRequest.builder()
                .pageNo(page)
                .eventType(accessEventRequest.getEventType())
                .startTime(accessEventRequest.getStartTime())
                .endTime(accessEventRequest.getEndTime())
                .build());
        while (CollectionUtils.isNotEmpty(event)) {
            deviceDataEventService.registerEvent(event, platformConnector, result, accessEventRequest);
            event = accessEventAdaptor.getEvent(AccessEventRequest.builder().pageNo(++ page) .startTime(accessEventRequest.getStartTime())
                    .eventType(accessEventRequest.getEventType()).endTime(accessEventRequest.getEndTime()).build());
        }

        return ReturnResult.ok(result);
    }
    /**
     * 视频监控
     *
     * @param deviceInfo 设备连接器id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/liveAddress")
    public ReturnResult<LiveResult> liveAddress(@RequestBody DeviceInfo deviceInfo) {
        Integer deviceConnectorId = deviceInfo.getDeviceId();
        if(null == deviceConnectorId) {
            return ReturnResult.illegal("设备不存在");
        }

        String deviceInCloud = deviceInfo.getDeviceInCloud();
        if(StringUtils.isEmpty(deviceInCloud)) {
            return ReturnResult.illegal("非云服务");
        }

        DeviceCloudPlatformConnector platformConnector = deviceCloudPlatformConnectorService.getOne(new MPJLambdaWrapper<DeviceCloudPlatformConnector>()
                .selectAll(DeviceCloudPlatformConnector.class)
                .selectAs(DeviceCloudPlatform::getDevicePlatformCode, "devicePlatformCode")
                .selectAs(DeviceCloudPlatform::getDevicePlatformName, "devicePlatformName")
                .innerJoin(DeviceCloudPlatform.class, DeviceCloudPlatform::getDevicePlatformId, DeviceCloudPlatformConnector::getDevicePlatformId)
                .eq(DeviceCloudPlatformConnector::getDeviceConnectorId, deviceInfo.getDeviceConnectorId())
        );
        if(null == platformConnector) {
            return ReturnResult.illegal("服务不存在");
        }

        CameraDeviceAdaptor cameraDeviceAdaptor = ServiceProvider.of(CameraDeviceAdaptor.class).getNewExtension(platformConnector.getDevicePlatformCode(), platformConnector);
        if(null == cameraDeviceAdaptor) {
            return ReturnResult.illegal(StringUtils.format("暂不支持从{}同步数据", platformConnector.getDeviceConnectorName()));
        }

        int page = 1;
        LiveResult liveAddress = cameraDeviceAdaptor.getLiveAddress(deviceInfo.getDeviceImsi());
        if(StringUtils.isNotBlank(liveAddress.getMsg())) {
            return ReturnResult.illegal(liveAddress.getMsg());
        }
        return ReturnResult.ok(liveAddress);
    }
    /**
     * 同步设备
     *
     * @param deviceCloudPlatformConnector 设备连接器id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/syncDevice")
    public ReturnResult<StaticResult> syncDevice(@RequestBody DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        Integer deviceConnectorId = deviceCloudPlatformConnector.getDeviceConnectorId();
        if(null == deviceConnectorId) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceCloudPlatformConnector platformConnector = deviceCloudPlatformConnectorService.getOne(new MPJLambdaWrapper<DeviceCloudPlatformConnector>()
                .selectAll(DeviceCloudPlatformConnector.class)
                .selectAs(DeviceCloudPlatform::getDevicePlatformCode, "devicePlatformCode")
                .selectAs(DeviceCloudPlatform::getDevicePlatformName, "devicePlatformName")
                .innerJoin(DeviceCloudPlatform.class, DeviceCloudPlatform::getDevicePlatformId, DeviceCloudPlatformConnector::getDevicePlatformId)
                .eq(DeviceCloudPlatformConnector::getDeviceConnectorId, deviceConnectorId)
        );
        if(null == platformConnector) {
            return ReturnResult.illegal("服务不存在");
        }

        DeviceDownloadAdaptor downloadAdaptor = ServiceProvider.of(DeviceDownloadAdaptor.class).getNewExtension(platformConnector.getDevicePlatformCode(), platformConnector);
        if(null == downloadAdaptor) {
            return ReturnResult.illegal(StringUtils.format("暂不支持从{}同步数据", platformConnector.getDeviceConnectorName()));
        }

        int page = 1;
        StaticResult result = new StaticResult();
        List<DeviceInfo> deviceInfos = downloadAdaptor.downloadFromCloud(page, 1000);
        while (CollectionUtils.isNotEmpty(deviceInfos)) {
            deviceInfoService.registerDevice(deviceInfos, platformConnector, result);
            deviceInfos = downloadAdaptor.downloadFromCloud(++ page, 1000);
        }

        return ReturnResult.ok(result);
    }
    /**
     * 同步组织
     *
     * @param deviceCloudPlatformConnector 设备连接器id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @PostMapping("/syncOrg")
    public ReturnResult<StaticResult> syncOrg(@RequestBody DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
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
        StaticResult result = new StaticResult();
        List<DeviceOrg> deviceInfos = downloadAdaptor.downloadFromCloud(page, 1000);
        while (CollectionUtils.isNotEmpty(deviceInfos)) {
            deviceOrgService.registerOrg(deviceInfos, platformConnector, result);
            deviceInfos = downloadAdaptor.downloadFromCloud(++ page, 1000);
        }

        return ReturnResult.ok(result);
    }

}

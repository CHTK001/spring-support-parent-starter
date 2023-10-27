package com.chua.starter.device.support.adaptor;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.starter.device.support.adaptor.client.HikAnFangClient;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

/**
 * a云耀
 * @author CH
 */
public abstract class HikAnFangAdaptor implements Adaptor{

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected HikAnFangClient hikAnFangClient;

    public HikAnFangAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        hikAnFangClient =
                Mapping.of(HikAnFangClient.class,
                        MappingConfig.builder()
                                .host(deviceCloudPlatformConnector.getDeviceConnectorAddress())
                                .path("/artemis")
                                .productId(deviceCloudPlatformConnector.getDeviceConnectorProjectId())
                                .appKey(deviceCloudPlatformConnector.getDeviceConnectorAppKey())
                                .secretAccessKey(deviceCloudPlatformConnector.getDeviceConnectorAppSecret())
                                .build()).get();
    }


}

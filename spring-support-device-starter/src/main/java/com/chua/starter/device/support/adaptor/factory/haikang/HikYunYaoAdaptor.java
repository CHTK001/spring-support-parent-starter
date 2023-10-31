package com.chua.starter.device.support.adaptor.factory.haikang;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.starter.device.support.adaptor.Adaptor;
import com.chua.starter.device.support.adaptor.factory.client.HikYunYaoClient;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

/**
 * a云耀
 * @author CH
 */
public abstract class HikYunYaoAdaptor implements Adaptor {

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected HikYunYaoClient hikYunYaoClient;

    public HikYunYaoAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        hikYunYaoClient =
                Mapping.of(HikYunYaoClient.class,
                        MappingConfig.builder()
                                .host(deviceCloudPlatformConnector.getDeviceConnectorAddress())
                                .path("/artemis")
                                .productId(deviceCloudPlatformConnector.getDeviceConnectorProjectId())
                                .appKey(deviceCloudPlatformConnector.getDeviceConnectorAppKey())
                                .secretAccessKey(deviceCloudPlatformConnector.getDeviceConnectorAppSecret())
                                .build()).get();
    }


}

package com.chua.starter.device.support.adaptor;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.common.support.mapping.MappingExecutor;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

/**
 * a云耀
 * @author CH
 */
public abstract class YunYaoAdaptor {

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected YunYaoClient yunYaoClient;

    public YunYaoAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        yunYaoClient =
                Mapping.of(YunYaoClient.class,
                        MappingConfig.builder()
                                .host(deviceCloudPlatformConnector.getDeviceConnectorAddress())
                                .path("/artemis")
                                .appKey(deviceCloudPlatformConnector.getDeviceConnectorAppKey())
                                .secretAccessKey(deviceCloudPlatformConnector.getDeviceConnectorAppSecret())
                                .build()).get();
    }


    interface YunYaoClient extends MappingExecutor {

    }
}

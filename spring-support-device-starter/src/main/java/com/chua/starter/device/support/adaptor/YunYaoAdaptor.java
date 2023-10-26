package com.chua.starter.device.support.adaptor;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.common.support.mapping.MappingExecutor;
import com.chua.starter.device.support.adaptor.client.HikYunYaoClient;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

/**
 * a云耀
 * @author CH
 */
public abstract class YunYaoAdaptor implements Adaptor{

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected HikYunYaoClient hikYunYaoClient;

    public YunYaoAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        hikYunYaoClient =
                Mapping.of(HikYunYaoClient.class,
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

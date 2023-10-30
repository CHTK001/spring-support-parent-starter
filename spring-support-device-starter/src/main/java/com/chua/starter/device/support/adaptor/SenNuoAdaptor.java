package com.chua.starter.device.support.adaptor;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.starter.device.support.adaptor.client.SnNuoClient;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

/**
 * a云耀
 * @author CH
 */
public abstract class SenNuoAdaptor implements Adaptor{

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected SnNuoClient snNuoClient;

    public SenNuoAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        snNuoClient =
                Mapping.of(SnNuoClient.class,
                        MappingConfig.builder()
                                .host(deviceCloudPlatformConnector.getDeviceConnectorAddress())
                                .build()).get();
    }


}

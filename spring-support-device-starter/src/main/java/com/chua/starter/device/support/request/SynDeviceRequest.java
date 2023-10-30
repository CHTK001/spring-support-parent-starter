package com.chua.starter.device.support.request;

import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import lombok.Data;

/**
 * syn设备请求
 *
 * @author CH
 * @since 2023/10/30
 */
@Data
public class SynDeviceRequest extends DeviceCloudPlatformConnector {

    /**
     * 设备imsi
     */
    private String deviceImsi;
}

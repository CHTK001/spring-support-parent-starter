package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceConnectInfo;

public interface DeviceProvider {
    String getVendor();
    boolean support(DeviceConnectInfo info);
    DeviceSession connect(DeviceConnectInfo info);
}
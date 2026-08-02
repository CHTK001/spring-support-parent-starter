package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 大华设备 Provider
 * <p>支持 SDK 与云睿平台两种接入方式。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DahuaProvider implements DeviceProvider {

    /**
     * 厂商标识
     */
    private static final String VENDOR = "dahua";

    @Override
    public String getVendor() {
        return VENDOR;
    }

    @Override
    public boolean support(DeviceConnectInfo info) {
        return VENDOR.equalsIgnoreCase(info.getVendor());
    }

    @Override
    public DeviceSession connect(DeviceConnectInfo info) {
        log.info("大华设备连接: authType={}, ip={}", info.getAuthType(), info.getIp());
        return new DahuaSession(info);
    }
}
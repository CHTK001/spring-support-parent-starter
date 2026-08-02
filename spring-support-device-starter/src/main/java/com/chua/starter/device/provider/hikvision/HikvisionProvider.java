package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 海康设备 Provider
 * <p>支持 SDK、ISUP、萤石云三种接入方式。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HikvisionProvider implements DeviceProvider {

    /**
     * 厂商标识
     */
    private static final String VENDOR = "hikvision";

    /**
     * SDK 直连模式
     */
    private static final String AUTH_SDK = "SDK";

    /**
     * ISUP 平台接入模式
     */
    private static final String AUTH_ISUP = "ISUP";

    /**
     * 萤石云平台接入模式
     */
    private static final String AUTH_EZVIZ = "EZVIZ";

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
        log.info("海康设备连接: authType={}, ip={}", info.getAuthType(), info.getIp());
        // 实际根据 authType 选择不同的 SDK 初始化
        return new HikvisionSession(info);
    }
}
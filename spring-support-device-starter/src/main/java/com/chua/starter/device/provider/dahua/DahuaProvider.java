package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.provider.dahua.sdk.DahuaRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 大华设备 Provider
 * <p>支持 SDK 与云睿平台两种接入方式。</p>
 *
 * <p>SDK 直连模式优先加载 dhnetsdk 动态库进行真实对接，</p>
 * <p>SDK 不可用或连接失败时回退到模拟会话，保证框架可独立运行。</p>
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

    /**
     * SDK 直连模式
     */
    private static final String AUTH_SDK = "SDK";

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
        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                DahuaRealSession session = new DahuaRealSession(info);
                log.info("大华设备 SDK 真实连接成功: ip={}", info.getIp());
                return session;
            } catch (Exception e) {
                log.warn("大华 SDK 真实连接失败，回退模拟会话: {}", e.getMessage());
            }
        } else {
            log.info("大华平台接入（{}）暂以模拟会话处理", info.getAuthType());
        }
        return new DahuaSession(info);
    }
}
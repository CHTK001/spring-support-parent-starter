package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.provider.hikvision.sdk.HikvisionRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 海康设备 Provider
 * <p>支持 SDK、ISUP、萤石云三种接入方式。</p>
 *
 * <p>SDK 直连模式优先加载 HCNetSDK 动态库进行真实对接，</p>
 * <p>SDK 不可用或连接失败时回退到模拟会话，保证框架可独立运行。</p>
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
        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                HikvisionRealSession session = new HikvisionRealSession(info);
                log.info("海康设备 SDK 真实连接成功: ip={}", info.getIp());
                return session;
            } catch (Exception e) {
                log.warn("海康 SDK 真实连接失败，回退模拟会话: {}", e.getMessage());
            }
        } else {
            log.info("海康平台接入（{}）暂以模拟会话处理", info.getAuthType());
        }
        return new HikvisionSession(info);
    }
}

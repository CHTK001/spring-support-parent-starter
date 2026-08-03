package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.provider.dahua.open.DahuaCloudSession;
import com.chua.starter.device.provider.dahua.open.DahuaLeChangeSession;
import com.chua.starter.device.provider.dahua.sdk.DahuaRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 大华设备 Provider
 * <p>支持两大平台对接 + 局域网 SDK 直连：</p>
 * <ul>
 *   <li>大华云睿（{@code platformType=dahua-cloud}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>大华乐橙（{@code platformType=lechange}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>局域网 SDK 直连（{@code authType=SDK}）—— 大华 NetSDK JNA（需厂商提供 dll/so）</li>
 * </ul>
 *
 * <p>SDK 直连模式若 SDK 不可用则自动回退到模拟会话。</p>
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
     * 局域网 SDK 直连
     */
    private static final String AUTH_SDK = "SDK";

    /**
     * 平台接入
     */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /**
     * 大华云睿
     */
    private static final String PLATFORM_DAHUA_CLOUD = "dahua-cloud";

    /**
     * 大华乐橙
     */
    private static final String PLATFORM_LECHANGE = "lechange";

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
        log.info("大华设备连接: authType={}, platformType={}, deviceSerial={}",
                info.getAuthType(), info.getPlatformType(), info.getDeviceSerial());

        if (AUTH_PLATFORM.equalsIgnoreCase(info.getAuthType())) {
            String platform = info.getPlatformType();
            if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(platform)) {
                try {
                    return new DahuaCloudSession(info);
                } catch (Exception e) {
                    log.warn("大华云睿连接失败，回退模拟会话: {}", e.getMessage());
                }
            } else if (PLATFORM_LECHANGE.equalsIgnoreCase(platform)) {
                try {
                    return new DahuaLeChangeSession(info);
                } catch (Exception e) {
                    log.warn("大华乐橙连接失败，回退模拟会话: {}", e.getMessage());
                }
            } else {
                log.warn("大华平台类型未识别: {}，回退模拟会话", platform);
            }
            return new DahuaSession(info);
        }

        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                return new DahuaRealSession(info);
            } catch (Exception e) {
                log.warn("大华 SDK 直连失败，回退模拟会话: {}", e.getMessage());
            }
        }
        return new DahuaSession(info);
    }
}

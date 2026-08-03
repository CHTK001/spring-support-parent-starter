package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.provider.hikvision.open.HikvisionCloudEyeSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionCloudYaoSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionSecurePlatformSession;
import com.chua.starter.device.provider.hikvision.sdk.HikvisionRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 海康设备 Provider
 * <p>支持三大平台对接 + 局域网 SDK 直连：</p>
 * <ul>
 *   <li>海康云耀（{@code platformType=cloud-yao}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>海康云眸（{@code platformType=cloud-eye}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>海康安防平台（{@code platformType=secure-platform}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>局域网 SDK 直连（{@code authType=SDK}）—— 海康 HCNetSDK JNA（需厂商提供 dll/so）</li>
 * </ul>
 *
 * <p>SDK 直连模式若 SDK 不可用（无 dll/so 或设备不可达）则自动回退到模拟会话。</p>
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
     * 局域网 SDK 直连
     */
    private static final String AUTH_SDK = "SDK";

    /**
     * 平台接入
     */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /**
     * 海康云耀
     */
    private static final String PLATFORM_CLOUD_YAO = "cloud-yao";

    /**
     * 海康云眸
     */
    private static final String PLATFORM_CLOUD_EYE = "cloud-eye";

    /**
     * 海康安防平台（iSecure Center / 综合安防管理平台）
     */
    private static final String PLATFORM_SECURE = "secure-platform";

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
        log.info("海康设备连接: authType={}, platformType={}, deviceSerial={}",
                info.getAuthType(), info.getPlatformType(), info.getDeviceSerial());

        if (AUTH_PLATFORM.equalsIgnoreCase(info.getAuthType())) {
            String platform = info.getPlatformType();
            if (PLATFORM_CLOUD_YAO.equalsIgnoreCase(platform)) {
                try {
                    return new HikvisionCloudYaoSession(info);
                } catch (Exception e) {
                    log.warn("海康云耀连接失败，回退模拟会话: {}", e.getMessage());
                }
            } else if (PLATFORM_CLOUD_EYE.equalsIgnoreCase(platform)) {
                try {
                    return new HikvisionCloudEyeSession(info);
                } catch (Exception e) {
                    log.warn("海康云眸连接失败，回退模拟会话: {}", e.getMessage());
                }
            } else if (PLATFORM_SECURE.equalsIgnoreCase(platform)) {
                try {
                    return new HikvisionSecurePlatformSession(info);
                } catch (Exception e) {
                    log.warn("海康安防平台连接失败，回退模拟会话: {}", e.getMessage());
                }
            } else {
                log.warn("海康平台类型未识别: {}，回退模拟会话", platform);
            }
            return new HikvisionSession(info);
        }

        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                return new HikvisionRealSession(info);
            } catch (Exception e) {
                log.warn("海康 SDK 直连失败，回退模拟会话: {}", e.getMessage());
            }
        }
        return new HikvisionSession(info);
    }
}

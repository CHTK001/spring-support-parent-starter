package com.chua.starter.device.provider.sdk;

import com.chua.starter.device.spi.model.DeviceConnectInfo;

import java.util.List;

/**
 * 平台 SDK 扩展的公共基类（common-starter {@code ServiceProvider} SPI 扩展点实现）。
 *
 * <p>每个「厂商 + 平台」对应一个本类子类，以无参构造声明 vendor / platform，
 * 由 {@code META-INF/services/...DevicePlatformSdkProvider} 登记后，
 * {@link DevicePlatformSdks} 通过 {@code ServiceProvider.collect()} 发现并注册。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public abstract class AbstractSdkProvider implements DevicePlatformSdkProvider {

    /** 厂商标识 */
    private final String vendor;

    /** 平台标识 */
    private final String platform;

    /**
     * 构造扩展。
     *
     * @param vendor   厂商标识
     * @param platform 平台标识
     */
    protected AbstractSdkProvider(String vendor, String platform) {
        this.vendor = vendor;
        this.platform = platform;
    }

    @Override
    public String name() {
        return vendor + "." + platform;
    }

    @Override
    public String vendor() {
        return vendor;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public boolean support(DeviceConnectInfo info) {
        if (info == null || info.vendor() == null) {
            return false;
        }
        String v = info.vendor().trim();
        String p = info.platformType() == null ? "" : info.platformType().trim();
        return v.equalsIgnoreCase(vendor) && p.equalsIgnoreCase(platform);
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return List.of();
    }
}

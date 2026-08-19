package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.provider.hikvision.open.HikvisionCloudEyeSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionCloudYaoSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionSecurePlatformSession;
import com.chua.starter.device.provider.hikvision.sdk.HikvisionRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

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
 * <p>真实对接策略：连接失败时严格抛错，由业务层向用户暴露具体错误。</p>
 * <p>仅当 {@code device.provider.fallback-mock=true} 时回退到 {@link HikvisionSession} 模拟会话，</p>
 * <p>供本地无厂商账号时调试 UI 流程使用，<strong>生产环境严禁启用</strong>。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class HikvisionProvider implements DeviceProvider {

    /**
     * 厂商标识
     */
    private static final String VENDOR = "hikvision";

    /**
     * 平台接入模式
     */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /**
     * 局域网 SDK 直连
     */
    private static final String AUTH_SDK = "SDK";

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

    /**
     * 是否允许回退到模拟会话（默认 false：严格真实对接）
     */
    private final boolean fallbackMock;

    /**
     * 创建 HikvisionProvider 实例
     * @param environment environment
     */
    public HikvisionProvider(Environment environment) {
        this.fallbackMock = environment.getProperty("plugin.device.provider.fallback-mock", boolean.class, false);
        if (fallbackMock) {
            log.warn("[HikvisionProvider] plugin.device.provider.fallback-mock=true，连接失败时将回退模拟会话（仅限调试）");
        }
    }

    @Override
    /** 获取Vendor */
    public String getVendor() {
        return VENDOR;
    }

    @Override
    /** Support */
    public boolean support(DeviceConnectInfo info) {
        return VENDOR.equalsIgnoreCase(info.getVendor());
    }

    @Override
    /** 连接 */
    public DeviceSession connect(DeviceConnectInfo info) {
        log.info("海康设备连接: authType={}, platformType={}, deviceSerial={}",
                info.getAuthType(), info.getPlatformType(), info.getDeviceSerial());

        if (AUTH_PLATFORM.equalsIgnoreCase(info.getAuthType())) {
            String platform = info.getPlatformType();
            try {
                if (PLATFORM_CLOUD_YAO.equalsIgnoreCase(platform)) {
                    return new HikvisionCloudYaoSession(info);
                }
                if (PLATFORM_CLOUD_EYE.equalsIgnoreCase(platform)) {
                    return new HikvisionCloudEyeSession(info);
                }
                if (PLATFORM_SECURE.equalsIgnoreCase(platform)) {
                    return new HikvisionSecurePlatformSession(info);
                }
                throw new IllegalArgumentException("海康平台类型不支持: " + platform
                        + "（期望 cloud-yao / cloud-eye / secure-platform）");
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("海康平台连接失败，回退模拟会话: {}", e.getMessage());
                    return new HikvisionSession(info);
                }
                throw new IllegalStateException("海康平台连接失败 [" + platform + "]: " + e.getMessage(), e);
            }
        }

        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                return new HikvisionRealSession(info);
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("海康 SDK 直连失败，回退模拟会话: {}", e.getMessage());
                    return new HikvisionSession(info);
                }
                throw new IllegalStateException("海康 SDK 直连失败: " + e.getMessage(), e);
            }
        }

        throw new IllegalArgumentException("不支持的海康认证类型: " + info.getAuthType());
    }

    @Override
    /** FetchOrganizations */
    public List<OrganizationNode> fetchOrganizations() {
        // 组织同步需在已连接的 OpenAPI 会话上下文中执行，由业务层调用 session 后再行实现。
        return List.of();
    }
}

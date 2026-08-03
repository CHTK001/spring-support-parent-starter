package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.provider.dahua.open.DahuaCloudSession;
import com.chua.starter.device.provider.dahua.open.DahuaLeChangeSession;
import com.chua.starter.device.provider.dahua.sdk.DahuaRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 大华设备 Provider
 * <p>支持两大平台对接 + 局域网 SDK 直连：</p>
 * <ul>
 *   <li>大华云睿（{@code platformType=dahua-cloud}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>大华乐橙（{@code platformType=lechange}）—— 纯 Java HTTP OpenAPI</li>
 *   <li>局域网 SDK 直连（{@code authType=SDK}）—— 大华 NetSDK JNA（需厂商提供 dll/so）</li>
 * </ul>
 *
 * <p>真实对接策略：连接失败时严格抛错，由业务层向用户暴露具体错误。</p>
 * <p>仅当 {@code device.provider.fallback-mock=true} 时回退到 {@link DahuaSession} 模拟会话，</p>
 * <p>供本地无厂商账号时调试 UI 流程使用，<strong>生产环境严禁启用</strong>。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
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
     * 平台接入模式
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

    /**
     * 是否允许回退到模拟会话（默认 false：严格真实对接）
     */
    private final boolean fallbackMock;

    public DahuaProvider(Environment environment) {
        this.fallbackMock = environment.getProperty("device.provider.fallback-mock", boolean.class, false);
        if (fallbackMock) {
            log.warn("[DahuaProvider] fallback-mock=true，连接失败时将回退模拟会话（仅限调试）");
        }
    }

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
            try {
                if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(platform)) {
                    return new DahuaCloudSession(info);
                }
                if (PLATFORM_LECHANGE.equalsIgnoreCase(platform)) {
                    return new DahuaLeChangeSession(info);
                }
                throw new IllegalArgumentException("大华平台类型不支持: " + platform
                        + "（期望 dahua-cloud / lechange）");
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("大华平台连接失败，回退模拟会话: {}", e.getMessage());
                    return new DahuaSession(info);
                }
                throw new IllegalStateException("大华平台连接失败 [" + platform + "]: " + e.getMessage(), e);
            }
        }

        if (AUTH_SDK.equalsIgnoreCase(info.getAuthType()) || info.getAuthType() == null) {
            try {
                return new DahuaRealSession(info);
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("大华 SDK 直连失败，回退模拟会话: {}", e.getMessage());
                    return new DahuaSession(info);
                }
                throw new IllegalStateException("大华 SDK 直连失败: " + e.getMessage(), e);
            }
        }

        throw new IllegalArgumentException("不支持的大华认证类型: " + info.getAuthType());
    }

    @Override
    public List<OrganizationNode> fetchOrganizations() {
        // 组织同步需在已连接的 OpenAPI 会话上下文中执行，由业务层处理。
        return List.of();
    }
}

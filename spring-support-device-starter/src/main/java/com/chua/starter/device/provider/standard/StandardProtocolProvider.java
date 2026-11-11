package com.chua.starter.device.provider.standard;

import com.chua.starter.device.provider.sdk.DeviceCapabilityMatrix;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AuthTypeDescriptor;
import com.chua.starter.device.spi.model.CredentialField;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.PlatformDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 标准协议设备 提供者（GB/T 28181、ONVIF）。
 *
 * <p>这两者不绑定厂商，是行业标准：</p>
 * <ul>
 *   <li><b>GB/T 28181</b>：国标 SIP 信令 + RTP 媒体，需部署 SIP 服务端（WVP/ZLMediaKit 等），
 *       本 Provider 以该服务端的 REST 封装地址接入。</li>
 *   <li><b>ONVIF</b>：SOAP over HTTP 直连设备，凭据填设备 ONVIF 地址 + 用户名/密码。</li>
 * </ul>
 *
 * <p>两者都通过 {@code DevicePlatformSdk} 门面调用，本 Provider 只负责声明描述符与建连。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Component
public class StandardProtocolProvider implements DeviceProvider {

    /** 厂商标识（标准协议） */
    private static final String VENDOR = "standard";

    /** GB28181 平台标识 */
    private static final String PLATFORM_GB = "gb28181";

    /** ONVIF 平台标识 */
    private static final String PLATFORM_ONVIF = "onvif";

    /** 平台接入模式 */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /** 是否允许回退模拟会话 */
    private final boolean fallbackMock;

    /**
     * 构造标准协议提供者。
     *
     * @param environment 环境
     */
    public StandardProtocolProvider(Environment environment) {
        this.fallbackMock = environment.getProperty("plugin.device.provider.fallback-mock", boolean.class, false);
    }

    @Override
    public String getVendor() {
        return VENDOR;
    }

    @Override
    public boolean support(DeviceConnectInfo info) {
        return info != null && VENDOR.equalsIgnoreCase(info.vendor());
    }

    @Override
    public DeviceSession connect(DeviceConnectInfo info) {
        log.info("标准协议连接: platformType={}, baseUrl={}", info.platformType(), info.extra());
        if (PLATFORM_ONVIF.equalsIgnoreCase(info.platformType())) {
            // ONVIF 直连设备，有完整会话实现（媒体/云台/抓图）
            return new OnvifSession(info);
        }
        if (PLATFORM_GB.equalsIgnoreCase(info.platformType())) {
            // 国标走 WVP/ZLMediaKit REST 网关，SIP 信令由服务端处理
            return new Gb28181Session(info);
        }
        // 其他标准协议类型明确失败，不伪造会话
        throw new IllegalStateException("标准协议类型不支持: " + info.platformType());
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        List<CredentialField> fields = List.of(
                new CredentialField("baseUrl", "服务/设备地址", false, true,
                        "GB28181 填 SIP 服务端 REST 地址；ONVIF 填设备 ONVIF 服务地址"),
                new CredentialField("appKey", "用户名 / AppKey", false, false,
                        "ONVIF 设备用户名；GB28181 服务端 AK（可选）"),
                new CredentialField("appSecret", "密码 / AppSecret", true, false,
                        "ONVIF 设备密码；GB28181 服务端 SK（可选）"));
        return new DeviceVendorDescriptor(
                VENDOR,
                "标准协议（GB28181 / ONVIF）",
                List.of(new AuthTypeDescriptor(AUTH_PLATFORM, "平台接入",
                        "国标 SIP 服务端或 ONVIF 直连设备")),
                List.of(
                        new PlatformDescriptor(PLATFORM_GB, "GB/T 28181 国标",
                                "", fields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_GB)),
                        new PlatformDescriptor(PLATFORM_ONVIF, "ONVIF",
                                "", fields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_ONVIF))),
                unionCapabilities(PLATFORM_GB, PLATFORM_ONVIF),
                60,
                "https://www.onvif.org/profiles/",
                "国标 GB28181 需部署 SIP 服务端（WVP/ZLMediaKit）；ONVIF 直连设备 IP，"
                        + "无需云端账号。两者均为标准协议，不绑定厂商；"
                        + "协议本身不支持的能力（如组织架构、人员）在前端直接隐藏。");
    }

    /**
     * 取厂商下若干平台能力位的并集。
     *
     * @param platforms 平台编码
     * @return 能力位并集
     */
    private static java.util.Set<DeviceCapability> unionCapabilities(String... platforms) {
        java.util.Set<DeviceCapability> result = java.util.EnumSet.noneOf(DeviceCapability.class);
        for (String platform : platforms) {
            result.addAll(DeviceCapabilityMatrix.capabilitiesOf(VENDOR, platform));
        }
        return result;
    }
}

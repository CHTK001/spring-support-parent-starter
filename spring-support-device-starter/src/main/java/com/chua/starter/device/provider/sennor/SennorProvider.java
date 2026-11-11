package com.chua.starter.device.provider.sennor;

import com.chua.starter.device.provider.sdk.DeviceCapabilityMatrix;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AuthTypeDescriptor;
import com.chua.starter.device.spi.model.CredentialField;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.PlatformDescriptor;
import com.chua.starter.device.spi.model.VendorDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 精讯畅通（Sennor）精讯云设备提供者。
 *
 * <p>精讯云面向传感器与继电器设备，和视频监控平台的能力模型差异很大：
 * 只有设备目录与在线状态，没有视频、门禁、组织、人员等业务。因此本 Provider
 * 只声明 {@code DEVICE} 能力，其余能力由能力矩阵明确标 {@code NO}。</p>
 *
 * <p>凭据字段复用平台的 {@code appKey/appSecret} 两列：账号模式填账号/密码，
 * HTTP 取数模式填 16 位 {@code secretKey}，密码留空。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
public class SennorProvider implements DeviceProvider {

    /** 厂商标识 */
    private static final String VENDOR = "sennor";

    /** 平台标识 */
    private static final String PLATFORM = "sennor-cloud";

    /** 平台接入模式 */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /** 默认平台地址 */
    private static final String DEFAULT_BASE_URL = "http://www.sennor.net";

    /** 官方文档地址 */
    private static final String DOC_URL = "http://docs.sennor.net";

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
        log.info("精讯云设备连接: platformType={}", info.platformType());
        return new SennorSession(info);
    }

    @Override
    public List<VendorDevice> fetchDevices(DeviceSession session) {
        if (session instanceof SennorSession sennorSession) {
            return sennorSession.fetchDevices();
        }
        return List.of();
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        List<CredentialField> fields = List.of(
                new CredentialField("appKey", "账号 / secretKey", false, true,
                        "精讯云登录账号；若只使用 HTTP 取数接口，填 16 位 secretKey"),
                new CredentialField("appSecret", "密码", true, false,
                        "账号模式必填；secretKey 模式留空")
        );
        return new DeviceVendorDescriptor(
                VENDOR,
                "精讯畅通（Sennor）",
                List.of(new AuthTypeDescriptor(AUTH_PLATFORM, "平台接入",
                        "精讯云控制台账号，或 HTTP 取数 secretKey")),
                List.of(new PlatformDescriptor(PLATFORM, "精讯云", DEFAULT_BASE_URL, fields,
                        DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM))),
                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM),
                600,
                DOC_URL,
                "精讯云传感器/继电器设备目录与在线状态；账号模式走控制台登录，secretKey 模式走官方 HTTP 取数接口。");
    }
}

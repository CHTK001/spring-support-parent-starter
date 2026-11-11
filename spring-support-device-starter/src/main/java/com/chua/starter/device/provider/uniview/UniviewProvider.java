package com.chua.starter.device.provider.uniview;

import com.chua.starter.device.provider.sdk.DeviceCapabilityMatrix;
import com.chua.starter.device.provider.uniview.open.UniviewSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AuthTypeDescriptor;
import com.chua.starter.device.spi.model.CredentialField;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.VendorDevice;
import com.chua.starter.device.spi.model.PlatformDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 宇视（Uniview）设备 提供者。
 *
 * <p>平台接入走宇视云开放平台（{@code ezcloud.uniview.com}），
 * 经 {@code DevicePlatformSdk} 门面调用；本 Provider 负责描述符与建连声明。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Component
public class UniviewProvider implements DeviceProvider {

    /** 厂商标识 */
    private static final String VENDOR = "uniview";

    /** 平台标识 */
    private static final String PLATFORM = "uniview";

    /** 平台接入模式 */
    private static final String AUTH_PLATFORM = "PLATFORM";

    /** 是否允许回退模拟会话 */
    private final boolean fallbackMock;

    /**
     * 构造宇视提供者。
     *
     * @param environment 环境
     */
    public UniviewProvider(Environment environment) {
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
        log.info("宇视设备连接: platformType={}", info.platformType());
        return new UniviewSession(info);
    }

    /**
     * 设备写操作：平台不存在则新增，已存在则改名（平台账号维度）。
     *
     * @param info   连接信息
     * @param device 设备
     * @param exists 平台是否已存在
     * @return 是否成功
     */
    public boolean upsertRemote(DeviceConnectInfo info, VendorDevice device, boolean exists) {
        if (info == null || device == null) {
            return false;
        }
        DeviceSession session = new UniviewSession(info);
        try {
            UniviewSession uniview = (UniviewSession) session;
            if (exists) {
                uniview.renameDevice(device.externalId(), device.name());
            } else {
                uniview.addDevice(device.externalId(), device.name());
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("宇视设备写操作失败: {}", e.getMessage());
            return false;
        } finally {
            session.close();
        }
    }

    /**
     * 删除设备（平台账号维度）。
     *
     * @param info       连接信息
     * @param externalId 设备序列号
     * @return 是否成功
     */
    public boolean removeRemote(DeviceConnectInfo info, String externalId) {
        if (info == null || externalId == null || externalId.isBlank()) {
            return false;
        }
        DeviceSession session = new UniviewSession(info);
        try {
            ((UniviewSession) session).deleteDevice(externalId);
            return true;
        } catch (RuntimeException e) {
            log.warn("宇视删除设备失败: {}", e.getMessage());
            return false;
        } finally {
            session.close();
        }
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        List<CredentialField> fields = List.of(
                new CredentialField("appKey", "AppKey", false, true, "宇视云开放平台应用的 AppKey"),
                new CredentialField("appSecret", "AppSecret", true, true, "宇视云开放平台应用的 AppSecret"));
        return new DeviceVendorDescriptor(
                VENDOR,
                "宇视（Uniview）",
                List.of(new AuthTypeDescriptor(AUTH_PLATFORM, "平台接入",
                        "通过宇视云开放平台接入")),
                List.of(new PlatformDescriptor(PLATFORM, "宇视云", "https://ezcloud.uniview.com", fields,
                        DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM))),
                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM),
                60,
                "https://ezcloud.uniview.com",
                "宇视云开放平台接入；接口清单在控制台，路径可由凭据覆盖。");
    }
}

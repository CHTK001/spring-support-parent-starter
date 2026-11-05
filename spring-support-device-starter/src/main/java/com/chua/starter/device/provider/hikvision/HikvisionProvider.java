// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.provider.hikvision.open.HikvisionCloudEyeSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionCloudYaoSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionSecurePlatformSession;
import com.chua.starter.device.provider.hikvision.sdk.HikvisionRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AuthTypeDescriptor;
import com.chua.starter.device.spi.model.CredentialField;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PlatformDescriptor;
import com.chua.starter.device.spi.model.VendorDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 海康设备 提供者
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
     * 海康安防平台（isecure Center / 综合安防管理平台）
     */
    private static final String PLATFORM_SECURE = "secure-platform";

    /**
     * 是否允许回退到模拟会话（默认 false：严格真实对接）
     */
    private final boolean fallbackMock;

    /**
     * 创建 hikvision提供者 实例
     * @param environment 环境
     */
    public HikvisionProvider(Environment environment) {
        this.fallbackMock = environment.getProperty("plugin.device.provider.fallback-mock", boolean.class, false);
        if (fallbackMock) {
            log.warn("[HikvisionProvider] plugin.device.provider.fallback-mock=true，连接失败时将回退模拟会话（仅限调试）");
        }
    }

    @Override
    /**
     * 获取供应商
     * @return 返回值
    */
    public String getVendor() {
        return VENDOR;
    }

    @Override
    /**
     * 支持
     * @param info 参数
     * @return 返回值
    */
    public boolean support(DeviceConnectInfo info) {
        return VENDOR.equalsIgnoreCase(info.vendor());
    }

    @Override
    /**
     * 连接
     * @param info 参数
     * @return 返回值
    */
    public DeviceSession connect(DeviceConnectInfo info) {
        log.info("海康设备连接: authType={}, platformType={}, deviceSerial={}",
                info.authType(), info.platformType(), info.deviceSerial());

        if (AUTH_PLATFORM.equalsIgnoreCase(info.authType())) {
            String platform = info.platformType();
            try {
                if (PLATFORM_CLOUD_YAO.equalsIgnoreCase(platform)) {
                    /**
                     * hikvisioncloudyao会话。
                     * @param info 信息
                     * @return hikvisioncloudyao会话的结果
                     */
                    return new HikvisionCloudYaoSession(info);
                }
                if (PLATFORM_CLOUD_EYE.equalsIgnoreCase(platform)) {
                    /**
                     * hikvisioncloudeye会话。
                     * @param info 信息
                     * @return hikvisioncloudeye会话的结果
                     */
                    return new HikvisionCloudEyeSession(info);
                }
                if (PLATFORM_SECURE.equalsIgnoreCase(platform)) {
                    /**
                     * hikvisionsecureplatform会话。
                     * @param info 信息
                     * @return hikvisionsecureplatform会话的结果
                     */
                    return new HikvisionSecurePlatformSession(info);
                }
                throw new IllegalArgumentException("海康平台类型不支持: " + platform
                        + "（期望 cloud-yao / cloud-eye / secure-platform）");
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("海康平台连接失败，回退模拟会话: {}", e.getMessage());
                    /**
                     * hikvision会话。
                     * @param info 信息
                     * @return hikvision会话的结果
                     */
                    return new HikvisionSession(info);
                }
                /**
                 * illegal状态异常。
                 * @param e.getMessage( e.获取消息(
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("海康平台连接失败 [" + platform + "]: " + e.getMessage(), e);
            }
        }

        if (AUTH_SDK.equalsIgnoreCase(info.authType()) || info.authType() == null) {
            try {
                /**
                 * hikvisionreal会话。
                 * @param info 信息
                 * @return hikvisionreal会话的结果
                 */
                return new HikvisionRealSession(info);
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("海康 SDK 直连失败，回退模拟会话: {}", e.getMessage());
                    /**
                     * hikvision会话。
                     * @param info 信息
                     * @return hikvision会话的结果
                     */
                    return new HikvisionSession(info);
                }
                /**
                 * illegal状态异常。
                 * @param e.getMessage( e.获取消息(
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("海康 SDK 直连失败: " + e.getMessage(), e);
            }
        }

        /**
         * IllegalArgumentException。
         * @param info.getAuthType( 信息.获取认证类型(
         * @return IllegalArgumentException的结果
         */
        throw new IllegalArgumentException("不支持的海康认证类型: " + info.authType());
    }

    @Override
    /**
     * 获取组织
     * @return 返回值
    */
    public List<OrganizationNode> fetchOrganizations() {
 // 组织同步需在已连接的 打开api 会话上下文中执行，由业务层调用 会话 后再行实现。
        return List.of();
    }

    @Override
    /**
     * 按凭据建连后的会话语境拉取设备目录（cloud-yao OpenAPI：厂商侧组织维度设备列表）
     * @param session 参数
     * @return 返回值
    */
    public List<VendorDevice> fetchDevices(DeviceSession session) {
        if (session instanceof HikvisionCloudYaoSession yao) {
            try {
                return yao.fetchDevices();
            } catch (Exception e) {
                log.warn("海康云耀 设备目录拉取失败: {}", e.getMessage());
                return List.of();
            }
        }
        return fetchDevices();
    }

    @Override
    /**
     * 按凭据建连后的会话语境拉取组织树（cloud-yao OpenAPI：厂商侧组织列表）
     * @param session 参数
     * @return 返回值
    */
    public List<OrganizationNode> fetchOrganizations(DeviceSession session) {
        if (session instanceof HikvisionCloudYaoSession yao) {
            try {
                return yao.fetchOrganizations();
            } catch (Exception e) {
                log.warn("海康云耀 组织树拉取失败: {}", e.getMessage());
                return List.of();
            }
        }
        return fetchOrganizations();
    }

    @Override
    /**
     * 按凭据建连后的会话语境拉取人员列表（cloud-yao OpenAPI：厂商侧组织维度人员列表）
     * @param session 参数
     * @return 返回值
    */
    public List<DevicePerson> fetchPersons(DeviceSession session) {
        if (session instanceof HikvisionCloudYaoSession yao) {
            try {
                return yao.fetchPersons();
            } catch (Exception e) {
                log.warn("海康云耀 人员列表拉取失败: {}", e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    @Override
    public List<VendorDevice> fetchDevices() {
        if (!fallbackMock) {
            // 真实设备目录需各平台开放 API 接入后实现
            return List.of();
        }
        return List.of(
                new VendorDevice("HK-MOCK-001", "海康模拟球机", "DS-2DE", "camera",
                        "HK-MOCK-001", null, null, "模拟园区 A 座",
                        new BigDecimal("30.2001"), new BigDecimal("120.2001"), 4, true),
                new VendorDevice("HK-MOCK-002", "海康模拟门禁", "DS-K1T", "accessControl",
                        "HK-MOCK-002", null, null, "模拟园区 B 座",
                        new BigDecimal("30.2002"), new BigDecimal("120.2002"), 1, true)
        );
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        List<CredentialField> platformFields = List.of(
                new CredentialField("appKey", "AppKey（AK）", false, true, "平台应用的 AppKey"),
                new CredentialField("appSecret", "AppSecret（SK）", true, true, "平台应用的 AppSecret"));
        List<CredentialField> productFields = List.of(
                new CredentialField("productCode", "产品 Code（Product Code）", false, true,
                        "云曜应用的 ProductCode，同步范围限定在此产品下"),
                new CredentialField("projectId", "项目 ID（Project ID）", false, true,
                        "云曜应用归属的项目 ID，组织/人员需按项目筛选"));
        // 简化作用域：在默认字段基底上追加产品维字段，只给云耀
        List<CredentialField> cloudYaoFields = Stream.concat(platformFields.stream(), productFields.stream())
                .collect(Collectors.toList());
        return new DeviceVendorDescriptor(
                VENDOR,
                "海康威视",
                List.of(
                        new AuthTypeDescriptor("PLATFORM", "平台接入", "通过海康开放平台账号接入，可跨公网"),
                        new AuthTypeDescriptor("SDK", "SDK 直连", "局域网直连设备，需要厂商 SDK 运行库")),
                List.of(
                        new PlatformDescriptor(PLATFORM_CLOUD_YAO, "海康云耀",
                                "https://open.hikyun.com", cloudYaoFields),
                        new PlatformDescriptor(PLATFORM_CLOUD_EYE, "海康云眸",
                                "https://api.hik-cloud-eye.com", platformFields),
                        new PlatformDescriptor(PLATFORM_SECURE, "海康综合安防平台",
                                "https://artemis.hikvision.com", platformFields)),
                Set.of(DeviceCapability.DEVICE, DeviceCapability.MEDIA, DeviceCapability.PTZ,
                        DeviceCapability.EVENT, DeviceCapability.ACCESS, DeviceCapability.CHANNEL,
                        DeviceCapability.ORG),
                60,
                null,
                "支持云耀、云眸、综合安防平台与 SDK 直连；组织同步与设备目录同步待各平台 API 接入后开放。");
    }
}
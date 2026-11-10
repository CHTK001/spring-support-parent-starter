// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.provider.hikvision.open.HikvisionCloudEyeSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionCloudYaoSession;
import com.chua.starter.device.provider.hikvision.open.HikvisionSecurePlatformSession;
import com.chua.starter.device.provider.hikvision.open.EzvizSession;
import com.chua.starter.device.provider.hikvision.sdk.HikvisionRealSession;
import com.chua.starter.device.provider.sdk.DeviceCapabilityMatrix;
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
     * 萤石云（EZVIZ 开放平台）
     */
    private static final String PLATFORM_EZVIZ = "ezviz";

    /**
     * ISUP / EHome 设备主动注册协议
     */
    private static final String PLATFORM_ISUP = "isup";

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
                if (PLATFORM_EZVIZ.equalsIgnoreCase(platform)) {
                    // 萤石走独立会话：form 表单提交 + /api/lapp/ 路径
                    return new EzvizSession(info);
                }
                throw new IllegalArgumentException("海康平台类型不支持: " + platform
                        + "（期望 cloud-yao / cloud-eye / secure-platform / ezviz）");
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

    /**
     * 按凭据直接走统一 SDK 门面拉设备目录（不经会话）。
     *
     * <p>同步服务优先走这里：官方 SDK / OpenAPI 的细节都在门面实现里，
     * 不再依赖具体 Session 类型判断。</p>
     *
     * @param info 连接信息（含 AK/SK、产品/项目）
     * @return 设备条目；平台未覆盖或失败时为空
     */
    public List<VendorDevice> fetchDevicesBySdk(com.chua.starter.device.spi.model.DeviceConnectInfo info) {
        com.chua.starter.device.provider.sdk.DevicePlatformSdk sdk =
                com.chua.starter.device.provider.sdk.DevicePlatformSdks.of(info);
        if (sdk == null) {
            return List.of();
        }
        try {
            return sdk.listDevices();
        } catch (Exception e) {
            log.warn("海康 {} 设备目录拉取失败: {}", sdk.platform(), e.getMessage());
            return List.of();
        }
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
        if (session instanceof EzvizSession ezviz) {
            try {
                return ezviz.fetchCompanyMembers(1, 200);
            } catch (Exception e) {
                log.warn("萤石 企业人员拉取失败: {}", e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    @Override
    public String upsertPerson(DevicePerson person) {
        throw new IllegalStateException("按凭据写人员请使用 upsertPerson(DeviceConnectInfo, DevicePerson)");
    }

    /**
     * 按凭据在厂商侧新增或更新人员。
     *
     * <p>萤石是当前唯一支持人员写入的海康子平台：{@code externalId} 为空走
     * {@code /api/v3/device/company/member/add}，非空走 {@code .../update}。</p>
     *
     * @param info   连接信息（平台须为 ezviz，含 appKey/appSecret）
     * @param person 人员条目
     * @return 厂商侧人员标识；不支持或失败为 null
     */
    public String upsertPerson(DeviceConnectInfo info, DevicePerson person) {
        if (info == null || !PLATFORM_EZVIZ.equalsIgnoreCase(info.platformType())) {
            return null;
        }
        EzvizSession ezviz = new EzvizSession(info);
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        // 萤石更新要用主键 empId（不是业务号 empNo），业务侧无法自造，故本入口只做新增
        return ezviz.addCompanyMember(person);
    }

    /**
     * 按凭据在厂商侧删除人员（萤石）。
     *
     * @param info     连接信息（平台须为 ezviz）
     * @param personId 厂商侧人员编号
     * @return 是否删除成功
     */
    public boolean removePerson(DeviceConnectInfo info, String personId) {
        if (info == null || !PLATFORM_EZVIZ.equalsIgnoreCase(info.platformType())) {
            return false;
        }
        return new EzvizSession(info).deleteCompanyMember(personId);
    }

    /**
     * 按凭据远程开门（萤石智能锁）。
     *
     * <p>萤石远程开锁以门锁设备序列号为目标，走 {@code /api/lapp/building/device/smartlock/unlock}。</p>
     *
     * @param info       连接信息（平台须为 ezviz）
     * @param doorSerial 门锁设备序列号
     * @return 是否开门成功
     */
    public boolean remoteUnlock(DeviceConnectInfo info, String doorSerial) {
        if (info == null || !PLATFORM_EZVIZ.equalsIgnoreCase(info.platformType())) {
            return false;
        }
        return new EzvizSession(info).unlockSmartLock(doorSerial);
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
                                "https://open.hikyun.com", cloudYaoFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_CLOUD_YAO)),
                        new PlatformDescriptor(PLATFORM_CLOUD_EYE, "海康云眸",
                                "https://api2.hik-cloud.com", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_CLOUD_EYE)),
                        new PlatformDescriptor(PLATFORM_SECURE, "海康综合安防平台",
                                "https://artemis.hikvision.com", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_SECURE)),
                        new PlatformDescriptor(PLATFORM_EZVIZ, "萤石云（EZVIZ）",
                                "https://open.ys7.com", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_EZVIZ)),
                        new PlatformDescriptor(PLATFORM_ISUP, "ISUP / EHome（设备主动注册）",
                                "", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_ISUP))),
                // 厂商级能力位取各平台的并集，仅用于「厂商是否能接入」这类粗粒度判断；
                // 功能入口的显隐必须按平台级 capabilities，不能依赖这里
                unionCapabilities(PLATFORM_CLOUD_YAO, PLATFORM_CLOUD_EYE, PLATFORM_SECURE,
                        PLATFORM_EZVIZ, PLATFORM_ISUP),
                60,
                null,
                "支持云耀、云眸、综合安防平台与 SDK 直连；各平台支持的能力以平台级能力矩阵为准，"
                        + "不支持的平台在前端直接隐藏对应功能入口。");
    }

    /**
     * 取厂商下若干平台能力位的并集。
     *
     * @param platforms 平台编码
     * @return 能力位并集
     */
    private static Set<DeviceCapability> unionCapabilities(String... platforms) {
        Set<DeviceCapability> result = java.util.EnumSet.noneOf(DeviceCapability.class);
        for (String platform : platforms) {
            result.addAll(DeviceCapabilityMatrix.capabilitiesOf(VENDOR, platform));
        }
        return result;
    }
}
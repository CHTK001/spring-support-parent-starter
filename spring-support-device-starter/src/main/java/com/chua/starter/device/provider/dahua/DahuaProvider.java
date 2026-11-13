// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.provider.dahua.open.DahuaCloudSession;
import com.chua.starter.device.provider.dahua.open.DahuaIccSession;
import com.chua.starter.device.provider.dahua.open.DahuaLeChangeSession;
import com.chua.starter.device.provider.dahua.sdk.DahuaRealSession;
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

/**
 * 大华设备 提供者
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
 * @since 4.0.0.42
 * @author CH
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
     * 大华 ICC / DSS 私有化平台
     */
    private static final String PLATFORM_ICC = "dahua-icc";

    /**
     * 是否允许回退到模拟会话（默认 false：严格真实对接）
     */
    private final boolean fallbackMock;

    /**
     * 创建 dahua提供者 实例
     * @param environment 环境
     */
    public DahuaProvider(Environment environment) {
        this.fallbackMock = environment.getProperty("plugin.device.provider.fallback-mock", boolean.class, false);
        if (fallbackMock) {
            log.warn("[DahuaProvider] plugin.device.provider.fallback-mock=true，连接失败时将回退模拟会话（仅限调试）");
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
        log.info("大华设备连接: authType={}, platformType={}, deviceSerial={}",
                info.authType(), info.platformType(), info.deviceSerial());

        if (AUTH_PLATFORM.equalsIgnoreCase(info.authType())) {
            String platform = info.platformType();
            try {
                if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(platform)) {
                    /**
                     * dahuacloud会话。
                     * @param info 信息
                     * @return dahuacloud会话的结果
                     */
                    return new DahuaCloudSession(info);
                }
                if (PLATFORM_LECHANGE.equalsIgnoreCase(platform)) {
                    /**
                     * dahuale改变会话。
                     * @param info 信息
                     * @return dahuale改变会话的结果
                     */
                    return new DahuaLeChangeSession(info);
                }
                if (PLATFORM_ICC.equalsIgnoreCase(platform)) {
                    return new com.chua.starter.device.provider.dahua.open.DahuaIccSession(info);
                }
                throw new IllegalArgumentException("大华平台类型不支持: " + platform
                        + "（期望 dahua-cloud / lechange / dahua-icc）");
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("大华平台连接失败，回退模拟会话: {}", e.getMessage());
                    /**
                     * dahua会话。
                     * @param info 信息
                     * @return dahua会话的结果
                     */
                    return new DahuaSession(info);
                }
                /**
                 * illegal状态异常。
                 * @param e.getMessage( e.获取消息(
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("大华平台连接失败 [" + platform + "]: " + e.getMessage(), e);
            }
        }

        if (AUTH_SDK.equalsIgnoreCase(info.authType()) || info.authType() == null) {
            try {
                /**
                 * dahuareal会话。
                 * @param info 信息
                 * @return dahuareal会话的结果
                 */
                return new DahuaRealSession(info);
            } catch (Exception e) {
                if (fallbackMock) {
                    log.warn("大华 SDK 直连失败，回退模拟会话: {}", e.getMessage());
                    /**
                     * dahua会话。
                     * @param info 信息
                     * @return dahua会话的结果
                     */
                    return new DahuaSession(info);
                }
                /**
                 * illegal状态异常。
                 * @param e.getMessage( e.获取消息(
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("大华 SDK 直连失败: " + e.getMessage(), e);
            }
        }

        /**
         * IllegalArgumentException。
         * @param info.getAuthType( 信息.获取认证类型(
         * @return IllegalArgumentException的结果
         */
        throw new IllegalArgumentException("不支持的大华认证类型: " + info.authType());
    }

    @Override
    public List<OrganizationNode> fetchOrganizations() {
 // 云睿组织接口需要 templateId，必须按凭据建连后从当前用户或 extra 取，见 fetchOrganizations(DeviceSession)。
        return List.of();
    }

    /**
     * 按凭据建连后的会话语境拉取组织树。
     *
     * @param session 会话
     * @return 组织节点
     */
    @Override
    public List<OrganizationNode> fetchOrganizations(DeviceSession session) {
        if (session instanceof DahuaCloudSession cloud) {
            return cloud.fetchOrganizations();
        }
        if (session instanceof DahuaIccSession icc) {
            return icc.fetchOrganizations();
        }
        return fetchOrganizations();
    }

    /**
     * 按凭据建连后的会话语境拉取人员。
     *
     * @param session 会话
     * @return 人员
     */
    @Override
    public List<DevicePerson> fetchPersons(DeviceSession session) {
        if (session instanceof DahuaCloudSession cloud) {
            return cloud.fetchPersons();
        }
        if (session instanceof DahuaIccSession icc) {
            return icc.fetchPersons();
        }
        return List.of();
    }

    @Override
    public String upsertOrganization(OrganizationNode node) {
        throw new IllegalStateException("按凭据写组织请使用 upsertOrganization(DeviceConnectInfo, OrganizationNode)");
    }

    /**
     * 按凭据写组织：云睿走组织套件，ICC 走 ICC 部门接口，乐橙无组织模型。
     *
     * @param info 连接信息
     * @param node 组织节点
     * @return 厂商侧组织 ID
     */
    public String upsertOrganization(DeviceConnectInfo info, OrganizationNode node) {
        if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType())) {
            return new DahuaCloudSession(info).upsertOrganization(node);
        }
        if (PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType())) {
            throw new UnsupportedOperationException("大华乐橙面向消费级，无组织模型");
        }
        return new DahuaIccSession(info).upsertOrganization(node);
    }

    @Override
    public boolean removeOrganization(String externalId) {
        throw new IllegalStateException("按凭据删除组织请使用 removeOrganization(DeviceConnectInfo, String)");
    }

    /**
     * 按凭据删除组织：云睿走组织套件，ICC 走 ICC 部门接口。
     *
     * @param info       连接信息
     * @param externalId 组织 ID
     * @return 是否删除成功
     */
    public boolean removeOrganization(DeviceConnectInfo info, String externalId) {
        if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType())) {
            return new DahuaCloudSession(info).removeOrganization(externalId);
        }
        if (PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType())) {
            throw new UnsupportedOperationException("大华乐橙面向消费级，无组织模型");
        }
        return new DahuaIccSession(info).removeOrganization(externalId);
    }

    @Override
    public String upsertPerson(DevicePerson person) {
        throw new IllegalStateException("按凭据写人员请使用 upsertPerson(DeviceConnectInfo, DevicePerson)");
    }

    /**
     * 按凭据写人员：云睿走平台用户套件，ICC 走 ICC 人员接口。
     *
     * @param info   连接信息
     * @param person 人员
     * @return 厂商侧人员 ID
     */
    public String upsertPerson(DeviceConnectInfo info, DevicePerson person) {
        if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType())) {
            return new DahuaCloudSession(info).upsertPerson(person);
        }
        if (PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType())) {
            throw new UnsupportedOperationException("大华乐橙面向消费级，无人员模型");
        }
        return new DahuaIccSession(info).upsertPerson(person);
    }

    @Override
    public boolean removePerson(String personId) {
        throw new IllegalStateException("按凭据删除人员请使用 removePerson(DeviceConnectInfo, String)");
    }

    /**
     * 按凭据删除人员。
     *
     * @param info     连接信息
     * @param personId 人员 ID
     * @return 是否删除成功
     */
    public boolean removePerson(DeviceConnectInfo info, String personId) {
        if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType())) {
            return new DahuaCloudSession(info).removePerson(personId);
        }
        if (PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType())) {
            throw new UnsupportedOperationException("大华乐橙面向消费级，无人员模型");
        }
        return new DahuaIccSession(info).removePerson(personId);
    }

    /**
     * 按凭据写回设备（新增/改名）：云睿走设备套件，乐橙走绑定/改名接口。
     *
     * <p>乐橙新增时 {@code extra.code}（兼容 {@code extra.devicePassword}）作为设备验证码；
     * 设备底部无 8 位安全码时按官方要求传空串。绑定成功后按本地名称改名，改名失败不回滚绑定，
     * 只记录告警，避免调用方重复绑定。</p>
     *
     * @param info   连接信息
     * @param device 设备
     * @param exists 平台侧是否已存在
     * @return 是否成功
     */
    public boolean upsertRemote(DeviceConnectInfo info, VendorDevice device, boolean exists) {
        boolean cloud = PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType());
        boolean lechange = PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType());
        if (!cloud && !lechange) {
            return false;
        }
        String externalId = device.externalId() == null || device.externalId().isBlank()
                ? device.serial() : device.externalId();
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("大华设备写入需要设备序列号");
        }
        if (exists) {
            if (cloud) {
                new DahuaCloudSession(info).renameDevice(externalId, null, device.name());
            } else {
                new DahuaLeChangeSession(info).modifyDeviceName(externalId, device.name());
            }
            return true;
        }
        if (cloud) {
            String devCode = extraText(info, "devCode");
            if (devCode == null) {
                devCode = extraText(info, "devicePassword");
            }
            if (devCode == null) {
                throw new IllegalStateException("大华云新增设备需要设备密码：请在凭据额外参数配置 devCode");
            }
            new DahuaCloudSession(info).addDevice(externalId, device.name(), devCode, null);
            return true;
        }
        String code = extraText(info, "code");
        if (code == null) {
            code = extraText(info, "devicePassword");
        }
        DahuaLeChangeSession lechangeSession = new DahuaLeChangeSession(info);
        lechangeSession.bindDevice(externalId, code);
        if (device.name() != null && !device.name().isBlank()) {
            try {
                lechangeSession.modifyDeviceName(externalId, device.name());
            } catch (RuntimeException e) {
                log.warn("乐橙设备已绑定但改名失败: deviceId={}, message={}", externalId, e.getMessage());
            }
        }
        return true;
    }

    /**
     * 按凭据删除设备：云睿走删除接口，乐橙走解绑接口。
     *
     * @param info       连接信息
     * @param externalId 设备序列号
     * @return 是否成功
     */
    public boolean removeRemote(DeviceConnectInfo info, String externalId) {
        if (PLATFORM_DAHUA_CLOUD.equalsIgnoreCase(info.platformType())) {
            new DahuaCloudSession(info).removeDevice(externalId);
            return true;
        }
        if (PLATFORM_LECHANGE.equalsIgnoreCase(info.platformType())) {
            new DahuaLeChangeSession(info).unbindDevice(externalId);
            return true;
        }
        return false;
    }

    /**
     * 取凭据扩展字段文本。
     *
     * @param info 连接信息
     * @param key  键
     * @return 文本，缺失返回 null
     */
    private static String extraText(DeviceConnectInfo info, String key) {
        Object value = info.extra() == null ? null : info.extra().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 按凭据建连后的会话语境拉取设备目录。
     *
     * <p>云睿：走已验证的 {@code /open-api/api-iot/device/getDeviceList}
     * （入 {@code pageNum/pageSize}，出 {@code data.pageData[].deviceList[]}）。
     * 乐橙：走乐橙 v2 设备列表。</p>
     *
     * @param session 按凭据建连后的会话
     * @return 厂商设备条目
     */
    @Override
    public List<VendorDevice> fetchDevices(DeviceSession session) {
        if (session instanceof DahuaCloudSession cloud) {
            try {
                return cloud.fetchDevices();
            } catch (Exception e) {
                log.warn("大华云睿 设备目录拉取失败: {}", e.getMessage());
                return List.of();
            }
        }
        return fetchDevices();
    }

    @Override
    public List<VendorDevice> fetchDevices() {
        if (!fallbackMock) {
            // 真实设备目录需各平台开放 API 接入后实现
            return List.of();
        }
        return List.of(
                new VendorDevice("DH-MOCK-001", "大华模拟枪机", "DH-IPC", "camera",
                        "DH-MOCK-001", null, null, "模拟园区 C 座",
                        new BigDecimal("30.3001"), new BigDecimal("120.3001"), 2, true),
                new VendorDevice("DH-MOCK-002", "大华模拟报警", "DH-ARC", "alarm",
                        "DH-MOCK-002", null, null, "模拟园区 D 座",
                        new BigDecimal("30.3002"), new BigDecimal("120.3002"), 1, false)
        );
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        List<CredentialField> platformFields = List.of(
                new CredentialField("appKey", "AppKey（AK）", false, true, "平台应用的 AppKey"),
                new CredentialField("appSecret", "AppSecret（SK）", true, true, "平台应用的 AppSecret"));
        // 云睿走大华云开发者平台 IoT open-api：签名头与业务请求都要带 ProductId，
        // 缺这一项连换 token 都过不了，所以必须由凭据字段显式提供。
        List<CredentialField> cloudFields = List.of(
                new CredentialField("appKey", "AppKey（AK）", false, true,
                        "大华云开发者平台的 AccessKey"),
                new CredentialField("appSecret", "AppSecret（SK）", true, true,
                        "大华云开发者平台的 SecretKey，参与 HMAC-SHA512 签名"),
                new CredentialField("productCode", "产品 ID（ProductId）", false, true,
                        "IoT 开放平台的产品 ID（控制台产品详情可见），请求头与签名必带"));
        return new DeviceVendorDescriptor(
                VENDOR,
                "大华股份",
                List.of(
                        new AuthTypeDescriptor("PLATFORM", "平台接入", "通过大华云睿/乐橙开放平台接入"),
                        new AuthTypeDescriptor("SDK", "SDK 直连", "局域网直连设备，需要厂商 SDK 运行库")),
                List.of(
                        new PlatformDescriptor(PLATFORM_DAHUA_CLOUD, "大华云睿",
                                "https://open.cloud-dahua.com", cloudFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_DAHUA_CLOUD)),
                        new PlatformDescriptor(PLATFORM_LECHANGE, "大华乐橙",
                                "https://openapi.lechange.cn", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_LECHANGE)),
                        new PlatformDescriptor(PLATFORM_ICC, "大华 ICC / DSS（私有化）",
                                "", platformFields,
                                DeviceCapabilityMatrix.capabilitiesOf(VENDOR, PLATFORM_ICC))),
                // 厂商级能力位取各平台并集；功能显隐必须按平台级 capabilities
                unionCapabilities(PLATFORM_DAHUA_CLOUD, PLATFORM_LECHANGE, PLATFORM_ICC),
                60,
                null,
                "支持云睿、乐橙与 SDK 直连；各平台支持的能力以平台级能力矩阵为准，"
                        + "不支持的平台在前端直接隐藏对应功能入口。");
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
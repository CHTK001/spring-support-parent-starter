// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.dahua;

import com.chua.starter.device.provider.dahua.open.DahuaCloudSession;
import com.chua.starter.device.provider.dahua.open.DahuaLeChangeSession;
import com.chua.starter.device.provider.dahua.sdk.DahuaRealSession;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AuthTypeDescriptor;
import com.chua.starter.device.spi.model.CredentialField;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
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
                throw new IllegalArgumentException("大华平台类型不支持: " + platform
                        + "（期望 dahua-cloud / lechange）");
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
    /**
     * 获取组织
     * @return 返回值
    */
    public List<OrganizationNode> fetchOrganizations() {
 // 组织同步需在已连接的 打开api 会话上下文中执行，由业务层处理。
        return List.of();
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
        return new DeviceVendorDescriptor(
                VENDOR,
                "大华股份",
                List.of(
                        new AuthTypeDescriptor("PLATFORM", "平台接入", "通过大华云睿/乐橙开放平台接入"),
                        new AuthTypeDescriptor("SDK", "SDK 直连", "局域网直连设备，需要厂商 SDK 运行库")),
                List.of(
                        new PlatformDescriptor(PLATFORM_DAHUA_CLOUD, "大华云睿",
                                "https://api.dahua-cloud.com", platformFields),
                        new PlatformDescriptor(PLATFORM_LECHANGE, "大华乐橙",
                                "https://openapi.lechange.com/openapi", platformFields)),
                Set.of(DeviceCapability.MEDIA, DeviceCapability.PTZ,
                        DeviceCapability.EVENT, DeviceCapability.ACCESS, DeviceCapability.CHANNEL),
                60,
                null,
                "支持云睿、乐橙与 SDK 直连；组织同步与设备目录同步待各平台 API 接入后开放。");
    }
}
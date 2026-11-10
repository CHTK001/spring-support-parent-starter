package com.chua.starter.device.provider.sdk;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台 SDK 工厂：按 {@code vendor + platformType} 选择实现。
 *
 * <p><b>机制</b>：走 common-starter 的 {@link ServiceProvider} SPI 扩展点，
 * 扩展类型为 {@link DevicePlatformSdkProvider}。每个「厂商 + 平台」对应一个
 * Provider 实现，由 {@code META-INF/services} 或 Spring Bean 登记；
 * 本类只负责按 {@code support(info)} 取扩展并 {@code create(info)}，
 * <b>不写任何 if 分支</b>——新增平台不改本类。</p>
 *
 * <p>内置 5 个平台在静态块里登记；外部模块新增平台只要提供
 * {@link DevicePlatformSdkProvider} 实现（SPI 文件或 Bean）即可被发现。</p>
 *
 * <p>未覆盖的平台返回 {@code null}，调用方回落到原有 Session 实现，保证兼容。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public final class DevicePlatformSdks {

    /**
     * SPI 扩展点：所有平台 SDK 提供者的唯一注册表。
     */
    private static final ServiceProvider<DevicePlatformSdkProvider> SPI =
            ServiceProvider.of(DevicePlatformSdkProvider.class);

    /**
     * 工具类不可实例化。
     */
    private DevicePlatformSdks() {
    }

    static {
        registerBuiltin();
    }

    /**
     * 登记模块内置的平台实现（综合安防 / 云曜 / 云眸 / 云睿 / 乐橙）。
     */
    private static void registerBuiltin() {
        register(new AbstractSdkProvider("hikvision", "secure-platform") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.hikvision.HikvisionArtemisApis.all();
            }
        });
        register(new AbstractSdkProvider("hikvision", "cloud-yao") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.hikvision.HikvisionCloudYaoSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.hikvision.HikvisionCloudYaoApis.all();
            }
        });
        register(new AbstractSdkProvider("hikvision", "cloud-eye") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.hikvision.HikvisionCloudEyeSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.hikvision.HikvisionCloudEyeApis.all();
            }
        });
        register(new AbstractSdkProvider("dahua", "dahua-cloud") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.dahua.DahuaCloudSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.dahua.DahuaCloudApis.all();
            }
        });
        register(new AbstractSdkProvider("dahua", "lechange") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.dahua.LeChangeSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.dahua.LeChangeApis.all();
            }
        });
        // ---------- 新增平台（SPI 扩展） ----------
        register(new AbstractSdkProvider("hikvision", "ezviz") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.ezviz.EzvizSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.ezviz.EzvizApis.all();
            }
        });
        register(new AbstractSdkProvider("uniview", "uniview") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.uniview.UniviewSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.uniview.UniviewApis.all();
            }
        });
        register(new AbstractSdkProvider("hikvision", "isup") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.hikvision.HikvisionIsupSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.hikvision.HikvisionIsupApis.all();
            }
        });
        register(new AbstractSdkProvider("dahua", "dahua-icc") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.dahua.DahuaIccSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.dahua.DahuaIccApis.all();
            }
        });
        register(new AbstractSdkProvider("standard", "gb28181") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.protocol.Gb28181Sdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.protocol.Gb28181Apis.gb();
            }
        });
        register(new AbstractSdkProvider("standard", "onvif") {
            @Override
            public DevicePlatformSdk create(DeviceConnectInfo info) {
                return new com.chua.starter.device.provider.sdk.protocol.OnvifSdk(info);
            }

            @Override
            public java.util.List<PlatformApiSpec> apis() {
                return com.chua.starter.device.provider.sdk.protocol.Gb28181Apis.onvif();
            }
        });
    }

    /**
     * 登记一个平台扩展（同名覆盖），写入 common-starter SPI 注册表。
     *
     * <p>注意：{@code ServiceProvider.register(String, Object)} 不会给
     * {@code ServiceDefinition} 设 name，而 {@code list()/collect()} 以 name 为键，
     * 多条扩展会全部落到 null 键互相覆盖（实测只剩 1 个）。这里显式构建带 name
     * 的定义再注册，保证每个扩展都能被 {@code getExtensions()/getExtension()} 看到。</p>
     *
     * @param provider 平台扩展
     */
    public static void register(DevicePlatformSdkProvider provider) {
        if (provider == null || provider.name() == null || provider.name().isBlank()) {
            return;
        }
        ServiceDefinition definition = new ServiceDefinition();
        definition.setName(provider.name());
        definition.setType(DevicePlatformSdkProvider.class);
        definition.setImplClass(provider.getClass());
        definition.setObj(provider);
        definition.setLoaded(true);
        definition.setLoadTime(System.currentTimeMillis());
        SPI.register(definition);
        log.info("[Device] 登记平台 SDK 扩展: {}", provider.name());
    }

    /**
     * 扩展注册表由 common-starter SPI 托管，不支持按名注销（SPI 只提供按 resolver 注销）。
     * 保留方法供兼容调用，实际不移除，返回 false。
     *
     * @param name 扩展名
     * @return 固定 false
     */
    public static boolean unregister(String name) {
        log.debug("[Device] SPI 扩展不支持按名注销: {}", name);
        return false;
    }

    /**
     * 已登记的平台扩展名。
     *
     * @return 扩展名集合
     */
    public static java.util.Set<String> extensions() {
        return SPI.getExtensions();
    }

    /**
     * 已登记的平台扩展（按 name 稳定遍历）。
     *
     * @return 扩展列表
     */
    private static List<DevicePlatformSdkProvider> allProviders() {
        List<DevicePlatformSdkProvider> result = new ArrayList<>();
        for (String name : SPI.getExtensions()) {
            DevicePlatformSdkProvider provider = SPI.getExtension(name);
            if (provider != null) {
                result.add(provider);
            }
        }
        return result;
    }

    /**
     * 按连接信息选择平台 SDK 实现：遍历 SPI 扩展，按 {@code support(info)} 匹配。
     *
     * @param info 连接信息
     * @return 实现；平台未覆盖时返回 null
     */
    public static DevicePlatformSdk of(DeviceConnectInfo info) {
        if (info == null || info.vendor() == null) {
            return null;
        }
        for (DevicePlatformSdkProvider provider : allProviders()) {
            if (provider.support(info)) {
                try {
                    return provider.create(info);
                } catch (Exception ex) {
                    log.warn("[Device] 平台 SDK 创建失败: {} -> {}", provider.name(), ex.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 该平台是否已有 SDK 门面实现（用于诊断输出）。
     *
     * @param vendor   厂商标识
     * @param platform 平台类型
     * @return true 表示已覆盖
     */
    public static boolean supports(String vendor, String platform) {
        if (vendor == null || platform == null) {
            return false;
        }
        String v = vendor.trim();
        String p = platform.trim();
        for (DevicePlatformSdkProvider provider : allProviders()) {
            if (v.equalsIgnoreCase(provider.vendor()) && p.equalsIgnoreCase(provider.platform())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 已登记平台的接口目录（诊断用）。
     *
     * @return 扩展名 → 接口数
     */
    public static Map<String, Integer> apiCatalog() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DevicePlatformSdkProvider provider : allProviders()) {
            result.put(provider.name(), provider.apis().size());
        }
        return result;
    }

    /**
     * 取指定扩展（供其它模块按名复用）。
     *
     * @param name 扩展名
     * @return 扩展，未找到返回空
     */
    public static Optional<DevicePlatformSdkProvider> provider(String name) {
        return SPI.getIfPresent(name);
    }
}

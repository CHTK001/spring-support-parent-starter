package com.chua.starter.common.support.api.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * API 统一配置属性
 * <p>
 * 整合版本控制、平台标识、编解码、SPI 等 API 相关配置。
 * </p>
 *
 * @author CH
 * @since 2025/6/3
 * @version 2.0.0
 */
@Validated
@ConfigurationProperties(prefix = ApiProperties.PRE, ignoreInvalidFields = true)
public class ApiProperties {

    public static final String PRE = "plugin.api";

    /**
     * 忽略返回格式（针对返回格式不进行统计处理）
     */
    private String[] ignoreFormatPackages;

    /**
     * 版本控制配置
     */
    @Valid
    @NotNull(message = "版本配置不能为空")
    private Version version = new Version();

    /**
     * 平台配置
     */
    @Valid
    @NotNull(message = "平台配置不能为空")
    private Platform platform = new Platform();

    /**
     * 自动统一返回结构
     */
    private boolean uniform = true;
    
    /**
     * 响应编码配置
     */
    @Valid
    @NotNull(message = "响应编码配置不能为空")
    private ResponseEncodeProperties encode = new ResponseEncodeProperties();

    /**
     * 请求解码配置
     */
    @Valid
    @NotNull(message = "请求解码配置不能为空")
    private RequestDecodeProperties decode = new RequestDecodeProperties();

    /**
     * SPI 配置
     */
    @Valid
    private SpiConfig spi = new SpiConfig();

    /**
     * Mock 配置
     */
    @Valid
    @NotNull(message = "Mock配置不能为空")
    private MockConfig mock = new MockConfig();

    /**
     * 功能开关配置
     */
    @Valid
    @NotNull(message = "功能开关配置不能为空")
    private FeatureConfig feature = new FeatureConfig();

    /**
     * 废弃接口配置
     */
    @Valid
    @NotNull(message = "废弃接口配置不能为空")
    private DeprecatedConfig deprecated = new DeprecatedConfig();

    /**
     * 内部接口配置
     */
    @Valid
    @NotNull(message = "内部接口配置不能为空")
    private InternalConfig internal = new InternalConfig();

    /**
     * 灰度发布配置
     */
    @Valid
    @NotNull(message = "灰度发布配置不能为空")
    private GrayConfig gray = new GrayConfig();

    /**
     * 是否启用 API 控制功能（版本或平台）
     *
     * @return 是否启用
     */
    public boolean isControlEnabled() {
        return (version != null && version.isEnable())
                || (platform != null && platform.isEnable());
    }

    /**
     * 版本控制配置
     */
        public static class Version {

        /**
         * 是否开启版本控制
         */
        private boolean enable = true;
    }

    /**
     * 平台类型枚举
     */
        public enum PlatformType {
        /**
         * 系统平台
         */
        SYSTEM("system"),
        /**
         * 租户平台
         */
        TENANT("tenant"),
        /**
         * 监控平台
         */
        MONITOR("monitor"),
        /**
         * 调度平台
         */
        SCHEDULER("scheduler"),
        /**
         * OAuth平台
         */
        OAUTH("oauth");

        private final String value;

        PlatformType(String value) {
            this.value = value;
        }

    }

    /**
     * 平台配置
     */
        public static class Platform {

        /**
         * 是否开启平台标识
         */
        private boolean enable = true;

        /**
         * 平台类型（枚举，优先级高于 aliasName）
         */
        private PlatformType name = PlatformType.SYSTEM;

        /**
         * 平台别名（当 name 无法满足需求时使用自定义名称）
         */
        private String aliasName;

        /**
         * 获取实际平台名称
         * <p>
         * 优先级：name > aliasName
         * </p>
         *
         * @return 平台名称
         */
        public String getPlatformName() {
            if (name != null) {
                return name.getValue();
            }
            return aliasName;
        }
    }

    /**
     * 编解码配置
     */
        public static class ResponseEncodeProperties {

        /**
         * 是否开启加密功能
         */
        private boolean enable = false;

        /**
         * 是否开启响应加密
         */
        private boolean responseEnable = false;

        /**
         * 是否由其它对象注入参数
         */
        private boolean extInject = false;

        /**
         * 编解码器类型（sm2/aes/rsa等）
         */
        @NotNull(message = "编解码器类型不能为空")
        @Pattern(regexp = "^(sm2|aes|rsa|des)$", message = "编解码器类型必须为sm2/aes/rsa/des之一")
        private String codecType = "sm2";

        /**
         * 白名单（不需要加密的接口路径）
         */
        @NotNull(message = "白名单不能为null，可以为空列表")
        private List<String> whiteList = Collections.emptyList();
    }

    /**
     * 请求解码配置
     */
        public static class RequestDecodeProperties {

        /**
         * 是否开启请求解密
         */
        private boolean enable = false;

        /**
         * 请求解密密钥
         */
        private String codecRequestKey;

        /**
         * 是否由其它对象注入参数
         */
        private boolean extInject = false;

        /**
         * 解码器类型（sm4/aes等）
         */
        @NotNull(message = "解码器类型不能为空")
        @Pattern(regexp = "^(sm4|aes|des)$", message = "解码器类型必须为sm4/aes/des之一")
        private String codecType = "sm4";
        
        /**
         * 解密失败时是否拒绝请求
         * <p>
         * true: 解密失败时抛出异常，拒绝请求
         * false: 解密失败时返回原始数据（默认，保证业务连续性）
         * </p>
         */
        private boolean rejectOnDecodeFailure = false;
        
        /**
         * 不需要解密的路径白名单
         * <p>
         * 支持Ant风格路径匹配，如: /api/public/**, /health
         * </p>
         */
        @NotNull(message = "白名单不能为null，可以为空列表")
        private List<String> whiteList = Collections.emptyList();
    }

    /**
     * SPI 配置
     */
        public static class SpiConfig {

        /**
         * 是否开启虚拟映射
         */
        private boolean enable = false;

        /**
         * 虚拟映射
         * <p>
         * 用于将简短的类型名称映射到完整的类名。
         * 例如：{"captcha": "com.chua.common.support.captcha.Captcha"}
         * </p>
         */
        private Map<String, String> mapping;
    }

    /**
     * Mock 配置
     */
        public static class MockConfig {

        /**
         * 是否开启 Mock 功能
         */
        private boolean enable = false;

        /**
         * Mock 生效的环境（逗号分隔）
         */
        @NotNull(message = "Mock生效环境不能为空")
        private String profiles = "dev,test";
    }

    /**
     * 功能开关配置
     */
        public static class FeatureConfig {

        /**
         * 是否开启功能开关
         */
        private boolean enable = false;

        /**
         * 功能开关管理接口路径
         */
        @NotNull(message = "功能开关管理接口路径不能为空")
        @Pattern(regexp = "^/.*", message = "接口路径必须以/开头")
        private String path = "/api/features";
    }

    /**
     * 内部接口配置
     */
        public static class InternalConfig {

        /**
         * 是否开启内部接口控制
         */
        private boolean enable = true;

        /**
         * 全局IP白名单（适用于所有内部接口）
         */
        private List<String> globalAllowedIps = Collections.emptyList();

        /**
         * 全局服务白名单（适用于所有内部接口）
         */
        private List<String> globalAllowedServices = Collections.emptyList();
    }

    /**
     * 废弃接口配置
     */
        public static class DeprecatedConfig {

        /**
         * 是否开启废弃接口提示
         */
        private boolean enable = true;

        /**
         * 是否在响应头中添加废弃警告
         */
        private boolean addWarningHeader = true;
    }

    /**
     * 灰度发布配置
     */
        public static class GrayConfig {

        /**
         * 是否开启灰度发布功能
         */
        private boolean enable = false;

        /**
         * 灰度标识请求头名称
         * <p>
         * 请求命中灰度后，会在响应头中添加此头
         * </p>
         */
        @NotNull(message = "灰度标识请求头名称不能为空")
        private String headerName = "X-Gray-Hit";

        /**
         * 全局灰度用户白名单
         * <p>
         * 这些用户始终进入灰度版本
         * </p>
         */
        @NotNull(message = "全局灰度用户白名单不能为null，可以为空列表")
        private List<String> globalUsers = Collections.emptyList();

        /**
         * 全局灰度IP白名单
         * <p>
         * 这些IP始终进入灰度版本
         * </p>
         */
        @NotNull(message = "全局灰度IP白名单不能为null，可以为空列表")
        private List<String> globalIps = Collections.emptyList();

        /**
         * 默认灰度百分比
         * <p>
         * 注解未指定百分比时使用此默认值
         * </p>
         */
        @Min(value = 0, message = "灰度百分比不能小于0")
        @Max(value = 100, message = "灰度百分比不能大于100")
        private int defaultPercentage = 0;
    }
    /**
     * 获取 ignoreFormatPackages
     *
     * @return ignoreFormatPackages
     */
    public String[] getIgnoreFormatPackages() {
        return ignoreFormatPackages;
    }

    /**
     * 获取 version
     *
     * @return version
     */
    public Version getVersion() {
        return version;
    }

    /**
     * 获取 platform
     *
     * @return platform
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * 获取 uniform
     *
     * @return uniform
     */
    public boolean getUniform() {
        return uniform;
    }

    /**
     * 获取 encode
     *
     * @return encode
     */
    public ResponseEncodeProperties getEncode() {
        return encode;
    }

    /**
     * 获取 decode
     *
     * @return decode
     */
    public RequestDecodeProperties getDecode() {
        return decode;
    }

    /**
     * 获取 spi
     *
     * @return spi
     */
    public SpiConfig getSpi() {
        return spi;
    }

    /**
     * 获取 mock
     *
     * @return mock
     */
    public MockConfig getMock() {
        return mock;
    }

    /**
     * 获取 feature
     *
     * @return feature
     */
    public FeatureConfig getFeature() {
        return feature;
    }

    /**
     * 获取 deprecated
     *
     * @return deprecated
     */
    public DeprecatedConfig getDeprecated() {
        return deprecated;
    }

    /**
     * 获取 internal
     *
     * @return internal
     */
    public InternalConfig getInternal() {
        return internal;
    }

    /**
     * 获取 gray
     *
     * @return gray
     */
    public GrayConfig getGray() {
        return gray;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 value
     *
     * @return value
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 name
     *
     * @return name
     */
    public PlatformType getName() {
        return name;
    }

    /**
     * 获取 aliasName
     *
     * @return aliasName
     */
    public String getAliasName() {
        return aliasName;
    }

        /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 responseEnable
     *
     * @return responseEnable
     */
    public boolean getResponseEnable() {
        return responseEnable;
    }

    /**
     * 获取 extInject
     *
     * @return extInject
     */
    public boolean getExtInject() {
        return extInject;
    }

    /**
     * 获取 codecType
     *
     * @return codecType
     */
    public String getCodecType() {
        return codecType;
    }

    /**
     * 获取 whiteList
     *
     * @return whiteList
     */
    public List<String> getWhiteList() {
        return whiteList;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 codecRequestKey
     *
     * @return codecRequestKey
     */
    public String getCodecRequestKey() {
        return codecRequestKey;
    }

    /**
     * 获取 extInject
     *
     * @return extInject
     */
    public boolean getExtInject() {
        return extInject;
    }

    /**
     * 获取 codecType
     *
     * @return codecType
     */
    public String getCodecType() {
        return codecType;
    }

    /**
     * 获取 rejectOnDecodeFailure
     *
     * @return rejectOnDecodeFailure
     */
    public boolean getRejectOnDecodeFailure() {
        return rejectOnDecodeFailure;
    }

    /**
     * 获取 whiteList
     *
     * @return whiteList
     */
    public List<String> getWhiteList() {
        return whiteList;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 mapping
     *
     * @return mapping
     */
    public Map<String, String> getMapping() {
        return mapping;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 profiles
     *
     * @return profiles
     */
    public String getProfiles() {
        return profiles;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 path
     *
     * @return path
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 globalAllowedIps
     *
     * @return globalAllowedIps
     */
    public List<String> getGlobalAllowedIps() {
        return globalAllowedIps;
    }

    /**
     * 获取 globalAllowedServices
     *
     * @return globalAllowedServices
     */
    public List<String> getGlobalAllowedServices() {
        return globalAllowedServices;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 addWarningHeader
     *
     * @return addWarningHeader
     */
    public boolean getAddWarningHeader() {
        return addWarningHeader;
    }

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 获取 headerName
     *
     * @return headerName
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * 获取 globalUsers
     *
     * @return globalUsers
     */
    public List<String> getGlobalUsers() {
        return globalUsers;
    }

    /**
     * 获取 globalIps
     *
     * @return globalIps
     */
    public List<String> getGlobalIps() {
        return globalIps;
    }

    /**
     * 获取 defaultPercentage
     *
     * @return defaultPercentage
     */
    public int getDefaultPercentage() {
        return defaultPercentage;
    }


}


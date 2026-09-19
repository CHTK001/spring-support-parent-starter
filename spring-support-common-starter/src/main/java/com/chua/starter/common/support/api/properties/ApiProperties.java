package com.chua.starter.common.support.api.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
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
 * @since 2025/6/3
 * @author CH
 */
    @Validated
    @ConfigurationProperties(prefix = ApiProperties.PRE, ignoreInvalidFields = true)
    @Getter
    @Setter
    public class ApiProperties {
        /**
         * 获取版本
         *
         * @return 获取版本的结果
         */
        public Version getVersion() {
            return version;
        }

        /**
         * 获取Ignore格式化包
         *
         * @return 获取ignore格式化包的结果
         */
        public String[] getIgnoreFormatPackages() {
            return ignoreFormatPackages;
        }

    /** PRE */
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
    /** 版本 */
    private Version version = new Version();

    /**
     * 平台配置
     */
    @Valid
    @NotNull(message = "平台配置不能为空")
    /** Platform */
    private Platform platform = new Platform();

    /**
     * 获取平台配置
     *
     * @return 平台配置
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * 自动统一返回结构
     */
    private boolean uniform = true;
    
    /**
     * 响应编码配置
     */
    @Valid
    @NotNull(message = "响应编码配置不能为空")
    /** Encode */
    private ResponseEncodeProperties encode = new ResponseEncodeProperties();

    /**
     * 获取编码
     *
     * @return 获取encode的结果
     */
    public ResponseEncodeProperties getEncode() {
        return encode;
    }

    /**
     * 请求解码配置
     */
    @Valid
    @NotNull(message = "请求解码配置不能为空")
    /** Decode */
    private RequestDecodeProperties decode = new RequestDecodeProperties();

    /**
     * 获取解码
     *
     * @return 获取decode的结果
     */
    public RequestDecodeProperties getDecode() {
        return decode;
    }

    /**
     * SPI 配置
     */
    @Valid
    /** SPI */
    private SpiConfig spi = new SpiConfig();

    /**
     * 获取SPI配置
     *
     * @return SPI配置
     */
    public SpiConfig getSpi() {
        return spi;
    }

    /**
     * Mock 配置
     */
    @Valid
    @NotNull(message = "Mock配置不能为空")
    /** 模拟 */
    private MockConfig mock = new MockConfig();

    /**
     * 获取Mock
     *
     * @return 获取mock的结果
     */
    public MockConfig getMock() {
        return mock;
    }

    /**
     * 废弃接口配置
     */
    private DeprecatedConfig deprecated = new DeprecatedConfig();

    /**
     * 获取已弃用
     *
     * @return 获取已弃用的结果
     */
    public DeprecatedConfig getDeprecated() {
        return deprecated;
    }

    /**
     * 内部接口配置
     */
    private InternalConfig internal = new InternalConfig();

    /**
     * 获取内部
     *
     * @return 获取内部的结果
     */
    public InternalConfig getInternal() {
        return internal;
    }

    /**
     * 功能开关配置
     */
    @Valid
    @NotNull(message = "功能开关配置不能为空")
    /** 特征 */
    private FeatureConfig feature = new FeatureConfig();

    /**
     * 获取特征
     *
     * @return 获取特征的结果
     */
    public FeatureConfig getFeature() {
        return feature;
    }

    /**
     * 灰度发布配置
     */
    @Valid
    @NotNull(message = "灰度发布配置不能为空")
    /** Gray */
    private GrayConfig gray = new GrayConfig();

    /**
     * 获取灰度发布配置
     *
     * @return 灰度发布配置
     */
    public GrayConfig getGray() {
        return gray;
    }

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
     * @author CH
     * @since 4.0.0
     */
    @Getter
    @Setter
    public static class Version {

        /**
         * 是否开启版本控制
         */
        private boolean enable = true;

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }
    }

    /**
     * 平台类型枚举
     * @author CH
     * @since 4.0.0
     */
    @Getter
    public enum PlatformType {
        /**
         * 系统平台
         * @param "system" "系统"
         * @return 系统的结果
         */
        SYSTEM("system"),
        /**
         * 租户平台
         * @param "tenant" "tenant"
         * @return TENANT的结果
         */
        TENANT("tenant"),
        /**
         * 监控平台
         * @param "monitor" "监控"
         * @return 监控的结果
         */
        MONITOR("monitor"),
        /**
         * 调度平台
         * @param "scheduler" "调度器"
         * @return 调度器的结果
         */
        SCHEDULER("scheduler"),
        /**
         * o认证平台
         * @param "oauth" "OAuth"
         * @return OAUTH的结果
         */
        OAUTH("oauth");

        /** 值 */
        private final String value;

        /**
         * platform类型。
         * @param value 值
         */
        PlatformType(String value) {
            this.value = value;
        }

        /**
         * 获取值
         *
         * @return 获取值的结果
         */
        public String getValue() {
            return value;
        }

    }

    /**
     * 平台配置
     * @author CH
     * @since 4.0.0
     */
    public static class Platform {

        /**
         * 是否开启平台标识
         */
        private boolean enable = true;

        /**
         * 平台类型（枚举，优先级高于 别名名称）
         */
        private PlatformType name = PlatformType.SYSTEM;

        /**
         * 平台别名（当 名称 无法满足需求时使用自定义名称）
         */
        private String aliasName;

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取名称
         *
         * @return 获取名称的结果
         */
        public PlatformType getName() {
            return name;
        }

        /**
         * 设置名称
         *
         * @param name 名称
         */
        public void setName(PlatformType name) {
            this.name = name;
        }

        /**
         * 获取别名名称
         *
         * @return 获取别名名称的结果
         */
        public String getAliasName() {
            return aliasName;
        }

        /**
         * 设置别名名称
         *
         * @param aliasName 别名名称
         */
        public void setAliasName(String aliasName) {
            this.aliasName = aliasName;
        }

        /**
         * 获取实际平台名称
         * <p>
         * 优先级：名称 > 别名名称
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
     * @author CH
     * @since 4.0.0
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
        /** Codec类型 */
        private String codecType = "sm2";

        /**
         * 白名单（不需要加密的接口路径）
         */
        @NotNull(message = "白名单不能为null，可以为空列表")
        /** White列表 */
        private List<String> whiteList = Collections.emptyList();

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 是否响应启用
         *
         * @return 是否响应enable的结果
         */
        public boolean isResponseEnable() {
            return responseEnable;
        }

        /**
         * 设置响应启用
         *
         * @param responseEnable 响应enable
         */
        public void setResponseEnable(boolean responseEnable) {
            this.responseEnable = responseEnable;
        }

        /**
         * 是否extinject
         *
         * @return 是否extinject的结果
         */
        public boolean isExtInject() {
            return extInject;
        }

        /**
         * 设置extinject
         *
         * @param extInject extinject
         */
        public void setExtInject(boolean extInject) {
            this.extInject = extInject;
        }

        /**
         * 获取codec类型
         *
         * @return 获取codec类型的结果
         */
        public String getCodecType() {
            return codecType;
        }

        /**
         * 设置codec类型
         *
         * @param codecType codec类型
         */
        public void setCodecType(String codecType) {
            this.codecType = codecType;
        }

        /**
         * 获取white列表
         *
         * @return 获取white列表的结果
         */
        public List<String> getWhiteList() {
            return whiteList;
        }

        /**
         * 设置white列表
         *
         * @param whiteList white列表
         */
        public void setWhiteList(List<String> whiteList) {
            this.whiteList = whiteList;
        }
    }

    /**
     * 请求解码配置
     * @author CH
     * @since 4.0.0
     */
    @Getter
    @Setter
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
        /** Codec类型 */
        private String codecType = "sm4";

        /**
         * 获取codec类型
         *
         * @return 获取codec类型的结果
         */
        public String getCodecType() {
            return codecType;
        }

        /**
         * 设置codec类型
         *
         * @param codecType codec类型
         */
        public void setCodecType(String codecType) {
            this.codecType = codecType;
        }

        /**
         * 获取white列表
         *
         * @return 获取white列表的结果
         */
        public List<String> getWhiteList() {
            return whiteList;
        }

        /**
         * 设置white列表
         *
         * @param whiteList white列表
         */
        public void setWhiteList(List<String> whiteList) {
            this.whiteList = whiteList;
        }

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取codec请求Key
         *
         * @return 获取codec请求Key的结果
         */
        public String getCodecRequestKey() {
            return codecRequestKey;
        }

        /**
         * 设置codec请求Key
         *
         * @param codecRequestKey codec请求Key
         */
        public void setCodecRequestKey(String codecRequestKey) {
            this.codecRequestKey = codecRequestKey;
        }

        /**
         * 是否extinject
         *
         * @return 是否extinject的结果
         */
        public boolean isExtInject() {
            return extInject;
        }

        /**
         * 设置extinject
         *
         * @param extInject extinject
         */
        public void setExtInject(boolean extInject) {
            this.extInject = extInject;
        }

        /**
         * 是否rejecton解码失败
         *
         * @return 是否rejectondecode失败的结果
         */
        public boolean isRejectOnDecodeFailure() {
            return rejectOnDecodeFailure;
        }

        /**
         * 设置rejecton解码失败
         *
         * @param rejectOnDecodeFailure rejectondecode失败
         */
        public void setRejectOnDecodeFailure(boolean rejectOnDecodeFailure) {
            this.rejectOnDecodeFailure = rejectOnDecodeFailure;
        }

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
         * 支持Ant风格路径匹配，如: /api/公共/**, /健康
         * </p>
         */
        @NotNull(message = "白名单不能为null，可以为空列表")
        /** White列表 */
        private List<String> whiteList = Collections.emptyList();
    }

    /**
     * SPI 配置
     * @author CH
     * @since 4.0.0
     */
    public static class SpiConfig {

        /**
         * 是否开启虚拟映射
         */
        private boolean enable = true;

        /**
         * 虚拟映射
         * <p>
         * 用于将简短的类型名称映射到完整的类名。
         * 例如：{"captcha": "com.chua.通用.支持.captcha.Captcha"}
         * </p>
         */
        private Map<String, String> mapping;

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

    /**
     * 获取Mapping
     *
     * @return 获取mapping的结果
     */
    public Map<String, String> getMapping() {
        return mapping;
    }

    /**
     * 设置Mapping
     *
     * @param mapping mapping
     */
    public void setMapping(Map<String, String> mapping) {
        this.mapping = mapping;
    }
}

    /**
     * Mock 配置
     * @author CH
     * @since 4.0.0
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
        /** 配置文件 */
        private String profiles = "dev,test";

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取配置文件
         *
         * @return 获取配置文件的结果
         */
        public String getProfiles() {
            return profiles;
        }

        /**
         * 设置配置文件
         *
         * @param profiles 配置文件
         */
        public void setProfiles(String profiles) {
            this.profiles = profiles;
        }
    }

    /**
     * 功能开关配置
     * @author CH
     * @since 4.0.0
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
        /** 路径 */
        private String path = "/api/features";

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取路径
         *
         * @return 获取路径的结果
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置路径
         *
         * @param path 路径
         */
        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * 内部接口配置
     * @author CH
     * @since 4.0.0
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

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取全局allowedips
         *
         * @return 获取全局allowedips的结果
         */
        public List<String> getGlobalAllowedIps() {
            return globalAllowedIps;
        }

        /**
         * 设置全局allowedips
         *
         * @param globalAllowedIps 全局allowedips
         */
        public void setGlobalAllowedIps(List<String> globalAllowedIps) {
            this.globalAllowedIps = globalAllowedIps;
        }

        /**
         * 获取全局allowed服务
         *
         * @return 获取全局allowed服务的结果
         */
        public List<String> getGlobalAllowedServices() {
            return globalAllowedServices;
        }

        /**
         * 设置全局allowed服务
         *
         * @param globalAllowedServices 全局allowed服务
         */
        public void setGlobalAllowedServices(List<String> globalAllowedServices) {
            this.globalAllowedServices = globalAllowedServices;
        }
    }

    /**
     * 废弃接口配置
     * @author CH
     * @since 4.0.0
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

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 是否添加警告头部
         *
         * @return 是否添加警告头部的结果
         */
        public boolean isAddWarningHeader() {
            return addWarningHeader;
        }

        /**
         * 设置添加警告头部
         *
         * @param addWarningHeader 添加警告头部
         */
        public void setAddWarningHeader(boolean addWarningHeader) {
            this.addWarningHeader = addWarningHeader;
        }
    }

    /**
     * 灰度发布配置
     * @author CH
     * @since 4.0.0
     */
    public static class GrayConfig {

        /**
         * 是否开启灰度发布功能
         * <p>默认开启（非实验性功能，遵循"默认开关默认开启"约定）</p>
         */
        private boolean enable = true;

        /**
         * 灰度标识请求头名称
         * <p>
         * 请求命中灰度后，会在响应头中添加此头
         * </p>
         */
        @NotNull(message = "灰度标识请求头名称不能为空")
        /** 头部名称 */
        private String headerName = "X-Gray-Hit";

        /**
         * 全局灰度用户白名单
         * <p>
         * 这些用户始终进入灰度版本
         * </p>
         */
        @NotNull(message = "全局灰度用户白名单不能为null，可以为空列表")
        /** 全局用户 */
        private List<String> globalUsers = Collections.emptyList();

        /**
         * 全局灰度IP白名单
         * <p>
         * 这些IP始终进入灰度版本
         * </p>
         */
        @NotNull(message = "全局灰度IP白名单不能为null，可以为空列表")
        /** 全局IPS */
        private List<String> globalIps = Collections.emptyList();

        /**
         * 全局灰度角色白名单
         * <p>
         * 拥有这些角色的用户始终进入灰度版本
         * </p>
         */
        @NotNull(message = "全局灰度角色白名单不能为null，可以为空列表")
        /** 全局角色 */
        private List<String> globalRoles = Collections.emptyList();

        /**
         * 默认灰度百分比
         * <p>
         * 注解未指定百分比时使用此默认值
         * </p>
         */
        @Min(value = 0, message = "灰度百分比不能小于0")
        @Max(value = 100, message = "灰度百分比不能大于100")
        /** 默认percentage */
        private int defaultPercentage = 0;

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置是否启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取头部名称
         *
         * @return 获取头部名称的结果
         */
        public String getHeaderName() {
            return headerName;
        }

        /**
         * 获取全局用户
         *
         * @return 获取全局用户的结果
         */
        public List<String> getGlobalUsers() {
            return globalUsers;
        }

        /**
         * 获取全局ips
         *
         * @return 获取全局ips的结果
         */
        public List<String> getGlobalIps() {
            return globalIps;
        }

        /**
         * 获取全局角色
         *
         * @return 获取全局角色的结果
         */
        public List<String> getGlobalRoles() {
            return globalRoles;
        }

        /**
         * 获取默认percentage
         *
         * @return 获取默认percentage的结果
         */
        public int getDefaultPercentage() {
            return defaultPercentage;
        }
    }
}


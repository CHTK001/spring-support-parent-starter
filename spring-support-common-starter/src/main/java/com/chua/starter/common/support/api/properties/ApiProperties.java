package com.chua.starter.common.support.api.properties;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@Data
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
    private Version version = new Version();

    /**
     * 平台配置
     */
    private Platform platform = new Platform();

    /**
     * 自动统一返回结构
     */
    private boolean uniform = true;
    /**
     * 响应编码配置
     */
    private ResponseEncodeProperties encode = new ResponseEncodeProperties();

    /**
     * 请求解码配置
     */
    private RequestDecodeProperties decode = new RequestDecodeProperties();

    /**
     * SPI 配置
     */
    private SpiConfig spi = new SpiConfig();

    /**
     * Mock 配置
     */
    private MockConfig mock = new MockConfig();

    /**
     * 功能开关配置
     */
    private FeatureConfig feature = new FeatureConfig();

    /**
     * 废弃接口配置
     */
    private DeprecatedConfig deprecated = new DeprecatedConfig();

    /**
     * 内部接口配置
     */
    private InternalConfig internal = new InternalConfig();

    /**
     * 灰度发布配置
     */
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
    @Data
    public static class Version {

        /**
         * 是否开启版本控制
         */
        private boolean enable = false;
    }

    /**
     * 平台类型枚举
     */
    @Getter
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
    @Data
    public static class Platform {

        /**
         * 是否开启平台标识
         */
        private boolean enable = false;

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
    @Data
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
        private String codecType = "sm2";

        /**
         * 白名单（不需要加密的接口路径）
         */
        private List<String> whiteList = Collections.emptyList();
    }

    /**
     * 请求解码配置
     */
    @Data
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
        private String codecType = "sm4";
    }

    /**
     * SPI 配置
     */
    @Data
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
    @Data
    public static class MockConfig {

        /**
         * 是否开启 Mock 功能
         */
        private boolean enable = false;

        /**
         * Mock 生效的环境（逗号分隔）
         */
        private String profiles = "dev,test";
    }

    /**
     * 功能开关配置
     */
    @Data
    public static class FeatureConfig {

        /**
         * 是否开启功能开关
         */
        private boolean enable = false;

        /**
         * 功能开关管理接口路径
         */
        private String path = "/api/features";
    }

    /**
     * 内部接口配置
     */
    @Data
    public static class InternalConfig {

        /**
         * 是否开启内部接口控制
         */
        private boolean enable = false;

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
    @Data
    public static class DeprecatedConfig {

        /**
         * 是否开启废弃接口提示
         */
        private boolean enable = false;

        /**
         * 是否在响应头中添加废弃警告
         */
        private boolean addWarningHeader = true;
    }

    /**
     * 灰度发布配置
     */
    @Data
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
        private String headerName = "X-Gray-Hit";

        /**
         * 全局灰度用户白名单
         * <p>
         * 这些用户始终进入灰度版本
         * </p>
         */
        private List<String> globalUsers = Collections.emptyList();

        /**
         * 全局灰度IP白名单
         * <p>
         * 这些IP始终进入灰度版本
         * </p>
         */
        private List<String> globalIps = Collections.emptyList();

        /**
         * 默认灰度百分比
         * <p>
         * 注解未指定百分比时使用此默认值
         * </p>
         */
        private int defaultPercentage = 0;
    }
}


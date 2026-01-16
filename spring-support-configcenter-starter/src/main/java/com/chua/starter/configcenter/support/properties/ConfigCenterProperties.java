package com.chua.starter.configcenter.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置中心配置属性
 * <p>
 * 支持从远程配置中心（如 Nacos、Apollo、Consul 等）加载配置，
 * 并支持配置热更新功能。
 * </p>
 *
 * @author CH
 * @since 2024/9/9
 */
@Data
@ConfigurationProperties(prefix = ConfigCenterProperties.PRE, ignoreInvalidFields = true)
public class ConfigCenterProperties {

    public static final String PRE = "plugin.config-center";

    /**
     * 是否启用配置中心
     */
    private boolean enable = false;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 5000;

    /**
     * 协议类型
     * <p>
     * 支持：nacos, apollo, consul, zookeeper 等
     * </p>
     */
    private String protocol;

    /**
     * 配置中心地址
     */
    private String address;

    /**
     * 账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 命名空间ID
     * <p>
     * 不填写默认为 spring.profiles.active
     * </p>
     */
    private String namespaceId;

    /**
     * 热更新配置
     */
    private HotReload hotReload = new HotReload();

    /**
     * 热更新配置
     */
    @Data
    public static class HotReload {

        /**
         * 是否启用热更新
         * <p>
         * 启用后，配置中心的配置变更会自动同步到应用
         * </p>
         */
        private boolean enabled = true;

        /**
         * 是否支持 @Value 注解热更新
         * <p>
         * 启用后，使用 @Value 注解的字段也能实现热更新。
         * 注意：需要配合 @RefreshScope 使用，或者使用 @ConfigValue 注解。
         * </p>
         */
        private boolean valueAnnotationEnabled = true;

        /**
         * 是否支持 @ConfigValue 注解热更新
         * <p>
         * 启用后，使用 @ConfigValue 注解的字段支持热更新，
         * 无需 @RefreshScope。
         * </p>
         */
        private boolean configValueAnnotationEnabled = true;

        /**
         * 配置变更后的延迟刷新时间（毫秒）
         * <p>
         * 防止配置频繁变更导致应用抖动
         * </p>
         */
        private long refreshDelayMs = 100;

        /**
         * 是否在配置变更时打印日志
         */
        private boolean logOnChange = true;
    }
}


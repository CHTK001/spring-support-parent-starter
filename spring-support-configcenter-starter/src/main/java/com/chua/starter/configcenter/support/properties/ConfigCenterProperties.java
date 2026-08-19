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
 * @since 2024/9/9
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = ConfigCenterProperties.PRE, ignoreInvalidFields = true)
public class ConfigCenterProperties {

    /** PRE */
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
    
    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    /** 是否启用 */
    public boolean isEnable() {
        return enable;
    }
    
    /** 设置启用 */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    
    /** 获取连接Timeout */
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    /** 设置连接Timeout */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    /** 获取读取Timeout */
    public int getReadTimeout() {
        return readTimeout;
    }
    
    /** 设置读取Timeout */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    /** 获取Protocol */
    public String getProtocol() {
        return protocol;
    }
    
    /** 设置Protocol */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    /** 获取Address */
    public String getAddress() {
        return address;
    }
    
    /** 设置Address */
    public void setAddress(String address) {
        this.address = address;
    }
    
    /** 获取Username */
    public String getUsername() {
        return username;
    }
    
    /** 设置Username */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /** 获取Password */
    public String getPassword() {
        return password;
    }
    
    /** 设置Password */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /** 获取NamespaceId */
    public String getNamespaceId() {
        return namespaceId;
    }
    
    /** 设置NamespaceId */
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    /** 获取HotReload */
    public HotReload getHotReload() {
        return hotReload;
    }
    
    /** 设置HotReload */
    public void setHotReload(HotReload hotReload) {
        this.hotReload = hotReload;
    }

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
        
        // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        /** 是否Enabled */
        public boolean isEnabled() {
            return enabled;
        }
        
        /** 设置Enabled */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /** 是否ValueAnnotationEnabled */
        public boolean isValueAnnotationEnabled() {
            return valueAnnotationEnabled;
        }
        
        /** 设置ValueAnnotationEnabled */
        public void setValueAnnotationEnabled(boolean valueAnnotationEnabled) {
            this.valueAnnotationEnabled = valueAnnotationEnabled;
        }
        
        /** 是否ConfigValueAnnotationEnabled */
        public boolean isConfigValueAnnotationEnabled() {
            return configValueAnnotationEnabled;
        }
        
        /** 设置ConfigValueAnnotationEnabled */
        public void setConfigValueAnnotationEnabled(boolean configValueAnnotationEnabled) {
            this.configValueAnnotationEnabled = configValueAnnotationEnabled;
        }
        
        /** 获取RefreshDelayMs */
        public long getRefreshDelayMs() {
            return refreshDelayMs;
        }
        
        /** 设置RefreshDelayMs */
        public void setRefreshDelayMs(long refreshDelayMs) {
            this.refreshDelayMs = refreshDelayMs;
        }
        
        /** 是否记录日志OnChange */
        public boolean isLogOnChange() {
            return logOnChange;
        }
        
        /** 设置记录日志OnChange */
        public void setLogOnChange(boolean logOnChange) {
            this.logOnChange = logOnChange;
        }
    }
}


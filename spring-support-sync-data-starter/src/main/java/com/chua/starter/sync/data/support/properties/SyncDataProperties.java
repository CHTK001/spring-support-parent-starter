package com.chua.starter.sync.data.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据同步配置属性
 *
 * @author System
 * @since 2026/03/09
 */
@Data
@ConfigurationProperties(prefix = "plugin.sync")
public class SyncDataProperties {

    /**
     * Web认证配置
     */
    private WebAuthConfig webAuth = new WebAuthConfig();

    @Data
    public static class WebAuthConfig {
        /**
         * 认证模式: embedded(嵌入式) 或 none(无认证)
         */
        private String mode = "embedded";
        
        /**
         * 用户名
         */
        private String username = "admin";
        
        /**
         * 密码
         */
        private String password = "admin123";
        
        /**
         * Session超时时间(秒)
         */
        private Integer sessionTimeout = 3600;
        
        /**
         * 是否启用记住我
         */
        private Boolean rememberMeEnabled = true;
        
        /**
         * 记住我持续时间(秒)
         */
        private Integer rememberMeDuration = 604800;
    }
}

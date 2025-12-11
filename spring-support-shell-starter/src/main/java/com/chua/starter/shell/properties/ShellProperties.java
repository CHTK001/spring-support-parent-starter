package com.chua.starter.shell.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Shell配置属性
 * 
 * @author CH
 * @version 4.0.0.32
 */
@Data
@ConfigurationProperties(prefix = "plugin.shell")
public class ShellProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 登录配置
     */
    private Login login = new Login();

    /**
     * 登录配置类
     */
    @Data
    public static class Login {
        
        /**
         * 追踪配置
         */
        private Tracking tracking = new Tracking();
        
        /**
         * 安全配置
         */
        private Security security = new Security();
    }

    /**
     * 登录追踪配置
     */
    @Data
    public static class Tracking {
        
        /**
         * 是否启用登录追踪
         */
        private boolean enabled = true;
        
        /**
         * Redis key前缀
         */
        private String redisPrefix = "shell:login:";
        
        /**
         * 数据保留天数
         */
        private int retentionDays = 30;
        
        /**
         * 是否记录详细信息
         */
        private boolean detailed = true;
    }

    /**
     * 安全配置
     */
    @Data
    public static class Security {
        
        /**
         * 最大失败尝试次数
         */
        private int maxFailedAttempts = 5;
        
        /**
         * 锁定时间（分钟）
         */
        private int lockoutDuration = 30;
        
        /**
         * 是否启用IP白名单
         */
        private boolean ipWhitelistEnabled = false;
        
        /**
         * IP白名单
         */
        private List<String> ipWhitelist = List.of("127.0.0.1", "localhost");
    }
}

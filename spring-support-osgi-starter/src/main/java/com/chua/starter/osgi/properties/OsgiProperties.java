package com.chua.starter.osgi.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSGi 集成配置属性。
 *
 * <p>配置前缀：{@code plugin.osgi}。控制 Felix 框架启动、Gogo shell 远程管理（SSH/Telnet）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.osgi")
public class OsgiProperties {

    /**
     * 是否启用 OSGi 集成（默认开启）。
     */
    private boolean enabled = true;

    /**
     * Felix 框架持久化存储目录。
     */
    private String storage = "./osgi-cache";

    /**
     * Felix 框架初始启动级别。
     */
    private int startLevel = 4;

    /**
     * Gogo shell 远程管理配置。
     */
    private Shell shell = new Shell();

    /**
     * Gogo shell 远程管理配置。
     */
    @Data
    public static class Shell {

        /**
         * 是否启用 Gogo shell 远程管理。
         */
        private boolean enabled = false;

        /**
         * Telnet 监听端口（0 表示不启用 Telnet）。
         */
        private int telnetPort = 6666;

        /**
         * SSH 监听端口（0 表示不启用 SSH）。
         */
        private int sshPort = 0;

        /**
         * SSH/Telnet 绑定地址。
         */
        private String host = "127.0.0.1";

        /**
         * 是否启用 SSH（兼容旧配置，等价于 sshPort &gt; 0）。
         */
        private boolean sshEnabled = false;

        /**
         * SSH 主机密钥文件路径（不配置时自动生成到缓存目录）。
         */
        private String sshKeys;

        /**
         * SSH 默认用户名。
         */
        private String username = "admin";

        /**
         * SSH 默认密码。
         */
        private String password = "admin";
    }
}

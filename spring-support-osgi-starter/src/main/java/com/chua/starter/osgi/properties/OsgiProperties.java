package com.chua.starter.osgi.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * osgi 集成配置属性。
 *
 * <p>配置前缀：{@code plugin.osgi}。控制 Felix 框架启动和远程管理。</p>
 *
 * <p>远程管理策略：shell 只支持一种协议，由 {@code protocol} 决定：
 * <ul>
 *   <li>{@code ssh}（默认）— 通过 Apache SSHD bundle 提供 SSH 远程管理</li>
 *   <li>{@code telnet} — 通过 Gogo shell bundle 提供 Telnet 远程管理（仅当 sshd-osgi 不在 classpath 时回退）</li>
 * </ul></p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.osgi")
public class OsgiProperties {

    /**
     * 是否启用 osgi 集成（默认开启）。
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
     * Felix 框架附加配置（原样透传给 Felix 框架）。
     *
     * <p>用于需要自定义框架行为的场景，例如跨 bundle 共享应用侧接口包：</p>
     * <pre>{@code
     * plugin:
     *   osgi:
     *     framework-properties:
     *       org.osgi.framework.system.packages.extra: com.example:api
     * }</pre>ges.extra: com.example:api
     * }</pre>
     */
    private Map<String, Object> frameworkProperties = new HashMap<>();

    /**
     * 远程管理配置（SSH 或 Telnet，二选一）。
     */
    private Shell shell = new Shell();

    /**
     * 远程管理配置。
     *
     * <p>单一协议原则：shell 同时只启用一种远程连接方式。
     * 默认使用 SSH（端口 8122），安全性更高。</p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Shell {

        /**
         * 是否启用远程管理。
         */
        private boolean enabled = false;

        /**
         * 协议类型：{@code ssh}（默认）或 {@code telnet}。
         */
        private String protocol = "ssh";

        /**
         * 监听端口。
         * <ul>
         *   <li>SSH 协议：默认 8122</li>
         *   <li>Telnet 协议：默认 6666</li>
         * </ul>
         */
        private int port;

        /**
         * 绑定地址。
         */
        private String host = "127.0.0.1";

        /**
         * 认证用户名（SSH 模式）。
         */
        private String username = "";

        /**
         * 认证密码（SSH 模式）。
         */
        private String password = "";
    }
}

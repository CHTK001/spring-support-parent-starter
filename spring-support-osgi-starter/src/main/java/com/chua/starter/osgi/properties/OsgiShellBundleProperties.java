package com.chua.starter.osgi.properties;

import lombok.Data;

/**
 * OSGi Shell Bundle 安装配置属性。
 *
 * <p>控制 Gogo shell 和 Apache SSHD bundle 的自动安装行为。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
public class OsgiShellBundleProperties {

    /**
     * 是否启用 Bundle 管理器（默认启用）。
     */
    private boolean enabled = true;

    /**
     * 是否自动安装 Gogo bundle 套件（runtime/command/shell）。
     * Gogo 提供本地 shell 和 telnet 远程管理（端口 6666）。
     */
    private boolean installGogo = true;

    /**
     * 是否自动安装 Apache SSHD OSGi bundle。
     * SSHD 提供 SSH 远程管理（端口 8122），高版本优先。
     */
    private boolean installSshd = true;

    /**
     * SSHD bundle 监听端口（默认 8122，Gogo SSH remote）。
     */
    private int sshdPort = 8122;

    /**
     * SSHD 绑定地址。
     */
    private String sshdHost = "127.0.0.1";

    /**
     * SSHD 认证用户名。
     */
    private String sshdUsername = "admin";

    /**
     * SSHD 认证密码。
     */
    private String sshdPassword = "";

    /**
     * 是否启用 SFTP 子系统（通过 SSHD bundle）。
     */
    private boolean sftpEnabled = true;

    /**
     * 是否允许 root 登录（仅测试用途，生产环境请勿启用）。
     */
    private boolean allowRootLogin = false;
}

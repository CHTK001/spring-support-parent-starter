package com.chua.starter.ssh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SSH服务端配置属性
 *
 * <p>绑定前缀：{@code plugin.ssh.server}（注意不是 {@code ssh.server}）。
 * 完整 YAML 配置示例（各项默认值与 {@code SshServerService} 的实际读取行为一致，
 * 「仅日志 / 未接入运行期」的项见各字段说明）：
 *
 * <pre>{@code
 * plugin:
 *   ssh:
 *     server:
 *       enabled: true                 # 总开关：是否真正启动 SSHD（缺省 false，仅注册 Bean 不启动）
 *       host: 0.0.0.0                 # 监听地址，默认所有网卡
 *       port: 2222                    # 监听端口
 *       authentication:
 *         password: true              # 密码认证（单账号字面量比对）
 *         public-key: false           # 公钥认证
 *         username: admin             # 唯一允许的用户名
 *         user-password: change-me    # 登录口令，默认空串，必须显式配置
 *         public-key-file: /etc/ssh/authorized_keys   # 公钥认证必配（authorized_keys 格式）
 *       session:
 *         timeout: 3600               # 会话超时（秒，当前仅 debug 日志）
 *         max-sessions: 10            # 最大会话数（当前仅 debug 日志）
 *         idle-timeout: 1800          # 空闲超时（秒，当前未生效）
 *       file-transfer:
 *         sftp-enabled: true          # SFTP 子系统开关
 *         root-directory: /data/sftp  # SFTP 虚拟根目录
 *       security:                     # 以下 security.* 当前均未接入运行期
 *         allowed-ips:
 *           - 127.0.0.1
 *         denied-ips: []
 *         max-login-attempts: 3
 *         lockout-duration: 300
 *         host-key-verification: true
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = "plugin.ssh.server")
public class SshServerProperties {
    /**
     * 是否启用（历史保留字段）。
     *
     * <p>当前实现（{@code SshServerService}）不读取该字段，配置不产生任何效果；
     * 真正控制 SSHD 启停的开关是 {@code enabled}。
     *
     * <p>默认值：{@code false}。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       enable: false   # 不生效，建议省略，改用 enabled
     * }</pre>
     */
    private boolean enable = false;


    /**
     * 是否启用 SSH 服务端（真正启动 SSHD 的总开关）。
     *
     * <p>双重作用：
     * <ul>
     *   <li>自动装配条件键：{@code @ConditionalOnProperty(..., havingValue = "true", matchIfMissing = true)}——
     *       该键缺省时自动配置仍通过、Bean 照常注册；显式写 {@code enabled=false} 时整个自动配置被跳过、Bean 不存在；</li>
     *   <li>运行期开关：{@code SshServerService#afterPropertiesSet} 仅在该值为 {@code true} 时启动服务端。</li>
     * </ul>
     *
     * <p>默认值：{@code false}（不启动服务端，仅打印「[SSH] 服务状态 [关闭]」，不占用端口）。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       enabled: true   # 必须显式设为 true 才会监听端口
     * }</pre>
     */
    private boolean enabled = false;

    /**
     * SSH服务端监听地址。
     *
     * <p>默认值：{@code 0.0.0.0}（监听本机所有网卡；生产环境建议收敛到内网地址）。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       host: 0.0.0.0
     * }</pre>
     */
    private String host = "0.0.0.0";

    /**
     * SSH服务端监听端口。
     *
     * <p>默认值：{@code 2222}。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       port: 2222
     * }</pre>
     */
    // [P3C A20 豁免]
    private int port = 2222;

    /**
     * 认证配置组。
     *
     * <p>默认值：{@code new Authentication()}（密码认证开启、公钥认证关闭、用户名 {@code admin}、口令空串）。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       authentication:
     *         password: true
     *         public-key: false
     *         username: admin
     *         user-password: change-me
     *         public-key-file: /etc/ssh/authorized_keys
     * }</pre>
     */
    private Authentication authentication = new Authentication();

    /**
     * 会话配置组。
     *
     * <p>默认值：{@code new Session()}（timeout=3600s、maxSessions=10、idleTimeout=1800s）。
     * 注意：当前实现仅打印 debug 日志，未把会话限制下发给 SSHD。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       session:
     *         timeout: 3600
     *         max-sessions: 10
     *         idle-timeout: 1800
     * }</pre>
     */
    private Session session = new Session();

    /**
     * 文件传输配置组。
     *
     * <p>默认值：{@code new FileTransfer()}（SFTP 开启、虚拟根目录 {@code /tmp}、上限 100MB）。
     * 注意：当前实现只装配 SFTP 子系统，SCP 未注册。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       file-transfer:
     *         sftp-enabled: true
     *         root-directory: /data/sftp
     * }</pre>
     */
    private FileTransfer fileTransfer = new FileTransfer();

    /**
     * 安全配置组。
     *
     * <p>默认值：{@code new Security()}（IP 白/黑名单为空、最大登录尝试 3 次、锁定 300s、主机密钥验证开启）。
     * 注意：当前实现未读取本组任何字段，这些配置不产生运行期效果。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       security:
     *         allowed-ips:
     *           - 127.0.0.1
     *         denied-ips: []
     *         max-login-attempts: 3
     *         lockout-duration: 300
     *         host-key-verification: true
     * }</pre>
     */
    private Security security = new Security();

    /**
     * 认证配置。
     *
     * <p>对应 YAML 前缀 {@code plugin.ssh.server.authentication}。
     * 当前实现支持两种认证：密码认证（单账号字面量比对）与公钥认证（authorized_keys 文件）。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       authentication:
     *         password: true
     *         public-key: false
     *         username: admin
     *         user-password: change-me
     *         public-key-file: /etc/ssh/authorized_keys
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Authentication {
        /**
         * 是否启用密码认证。
         *
         * <p>开启后注册 SSHD {@code PasswordAuthenticator}，按 {@code username} + {@code userPassword}
         * 字面量比对（无用户库、无加密存储）。默认值：{@code true}。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         password: true
         * }</pre>
         */
        private boolean password = true;

        /**
         * 是否启用公钥认证。
         *
         * <p>开启后按 {@code publicKeyFile} 加载 authorized_keys 格式的授权公钥；
         * 若 {@code publicKeyFile} 为空，则回落为 {@code RejectAllPublickeyAuthenticator}
         * （拒绝所有公钥登录）并打印 WARN。默认值：{@code false}。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         public-key: false
         * }</pre>
         */
        private boolean publicKey = false;

        /**
         * 默认用户名（唯一允许登录的账号）。
         *
         * <p>密码认证时与客户端提交的用户名做字面量比对，不匹配即拒绝。默认值：{@code admin}。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         username: admin
         * }</pre>
         */
        private String username = "admin";

        /**
         * 默认密码（通过配置文件覆盖）。
         *
         * <p>默认值：空串。为空时的实际行为（与实现一致）：密码按字面量比对，
         * 用户名匹配且客户端提交空口令即可通过认证——因此启用密码认证时必须配置非空强口令，
         * 建议经环境变量 / 配置中心注入，不要把真实口令写入仓库。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         user-password: change-me
         * }</pre>
         */
        private String userPassword = "";

        /**
         * 公钥文件路径（authorized_keys 格式，一行一个公钥）。
         *
         * <p>默认值：{@code null}。为空且 {@code public-key=true} 时回落为
         * {@code RejectAllPublickeyAuthenticator}（公钥登录全部拒绝）——即「公钥认证开启但等于没开」，
         * 启用公钥认证时必须配置该文件路径。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         public-key-file: /etc/ssh/authorized_keys
         * }</pre>
         */
        private String publicKeyFile;

        /**
         * 私钥文件路径（历史保留字段）。
         *
         * <p>默认值：{@code null}。当前实现（{@code SshServerService}）不读取该字段，
         * 配置不产生任何效果；服务端主机密钥由 {@code SimpleGeneratorHostKeyProvider} 自动生成。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       authentication:
         *         private-key-file: /etc/ssh/id_rsa   # 当前未使用
         * }</pre>
         */
        private String privateKeyFile;
        
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        /**
         * 是否密码
         *
         * @return 是否密码的结果
         */
        public boolean isPassword() {
            return password;
        }
        
        /**
         * 设置密码
         *
         * @param password 密码
         */
        public void setPassword(boolean password) {
            this.password = password;
        }
        
        /**
         * 获取用户名
         *
         * @return 获取用户名的结果
         */
        public String getUsername() {
            return username;
        }
        
        /**
         * 设置用户名
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }
        
        /**
         * 获取用户密码
         *
         * @return 获取用户密码的结果
         */
        public String getUserPassword() {
            return userPassword;
        }
        
        /**
         * 设置用户密码
         *
         * @param userPassword 用户密码
         */
        public void setUserPassword(String userPassword) {
            this.userPassword = userPassword;
        }
        
        /**
         * 是否公共Key
         *
         * @return 是否公共Key的结果
         */
        public boolean isPublicKey() {
            return publicKey;
        }
        
        /**
         * 设置公共Key
         *
         * @param publicKey 公共Key
         */
        public void setPublicKey(boolean publicKey) {
            this.publicKey = publicKey;
        }
    }

    /**
     * 会话配置。
     *
     * <p>对应 YAML 前缀 {@code plugin.ssh.server.session}。
     * 注意：当前实现只在 debug 日志中记录这些值，未真正下发给 SSHD（预留配置）。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       session:
     *         timeout: 3600
     *         max-sessions: 10
     *         idle-timeout: 1800
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Session {
        /**
         * 会话超时时间（秒）。
         *
         * <p>默认值：{@code 3600}（1 小时）。当前实现仅在 debug 日志打印，不真正生效。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       session:
         *         timeout: 3600
         * }</pre>
         */
        // [P3C A20 豁免]
        private long timeout = 3600;

        /**
         * 最大会话数。
         *
         * <p>默认值：{@code 10}。当前实现仅在 debug 日志打印，不限制并发连接。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       session:
         *         max-sessions: 10
         * }</pre>
         */
        // [P3C A20 豁免]
        private int maxSessions = 10;

        /**
         * 空闲超时时间（秒）。
         *
         * <p>默认值：{@code 1800}（30 分钟）。服务端设置空闲超时的逻辑已被注释，
         * 当前实现未读取该字段，配置不生效。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       session:
         *         idle-timeout: 1800   # 当前未生效
         * }</pre>
         */
        // [P3C A20 豁免]
        private long idleTimeout = 1800;
        
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        /**
         * 获取超时
         *
         * @return 获取超时的结果
         */
        public long getTimeout() {
            return timeout;
        }
        
        /**
         * 设置超时
         *
         * @param timeout 超时
         */
        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
        
        /**
         * 获取最大值会话
         *
         * @return 获取最大会话的结果
         */
        public int getMaxSessions() {
            return maxSessions;
        }
        
        /**
         * 设置最大值会话
         *
         * @param maxSessions 最大会话
         */
        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }
    }

    /**
     * 文件传输配置。
     *
     * <p>对应 YAML 前缀 {@code plugin.ssh.server.file-transfer}。
     * 当前实现只装配 SFTP 子系统（{@code SftpSubsystemFactory} + 虚拟根目录文件系统），
     * SCP 命令工厂未注册。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       file-transfer:
     *         sftp-enabled: true
     *         root-directory: /data/sftp
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class FileTransfer {
        /**
         * 是否启用SCP。
         *
         * <p>默认值：{@code true}，但当前实现未注册 SCP 命令工厂，该开关不产生任何效果；
         * 文件传输实际只支持 SFTP 协议。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       file-transfer:
         *         scp-enabled: true   # 当前未生效
         * }</pre>
         */
        private boolean scpEnabled = true;

        /**
         * 是否启用SFTP。
         *
         * <p>开启后注册 {@code SftpSubsystemFactory} 并挂载 {@code VirtualFileSystemFactory}
         * 虚拟文件系统（根目录见 {@code rootDirectory}）。默认值：{@code true}。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       file-transfer:
         *         sftp-enabled: true
         * }</pre>
         */
        private boolean sftpEnabled = true;

        /**
         * 文件传输根目录（SFTP 虚拟文件系统的根，{@code VirtualFileSystemFactory}）。
         *
         * <p>登录用户只能在该目录内活动。默认值：{@code /tmp}；
         * Windows 下会解析为当前盘符根目录下的 {@code \tmp}，目录不存在时 SFTP 操作失败，
         * 建议显式配置平台绝对路径（如 {@code D:/sftp-root}）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       file-transfer:
         *         root-directory: /data/sftp
         * }</pre>
         */
        private String rootDirectory = "/tmp";

        /**
         * 最大文件大小（字节）。
         *
         * <p>默认值：{@code 104857600}（100MB）。当前实现无大小校验逻辑，该配置不生效（预留）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       file-transfer:
         *         max-file-size: 104857600   # 100MB，当前未生效
         * }</pre>
         */
        // [P3C A20 豁免]
        private long maxFileSize = 100 * 1024 * 1024; // 100MB
        
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        /**
         * 是否sftp已启用
         *
         * @return 是否sftp已启用的结果
         */
        public boolean isSftpEnabled() {
            return sftpEnabled;
        }
        
        /**
         * 设置sftp已启用
         *
         * @param sftpEnabled sftp已启用
         */
        public void setSftpEnabled(boolean sftpEnabled) {
            this.sftpEnabled = sftpEnabled;
        }
        
        /**
         * 获取根目录
         *
         * @return 获取根目录的结果
         */
        public String getRootDirectory() {
            return rootDirectory;
        }
        
        /**
         * 设置根目录
         *
         * @param rootDirectory 根目录
         */
        public void setRootDirectory(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }
    }

    /**
     * 安全配置。
     *
     * <p>对应 YAML 前缀 {@code plugin.ssh.server.security}。
     * 注意：当前实现未读取本组任何字段（IP 过滤、登录失败锁定、主机密钥验证均未接入运行期），
     * 实际访问控制需依赖监听地址、防火墙与 OS 权限。
     *
     * <pre>{@code
     * plugin:
     *   ssh:
     *     server:
     *       security:
     *         allowed-ips:
     *           - 127.0.0.1
     *         denied-ips: []
     *         max-login-attempts: 3
     *         lockout-duration: 300
     *         host-key-verification: true
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Security {
        /**
         * 允许的IP地址列表。
         *
         * <p>默认值：空列表。当前实现未读取该字段，配置不生效（预留）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       security:
         *         allowed-ips:
         *           - 127.0.0.1
         *           - 192.168.1.0/24
         * }</pre>
         */
        private List<String> allowedIps = new ArrayList<>();

        /**
         * 禁止的IP地址列表。
         *
         * <p>默认值：空列表。当前实现未读取该字段，配置不生效（预留）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       security:
         *         denied-ips:
         *           - 10.0.0.0/8
         * }</pre>
         */
        private List<String> deniedIps = new ArrayList<>();

        /**
         * 最大登录尝试次数。
         *
         * <p>默认值：{@code 3}。当前实现未读取该字段，无登录失败计数与锁定逻辑（预留）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       security:
         *         max-login-attempts: 3
         * }</pre>
         */
        private int maxLoginAttempts = 3;

        /**
         * 登录失败锁定时间（秒）。
         *
         * <p>默认值：{@code 300}（5 分钟）。当前实现未读取该字段，配置不生效（预留）。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       security:
         *         lockout-duration: 300
         * }</pre>
         */
        // [P3C A20 豁免]
        private long lockoutDuration = 300;

        /**
         * 是否启用主机密钥验证。
         *
         * <p>默认值：{@code true}。当前实现未读取该字段，配置不生效（预留）；
         * 服务端主机密钥固定由 {@code SimpleGeneratorHostKeyProvider} 生成到工作目录下的 {@code hostkey.ser}。
         *
         * <pre>{@code
         * plugin:
         *   ssh:
         *     server:
         *       security:
         *         host-key-verification: true   # 当前未生效
         * }</pre>
         */
        private boolean hostKeyVerification = true;
    }
    
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    /**
     * 是否已启用
     *
     * @return 是否已启用的结果
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置已启用
     *
     * @param enabled 已启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 获取主机
     *
     * @return 获取主机的结果
     */
    public String getHost() {
        return host;
    }
    
    /**
     * 设置主机
     *
     * @param host 主机
     */
    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * 获取端口
     *
     * @return 获取端口的结果
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 设置端口
     *
     * @param port 端口
     */
    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * 获取认证
     *
     * @return 获取认证的结果
     */
    public Authentication getAuthentication() {
        return authentication;
    }
    
    /**
     * 设置认证
     *
     * @param authentication 认证
     */
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
    
    /**
     * 获取会话
     *
     * @return 获取会话的结果
     */
    public Session getSession() {
        return session;
    }
    
    /**
     * 设置会话
     *
     * @param session 会话
     */
    public void setSession(Session session) {
        this.session = session;
    }
    
    /**
     * 获取文件调动
     *
     * @return 获取文件调动的结果
     */
    public FileTransfer getFileTransfer() {
        return fileTransfer;
    }
    
    /**
     * 设置文件调动
     *
     * @param fileTransfer 文件调动
     */
    public void setFileTransfer(FileTransfer fileTransfer) {
        this.fileTransfer = fileTransfer;
    }
    
    /**
     * 获取安全性
     *
     * @return 获取安全性的结果
     */
    public Security getSecurity() {
        return security;
    }
    
    /**
     * 设置安全性
     *
     * @param security 安全性
     */
    public void setSecurity(Security security) {
        this.security = security;
    }
}

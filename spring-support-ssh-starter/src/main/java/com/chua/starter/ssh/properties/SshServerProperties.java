package com.chua.starter.ssh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SSH服务端配置属性
 * 
 * @版本 4.0.0.32
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = "plugin.ssh.server")
public class SshServerProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 是否启用SSH服务端
     */
    private boolean enabled = false;

    /**
     * SSH服务端监听地址
     */
    private String host = "0.0.0.0";

    /**
     * SSH服务端端口
     */
    private int port = 2222;

    /**
     * 认证配置
     */
    private Authentication authentication = new Authentication();

    /**
     * 会话配置
     */
    private Session session = new Session();

    /**
     * 文件传输配置
     */
    private FileTransfer fileTransfer = new FileTransfer();

    /**
     * 安全配置
     */
    private Security security = new Security();

    /**
     * 认证配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Authentication {
        /**
         * 是否启用密码认证
         */
        private boolean password = true;

        /**
         * 是否启用公钥认证
         */
        private boolean publicKey = false;

        /**
         * 默认用户名
         */
        private String username = "admin";

        /**
         * 默认密码（通过配置文件覆盖）
         */
        private String userPassword = "";

        /**
         * 公钥文件路径
         */
        private String publicKeyFile;

        /**
         * 私钥文件路径
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
     * 会话配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Session {
        /**
         * 会话超时时间（秒）
         */
        private long timeout = 3600;

        /**
         * 最大会话数
         */
        private int maxSessions = 10;

        /**
         * 空闲超时时间（秒）
         */
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
     * 文件传输配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class FileTransfer {
        /**
         * 是否启用SCP
         */
        private boolean scpEnabled = true;

        /**
         * 是否启用SFTP
         */
        private boolean sftpEnabled = true;

        /**
         * 文件传输根目录
         */
        private String rootDirectory = "/tmp";

        /**
         * 最大文件大小（字节）
         */
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
     * 安全配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Security {
        /**
         * 允许的IP地址列表
         */
        private List<String> allowedIps = new ArrayList<>();

        /**
         * 禁止的IP地址列表
         */
        private List<String> deniedIps = new ArrayList<>();

        /**
         * 最大登录尝试次数
         */
        private int maxLoginAttempts = 3;

        /**
         * 登录失败锁定时间（秒）
         */
        private long lockoutDuration = 300;

        /**
         * 是否启用主机密钥验证
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

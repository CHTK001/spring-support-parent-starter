package com.chua.starter.ssh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SSH服务端配置属性
 * 
 * @author CH
 * @version 4.0.0.32
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
         * 默认密码
         */
        private String userPassword = "admin123";

        /**
         * 公钥文件路径
         */
        private String publicKeyFile;

        /**
         * 私钥文件路径
         */
        private String privateKeyFile;
        
        // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        public boolean isPassword() {
            return password;
        }
        
        public void setPassword(boolean password) {
            this.password = password;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getUserPassword() {
            return userPassword;
        }
        
        public void setUserPassword(String userPassword) {
            this.userPassword = userPassword;
        }
        
        public boolean isPublicKey() {
            return publicKey;
        }
        
        public void setPublicKey(boolean publicKey) {
            this.publicKey = publicKey;
        }
    }

    /**
     * 会话配置
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
        
        // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        public long getTimeout() {
            return timeout;
        }
        
        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
        
        public int getMaxSessions() {
            return maxSessions;
        }
        
        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }
    }

    /**
     * 文件传输配置
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
        
        // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
        public boolean isSftpEnabled() {
            return sftpEnabled;
        }
        
        public void setSftpEnabled(boolean sftpEnabled) {
            this.sftpEnabled = sftpEnabled;
        }
        
        public String getRootDirectory() {
            return rootDirectory;
        }
        
        public void setRootDirectory(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }
    }

    /**
     * 安全配置
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
    
    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public Authentication getAuthentication() {
        return authentication;
    }
    
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
    
    public Session getSession() {
        return session;
    }
    
    public void setSession(Session session) {
        this.session = session;
    }
    
    public FileTransfer getFileTransfer() {
        return fileTransfer;
    }
    
    public void setFileTransfer(FileTransfer fileTransfer) {
        this.fileTransfer = fileTransfer;
    }
    
    public Security getSecurity() {
        return security;
    }
    
    public void setSecurity(Security security) {
        this.security = security;
    }
}

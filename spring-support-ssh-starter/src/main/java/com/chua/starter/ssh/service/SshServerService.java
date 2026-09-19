package com.chua.starter.ssh.service;

import com.chua.starter.ssh.properties.SshServerProperties;
import lombok.extern.slf4j.Slf4j;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * SSH服务端服务
 * 
 * @版本 4.0.0.32
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class SshServerService implements InitializingBean, DisposableBean {

    /** 属性 */
    private final SshServerProperties properties;
    /** SSH服务器 */
    private SshServer sshServer;

    /**
     * 创建 ssh服务端服务 实例
     * @param properties 属性
     */
    public SshServerService(SshServerProperties properties) {
        this.properties = properties;
    }

    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() throws Exception {
        if (properties.isEnabled()) {
            /**
             * 启动ssh服务端。
             */
            startSshServer();
        } else {
            log.info("[SSH] 服务状态 [{}]", disabled());
        }
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        /**
         * 停止ssh服务端。
         */
        stopSshServer();
    }

    /**
     * 启动SSH服务端
     */
    private void startSshServer() throws IOException {
        sshServer = SshServer.setUpDefaultServer();
        
        // 设置监听地址和端口
        sshServer.setHost(properties.getHost());
        sshServer.setPort(properties.getPort());

        // 启用X11转发（新版本方式）
        sshServer.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        // 设置主机密钥
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("hostkey.ser")));

        // 配置认证
        /**
         * configure认证。
         */
        configureAuthentication();

        // 配置Shell命令工厂
        sshServer.setShellFactory(new ProcessShellFactory());

        // 配置文件传输
        /**
         * configure文件调动。
         */
        configureFileTransfer();

        // 配置会话
        /**
         * configure会话。
         */
        configureSession();

        // 启动服务器
        sshServer.start();
        
        log.info("[SSH] 服务已启动 {} [{}]", address(properties.getHost(), properties.getPort()), enabled());
    }

    /**
     * 停止SSH服务端
     */
    private void stopSshServer() throws IOException {
        if (sshServer != null && !sshServer.isClosed()) {
            sshServer.stop();
            log.info("[SSH] 服务已停止");
        }
    }

    /**
     * 配置认证
     */
    private void configureAuthentication() {
        SshServerProperties.Authentication auth = properties.getAuthentication();
        
        if (auth.isPassword()) {
            // 配置密码认证
            sshServer.setPasswordAuthenticator(new PasswordAuthenticator() {
                @Override
                /** Authenticate */
                public boolean authenticate(String username, String password, ServerSession session) {
                    // 简单的用户名密码验证
                    boolean authenticated = auth.getUsername().equals(username) && 
                                          auth.getUserPassword().equals(password);
                    
                    if (authenticated) {
                        log.info("[SSH] 用户 {} 认证 {} - IP: {}", username, success(), session.getClientAddress());
                    } else {
                        log.warn("[SSH] 用户 {} 认证 {} - IP: {}", username, failed(), session.getClientAddress());
                    }
                    
                    return authenticated;
                }
            });
        }

 // 公钥认证：从 公共Key文件 加载授权公钥
        if (auth.isPublicKey()) {
            if (StringUtils.hasText(auth.getPublicKeyFile())) {
                sshServer.setPublickeyAuthenticator(
                        new AuthorizedKeysAuthenticator(Paths.get(auth.getPublicKeyFile())));
                log.info("[SSH] 公钥认证已启用 [{}]", highlight(auth.getPublicKeyFile()));
            } else {
                log.warn("[SSH] 公钥认证已开启但未配置 public-key-file, 使用拒绝策略");
                sshServer.setPublickeyAuthenticator(RejectAllPublickeyAuthenticator.INSTANCE);
            }
        }
    }

    /**
     * 配置文件传输
     */
    private void configureFileTransfer() {
        SshServerProperties.FileTransfer fileTransfer = properties.getFileTransfer();
        
        if (fileTransfer.isSftpEnabled()) {
            // 启用SFTP子系统
            sshServer.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
            
            // 设置文件系统工厂
            sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(fileTransfer.getRootDirectory())));
            
            log.info("[SSH] SFTP文件传输 [{}] 根目录: {}", enabled(), highlight(fileTransfer.getRootDirectory()));
        }
    }

    /**
     * 配置会话
     */
    private void configureSession() {
        SshServerProperties.Session session = properties.getSession();
        
        // 设置会话超时
 // ssh服务端.获取属性().放入(ssh服务端.空闲_超时, 会话.获取空闲超时() * 1000);

        log.debug("[SSH] 会话配置 - 超时: {}s, 最大会话: {}", session.getTimeout(), session.getMaxSessions());
    }

    /**
     * 获取SSH服务端状态
     * @return 是否running的结果
     */
    public boolean isRunning() {
        return sshServer != null && sshServer.isStarted() && !sshServer.isClosed();
    }

    /**
     * 获取当前连接数
     * @return 获取活跃会话数量的结果
     */
    public int getActiveSessionCount() {
        return sshServer != null ? sshServer.getActiveSessions().size() : 0;
    }
}

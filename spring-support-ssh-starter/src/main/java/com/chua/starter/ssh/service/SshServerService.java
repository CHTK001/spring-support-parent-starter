package com.chua.starter.ssh.service;

import com.chua.starter.ssh.properties.SshServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * SSH服务端服务
 * 
 * @author CH
 * @version 4.0.0.32
 */
@Slf4j
public class SshServerService implements InitializingBean, DisposableBean {

    private final SshServerProperties properties;
    private SshServer sshServer;

    public SshServerService(SshServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (properties.isEnabled()) {
            startSshServer();
        }
    }

    @Override
    public void destroy() throws Exception {
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
        configureAuthentication();

        // 配置Shell命令工厂
        sshServer.setShellFactory(new ProcessShellFactory());

        // 配置文件传输
        configureFileTransfer();

        // 配置会话
        configureSession();

        // 启动服务器
        sshServer.start();
        
        log.info("SSH服务端已启动，监听地址: {}:{}", properties.getHost(), properties.getPort());
    }

    /**
     * 停止SSH服务端
     */
    private void stopSshServer() throws IOException {
        if (sshServer != null && !sshServer.isClosed()) {
            sshServer.stop();
            log.info("SSH服务端已停止");
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
                public boolean authenticate(String username, String password, ServerSession session) {
                    // 简单的用户名密码验证
                    boolean authenticated = auth.getUsername().equals(username) && 
                                          auth.getUserPassword().equals(password);
                    
                    if (authenticated) {
                        log.info("用户 {} 通过密码认证成功，来源IP: {}", username, session.getClientAddress());
                    } else {
                        log.warn("用户 {} 密码认证失败，来源IP: {}", username, session.getClientAddress());
                    }
                    
                    return authenticated;
                }
            });
        }

        // TODO: 实现公钥认证
        if (auth.isPublicKey()) {
            log.info("公钥认证已启用（待实现）");
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
            
            log.info("SFTP文件传输已启用，根目录: {}", fileTransfer.getRootDirectory());
        }
    }

    /**
     * 配置会话
     */
    private void configureSession() {
        SshServerProperties.Session session = properties.getSession();
        
        // 设置会话超时
//        sshServer.getProperties().put(SshServer.IDLE_TIMEOUT, session.getIdleTimeout() * 1000);

        log.info("会话配置完成，超时时间: {}秒，最大会话数: {}", session.getTimeout(), session.getMaxSessions());
    }

    /**
     * 获取SSH服务端状态
     */
    public boolean isRunning() {
        return sshServer != null && sshServer.isStarted() && !sshServer.isClosed();
    }

    /**
     * 获取当前连接数
     */
    public int getActiveSessionCount() {
        return sshServer != null ? sshServer.getActiveSessions().size() : 0;
    }
}

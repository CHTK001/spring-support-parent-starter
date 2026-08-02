package com.chua.starter.osgi.server;

import com.chua.common.support.osgi.OsgiLauncher;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ShellFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.function.Supplier;

/**
 * 基于 Apache SSHD 的 SSH 服务器。
 * <p>
 * 为 Felix OSGi Gogo shell 提供 SSH 远程管理能力。
 * 当前仅支持 command 模式（非交互式 shell），
 * 每个连接执行一条命令并返回结果。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OsgiSshServer {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String keysFile;
    private final Supplier<OsgiLauncher> launcherSupplier;
    private SshServer sshServer;

    public OsgiSshServer(String host, int port, String username, String password,
                         String keysFile, Supplier<OsgiLauncher> launcherSupplier) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.keysFile = keysFile;
        this.launcherSupplier = launcherSupplier;
    }

    /**
     * 启动 SSH 服务器。
     */
    public void start() {
        if (port <= 0) {
            log.warn("[OSGi-SSH] SSH 端口未配置（<=0），跳过 SSH 启动");
            return;
        }
        try {
            sshServer = SshServer.setUpDefaultServer();
            sshServer.setHost(host);
            sshServer.setPort(port);
            sshServer.setKeyPairProvider(createKeyPairProvider());
            sshServer.setPasswordAuthenticator(createPasswordAuthenticator());
            sshServer.setPublickeyAuthenticator(createPublickeyAuthenticator());
            sshServer.setShellFactory(createShellFactory());
            sshServer.start();
            log.info("[OSGi-SSH] SSH 服务已启动 [{}:{}]", host, port);
        } catch (IOException e) {
            log.error("[OSGi-SSH] SSH 服务启动失败", e);
        }
    }

    /**
     * 停止 SSH 服务器。
     */
    public void stop() {
        if (sshServer != null && sshServer.isOpen()) {
            try {
                sshServer.stop();
                log.info("[OSGi-SSH] SSH 服务已停止");
            } catch (IOException e) {
                log.warn("[OSGi-SSH] SSH 服务停止异常", e);
            }
        }
    }

    private org.apache.sshd.common.keyprovider.KeyPairProvider createKeyPairProvider() {
        if (keysFile != null && !keysFile.isEmpty()) {
            Path path = Paths.get(keysFile);
            if (path.toFile().exists()) {
                return new FileKeyPairProvider(path);
            }
        }
        Path cacheDir = Paths.get(System.getProperty("user.dir"), ".workbuddy", "ssh-keys");
        cacheDir.toFile().mkdirs();
        Path keyFile = cacheDir.resolve("hostkey.ser");
        return new SimpleHostKeyProvider(keyFile);
    }

    private PasswordAuthenticator createPasswordAuthenticator() {
        return (session, user, pwd) -> {
            boolean ok = username.equals(user) && password.equals(pwd);
            if (!ok) {
                log.warn("[OSGi-SSH] SSH 认证失败 [user={}]", user);
            }
            return ok;
        };
    }

    private PublickeyAuthenticator createPublickeyAuthenticator() {
        return (session, key) -> {
            log.info("[OSGi-SSH] 公钥认证请求 [algorithm={}]", key.getAlgorithm());
            return true;
        };
    }

    private ShellFactory createShellFactory() {
        return session -> {
            OsgiLauncher launcher = launcherSupplier.get();
            return new OsgiCommandShell(launcher);
        };
    }
}

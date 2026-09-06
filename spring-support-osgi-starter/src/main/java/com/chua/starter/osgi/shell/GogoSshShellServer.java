package com.chua.starter.osgi.shell;

import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

/**
 * Gogo SSH shell 服务器：补齐 {@code plugin.osgi.shell.protocol=ssh} 的缺失装配——
 * 以 MINA SSHD 2.x API 直连启动 SSH 服务器，将 SSH 通道会话接线到 Felix 框架内的
 * Gogo {@code CommandProcessor}（经框架 BundleContext 按名查找服务 + 反射调用——
 * bundle 与应用类加载器的类身份不同，不能用 Class 对象直查），实现 bundle 管理命令
 * （bundle:install/list/start/uninstall 等）的 SSH 远程操作。
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class GogoSshShellServer {

    private static final String COMMAND_PROCESSOR_NAME = "org.apache.felix.service.command.CommandProcessor";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final BundleContext bundleContext;
    private SshServer sshServer;

    public GogoSshShellServer(String host, int port, String username, String password, BundleContext bundleContext) {
        this.host = host == null ? "127.0.0.1" : host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.bundleContext = bundleContext;
    }

    /** 启动 SSH 服务器（MINA SSHD 2.x） */
    public void start() throws IOException {
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost(host);
        sshServer.setPort(port);
        sshServer.setKeyPairProvider(
                new org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider(
                        java.nio.file.Paths.get("target", "osgi-hostkey.ser")));
        sshServer.setPasswordAuthenticator(
                (user, pass, session) -> username.equals(user) && password.equals(pass));
        sshServer.setShellFactory((ShellFactory) (ChannelSession channel) -> new GogoShellCommand());
        sshServer.start();
        log.info("[OSGi] Gogo SSH shell 已监听: {}:{}", host, port);
    }

    /** 停止 SSH 服务器 */
    public void stop() {
        if (sshServer != null) {
            try {
                sshServer.stop();
            } catch (Exception e) {
                log.warn("[OSGi] SSH shell 停止异常", e);
            }
        }
    }

    /** 按名查找 Gogo CommandProcessor 服务（规避 bundle/应用类加载器类身份差异） */
    private Object lookupCommandProcessor() {
        if (bundleContext == null) {
            return null;
        }
        // 注：getAllServiceReferences(null,null) 可见该服务，而单数 getServiceReference(name)
        // 在 Felix 7 下偶发不匹配——统一用全量 API + objectClass 手动匹配（实证可靠）
        try {
            org.osgi.framework.ServiceReference<?>[] all =
                    bundleContext.getAllServiceReferences(null, null);
            if (all != null) {
                for (org.osgi.framework.ServiceReference<?> ref : all) {
                    Object objectClass = ref.getProperty("objectClass");
                    if (objectClass instanceof String[]
                            && java.util.Arrays.asList((String[]) objectClass).contains(COMMAND_PROCESSOR_NAME)) {
                        return bundleContext.getService(ref);
                    }
                }
            }
            log.warn("[OSGi] Gogo CommandProcessor 服务未注册（框架服务 {} 项，均不含 {}）",
                    all == null ? 0 : all.length, COMMAND_PROCESSOR_NAME);
        } catch (Exception e) {
            log.warn("[OSGi] CommandProcessor 服务查找异常: {}", e.getMessage());
        }
        return null;
    }

    /** SSH 通道会话 -> Gogo CommandProcessor 会话接线 */
    private class GogoShellCommand implements Command {

        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            Object processor = lookupCommandProcessor();
            if (processor == null) {
                log.warn("[OSGi] Gogo CommandProcessor 服务不可用（gogo.runtime bundle 未激活？）");
                if (callback != null) {
                    callback.onExit(1);
                }
                return;
            }
            try {
                Method createSession = processor.getClass().getMethod(
                        "createSession", InputStream.class, OutputStream.class, OutputStream.class);
                Object session = createSession.invoke(processor, in, out, err);
                PrintStream console = (PrintStream) session.getClass().getMethod("getConsole").invoke(session);
                final Object sessionRef = session;
                Thread thread = new Thread(() -> runLoop(sessionRef, console), "gogo-ssh-shell");
                thread.setDaemon(true);
                thread.start();
            } catch (Exception e) {
                log.warn("[OSGi] Gogo 会话创建失败", e);
                if (callback != null) {
                    callback.onExit(1);
                }
            }
        }

        @Override
        public void destroy(ChannelSession channel) {
        }

        /** 读取行 -> 回显 -> session.execute，输出提示符 */
        private void runLoop(Object session, PrintStream console) {
            try {
                Method execute = session.getClass().getMethod("execute", CharSequence.class);
                Method close = session.getClass().getMethod("close");
                StringBuilder line = new StringBuilder();
                int c;
                console.print("g! ");
                console.flush();
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        String raw = line.toString();
                        String command = raw.trim();
                        if (!command.isEmpty()) {
                            console.println(raw);
                            console.flush();
                            log.info("[OSGi] Gogo 命令执行: {}", command);
                            execute.invoke(session, command);
                            log.info("[OSGi] Gogo 命令执行完成: {}", command);
                            console.print("g! ");
                            console.flush();
                        }
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append((char) c);
                    }
                }
                close.invoke(session);
                if (callback != null) {
                    callback.onExit(0);
                }
            } catch (Exception e) {
                log.warn("[OSGi] SSH shell 会话结束（异常 {}: {}）", e.getClass().getSimpleName(), e.getMessage());
                if (callback != null) {
                    callback.onExit(1);
                }
            }
        }
    }
}

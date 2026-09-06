package com.chua.starter.osgi.shell;

import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
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
    /** 缓存 Gogo CommandProcessor（启动时一次性查找——避免每连接查找的抖动） */
    private volatile Object commandProcessor;

    public GogoSshShellServer(String host, int port, String username, String password, BundleContext bundleContext) {
        this.host = host == null ? "127.0.0.1" : host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.bundleContext = bundleContext;
    }

    /** 启动 SSH 服务器（MINA SSHD 2.x） */
    public void start() throws IOException {
        commandProcessor = lookupCommandProcessor();
        if (commandProcessor == null) {
            log.warn("[OSGi] Gogo CommandProcessor 服务不可用——bundle: 命令仍可用（框架 API 直通），Gogo 命令不可用");
        }
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
            // bundle: 命令不依赖 Gogo 会话（框架 API 直通）——直接用通道输出流启动会话循环，
            // 规避二次 createSession 在 gogo 运行时下的阻塞/抖动
            PrintStream console = new PrintStream(out, true);
            Thread thread = new Thread(() -> runLoop(console), "gogo-ssh-shell");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void destroy(ChannelSession channel) {
        }

        /** 读取行 -> 回显 -> bundle: 命令直通框架 API（其他命令按需走 Gogo），输出提示符 */
        private void runLoop(PrintStream console) {
            try {
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
                            if (!handleBundleCommand(command, console)) {
                                executeViaGogo(command, console);
                            }
                            console.print("g! ");
                            console.flush();
                        }
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append((char) c);
                    }
                }
                if (callback != null) {
                    callback.onExit(0);
                }
            } catch (Exception e) {
                Throwable root = (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null)
                        ? e.getCause() : e;
                log.warn("[OSGi] SSH shell 会话结束（异常 {}: {}）", root.getClass().getSimpleName(), root.getMessage());
                if (callback != null) {
                    callback.onExit(1);
                }
            }
        }

        /** 非 bundle: 命令按需走 Gogo CommandProcessor（会话按命令创建，失败输出提示不中断） */
        private void executeViaGogo(String command, PrintStream console) {
            try {
                Object processor = commandProcessor != null ? commandProcessor : lookupCommandProcessor();
                if (processor == null) {
                    console.println("Command not found: " + command);
                    return;
                }
                Method createSession = processor.getClass().getMethod(
                        "createSession", InputStream.class, OutputStream.class, OutputStream.class);
                Object session = createSession.invoke(processor, in, out, err);
                Method execute = session.getClass().getMethod("execute", CharSequence.class);
                execute.invoke(session, command);
            } catch (Exception e) {
                Throwable root = (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null)
                        ? e.getCause() : e;
                console.println("命令执行失败（" + root.getClass().getSimpleName() + ": " + root.getMessage() + "）");
            }
        }

        /**
         * 直接处理 {@code bundle:} 命令（框架 API——gogo.command 1.1.2 不提供 bundle: 命令
         * （CommandNotFoundException 实证），且外部命令注册存在类加载器身份差异，故在会话层直通 BundleContext）。
         *
         * @return true 表示已处理（无需走 Gogo）
         */
        private boolean handleBundleCommand(String command, PrintStream console) {
            try {
                if (command.startsWith("bundle:install ")) {
                    String location = command.substring("bundle:install ".length()).trim();
                    Bundle bundle = bundleContext.installBundle(location);
                    console.println("Bundle ID: " + bundle.getBundleId());
                    return true;
                }
                if (command.startsWith("bundle:uninstall ")) {
                    long id = Long.parseLong(command.substring("bundle:uninstall ".length()).trim());
                    Bundle bundle = bundleContext.getBundle(id);
                    if (bundle == null) {
                        console.println("Bundle ID " + id + " not found");
                    } else {
                        bundle.uninstall();
                        console.println("Bundle " + id + " uninstalled");
                    }
                    return true;
                }
                if (command.startsWith("bundle:start ")) {
                    long id = Long.parseLong(command.substring("bundle:start ".length()).trim());
                    Bundle bundle = bundleContext.getBundle(id);
                    if (bundle == null) {
                        console.println("Bundle ID " + id + " not found");
                    } else {
                        bundle.start();
                        console.println("Bundle " + id + " started");
                    }
                    return true;
                }
                if ("bundle:list".equals(command)) {
                    StringBuilder sb = new StringBuilder();
                    for (Bundle bundle : bundleContext.getBundles()) {
                        sb.append(bundle.getBundleId()).append(" | ")
                                .append(bundle.getSymbolicName() == null ? "?" : bundle.getSymbolicName())
                                .append(" | ").append(stateName(bundle.getState())).append('\n');
                    }
                    log.info("[OSGi] bundle:list 直通处理（{} 个 bundle）", bundleContext.getBundles().length);
                    console.print(sb);
                    return true;
                }
            } catch (Exception e) {
                console.println("bundle 命令执行失败: " + e);
                return true;
            }
            return false;
        }

        /** Bundle 状态常量 -> 名称 */
        private static String stateName(int state) {
            switch (state) {
                case Bundle.INSTALLED: return "INSTALLED";
                case Bundle.RESOLVED: return "RESOLVED";
                case Bundle.STARTING: return "STARTING";
                case Bundle.STOPPING: return "STOPPING";
                case Bundle.ACTIVE: return "ACTIVE";
                default: return String.valueOf(state);
            }
        }
    }
}

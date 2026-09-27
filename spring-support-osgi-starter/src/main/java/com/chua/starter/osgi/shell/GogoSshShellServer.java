package com.chua.starter.osgi.shell;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gogo SSH Shell 服务器：补齐 {@code plugin.osgi.shell.protocol=ssh} 的缺失装配——
 * 以 MINA SSHD 2.x API 直连启动 SSH 服务器，将 SSH 通道会话接线到 Felix 框架内的
 * Gogo {@code CommandProcessor}（经框架 bundle 上下文按名查找服务 + 反射调用——
 * bundle 与应用类加载器的类身份不同，不能用类对象直查），实现 bundle 管理命令
 * （bundle:install/list/start/stop/update/uninstall 等）的 SSH 远程操作。
 *
 * <h3>认证</h3>
 * <p>
 * 默认要求用户名与密码均非空：两者任一为空时拒绝所有认证尝试，
 * 避免"空用户名 + 空密码"这种恒真的比较把远程管理通道变成无认证入口。
 * 仅当显式开启 {@code shell.allow-empty-credential} 时才允许空凭据登录。
 * </p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class GogoSshShellServer {

    /**
     * Gogo 命令处理器服务类型名。
     */
    private static final String COMMAND_PROCESSOR_NAME = "org.apache.felix.service.command.CommandProcessor";

    /**
     * 主机密钥默认路径。
     */
    private static final String DEFAULT_HOST_KEY = "target/osgi-hostkey.ser";

    /**
     * shell 提示符。
     */
    private static final String PROMPT = "g! ";

    /**
     * 单会话最大处理命令数，避免异常客户端无限占用线程。
     */
    private static final int MAX_COMMANDS_PER_SESSION = 10000;

    /**
     * 监听地址。
     */
    private final String host;

    /**
     * 监听端口。
     */
    private final int port;

    /**
     * 认证用户名。
     */
    private final String username;

    /**
     * 认证密码。
     */
    private final String password;

    /**
     * 是否允许空凭据登录。
     */
    private final boolean allowEmptyCredential;

    /**
     * bundle 上下文。
     */
    private final BundleContext bundleContext;

    /**
     * ssh 服务端。
     */
    private volatile SshServer sshServer;

    /**
     * 主机密钥文件路径。
     */
    private final String hostKeyPath;

    /**
     * 缓存 Gogo 命令处理器（启动时一次性查找——避免每连接查找的抖动）。
     */
    private volatile Object commandProcessor;

    /**
     * 构造 gogo ssh shell 服务端。
     *
     * @param host                 监听地址
     * @param port                 监听端口
     * @param username             认证用户名
     * @param password             认证密码
     * @param bundleContext        bundle 上下文
     * @param hostKeyPath          主机密钥文件路径，留空使用默认路径
     * @param allowEmptyCredential 是否允许空凭据登录
     */
    public GogoSshShellServer(String host, int port, String username, String password,
                              BundleContext bundleContext, String hostKeyPath,
                              boolean allowEmptyCredential) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.bundleContext = bundleContext;
        this.allowEmptyCredential = allowEmptyCredential;
        this.hostKeyPath = hostKeyPath == null || hostKeyPath.isBlank() ? DEFAULT_HOST_KEY : hostKeyPath;
    }

    /**
     * 启动 SSH 服务器（MINA SSHD 2.x）。
     *
     * @throws IOException 监听或密钥初始化失败时抛出
     */
    public synchronized void start() throws IOException {
        if (sshServer != null && sshServer.isOpen()) {
            log.warn("[OSGi] Gogo SSH shell 已在监听，跳过重复启动");
            return;
        }
        if (!allowEmptyCredential && (username.isEmpty() || password.isEmpty())) {
            throw new IllegalStateException(
                    "[OSGi] SSH shell 认证凭据不完整：username 与 password 均不得为空。"
                            + "如确需空凭据访问，请显式设置 plugin.osgi.shell.allow-empty-credential=true");
        }
        commandProcessor = lookupCommandProcessor();
        if (commandProcessor == null) {
            log.warn("[OSGi] Gogo CommandProcessor 服务不可用——bundle: 命令仍可用（框架 API 直通），Gogo 命令不可用");
        }
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost(host);
        server.setPort(port);
        server.setKeyPairProvider(
                new org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider(Paths.get(hostKeyPath)));
        server.setPasswordAuthenticator((user, pass, session) -> authenticate(user, pass));
        server.setShellFactory((ShellFactory) channel -> new GogoShellCommand());
        server.start();
        this.sshServer = server;
        log.info("[OSGi] Gogo SSH shell 已监听: {}:{}（主机密钥 {}，空凭据登录 {}）",
                host, port, hostKeyPath, allowEmptyCredential ? "已允许" : "已禁止");
    }

    /**
     * 停止 SSH 服务器并等待其完全关闭。
     */
    public synchronized void stop() {
        SshServer server = this.sshServer;
        if (server == null) {
            return;
        }
        this.sshServer = null;
        try {
            server.stop(true);
            log.info("[OSGi] Gogo SSH shell 已停止: {}:{}", host, port);
        } catch (Exception e) {
            log.warn("[OSGi] SSH shell 停止异常", e);
        }
    }

    /**
     * SSH 服务端是否处于监听状态。
     *
     * @return 监听中返回 true
     */
    public boolean isRunning() {
        SshServer server = this.sshServer;
        return server != null && server.isOpen();
    }

    /**
     * 校验登录凭据。
     *
     * @param user 输入的用户名
     * @param pass 输入的密码
     * @return 校验通过返回 true
     */
    private boolean authenticate(String user, String pass) {
        if (!allowEmptyCredential && (username.isEmpty() || password.isEmpty())) {
            log.warn("[OSGi] SSH shell 拒绝认证：服务端凭据未配置完整");
            return false;
        }
        return username.equals(user) && password.equals(pass);
    }

    /**
     * 按名查找 Gogo 命令处理器服务（规避 bundle/应用类加载器类身份差异）。
     *
     * @return 查找结果，未注册时返回 {@code null}
     */
    private Object lookupCommandProcessor() {
        if (bundleContext == null) {
            return null;
        }
        // 注：获取全部服务引用(null, null) 才能让该服务可见，而单数 获取服务引用(名称)
        // 在 Felix 7 下偶发不匹配——统一用全量 API + 对象类手动匹配（实证可靠）
        try {
            ServiceReference<?>[] all = bundleContext.getAllServiceReferences(null, null);
            if (all != null) {
                for (ServiceReference<?> ref : all) {
                    Object objectClass = ref.getProperty("objectClass");
                    if (objectClass instanceof String[]
                            && Arrays.asList((String[]) objectClass).contains(COMMAND_PROCESSOR_NAME)) {
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

    /**
     * SSH 通道会话 -> Gogo 命令处理器 会话接线。
     */
    private class GogoShellCommand implements Command {

        /**
         * 输入流。
         */
        private InputStream in;

        /**
         * 输出流。
         */
        private OutputStream out;

        /**
         * 错误输出流。
         */
        private OutputStream err;

        /**
         * 退出回调。
         */
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
            // 规避二次创建会话在 gogo 运行时下的阻塞/抖动
            PrintStream console = new PrintStream(out, true);
            Thread thread = new Thread(() -> runLoop(console), "gogo-ssh-shell");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void destroy(ChannelSession channel) {
            // 会话循环随输入流关闭而自然结束
        }

        /**
         * 读取行 -> 回显 -> bundle: 命令直通框架 API（其他命令按需走 Gogo），输出提示符。
         *
         * @param console 控制台
         */
        private void runLoop(PrintStream console) {
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicInteger commandCount = new AtomicInteger();
            try {
                StringBuilder line = new StringBuilder();
                int c;
                console.print(PROMPT);
                console.flush();
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        String raw = line.toString();
                        String command = raw.trim();
                        if (!command.isEmpty()) {
                            console.println(raw);
                            console.flush();
                            if (commandCount.incrementAndGet() > MAX_COMMANDS_PER_SESSION) {
                                console.println("单会话命令数超过上限，连接已断开");
                                finished.set(true);
                                break;
                            }
                            if (!handleBundleCommand(command, console)) {
                                executeViaGogo(command, console);
                            }
                            console.print(PROMPT);
                            console.flush();
                        }
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append((char) c);
                    }
                }
                if (callback != null) {
                    callback.onExit(finished.get() ? 1 : 0);
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

        /**
         * 非 bundle: 命令按需走 Gogo 命令处理器（会话按命令创建，失败输出提示不中断）。
         *
         * @param command 命令
         * @param console 控制台
         */
        private void executeViaGogo(String command, PrintStream console) {
            try {
                Object processor = commandProcessor != null ? commandProcessor : lookupCommandProcessor();
                if (processor == null) {
                    console.println("Command not found: " + command);
                    return;
                }
                // 方法定位统一经 ReflectUtils.findMethod，调用仍用 Method 以便外层 catch 解包 InvocationTargetException
                Method createSession = ReflectUtils.findMethod(processor.getClass(), "createSession",
                        InputStream.class, OutputStream.class, OutputStream.class);
                if (createSession == null) {
                    console.println("命令执行失败（Gogo CommandProcessor.createSession 方法不存在）");
                    return;
                }
                Object session = createSession.invoke(processor, in, out, err);
                Method execute = ReflectUtils.findMethod(session.getClass(), "execute", CharSequence.class);
                if (execute == null) {
                    console.println("命令执行失败（Gogo Session.execute 方法不存在）");
                    return;
                }
                execute.invoke(session, command);
            } catch (Exception e) {
                Throwable root = (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null)
                        ? e.getCause() : e;
                console.println("命令执行失败（" + root.getClass().getSimpleName() + ": " + root.getMessage() + "）");
            }
        }

        /**
         * 直接处理 {@code bundle:} 命令。
         *
         * <p>在会话层直通 bundle 上下文，原因有二：gogo.command 1.1.2 不提供 bundle: 命令
         * （实证 CommandNotFoundException），且经 Gogo 命令表注册命令存在类加载器身份差异。</p>
         *
         * @param command 命令
         * @param console 控制台
         * @return 已处理（无需走 Gogo）返回 true
         */
        private boolean handleBundleCommand(String command, PrintStream console) {
            if (!command.startsWith("bundle:")) {
                return false;
            }
            try {
                if (command.startsWith("bundle:install ")) {
                    String location = command.substring("bundle:install ".length()).trim();
                    Bundle bundle = bundleContext.installBundle(location);
                    console.println("Bundle ID: " + bundle.getBundleId()
                            + " | " + bundle.getSymbolicName()
                            + " | " + bundle.getVersion());
                    return true;
                }
                if (command.startsWith("bundle:update ")) {
                    String symbolicName = command.substring("bundle:update ".length()).trim();
                    Bundle bundle = findBundle(symbolicName);
                    if (bundle == null) {
                        console.println("Bundle " + symbolicName + " not found");
                    } else {
                        boolean wasActive = bundle.getState() == Bundle.ACTIVE;
                        bundle.update();
                        if (wasActive && bundle.getState() != Bundle.ACTIVE) {
                            bundle.start();
                        }
                        console.println("Bundle " + symbolicName + " updated to version " + bundle.getVersion());
                    }
                    return true;
                }
                if (command.startsWith("bundle:uninstall ")) {
                    String symbolicName = command.substring("bundle:uninstall ".length()).trim();
                    Bundle bundle = findBundle(symbolicName);
                    if (bundle == null) {
                        console.println("Bundle " + symbolicName + " not found");
                    } else {
                        bundle.uninstall();
                        console.println("Bundle " + symbolicName + " uninstalled");
                    }
                    return true;
                }
                if (command.startsWith("bundle:start ")) {
                    String symbolicName = command.substring("bundle:start ".length()).trim();
                    Bundle bundle = findBundle(symbolicName);
                    if (bundle == null) {
                        console.println("Bundle " + symbolicName + " not found");
                    } else {
                        bundle.start();
                        console.println("Bundle " + symbolicName + " started");
                    }
                    return true;
                }
                if (command.startsWith("bundle:stop ")) {
                    String symbolicName = command.substring("bundle:stop ".length()).trim();
                    Bundle bundle = findBundle(symbolicName);
                    if (bundle == null) {
                        console.println("Bundle " + symbolicName + " not found");
                    } else {
                        bundle.stop();
                        console.println("Bundle " + symbolicName + " stopped");
                    }
                    return true;
                }
                if ("bundle:list".equals(command)) {
                    printBundleList(console);
                    return true;
                }
                console.println("未知的 bundle 命令，可用："
                        + "bundle:list / bundle:install <位置> / bundle:update <符号名> / "
                        + "bundle:start <符号名> / bundle:stop <符号名> / bundle:uninstall <符号名>");
                return true;
            } catch (Exception e) {
                console.println("bundle 命令执行失败: " + e);
                return true;
            }
        }

        /**
         * 打印 bundle 清单。
         *
         * @param console 控制台
         */
        private void printBundleList(PrintStream console) {
            Bundle[] bundles = bundleContext.getBundles();
            StringBuilder sb = new StringBuilder();
            for (Bundle bundle : bundles) {
                sb.append(bundle.getBundleId()).append(" | ")
                        .append(bundle.getSymbolicName() == null ? "?" : bundle.getSymbolicName())
                        .append(" | ").append(bundle.getVersion())
                        .append(" | ").append(stateName(bundle.getState())).append('\n');
            }
            log.info("[OSGi] bundle:list 直通处理（{} 个 bundle）", bundles.length);
            console.print(sb);
        }

        /**
         * 按符号名或数字编号查找 bundle。
         *
         * @param key 符号名或 bundle 编号
         * @return 找到的 bundle，未找到返回 {@code null}
         */
        private Bundle findBundle(String key) {
            if (key == null || key.isBlank()) {
                return null;
            }
            for (Bundle bundle : bundleContext.getBundles()) {
                if (key.equals(bundle.getSymbolicName())) {
                    return bundle;
                }
            }
            try {
                return bundleContext.getBundle(Long.parseLong(key.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Bundle 状态常量 -> 名称。
         *
         * @param state 状态
         * @return 状态名称
         */
        private static String stateName(int state) {
            return switch (state) {
                case Bundle.UNINSTALLED -> "UNINSTALLED";
                case Bundle.INSTALLED -> "INSTALLED";
                case Bundle.RESOLVED -> "RESOLVED";
                case Bundle.STARTING -> "STARTING";
                case Bundle.STOPPING -> "STOPPING";
                case Bundle.ACTIVE -> "ACTIVE";
                default -> String.valueOf(state);
            };
        }
    }
}

package com.chua.starter.osgi.shell;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
     * 并发 shell 会话上限默认值。
     */
    private static final int DEFAULT_MAX_SESSIONS = 8;

    /**
     * 空闲会话超时默认值（秒）。
     */
    private static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 600;

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
     * 认证失败次数上限（同一连接认证请求达到该次数即断开）。
     */
    private int maxAuthAttempts = 3;

    /**
     * 空闲会话超时秒数；&le;0 表示使用 SSHD 默认值。
     */
    private int idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;

    /**
     * 并发 shell 会话上限；&le;0 表示不限制。
     */
    private int maxSessions = DEFAULT_MAX_SESSIONS;

    /**
     * 当前活跃 shell 会话数（并发上限控制）。
     */
    private final AtomicInteger activeSessions = new AtomicInteger();

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
     * 设置认证失败次数上限（&lt;1 时按 1 处理）。
     *
     * @param maxAuthAttempts 认证失败次数上限
     */
    public void setMaxAuthAttempts(int maxAuthAttempts) {
        this.maxAuthAttempts = maxAuthAttempts;
    }

    /**
     * 设置空闲会话超时秒数；&le;0 表示使用 SSHD 默认值。
     *
     * @param idleTimeoutSeconds 空闲超时秒数
     */
    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    /**
     * 设置并发 shell 会话上限；&le;0 表示不限制。
     *
     * @param maxSessions 并发会话上限
     */
    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
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
        // 主机密钥默认落在 target/osgi-hostkey.ser：父目录不存在时 MINA SSHD 每次启动都
        // WARN 并放弃持久化，这里先补建父目录，保证密钥文件可落盘、重启后服务端指纹稳定
        Path hostKey = Paths.get(hostKeyPath).toAbsolutePath();
        if (hostKey.getParent() != null) {
            Files.createDirectories(hostKey.getParent());
        }
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost(host);
        server.setPort(port);
        server.setKeyPairProvider(
                new org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider(hostKey));
        server.setPasswordAuthenticator((user, pass, session) -> authenticate(user, pass));
        server.setShellFactory((ShellFactory) channel -> new GogoShellCommand());
        // README 承诺的 max-auth-attempts 在此真正生效：同一连接认证失败达到上限即断开，
        // 不再交给 SSHD 默认的 20 次上限
        server.getProperties().put(CoreModuleProperties.MAX_AUTH_REQUESTS.getName(),
                Math.max(1, maxAuthAttempts));
        if (idleTimeoutSeconds > 0) {
            // 空闲超时：会话持续无流量即断开，防止半死连接长期占用服务端线程。
            // 必须经 Property.set 落成毫秒数（toStorage → toMillis）；若直接向 properties 塞
            // Duration，SSHD 读取时会按 Long.parseLong(duration.toString()) 解析 "PT5S"
            // 抛 NumberFormatException，每秒空闲检查失败，空闲断开永不生效
            CoreModuleProperties.IDLE_TIMEOUT.set(server, Duration.ofSeconds(idleTimeoutSeconds));
        }
        server.start();
        this.sshServer = server;
        log.info("[OSGi] Gogo SSH shell 已监听: {}:{}（主机密钥 {}，空凭据登录 {}，认证失败上限 {}，空闲超时 {}，会话上限 {}）",
                host, port, hostKeyPath, allowEmptyCredential ? "已允许" : "已禁止",
                Math.max(1, maxAuthAttempts),
                idleTimeoutSeconds > 0 ? idleTimeoutSeconds + "s" : "SSHD默认",
                maxSessions > 0 ? maxSessions : "不限");
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
            // 并发会话上限：超限立即拒绝并关闭该通道，防止远程管理通道被占满
            if (maxSessions > 0 && activeSessions.incrementAndGet() > maxSessions) {
                activeSessions.decrementAndGet();
                PrintStream reject = new PrintStream(out, true);
                reject.println("并发 SSH shell 会话数已达上限（" + maxSessions + "），"
                        + "请先断开其他会话再重试");
                reject.flush();
                if (callback != null) {
                    callback.onExit(1);
                }
                return;
            }
            // bundle: 命令不依赖 Gogo 会话（框架 API 直通）——直接用通道输出流启动会话循环，
            // 规避二次创建会话在 gogo 运行时下的阻塞/抖动
            PrintStream console = new PrintStream(out, true);
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
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
                            LifecycleCommand lifecycle = classifyLifecycleCommand(command);
                            if (lifecycle == LifecycleCommand.EXIT_SESSION) {
                                // exit 只结束当前 SSH 会话；宿主 JVM 与 OSGi 框架保持运行
                                console.println("bye（SSH 会话结束，OSGi 框架保持运行）");
                                console.flush();
                                break;
                            }
                            if (lifecycle == LifecycleCommand.DENIED) {
                                console.println("拒绝执行 " + command.split("\\s+", 2)[0]
                                        + "：该命令会停止 OSGi 框架/宿主 JVM，SSH shell 中已禁用；"
                                        + "如需下线服务请走运维停机流程");
                            } else if (!handleDirectCommand(command, console)) {
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
            } finally {
                activeSessions.decrementAndGet();
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
                Object result = execute.invoke(session, command);
                printResult(result, console);
            } catch (Exception e) {
                Throwable root = (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null)
                        ? e.getCause() : e;
                console.println("命令执行失败（" + root.getClass().getSimpleName() + ": " + root.getMessage() + "）");
            }
        }

        /**
         * 打印 Gogo 命令返回值。
         *
         * <p>felix:* 命令（gogo.command 的 Basic）把全部输出放进返回值而不是 System.out；
         * 交互式 gosh 由 Console 负责打印返回值，这里直连 CommandProcessor 必须自己补上，
         * 否则命令「执行了但 SSH 与宿主 stdout 两端都看不到输出」。</p>
         *
         * @param result  命令返回值，可为 {@code null}
         * @param console 控制台
         */
        private void printResult(Object result, PrintStream console) {
            if (result == null) {
                return;
            }
            String text = result instanceof String s ? s
                    : result instanceof Object[] array ? Arrays.toString(array)
                    : String.valueOf(result);
            if (!text.isBlank()) {
                console.println(text);
            }
        }

        /**
         * 生命周期命令分类。
         */
        private enum LifecycleCommand {
            /** 非生命周期命令。 */
            NONE,
            /** 结束当前 SSH 会话（宿主 JVM 与 OSGi 框架保持运行）。 */
            EXIT_SESSION,
            /** 拒绝执行（会停止框架/宿主 JVM）。 */
            DENIED
        }

        /**
         * 识别会终止会话或宿主进程的命令。
         *
         * <p>gogo shell 的 gosh_profile 把 {@link System} 注册为 {@code system:} 命令，
         * 裸 {@code exit} 会解析到 {@code system:exit(int)} 直通 {@code System.exit}，
         * JDK 25 已无 SecurityManager 可拦——这些命令必须在 runLoop 层就地处置。</p>
         *
         * @param command 命令行
         * @return 分类结果
         */
        private static LifecycleCommand classifyLifecycleCommand(String command) {
            String first = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            return switch (first) {
                case "exit" -> LifecycleCommand.EXIT_SESSION;
                case "shutdown", "felix:shutdown", "system:exit", "system:shutdown" -> LifecycleCommand.DENIED;
                default -> LifecycleCommand.NONE;
            };
        }

        /**
         * 直接处理 shell 内置命令（不经过 Gogo 命令处理器）。
         *
         * <p>在会话层直通 bundle 上下文，原因有二：gogo.command 1.1.2 不提供 bundle: 命令
         * （实证 CommandNotFoundException），且经 Gogo 命令表注册命令存在类加载器身份差异。
         * {@code help} / {@code stats} / {@code services} 运维命令同样在此直通处理。</p>
         *
         * @param command 命令
         * @param console 控制台
         * @return 已处理（无需走 Gogo）返回 true
         */
        private boolean handleDirectCommand(String command, PrintStream console) {
            if ("help".equals(command)) {
                printHelp(console);
                return true;
            }
            if ("stats".equals(command)) {
                printStats(console);
                return true;
            }
            if ("services".equals(command)) {
                printServices(console);
                return true;
            }
            if (command.equals("invoke") || command.startsWith("invoke ")) {
                handleInvoke(command.substring(Math.min(command.length(), "invoke ".length())).trim(), console);
                return true;
            }
            if (command.equals("impls") || command.startsWith("impls ")) {
                printImpls(command.substring(Math.min(command.length(), "impls ".length())).trim(), console);
                return true;
            }
            if (!command.startsWith("bundle:")) {
                return false;
            }
            try {
                if (command.startsWith("bundle:info ")) {
                    printBundleInfo(command.substring("bundle:info ".length()).trim(), console);
                    return true;
                }
                if (command.startsWith("bundle:headers ")) {
                    printBundleHeaders(command.substring("bundle:headers ".length()).trim(), console);
                    return true;
                }
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
                console.println("未知的 bundle 命令，可用：bundle:list / bundle:info <名> / bundle:headers <名> / "
                        + "bundle:install <位置> / bundle:update <符号名> / bundle:start <符号名> / "
                        + "bundle:stop <符号名> / bundle:uninstall <符号名>（help 查看全部命令）");
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
         * 打印 shell 内置命令帮助。
         *
         * @param console 控制台
         */
        private void printHelp(PrintStream console) {
            console.println("可用命令：");
            console.println("  bundle:list                          bundle 清单");
            console.println("  bundle:info <符号名|编号>            bundle 详情（状态/位置/注册服务）");
            console.println("  bundle:headers <符号名|编号>         bundle Manifest 头");
            console.println("  bundle:install <位置>                安装 bundle");
            console.println("  bundle:update <符号名|编号>          原地升级");
            console.println("  bundle:start <符号名|编号>           启动");
            console.println("  bundle:stop <符号名|编号>            停止");
            console.println("  bundle:uninstall <符号名|编号>       卸载");
            console.println("  stats                                框架统计（bundle 状态分布/服务数）");
            console.println("  services                             服务注册表");
            console.println("  impls <接口FQN>                      某接口在注册表中的实现服务");
            console.println("  invoke <接口FQN> <方法名> [参数...]  真实调用注册表中的服务方法并回传输出");
            console.println("  felix:* / gogo:* 等                  经 Gogo 命令处理器执行");
            console.println("  exit                                 结束当前 SSH 会话（框架保持运行）");
            console.println("注：shutdown/system:exit 等停机命令已在 SSH shell 中禁用。");
        }

        /**
         * 打印框架统计：框架状态、bundle 状态分布、注册服务数。
         *
         * @param console 控制台
         */
        private void printStats(PrintStream console) {
            Bundle[] bundles = bundleContext.getBundles();
            Map<String, Integer> byState = new LinkedHashMap<>();
            for (Bundle bundle : bundles) {
                byState.merge(stateName(bundle.getState()), 1, Integer::sum);
            }
            ServiceReference<?>[] services = null;
            try {
                services = bundleContext.getAllServiceReferences(null, null);
            } catch (Exception e) {
                // 统计展示用途，服务枚举失败不阻断 stats
            }
            String states = byState.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
            console.println("框架状态: " + stateName(bundleContext.getBundle(0).getState()));
            console.println("bundle 总数: " + bundles.length + "（" + states + "）");
            console.println("注册服务数: " + (services == null ? 0 : services.length));
        }

        /**
         * 打印服务注册表（按 objectClass 排序）。
         *
         * @param console 控制台
         */
        private void printServices(PrintStream console) {
            ServiceReference<?>[] refs;
            try {
                refs = bundleContext.getAllServiceReferences(null, null);
            } catch (Exception e) {
                console.println("服务枚举失败: " + e.getMessage());
                return;
            }
            if (refs == null || refs.length == 0) {
                console.println("当前无注册服务");
                return;
            }
            ServiceReference<?>[] sorted = refs.clone();
            Arrays.sort(sorted, Comparator.comparing(ref -> String.join(",", objectClassOf(ref))));
            console.println("共 " + sorted.length + " 项服务：");
            for (ServiceReference<?> ref : sorted) {
                Bundle owner = ref.getBundle();
                console.println("  [" + ref.getProperty(Constants.SERVICE_ID) + "] "
                        + String.join(",", objectClassOf(ref))
                        + " | bundle=" + (owner == null ? "?" : owner.getSymbolicName())
                        + " | ranking=" + ref.getProperty(Constants.SERVICE_RANKING));
            }
        }

        /**
         * 打印某接口在服务注册表中的实现服务清单。
         *
         * @param fqn     接口全限定名
         * @param console 控制台
         */
        private void printImpls(String fqn, PrintStream console) {
            if (fqn.isEmpty()) {
                console.println("用法: impls <接口FQN>");
                return;
            }
            List<ServiceReference<?>> matches = findServiceReferences(fqn);
            if (matches.isEmpty()) {
                console.println("接口 " + fqn + " 当前无实现服务（0 项）");
                return;
            }
            console.println("接口 " + fqn + " 的实现服务 " + matches.size() + " 项：");
            for (ServiceReference<?> ref : matches) {
                Bundle owner = ref.getBundle();
                console.println("  [" + ref.getProperty(Constants.SERVICE_ID) + "] "
                        + String.join(",", objectClassOf(ref))
                        + " | bundle=" + (owner == null ? "?" : owner.getSymbolicName())
                        + " | ranking=" + ref.getProperty(Constants.SERVICE_RANKING));
            }
        }

        /**
         * 调用注册表中某接口服务的真实方法并回传运行输出。
         *
         * <p>外部 bundle 发布的服务实现类与宿主类加载器类身份不同，不能按宿主
         * classpath 加载接口后强转；这里按服务实例自身的 classloader 加载接口、
         * 反射定位方法并调用，把返回值/异常原样回显到 SSH 控制台，用于验证
         * 「安装上去的 SPI 服务真实可用」。多个候选服务时按 service id 从小到大
         * 逐个尝试，首个成功调用即返回。</p>
         *
         * @param rest    命令参数：接口FQN 方法名 [参数...]
         * @param console 控制台
         */
        private void handleInvoke(String rest, PrintStream console) {
            String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+");
            if (parts.length < 2) {
                console.println("用法: invoke <接口FQN> <方法名> [参数...]（参数按 字符串/整数/小数/布尔 自动识别）");
                return;
            }
            String fqn = parts[0];
            String methodName = parts[1];
            String[] rawArgs = parts.length > 2 ? Arrays.copyOfRange(parts, 2, parts.length) : new String[0];
            List<ServiceReference<?>> matches = findServiceReferences(fqn);
            Exception lastError = null;
            for (ServiceReference<?> ref : matches) {
                Object service;
                try {
                    service = bundleContext.getService(ref);
                } catch (Exception e) {
                    lastError = e;
                    continue;
                }
                if (service == null) {
                    continue;
                }
                Class<?> iface = loadServiceInterface(service, fqn);
                if (iface == null) {
                    continue;
                }
                try {
                    Object result = invokeServiceMethod(service, iface, methodName, rawArgs);
                    console.println("[" + fqn + "." + methodName + "] -> " + stringifyResult(result));
                    return;
                } catch (Exception e) {
                    lastError = e;
                }
            }
            if (lastError != null) {
                Throwable root = (lastError instanceof InvocationTargetException ite && ite.getCause() != null)
                        ? ite.getCause() : lastError;
                console.println("调用失败: " + root.getClass().getName() + ": " + root.getMessage());
            } else {
                console.println("服务未找到: " + fqn);
            }
        }

        /**
         * 按接口全限定名查找注册表中的全部服务引用（按 service id 升序）。
         *
         * @param fqn 接口全限定名
         * @return 匹配的服务引用列表
         */
        private List<ServiceReference<?>> findServiceReferences(String fqn) {
            List<ServiceReference<?>> result = new ArrayList<>();
            try {
                ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(null, null);
                if (refs != null) {
                    for (ServiceReference<?> ref : refs) {
                        if (Arrays.asList(objectClassOf(ref)).contains(fqn)) {
                            result.add(ref);
                        }
                    }
                }
            } catch (Exception e) {
                // 注册表枚举失败按无服务处理，由调用方给出「服务未找到」
            }
            result.sort(Comparator.comparingInt(ref -> {
                Object id = ref.getProperty(Constants.SERVICE_ID);
                return id instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
            }));
            return result;
        }

        /**
         * 用服务实例自身的 classloader 加载接口类型（跨 bundle 类空间）。
         *
         * @param service 服务实例
         * @param fqn     接口全限定名
         * @return 接口类型；加载失败或实例不匹配时返回 {@code null}
         */
        private Class<?> loadServiceInterface(Object service, String fqn) {
            try {
                ClassLoader loader = service.getClass().getClassLoader();
                Class<?> candidate = Class.forName(fqn, false, loader);
                if (candidate.isInterface() && candidate.isInstance(service)) {
                    return candidate;
                }
            } catch (Throwable e) {
                // 回退到实例接口层级匹配
            }
            for (Class<?> iface : allInterfacesOf(service.getClass())) {
                if (iface.getName().equals(fqn) && iface.isInstance(service)) {
                    return iface;
                }
            }
            return null;
        }

        /**
         * 收集类的全部接口（含父类与父接口，去重由调用方语义保证）。
         *
         * @param clazz 起始类
         * @return 接口列表
         */
        private List<Class<?>> allInterfacesOf(Class<?> clazz) {
            List<Class<?>> result = new ArrayList<>();
            for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Class<?> iface : current.getInterfaces()) {
                    result.add(iface);
                    result.addAll(allInterfacesOf(iface));
                }
            }
            return result;
        }

        /**
         * 在接口上按名称与参数个数定位方法并反射调用。
         *
         * @param service    服务实例
         * @param iface      接口类型（服务实例自身 classloader 加载）
         * @param methodName 方法名
         * @param rawArgs    字符串形式的方法参数
         * @return 方法返回值
         * @throws Exception 方法不存在、参数不匹配或调用抛出异常
         */
        private Object invokeServiceMethod(Object service, Class<?> iface, String methodName, String[] rawArgs)
                throws Exception {
            Method chosen = null;
            Object[] chosenArgs = null;
            for (Method method : iface.getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != rawArgs.length) {
                    continue;
                }
                Object[] args = new Object[rawArgs.length];
                boolean matched = true;
                for (int i = 0; i < rawArgs.length; i++) {
                    Object converted = convertInvokeArg(rawArgs[i], parameterTypes[i]);
                    if (converted == null) {
                        matched = false;
                        break;
                    }
                    args[i] = converted;
                }
                if (matched) {
                    chosen = method;
                    chosenArgs = args;
                    break;
                }
            }
            if (chosen == null) {
                throw new IllegalArgumentException("方法未找到: " + iface.getName() + "." + methodName
                        + "（参数个数 " + rawArgs.length + "）");
            }
            try {
                return chosen.invoke(service, chosenArgs);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new IllegalStateException("方法执行异常: " + cause.getClass().getName() + ": " + cause.getMessage(), cause);
            }
        }

        /**
         * 把字符串参数转换为方法形参类型（字符串/布尔/整型/浮点，其余按字符串兜底）。
         *
         * @param raw  原始字符串参数
         * @param type 形参类型
         * @return 转换结果；无法转换返回 {@code null}
         */
        private Object convertInvokeArg(String raw, Class<?> type) {
            if (type == String.class || type == Object.class || type.isAssignableFrom(String.class)) {
                return raw;
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.valueOf(raw);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(raw);
            }
            if (type == long.class || type == Long.class) {
                return Long.valueOf(raw);
            }
            if (type == double.class || type == Double.class) {
                return Double.valueOf(raw);
            }
            if (type == float.class || type == Float.class) {
                return Float.valueOf(raw);
            }
            if (type == short.class || type == Short.class) {
                return Short.valueOf(raw);
            }
            if (type == byte.class || type == Byte.class) {
                return Byte.valueOf(raw);
            }
            if (type == char.class || type == Character.class) {
                return raw.length() == 1 ? raw.charAt(0) : null;
            }
            return null;
        }

        /**
         * 结果转可读文本（数组展开、null 显式化）。
         *
         * @param result 调用结果
         * @return 展示文本
         */
        private String stringifyResult(Object result) {
            if (result == null) {
                return "null";
            }
            if (result instanceof Object[] array) {
                return Arrays.toString(array);
            }
            if (result instanceof byte[] bytes) {
                return Arrays.toString(bytes);
            }
            return String.valueOf(result);
        }

        /**
         * 打印单个 bundle 详情。
         *
         * @param key     符号名或 bundle 编号
         * @param console 控制台
         */
        private void printBundleInfo(String key, PrintStream console) {
            Bundle bundle = findBundle(key);
            if (bundle == null) {
                console.println("Bundle " + key + " not found");
                return;
            }
            console.println("ID       : " + bundle.getBundleId());
            console.println("符号名   : " + (bundle.getSymbolicName() == null ? "?" : bundle.getSymbolicName()));
            console.println("版本     : " + bundle.getVersion());
            console.println("状态     : " + stateName(bundle.getState()));
            console.println("位置     : " + bundle.getLocation());
            ServiceReference<?>[] refs = bundle.getRegisteredServices();
            console.println("注册服务 : " + (refs == null ? 0 : refs.length) + " 项");
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    console.println("  - " + String.join(",", objectClassOf(ref)));
                }
            }
        }

        /**
         * 打印单个 bundle 的 Manifest 头。
         *
         * @param key     符号名或 bundle 编号
         * @param console 控制台
         */
        private void printBundleHeaders(String key, PrintStream console) {
            Bundle bundle = findBundle(key);
            if (bundle == null) {
                console.println("Bundle " + key + " not found");
                return;
            }
            Dictionary<String, String> headers = bundle.getHeaders();
            Enumeration<String> keys = headers.keys();
            while (keys.hasMoreElements()) {
                String name = keys.nextElement();
                console.println(name + ": " + headers.get(name));
            }
        }

        /**
         * 取服务引用的 objectClass 多值。
         *
         * @param ref 服务引用
         * @return objectClass 列表（可能为空数组）
         */
        private static String[] objectClassOf(ServiceReference<?> ref) {
            Object value = ref.getProperty(Constants.OBJECTCLASS);
            return value instanceof String[] names ? names : new String[0];
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
                case Bundle.RESOLVED -> "
// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.cdp.service;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.browser.cdp.CdpBrowserEngine;
import com.chua.starter.cdp.config.CdpProperties;
import com.chua.starter.cdp.conn.CdpWsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * CDP 浏览器引擎服务（核心）
 *
 * <p>负责 Chromium 的生命周期管理与浏览器级 CDP 连接：</p>
 * <ul>
 *   <li>{@code start()} / {@code stop()}：启动 / 停止 Chromium 进程；</li>
 *   <li>{@code callBrowser(String, Map)}：浏览器级 CDP 调用（版本、目标列表、截屏等）；</li>
 *   <li>{@code on(String, Consumer)}：注册浏览器级 CDP 事件（控制台 / 网络 / 页面事件），
 *       由 {@link com.chua.starter.cdp.ws.CdpWebSocketHandler} 转发到前端的 DevTools 面板。</li>
 * </ul>
 *
 * <p><h3>浏览器启动</h3>
 * 使用 {@code --remote-debugging-port=PORT} 启动 Chromium，随后从
 * {@code http://127.0.0.1:{port}/json/version} 中提取浏览器 WS 端点
 * {@code ws://127.0.0.1:{port}/devtools/browser/<uuid>}，建立请求通道。
 * 具体的可执行文件由 {@link CdpBrowserEngine#locateBrowser(String)} 按环境变量
 * / Playwright 缓存 / 搜索路径探测，这里复用了同一份 CDP 引擎实现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdpEngineService {

    /**
     * 状态字
     */
    public enum Status {
        /** 未启动 */
        NOT_STARTED,
        /** 启动中 */
        STARTING,
        /** 已启动 */
        STARTED,
        /** 停止中 */
        STOPPING,
        /** 停止 / 失败 */
        STOPPED,
        /** 启动失败（可能重试后恢复） */
        FAILED
    }

    /**
     * 需要向所有打开的控制台推送的事件方法
     *
     * <p>拆成浏览器级与页面级两组：{@code Runtime.*} / {@code Network.*} / {@code Page.*}
     * 都是**页面级**事件，只在 {@code devtools/page/<id>} 通道上产生。</p>
     */
    private static final List<String> EVENT_METHODS = List.of(
            "Runtime.consoleAPICalled",
            "Runtime.exceptionThrown",
            "Runtime.bindingCalled",
            "Network.requestWillBeSent",
            "Network.responseReceived",
            "Network.requestServedFromCache",
            "Network.dataReceived",
            "Network.loadingFinished",
            "Network.loadingFailed",
            "Page.frameStartedLoading",
            "Page.frameNavigated",
            "Page.frameDetached",
            "Page.frameAttached",
            "Page.loadEventFired",
            "Page.navigateFrameCompleted",
            "Page.navigateRequested",
            "Page.screenshot",
            "Target.targetCreated",
            "Target.targetDestroyed",
            "Target.targetCrashed",
            "Target.targetChanged"
    );

    /**
     * 浏览器级事件（只有这类事件会在 {@code devtools/browser} 通道上出现）
     */
    private static final List<String> BROWSER_EVENT_METHODS = List.of(
            "Target.targetCreated",
            "Target.targetDestroyed",
            "Target.targetCrashed",
            "Target.targetChanged"
    );

    /**
     * 页面级事件（必须在页面通道上订阅）
     */
    private static final List<String> PAGE_EVENT_METHODS = List.of(
            "Runtime.consoleAPICalled",
            "Runtime.exceptionThrown",
            "Runtime.bindingCalled",
            "Network.requestWillBeSent",
            "Network.responseReceived",
            "Network.requestServedFromCache",
            "Network.dataReceived",
            "Network.loadingFinished",
            "Network.loadingFailed",
            "Page.frameStartedLoading",
            "Page.frameNavigated",
            "Page.frameDetached",
            "Page.frameAttached",
            "Page.loadEventFired",
            "Page.navigateFrameCompleted",
            "Page.navigateRequested",
            "Page.screenshot"
    );

    /**
     * 需要启用的 CDP 域
     *
     * <p>{@code Runtime.enable} / {@code Network.enable} 属于页面级域，只能在页面通道上发送；
     * 原实现把它们连同 {@code Page.enable} 一起发到浏览器级通道，浏览器回 {@code -32601}，
     * 被 catch 吞掉后 Console / Network 两个页签永远没有数据。</p>
     */
    private static final List<String> PAGE_ENABLED_DOMAINS = List.of(
            "Network.enable",
            "Runtime.enable",
            "Page.enable",
            "Performance.enable"
    );

    /**
     * 属性
     */
    private final CdpProperties properties;

    /**
     * 当前状态
     */
    private final Map<String, Object> runtime = new ConcurrentHashMap<>();

    /**
     * 浏览器级 CDP 客户端
     */
    private CdpWsClient browserClient;

    /**
     * 浏览器进程
     */
    private Process process;

    /**
     * 用户数据目录
     */
    private Path userDataDir;

    /**
     * 页面级 CDP 客户端（targetId -> client）
     */
    private final Map<String, CdpWsClient> pageClients = new ConcurrentHashMap<>();

    /**
     * 启动状态
     */
    private volatile Status state = Status.NOT_STARTED;

    /**
     * 浏览器启动完成后的 WS 端点
     */
    private String wsEndpoint;

    /**
     * 浏览器版本
     */
    private String version;

    /**
     * 是否已经启动了事件订阅
     */
    private volatile boolean eventsSubscribed;

    /**
     * 浏览器级通道是否已挂上事件转发器（Target.* 这类浏览器级事件）
     */
    private volatile boolean browserSubscribed;

    /**
     * 已经挂好转发器的页面目标（避免重复挂载导致事件被投递多次）
     */
    private final Set<String> attachedPages = ConcurrentHashMap.newKeySet();

    /**
     * 全局事件订阅者（通过 CDN 回传到每个 WS 前端）
     */
    private final Map<String, List<Consumer<Map<String, Object>>>> globalSubscribers = new ConcurrentHashMap<>();

    /**
     * 开始一个浏览器
     *
     * <p>幂等：已经在跑就直接返回。但「在跑」不能只看状态字段——浏览器进程可能
     * 被外部关掉、WS 可能已断开，那时状态仍是 {@code STARTED}，界面显示正常
     * 却什么都操作不了。所以这里先校验端口与通道是否真的活着，不活就按
     * STOPPED 重来。</p>
     */
    public synchronized Status start() {
        if (state == Status.STARTED) {
            if (browserClient != null && !browserClient.isClosed() && isDebugPortListening()) {
                return state;
            }
            log.warn("[cdp] 状态为 STARTED 但浏览器已不可用（端口或通道已断），重新启动");
            cleanupAfterFailedStart();
            state = Status.STOPPED;
        }
        if (!properties.isEnabled()) {
            log.info("[cdp] 模块已关闭（spring.cdp.enabled=false）");
            return state = Status.STOPPED;
        }
        state = Status.STARTING;
        try {
            String executable = properties.getExecutable();
            if (StringUtils.isBlank(executable)) {
                executable = CdpBrowserEngine.locateBrowser(null);
            }
            if (StringUtils.isBlank(executable)) {
                throw new IllegalStateException("未找到 Chrome/Chromium 可执行文件，请设置 spring.cdp.executable"
                        + " 或环境变量 CHUA_CHROME / CHROME_PATH / CHROME_BIN");
            }

            userDataDir = createUserDataDir();

            // 端口若已被别的浏览器占着，新实例会静默绑不上，而 /json/version 读到的是
            // 那个旧进程——后面所有操作都作用在看不见的旧页面上。先抢占校验。
            if (isDebugPortListening()) {
                killByDebugPort();
                if (isDebugPortListening()) {
                    throw new IllegalStateException("调试端口 " + properties.getPort()
                            + " 已被其它进程占用（可能是上次未退出的浏览器），请先关闭它或改用别的 spring.cdp.port");
                }
            }

            List<String> command = new ArrayList<>();
            command.add(executable);
            if (properties.isHeadless()) {
                command.add("--headless=new");
            }
            command.add("--remote-debugging-port=" + properties.getPort());
            command.add("--user-data-dir=" + userDataDir.toAbsolutePath());
            command.add("--no-first-run");
            command.add("--no-default-browser-check");
            command.add("--disable-blink-features=AutomationControlled");
            command.add("--disable-dev-shm-usage");
            command.add("--window-size=" + properties.getViewportWidth() + "," + properties.getViewportHeight());
            command.add("--lang=zh-CN");
            if (StringUtils.isNotBlank(properties.getProxy())) {
                command.add("--proxy-server=" + properties.getProxy().trim());
            }
            if (StringUtils.isNotBlank(properties.getUserAgent())) {
                command.add("--user-agent=" + properties.getUserAgent().trim());
            }
            for (String arg : properties.getArgs()) {
                command.add(arg);
            }
            command.add("about:blank");
            if (StringUtils.isNotBlank(properties.getUserDataDir()) && !properties.getUserDataDir().isBlank()) {
                command.add("--auto-open-devtools-for-tabs");
            }

            Path logFile = userDataDir.resolve("chrome.log");
            try {
                process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                        .start();
            } catch (IOException e) {
                throw new IllegalStateException("启动 Chromium 失败：" + executable + ": " + e.getMessage(), e);
            }

            waitForRemoteDebugEnabled();

            // 从 /json/version 中拿到浏览器的 DevTools WS 端点
            Map<String, Object> versionMap = version();
            String debugUrl = (String) versionMap.get("webSocketDebuggerUrl");
            if (StringUtils.isBlank(debugUrl)) {
                throw new IllegalStateException("未发现浏览器的 DevTools WebSocket 端点: " + Json.toJson(versionMap));
            }
            wsEndpoint = debugUrl;
            browserClient = CdpWsClient.connect(debugUrl, Duration.ofSeconds(30));

            // 需要的时候才启用（延迟到风险最小化）
            subscribeEvents();

            Map<String, Object> info = (Map<String, Object>) versionMap.get("info");
            if (info != null) {
                log.info("[cdp] 已连接浏览器: {}", info.getOrDefault("type", ""));
            }

            runtime.put("version", version);
            runtime.put("port", properties.getPort());
            runtime.put("targetCount", 0);
            runtime.put("startedAt", System.currentTimeMillis());
            // 上一次失败留下的 error 必须清掉：否则 state=STARTED 的同时
            // status 里还挂着旧错误，前端与排查的人只会看到「自相矛盾」。
            runtime.remove("error");
            state = Status.STARTED;
            return state;
        } catch (RuntimeException | IOException e) {
            state = Status.FAILED;
            runtime.put("error", e.getMessage());
            log.error("[cdp] 启动失败: {}", e.getMessage(), e);
            // 注意：不能调 stop()。stop() 开头有 `state != STARTED 就返回`，
            // 此时 state 已是 FAILED，stop() 会**什么都不做**——刚启动的浏览器
            // 与已打开的 WS 通道就都留在那里，端口被自己占死，下一次启动必失败。
            cleanupAfterFailedStart();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 启动失败后的就地清理
     *
     * <p>不复用 {@link #stop()}：{@code stop()} 只处理 {@code STARTED} 状态，
     * 在失败路径上会被状态守卫直接跳过，留下半启动的浏览器进程与端口占用。</p>
     */
    private void cleanupAfterFailedStart() {
        try {
            if (browserClient != null) {
                browserClient.markClosed();
                try {
                    browserClient.close();
                } catch (Exception ignored) {
                }
                browserClient = null;
            }
            for (CdpWsClient client : new ArrayList<>(pageClients.values())) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
            pageClients.clear();
            eventsSubscribed = false;
            browserSubscribed = false;
            attachedPages.clear();
            killProcessTree(process);
            killByDebugPort();
            process = null;
        } catch (Exception e) {
            log.warn("[cdp] 启动失败后的清理未完成: {}", e.getMessage());
        }
    }

    /**
     * 创建 / 复用用户数据目录（临时目录为默认值）
     *
     * @return 用户数据目录
     */
    private Path createUserDataDir() throws IOException {
        String userDataDir = properties.getUserDataDir();
        if (StringUtils.isNotBlank(userDataDir)) {
            Path dir = Path.of(userDataDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        }
        // 使用系统临时目录，用完即离
        return Files.createTempDirectory("chua-cdp-");
    }

    /**
     * 注册所有事件的全局订阅者
     *
     * <p>只登记，不逐方法挂载。真正挂到 CDP 通道上的是每个通道一个 {@code all} 分发器，
     * 它在分发时动态读取 {@link #globalSubscribers}——因此后连上来的前端也能立刻生效，
     * 且同一个事件不会被投递多次。</p>
     *
     * @param handler 订阅者
     */
    public void subscribeAll(Consumer<Map<String, Object>> handler) {
        for (String method : EVENT_METHODS) {
            globalSubscribers.computeIfAbsent(method, k -> new ArrayList<>()).add(handler);
        }
        // 浏览器通道尚未建立 / 尚未安装分发器时，补装一次
        installBrowserDispatcher();
        for (String targetId : attachedPages) {
            // 每个页面通道只装一次 all 分发器，重复调用无副作用
            installPageDispatcher(targetId);
        }
    }

    /**
     * 往浏览器级通道装一个 all 分发器（只转发 Target.* 这类浏览器级事件）
     */
    private void installBrowserDispatcher() {
        CdpWsClient client = browserClient;
        if (client == null || browserSubscribed) {
            return;
        }
        browserSubscribed = true;
        client.on("all", msg -> {
            String method = eventMethod(msg);
            if (!BROWSER_EVENT_METHODS.contains(method)) {
                return;
            }
            List<Consumer<Map<String, Object>>> subs = globalSubscribers.get(method);
            if (subs == null || subs.isEmpty()) {
                return;
            }
            Map<String, Object> enriched = new LinkedHashMap<>(msg);
            for (Consumer<Map<String, Object>> sub : new ArrayList<>(subs)) {
                try {
                    sub.accept(enriched);
                } catch (Exception e) {
                    log.warn("[cdp] 订阅者处理失败 {}: {}", method, e.getMessage());
                }
            }
        });
    }

    /**
     * 读取事件消息里的 method（缺失时返回空串）
     *
     * @param message CDP 事件消息
     * @return 事件方法名
     */
    private static String eventMethod(Map<String, Object> message) {
        Object method = message.get("method");
        return method == null ? "" : String.valueOf(method);
    }

    /**
     * 往页面级通道装 all 分发器
     *
     * <p>页面通道的事件只属于这个页面，因此在这里补上 {@code targetId}，前端才能区分来源；
     * 原始事件里没有该字段，而 {@code Map.of} 不接受 null，直接塞会抛 NPE 并让整条推送消失。</p>
     *
     * @param targetId 页面标识
     */
    @SuppressWarnings("unchecked")
    private void installPageDispatcher(String targetId) {
        CdpWsClient client = pageClients.get(targetId);
        if (client == null) {
            return;
        }
        client.on("all", msg -> {
            String method = eventMethod(msg);
            if (!PAGE_EVENT_METHODS.contains(method)) {
                return;
            }
            List<Consumer<Map<String, Object>>> subs = globalSubscribers.get(method);
            if (subs == null || subs.isEmpty()) {
                return;
            }
            Map<String, Object> enriched = new LinkedHashMap<>(msg);
            enriched.putIfAbsent("targetId", targetId);
            for (Consumer<Map<String, Object>> sub : new ArrayList<>(subs)) {
                try {
                    sub.accept(enriched);
                } catch (Exception e) {
                    log.warn("[cdp] 订阅者处理失败 {}: {}", method, e.getMessage());
                }
            }
        });
    }

    /**
     * 把页面级事件接到某个目标上（幂等）
     *
     * <p>必须做两件事，缺一不可：</p>
     * <ol>
     *   <li>向页面通道发 {@code Runtime.enable} / {@code Network.enable} — 浏览器只在
     *       页面级通道上接受这两个域，发到浏览器级通道会被回 {@code -32601}，
     *       原实现就是这么做的，异常被 catch 吞掉后 Console / Network 永远为空；</li>
     *   <li>给该页面通道装一个 all 分发器 — 页面事件只在这个页面自己的通道上产生。</li>
     * </ol>
     *
     * @param targetId 页面标识
     */
    public void attachPageEvents(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        CdpWsClient client = pageClients.get(targetId);
        if (client == null) {
            try {
                client = openPageClient(targetId);
            } catch (Exception e) {
                log.debug("[cdp] 页面通道建立失败 {}: {}", targetId, e.getMessage());
                return;
            }
        }
        if (!attachedPages.add(targetId)) {
            return;
        }
        for (String domain : PAGE_ENABLED_DOMAINS) {
            try {
                client.call(domain, Map.of(), Duration.ofSeconds(10));
            } catch (Exception e) {
                log.debug("[cdp] 页面域启用失败 {} {}: {}", targetId, domain, e.getMessage());
            }
        }
        installPageDispatcher(targetId);
        log.debug("[cdp] 页面事件已就绪: {}", targetId);
    }


    /**
     * 等待远程调试的端口可访问并拉取版本
     */
    private void waitForRemoteDebugEnabled() {
        long deadline = System.currentTimeMillis() + properties.getLaunchTimeout().toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Map<String, Object> versionMap = version();
                if (versionMap != null) {
                    version = (String) versionMap.getOrDefault("version", "");
                    if (StringUtils.isNotBlank(version) || versionMap.containsKey("webSocketDebuggerUrl")) {
                        return;
                    }
                }
                Thread.sleep(200L);
            } catch (Exception e) {
                log.debug("[cdp] 等待浏览器就绪: {}", e.getMessage());
            }
        }
        throw new IllegalStateException("启动 Chromium 后等待 DevTools 超时");
    }

    /**
     * 拉取版本信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> version() {
        try {
            URL url = new URL("http://127.0.0.1:" + properties.getPort() + "/json/version");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(1000);
            try {
                return (Map<String, Object>) Json.fromJson(con.getInputStream(), Map.class);
            } finally {
                con.disconnect();
            }
        } catch (IOException e) {
            // 还没上线，后面轮询等
            return new LinkedHashMap<>();
        }
    }

    /**
     * 结束整个浏览器进程树
     *
     * <p>Windows 上 {@code destroy()} 只结束直接子进程；Chromium 会再派生浏览器进程，
     * 只 destroy 一层会留下孤儿进程占着调试端口。这里先 destroy 再对
     * {@code descendants()} 逐个 {@code destroyForcibly()}，最后兜底
     * {@code taskkill /T /F}。</p>
     *
     * @param process 启动器进程
     */
    private void killProcessTree(Process process) {
        if (process == null) {
            return;
        }
        ProcessHandle handle = process.toHandle();
        List<ProcessHandle> descendants = new ArrayList<>();
        try {
            handle.descendants().forEach(descendants::add);
        } catch (Exception ignored) {
            // 拿不到就算了，后面还有 taskkill 兜底
        }
        try {
            process.destroy();
        } catch (Exception ignored) {
        }
        for (ProcessHandle child : descendants) {
            try {
                child.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        try {
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[cdp] 杀进程被中断");
        }
        try {
            handle.destroyForcibly();
        } catch (Exception ignored) {
        }
        // 兜底：Chromium 在 Windows 上可能已经脱离父子关系，按命令行匹配强杀
        try {
            Process killer = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(handle.pid()))
                    .redirectErrorStream(true)
                    .start();
            killer.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[cdp] taskkill 兜底失败: {}", e.getMessage());
        }
    }

    /**
     * 清理占用本模块调试端口的浏览器进程
     *
     * <p>Chromium 在 Windows 上会脱离启动器的父子关系，进程树杀不到它；{@code start()}
     * 又每次都新建临时 user-data-dir，所以「本次目录」匹配不到历史遗留的孤儿进程。
     * 唯一稳定的归属标记是命令行里的 {@code --remote-debugging-port=<本模块端口>}。</p>
     *
     * <p>只清理由本模块启动的 headless 浏览器（命令行同时含 {@code --remote-debugging-port}
     * 与 {@code --headless}），绝不碰用户自己开的日常浏览器。</p>
     *
     * @return 是否清理掉了占用端口的进程
     */
    private boolean killByDebugPort() {
        int port = properties.getPort();
        String script = "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*--remote-debugging-port=" + port
                + "*' -and $_.CommandLine -like '*--headless*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }";
        try {
            Process killer = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            killer.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            log.info("[cdp] 已清理占用调试端口 {} 的残留无头浏览器", port);
            // 强杀是异步的：进程退出、端口从内核表里摘掉需要几十到几百毫秒。
            // 不等就立刻复查，会看到「刚被杀掉的进程」还占着端口，从而误判成
            // 「端口被别人占用，启动失败」——实测就是这样把 STARTED 变成 FAILED 的。
            awaitDebugPortClosed(5_000L);
            return true;
        } catch (Exception e) {
            log.warn("[cdp] 按调试端口清理残留进程失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 调试端口是否已在监听
     */
    private boolean isDebugPortListening() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", properties.getPort()), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 等调试端口真正关闭
     *
     * <p>端口不关说明还有残留浏览器进程，此时不能对外宣称「已停止」。</p>
     */
    private void awaitDebugPortClosed() {
        awaitDebugPortClosed(10_000L);
    }
    /**
     * 等调试端口真正关闭（可指定上限）
     *
     * @param timeoutMillis 最长等待毫秒数
     * @return 端口是否确实已关闭
     */
    private boolean awaitDebugPortClosed(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!isDebugPortListening()) {
                return true;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !isDebugPortListening();
            }
        }
        log.warn("[cdp] 停止后调试端口 {} 仍在监听，可能存在残留浏览器进程", properties.getPort());
        return !isDebugPortListening();
    }

    /**
     * 停止浏览器
     *
     * <p>注意：{@code Process.destroy()} 在 Windows 上只结束直接子进程，Chromium
     * 自己会再派生一层浏览器进程。只 destroy 启动器时，浏览器进程仍然活着、
     * 9222 端口仍然响应、页面还在——界面却显示「已停止」，这是最误导人的状态。
     * 因此按进程树强杀，并回收端口。</p>
     */
    public synchronized Status stop() {
        // 只有「确实可能还活着东西」的状态才需要清理：
        // STOPPED / NOT_STARTED 没有进程可杀；STARTED / STARTING / FAILED
        // 都可能有半启动的浏览器或残留在端口上的进程。
        if (state == Status.STOPPED || state == Status.NOT_STARTED) {
            return state;
        }
        boolean wasStarted = state == Status.STARTED;
        state = Status.STOPPING;
        try {
            if (browserClient != null) {
                browserClient.close();
                browserClient = null;
            }
            for (CdpWsClient client : new ArrayList<>(pageClients.values())) {
                client.close();
            }
            pageClients.clear();
            eventsSubscribed = false;
            browserSubscribed = false;
            attachedPages.clear();
            killProcessTree(process);
            // Chromium 会脱离父子关系，必须再按调试端口清一次
            killByDebugPort();
            process = null;
            // 杀掉后确认调试端口真的关了：没关就说明还有残留的浏览器进程，
            // 这时界面显示「已停止」但页面还活着，是最误导人的状态。
            if (!awaitDebugPortClosed(10_000L)) {
                state = Status.FAILED;
                runtime.put("error", "调试端口 " + properties.getPort() + " 仍在监听：停止未生效，可能还有残留浏览器进程");
                return state;
            }
            state = Status.STOPPED;
            if (wasStarted) {
                // 成功停止后，之前失败留下的错误信息不再有任何意义
                runtime.remove("error");
            }
            return state;
        } catch (Exception e) {
            log.warn("[cdp] 停止浏览器失败: {}", e.getMessage(), e);
            state = Status.FAILED;
            return state;
        } finally {
            runtime.put("state", "stopped");
        }
    }

    /**
     * @return 浏览器级客户端
     */
    public CdpWsClient browserClient() {
        return browserClient;
    }

    /**
     * @return 状态
     */
    public Status getState() {
        return state;
    }

    /**
     * 获取所有已打开的目标
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> targets() {
        try {
            URL url = new URL("http://127.0.0.1:" + properties.getPort() + "/json");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(1000);
            con.setRequestMethod("GET");
            try {
                return (List<Map<String, Object>>) Json.fromJson(
                        con.getInputStream(), new TargetListTypeReference());
            } finally {
                con.disconnect();
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /**
     * 拿到可用的浏览器级通道
     *
     * <p>浏览器进程可能被外部关掉（用户手动退出、崩溃、端口被回收），此时 WS 已经断开，
     * {@code browserClient} 仍是失效对象——直接调用会抛 {@code Output closed}，
     * 界面只看到「操作处理失败」。这里统一做自愈：断开或为空就重建浏览器。</p>
     *
     * @return 可用的浏览器级客户端
     */
    private CdpWsClient requireBrowserClient() {
        if (state == Status.STARTED && browserClient != null && !browserClient.isClosed()) {
            return browserClient;
        }
        if (browserClient != null) {
            // 已经断开：先标记并丢弃，避免 start() 里的幂等判断把它当成「还在跑」
            browserClient.markClosed();
            browserClient = null;
            state = Status.STOPPED;
        }
        start();
        if (browserClient == null) {
            throw new IllegalStateException("浏览器未启动");
        }
        return browserClient;
    }

    /**
     * 创建目标
     *
     * <p>如果浏览器已停止（如 {@code stop()} 之后），先自动恢复浏览器，再创建页面。</p>
     *
     * <p>新建完成后立刻把这个页面接上页面级事件域（{@code Runtime.enable} / {@code Network.enable}）：
     * 否则用户在没点过这个页面之前，它的 console / network 永远不会出现在面板里。</p>
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTarget(Map<String, Object> params) {
        CdpWsClient client = requireBrowserClient();
        Map<String, Object> result = (Map<String, Object>) client.call("Target.createTarget", params,
                properties.getCommandTimeout());
        Object targetId = result == null ? null : result.get("targetId");
        if (targetId != null) {
            attachPageEvents(String.valueOf(targetId));
        }
        return result;
    }

    /**
     * 关闭目标
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> closeTarget(String targetId) {
        CdpWsClient browser = requireBrowserClient();
        Map<String, Object> result = (Map<String, Object>) browser.call("Target.closeTarget",
                Map.of("targetId", targetId), properties.getCommandTimeout());
        CdpWsClient client = pageClients.remove(targetId);
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        attachedPages.remove(targetId);
        return result;
    }

    /**
     * 获取页面级 CDP 客户端（按需连接到 devtools/page/<targetId>）
     *
     * <p>新建 / 首次触达的页面通道会自动挂上页面级域与事件分发器。</p>
     *
     * @param targetId 页面标识
     * @return 页面级客户端
     */
    public CdpWsClient pageClient(String targetId) {
        CdpWsClient existing = pageClients.get(targetId);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        if (existing != null) {
            // 通道已断：丢掉旧对象，重新连一次
            pageClients.remove(targetId, existing);
            attachedPages.remove(targetId);
        }
        CdpWsClient client = openPageClient(targetId);
        // 页面新开：把 Runtime/Network/Page 域与事件分发器接上
        attachPageEvents(targetId);
        return client;
    }

    /**
     * 仅建立页面通道（不挂事件，避免与 {@link #attachPageEvents(String)} 递归）
     *
     * @param targetId 页面标识
     * @return 页面级客户端
     */
    private CdpWsClient openPageClient(String targetId) {
        String endpoint = "ws://127.0.0.1:" + properties.getPort() + "/devtools/page/" + targetId;
        CdpWsClient client = CdpWsClient.connect(endpoint, Duration.ofSeconds(15));
        pageClients.put(targetId, client);
        return client;
    }

    /**
     * 通过页面级客户端调用 CDP 命令
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pageCall(String targetId, String method, Map<String, Object> params) {
        return pageCall(targetId, method, params, properties.getCommandTimeout());
    }

    /**
     * 通过页面级客户端调用 CDP 命令（自定义超时）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pageCall(String targetId, String method, Map<String, Object> params, Duration timeout) {
        CdpWsClient client = pageClient(targetId);
        return (Map<String, Object>) client.call(method, params, timeout);
    }

    /**
     * 页面截屏
     *
     * <p>{@code Page.captureScreenshot} **没有** {@code fullPage} 参数——传了会被浏览器忽略，
     * 长页截屏和普通截屏结果一模一样（一样只有一屏高）。整页截屏必须自己量出
     * {@code Page.getLayoutMetrics} 里的内容尺寸，再放进 {@code clip} 并打开
     * {@code captureBeyondViewport}。</p>
     *
     * @param targetId 页面标识
     * @param fullPage 是否整页
     * @return base64 编码的 PNG；失败返回 null
     */
    @SuppressWarnings("unchecked")
    public String captureScreenshot(String targetId, boolean fullPage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("format", "png");
        if (fullPage) {
            Map<String, Object> metrics = pageCall(targetId, "Page.getLayoutMetrics", Map.of());
            Map<String, Object> size = null;
            Object contentSize = metrics.get("cssContentSize");
            if (contentSize instanceof Map<?, ?> css) {
                size = (Map<String, Object>) css;
            }
            if (size == null && metrics.get("contentSize") instanceof Map<?, ?> raw) {
                size = (Map<String, Object>) raw;
            }
            if (size != null) {
                double width = toDouble(size.get("width"));
                double height = toDouble(size.get("height"));
                Map<String, Object> clip = new LinkedHashMap<>();
                clip.put("x", 0);
                clip.put("y", 0);
                clip.put("width", width > 0 ? width : properties.getViewportWidth());
                clip.put("height", height > 0 ? height : properties.getViewportHeight());
                clip.put("scale", 1);
                params.put("clip", clip);
                params.put("captureBeyondViewport", true);
            }
        }
        Map<String, Object> result = pageCall(targetId, "Page.captureScreenshot", params,
                properties.getScreenshotTimeout());
        Object data = result == null ? null : result.get("data");
        return data == null ? null : String.valueOf(data);
    }

    /**
     * 取数值（CDP 里尺寸可能是 Integer / Double / Long）
     */
    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 通过页面级客户端调用 CDP 事件
     */
    public void pageOn(String targetId, String method, Consumer<Map<String, Object>> handler) {
        CdpWsClient client = pageClient(targetId);
        client.on(method, handler);
    }

    /**
     * 暴露控制台等事件监听
     *
     * @param method 方法名，如 Runtime.consoleAPICalled
     * @param handler 处理程序，有时抛出异常
     */
    public void on(String method, Consumer<Map<String, Object>> handler) {
        if (browserClient == null) {
            throw new IllegalStateException("浏览器未启动");
        }
        if (!eventsSubscribed) {
            subscribeEvents();
        }
        browserClient.on(method, handler);
    }

    /**
     * 获取当前的页面
     */
    public List<Map<String, Object>> pages() {
        List<Map<String, Object>> targets = targets();
        List<Map<String, Object>> pages = new ArrayList<>();
        for (Map<String, Object> t : targets) {
            if ("page".equals(t.get("type"))) {
                pages.add(t);
            }
        }
        return pages;
    }

    /**
     * @return 状态与页面信息
     */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("port", properties.getPort());
        result.put("state", state);
        result.put("endpoint", wsEndpoint);
        result.put("version", version);
        result.put("targetCount", targetCount());
        result.putAll(runtime);
        // 以状态字段为准（stop() 会把 runtime.state 写为 "stopped"，优先保证大小写规范）
        result.put("state", state);
        return result;
    }

    /**
     * 当前页面的数量
     */
    private int targetCount() {
        return targets().size();
    }

    /**
     * 启动事件通道
     *
     * <p>浏览器级只挂 {@code Target.*}（{@link #installBrowserDispatcher()}）；页面级域与事件
     * 分发器交给 {@link #attachPageEvents(String)}，在目标创建 / 首次触达时挂载。</p>
     */
    private void subscribeEvents() {
        if (eventsSubscribed || browserClient == null) {
            return;
        }
        eventsSubscribed = true;
        installBrowserDispatcher();
        // 先把已有页面接上（浏览器启动时自带一个 about:blank）
        for (Map<String, Object> target : targets()) {
            if ("page".equals(target.get("type"))) {
                attachPageEvents(String.valueOf(target.get("id")));
            }
        }
    }

    /**
     * 应用启动后，打开浏览器
     */
    @PostConstruct
    public void init() {
        if (properties.isEnabled()) {
            try {
                start();
            } catch (RuntimeException e) {
                log.warn("[cdp] 启动 CDP 引擎失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 应用关闭前，释放浏览器
     */
    @PreDestroy
    public void destroy() {
        if (properties.isEnabled()) {
            try {
                stop();
            } catch (RuntimeException e) {
                log.warn("[cdp] 停止 CDP 引擎失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 目标列表 TypeReference
     */
    private static final class TargetListTypeReference extends com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>> {
    }
}
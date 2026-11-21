// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.cdp.service;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.browser.cdp.CdpBrowserEngine;
import com.chua.starter.cdp.CdpEventBuffer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
            "Network.webSocketCreated",
            "Network.webSocketFrameSent",
            "Network.webSocketFrameReceived",
            "Network.webSocketFrameError",
            "Page.frameStartedLoading",
            "Page.frameNavigated",
            "Page.frameDetached",
            "Page.frameAttached",
            "Page.loadEventFired",
            "Page.navigateFrameCompleted",
            "Page.navigateRequested",
            "Page.screenshot",
            "Page.screencastFrame",
            "Tracing.dataCollected",
            "Tracing.tracingComplete",
            "Tracing.bufferUsage",
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
            "Network.webSocketCreated",
            "Network.webSocketFrameSent",
            "Network.webSocketFrameReceived",
            "Network.webSocketFrameError",
            "Page.frameStartedLoading",
            "Page.frameNavigated",
            "Page.frameDetached",
            "Page.frameAttached",
            "Page.loadEventFired",
            "Page.navigateFrameCompleted",
            "Page.navigateRequested",
            "Page.screenshot",
            "Page.screencastFrame",
            "Tracing.dataCollected",
            "Tracing.tracingComplete",
            "Tracing.bufferUsage"
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
     * 控制台 / 网络事件环形缓冲（REST 一次性拉取的数据源）
     */
    private final CdpEventBuffer eventBuffer;

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
     * 目标地址备忘：页面被 DevTools 通道挂上后，Edge 157 的 {@code GET /json} 不再回填
     * 该页面的 url（实测恒为空串），前端目标轨道只剩裸 targetId。这里记下各目标的最近
     * 已知地址（创建地址 / Target.targetChanged / Page.frameNavigated），供
     * {@link #targets()} 在 /json 给不出地址时回填；目标消失时随列表对账清理。
     */
    private final Map<String, String> targetUrlHints = new ConcurrentHashMap<>();

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
     * 已装过「页面级 all 事件分发器」的目标。
     *
     * <p>分发器是闭包，运行时动态读 {@link #globalSubscribers}，因此一个页面通道
     * 只需装一次；{@link #subscribeAll} 每次 SSE 连接都会重新遍历 {@link #attachedPages}，
     * 若不做幂等，同一个事件会被投递 N 次（实测每连一次 SSE 就多一份重复帧）。</p>
     */
    private final Set<String> pageDispatchers = ConcurrentHashMap.newKeySet();

    /**
     * 全局事件订阅者（通过 CDN 回传到每个 WS 前端 / SSE 流）
     *
     * <p>用 {@link java.util.concurrent.CopyOnWriteArrayList}：订阅 / 退订与事件分发
     * （{@code installXxxDispatcher} 里以 {@code new ArrayList<>(subs)} 快照遍历）会并发发生，
     * 普通 {@code ArrayList} 在遍历时被改动会抛 {@code ConcurrentModificationException}，
     * 直接吞掉整批事件。且必须在连接关闭时 {@link #unsubscribeAll} 移除，否则会无限堆积。</p>
     */
    private final Map<String, CopyOnWriteArrayList<Consumer<Map<String, Object>>>> globalSubscribers = new ConcurrentHashMap<>();

    /**
     * 开始一个浏览器
     *
     * <p>幂等：已经在跑就直接返回。但「在跑」不能只看状态字段——浏览器进程可能
     * 被外部关掉、WS 可能已断开，那时状态仍是 {@code STARTED}，界面显示正常
     * 却什么都操作不了。所以这里先校验端口与通道是否真的活着，不活就按
     * STOPPED 重来。</p>
     *
     * @return 启动后的状态
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
            assertBrowserLaunchable();
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
                // 端口被占 ≠ 必须杀进程：常见情形是上一次会话遗留的「自家无头浏览器」还活着
                //（后端重启、浏览器级 WS 通道静默断开都会走到这里），它本身完全健康。
                // 优先凭启动时留下的浏览器指纹直接复用：省一次冷启动，也绕开跨账号
                // 杀进程被拒的权限坑（NETWORK SERVICE 的 JVM 未必杀得掉别的账号名下进程）。
                if (tryAdoptExistingBrowser()) {
                    return state;
                }
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
            // 新 headless（Chrome/Edge 132+）里位置 URL 会与启动初始页构成多目标，
            // 触发 chrome_main.cc 的 "Multiple targets are not supported in headless mode"
            // 而直接拒绝启动；页面目标本就由 CDP 按需创建，headless 时不再传位置 URL。
            if (!properties.isHeadless()) {
                command.add("about:blank");
            }
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
            recordBrowserGuid(debugUrl);
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
     * 启动前置环境校验
     *
     * <p>SYSTEM 令牌下 Chromium/Edge 会在进程创建瞬间静默退出（无头模式亦然，
     * chrome.log 零输出、无 WER 记录），导致 {@link #waitForRemoteDebugEnabled()}
     * 固定等待 {@code launchTimeout} 后才超时。此处提前识别，秒级给出可行动的
     * 错误信息，避免前端与运维对着「等待 DevTools 超时」去排查网络与启动参数。</p>
     */
    private void assertBrowserLaunchable() {
        String user = ProcessHandle.current().info().user().orElse("");
        if (user.equalsIgnoreCase("SYSTEM") || user.toLowerCase().endsWith("\\system")) {
            throw new IllegalStateException("后端 JVM 正以 SYSTEM 身份运行，Chromium/Edge 无法在 SYSTEM 令牌下启动"
                    + "（无头进程会立即静默退出）。请将后端服务改用非 SYSTEM 账号运行"
                    + "（如 NETWORK SERVICE 或普通管理员账号）后再启用 CDP 调试浏览器。");
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
                // [P3C F1 豁免]
                } catch (Exception ignored) {
                }
                browserClient = null;
            }
            for (CdpWsClient client : new ArrayList<>(pageClients.values())) {
                try {
                    client.close();
                // [P3C F1 豁免]
                } catch (Exception ignored) {
                }
            }
            pageClients.clear();
            eventsSubscribed = false;
            browserSubscribed = false;
            attachedPages.clear();
            pageDispatchers.clear();
            // 浏览器都没了，旧页面的事件不再有意义
            eventBuffer.clear();
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
            // addIfAbsent：全局 SSE 广播器可能随多次连接重复 subscribeAll，
            // 重复 add 会让同一条事件被推送 N 份（实测每连一次 SSE 就多一份重复帧）
            globalSubscribers.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>()).addIfAbsent(handler);
        }
        // 浏览器通道尚未建立 / 尚未安装分发器时，补装一次
        installBrowserDispatcher();
        for (String targetId : attachedPages) {
            // installPageDispatcher 内部按 pageDispatchers 幂等，重复调用无副作用
            installPageDispatcher(targetId);
        }
    }

    /**
     * 注销一个全局订阅者（连接关闭 / SSE 断开时必须调用）
     *
     * <p>旧实现只在 {@code subscribeAll} 里 add，从不 remove：前端每刷新一次页面，
     * WS 重连就往 {@link #globalSubscribers} 追加一个指向已关闭会话的 handler，
     * 广播时不断抛异常、内存无限增长，也是「刷新后功能失效」的隐藏元凶之一。</p>
     *
     * @param handler {@link #subscribeAll} 时传入的同一个引用
     */
    public void unsubscribeAll(Consumer<Map<String, Object>> handler) {
        if (handler == null) {
            return;
        }
        for (List<Consumer<Map<String, Object>>> subs : globalSubscribers.values()) {
            subs.remove(handler);
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
            Map<String, Object> enriched = new LinkedHashMap<>(msg);
            // Target.* 不是控制台 / 网络事件，缓冲会自行忽略；统一过一遍保持链路一致
            eventBuffer.record(enriched);
            trackTargetUrlChange(method, enriched);
            List<Consumer<Map<String, Object>>> subs = globalSubscribers.get(method);
            if (subs == null || subs.isEmpty()) {
                return;
            }
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
        // 一个页面通道只装一次 all 分发器：分发器是闭包，运行时动态读 globalSubscribers，
        // 订阅者增减都不需要重装。重复装会让同一条事件被投递多份。
        // 页面通道重连时会从 pageDispatchers 移除该目标，届时可重新装。
        if (!pageDispatchers.add(targetId)) {
            return;
        }
        client.on("all", msg -> {
            String method = eventMethod(msg);
            if (!PAGE_EVENT_METHODS.contains(method)) {
                return;
            }
            Map<String, Object> enriched = new LinkedHashMap<>(msg);
            enriched.putIfAbsent("targetId", targetId);
            // 先写环形缓冲再做订阅者分发：没有 SSE 订阅者在线时，REST 一次性拉取也要有数据
            eventBuffer.record(enriched);
            if ("Page.frameNavigated".equals(method)) {
                trackFrameNavigatedUrl(targetId, enriched);
            }
            List<Consumer<Map<String, Object>>> subs = globalSubscribers.get(method);
            if (subs == null || subs.isEmpty()) {
                return;
            }
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
     *
     * @return 版本信息映射（含 webSocketDebuggerUrl / version 等）；还没上线时返回空映射
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
        // [P3C F1 豁免]
        } catch (Exception ignored) {
        }
        for (ProcessHandle child : descendants) {
            try {
                child.destroyForcibly();
            // [P3C F1 豁免]
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
        // [P3C F1 豁免]
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
     * 尝试直接复用调试端口上已存活的浏览器
     *
     * <p>后端重启或浏览器级 WebSocket 静默断开时，Edge/Chromium 进程本身仍然健康、
     * 调试端口仍在监听。此时杀掉重启既浪费一次冷启动，也可能因权限/安全软件拦截
     * 而杀不掉（194 实测 destroyForcibly / taskkill / Stop-Process 都杀不动），
     * 把可自愈的故障放大成「端口被占用」导致 CDP 长期不可用——因此复用判定必须
     * 极度稳健：/json/version 用独立的长超时加重试取回；指纹（devtools browser GUID，
     * 每次启动随机生成、不重号）一致时直接复用；指纹缺失或不符时，鉴于本端口是
     * 模块专用调试端口，仍然接管（否则在杀不掉进程的环境里没有第二条路）。</p>
     *
     * @return 复用成功返回 true；失败时回收半连接状态并返回 false（调用方继续走清理重启路径）
     */
    private boolean tryAdoptExistingBrowser() {
        int port = properties.getPort();
        Map<String, Object> versionMap = fetchVersionForAdoption();
        String debugUrl = versionMap == null ? null : (String) versionMap.get("webSocketDebuggerUrl");
        if (StringUtils.isBlank(debugUrl)) {
            // /json/version 都不响应，说明不是（或尚未就绪的）DevTools 端点，不能盲目接管
            log.info("[cdp] 调试端口 {} 上的进程未响应 DevTools /json/version，不复用", port);
            return false;
        }
        String liveGuid = guidOf(debugUrl);
        String guid = readRecordedBrowserGuid();
        if (StringUtils.isBlank(guid)) {
            log.info("[cdp] 无本模块浏览器指纹记录，调试端口为本模块专用端口，尝试接管既有无头浏览器（现场指纹 {}）", liveGuid);
        } else if (!guid.equals(liveGuid)) {
            log.warn("[cdp] 调试端口 {} 上的浏览器与记录指纹不符（记录 {}，现场 {}），仍尝试接管", port, guid, liveGuid);
        }
        try {
            wsEndpoint = debugUrl;
            browserClient = CdpWsClient.connect(debugUrl, Duration.ofSeconds(30));
            subscribeEvents();
            if (StringUtils.isBlank(guid)) {
                // 接管成功后把指纹补记上，后续重启可走精确匹配
                recordBrowserGuid(debugUrl);
            }
            if (StringUtils.isBlank(version)) {
                // 复用路径不经过正常启动的版本解析，version 字段此时是缺省 null；
                // ConcurrentHashMap 不允许 null value，直接 put 会抛 message 为 null 的 NPE
                // （真实事故：复用判定、WS 连接、事件订阅全部通过，却在收尾一步 NPE，
                // 被日志记成「复用失败: null」而转入清理重启，复用自愈形同虚设）。
                Object browserName = versionMap.get("Browser");
                version = browserName == null ? "" : String.valueOf(browserName);
            }
            runtime.put("version", version);
            runtime.put("port", port);
            runtime.put("targetCount", 0);
            runtime.put("startedAt", System.currentTimeMillis());
            runtime.remove("error");
            state = Status.STARTED;
            log.info("[cdp] 已复用调试端口 {} 上的存活无头浏览器（指纹 {}），跳过重启", port, liveGuid);
            return true;
        } catch (Exception e) {
            log.info("[cdp] 复用调试端口 {} 上已有浏览器失败，转入清理重启流程: {}", port, e.getMessage());
            if (browserClient != null) {
                try {
                    browserClient.close();
                // [P3C F1 豁免]
                } catch (Exception ignored) {
                }
                browserClient = null;
            }
            eventsSubscribed = false;
            browserSubscribed = false;
            return false;
        }
    }

    /**
     * 供复用判定使用的 /json/version 拉取
     *
     * <p>独立于 {@link #version()}：后者 1s 连接超时是为「启动后轮询」设计的，
     * 在 194 这类内存吃紧、开机即分页抖动的机器上会把健康的浏览器误判成无响应
     * （真实事故：复用被误判成「指纹不符」而拒绝）。这里用 5s 超时 + 3 次重试。</p>
     *
     * @return 版本信息映射；三次都失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchVersionForAdoption() {
        int port = properties.getPort();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                URL url = new URL("http://127.0.0.1:" + port + "/json/version");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(5_000);
                con.setReadTimeout(5_000);
                try {
                    Object parsed = Json.fromJson(con.getInputStream(), Map.class);
                    if (parsed instanceof Map) {
                        return (Map<String, Object>) parsed;
                    }
                } finally {
                    con.disconnect();
                }
            } catch (IOException e) {
                log.info("[cdp] 读取调试端口 {} 的 /json/version 失败（第 {} 次）: {}", port, attempt, e.getMessage());
            }
            try {
                Thread.sleep(2_000L);
            // [P3C F1 豁免]
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    /**
     * @return DevTools WebSocket 端点里的浏览器指纹（最后一段 GUID）
     */
    private String guidOf(String debugUrl) {
        int idx = debugUrl.lastIndexOf('/');
        return idx >= 0 ? debugUrl.substring(idx + 1) : debugUrl;
    }

    /**
     * 记录本次启动的浏览器指纹（devtools browser GUID），供重启后安全复用
     *
     * @param debugUrl 浏览器级 DevTools WebSocket 端点
     */
    private void recordBrowserGuid(String debugUrl) {
        try {
            Files.write(browserGuidFile(), guidOf(debugUrl).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[cdp] 记录浏览器指纹失败: {}", e.getMessage());
        }
    }

    /**
     * @return 启动时记录的浏览器指纹；没有记录返回空串
     */
    private String readRecordedBrowserGuid() {
        try {
            Path file = browserGuidFile();
            if (!Files.exists(file)) {
                return "";
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private Path browserGuidFile() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "chua-cdp-browser.guid");
    }

    /**
     * 清理占用本模块调试端口的浏览器进程
     *
     * <p>两个实测约束决定了实现方式：</p>
     * <ul>
     *   <li>本机（含管理员上下文）所有进程的 {@code ProcessHandle.info().commandLine()}
     *       均不可读，按命令行匹配「自家无头浏览器」永远落空；</li>
     *   <li>PowerShell 冷启动在本机可达数十秒，同步等待它的旧实现会在 PowerShell
     *       完成前超时，误报「已清理」而端口原封不动。</li>
     * </ul>
     *
     * <p>因此改用 {@code netstat -ano}（秒级）定位监听 pid，核对可读的
     * {@code command()} 确属 Chromium 系浏览器后定点清除（destroyForcibly +
     * taskkill /T /F 兜底），再等待端口真正释放。</p>
     *
     * @return 端口是否已释放
     */
    private boolean killByDebugPort() {
        int port = properties.getPort();
        // 实测两坑：① 本机（含管理员上下文）所有进程的 ProcessHandle.info().commandLine()
        // 均不可读，纯 Java 按命令行匹配「自家无头浏览器」永远落空；② 194 内存吃紧时
        // 连 netstat 都会超过 15s，PowerShell 冷启动可达 45s+——同步等待窗口短了，
        // 就会误报「已清理」而端口原封不动（真实事故）。
        // 因此：异步触发 PowerShell 清理（WMI 命令行匹配，只杀带
        // --remote-debugging-port=<port> 且 --headless 的自家浏览器，不碰日常浏览器），
        // 由本方法轮询端口释放情况判定结果，窗口拉长到 120s 并周期性补触发。
        long deadline = System.currentTimeMillis() + 120_000L;
        long lastFired = 0L;
        boolean fired = false;
        while (System.currentTimeMillis() < deadline) {
            if (!isDebugPortListening()) {
                if (fired) {
                    log.info("[cdp] 已清理占用调试端口 {} 的残留无头浏览器", port);
                }
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastFired >= 40_000L) {
                fireDebugPortKiller(port);
                lastFired = now;
                fired = true;
            }
            try {
                Thread.sleep(3_000L);
            // [P3C F1 豁免]
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        boolean closed = !isDebugPortListening();
        if (!closed) {
            log.warn("[cdp] 清理调试端口 {} 的残留浏览器超时（120s），端口仍被占用", port);
        }
        return closed;
    }

    /**
     * 异步触发一次「按命令行匹配本模块无头浏览器并强杀」的 PowerShell 清理
     *
     * <p>不等待其完成（冷启动可达 45s+），由 {@link #killByDebugPort()} 的端口轮询
     * 判定实际效果；看护线程在 90s 后仍未退出时强制回收，避免残留 powershell 进程。</p>
     *
     * @param port 调试端口
     */
    private void fireDebugPortKiller(int port) {
        String script = "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*--remote-debugging-port=" + port
                + "*' -and $_.CommandLine -like '*--headless*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }";
        try {
            Process killer = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            Thread reaper = new Thread(() -> {
                try {
                    if (!killer.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                        killer.destroyForcibly();
                    }
                // [P3C F1 豁免]
                } catch (InterruptedException ignored) {
                }
            });
            reaper.setDaemon(true);
            reaper.setName("cdp-port-killer-reaper");
            reaper.start();
        } catch (IOException e) {
            log.warn("[cdp] 触发调试端口 {} 清理进程失败: {}", port, e.getMessage());
        }
    }

    /**
     * 调试端口是否已在监听
     *
     * @return true 表示端口可连接
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
     *
     * @return 停止后的状态（端口未释放时为 FAILED）
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
            pageDispatchers.clear();
            // 浏览器要停了，旧页面的事件不再有意义
            eventBuffer.clear();
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
     *
     * @return 目标列表；失败时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> targets() {
        // Chrome 忙时 /json 响应会到秒级：连接超时 1s 太紧，一抖就把目标列表打成空，
        // 前端面板会跟着清空。放宽到连接 3s / 读 5s，失败后再重试一次。
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                URL url = new URL("http://127.0.0.1:" + properties.getPort() + "/json");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(3000);
                con.setReadTimeout(5000);
                con.setRequestMethod("GET");
                try {
                    List<Map<String, Object>> list = (List<Map<String, Object>>) Json.fromJson(
                            con.getInputStream(), new TargetListTypeReference());
                    return enrichTargetUrls(list);
                } finally {
                    con.disconnect();
                }
            } catch (Exception ignored) {
                // 重试一次；仍失败则回空列表
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 从浏览器级 {@code Target.*} 事件里维护目标地址备忘
     *
     * <p>{@code targetCreated} / {@code targetChanged} 的 {@code targetInfo.url} 是可靠的
     * 地址来源；{@code targetDestroyed} / {@code targetCrashed} 顺手清掉对应备忘。</p>
     *
     * @param method  事件名
     * @param message 事件消息（含 params）
     */
    private void trackTargetUrlChange(String method, Map<String, Object> message) {
        Object paramsRaw = message.get("params");
        if (!(paramsRaw instanceof Map<?, ?> params)) {
            return;
        }
        if ("Target.targetDestroyed".equals(method) || "Target.targetCrashed".equals(method)) {
            Object destroyedId = params.get("targetId");
            if (destroyedId != null) {
                targetUrlHints.remove(String.valueOf(destroyedId));
            }
            return;
        }
        if ("Target.targetCreated".equals(method) || "Target.targetChanged".equals(method)) {
            Object infoRaw = params.get("targetInfo");
            if (infoRaw instanceof Map<?, ?> info) {
                Object changedId = info.get("targetId");
                Object url = info.get("url");
                if (changedId != null && url != null && StringUtils.isNotBlank(String.valueOf(url))) {
                    targetUrlHints.put(String.valueOf(changedId), String.valueOf(url));
                }
            }
        }
    }

    /**
     * 从页面级 {@code Page.frameNavigated} 事件里刷新目标地址备忘（只认主框架）
     *
     * @param targetId 页面标识
     * @param message  事件消息（含 params.frame）
     */
    private void trackFrameNavigatedUrl(String targetId, Map<String, Object> message) {
        Object paramsRaw = message.get("params");
        if (!(paramsRaw instanceof Map<?, ?> params)) {
            return;
        }
        Object frameRaw = params.get("frame");
        if (!(frameRaw instanceof Map<?, ?> frame) || frame.get("parentId") != null) {
            return;
        }
        Object url = frame.get("url");
        if (url != null && StringUtils.isNotBlank(String.valueOf(url))) {
            targetUrlHints.put(targetId, String.valueOf(url));
        }
    }

    /**
     * 回填目标列表里被浏览器吞掉的 url
     *
     * <p>页面被 DevTools 通道挂上后 {@code GET /json} 的 url 恒为空串（Edge 157 实测），
     * 这里用事件里记下的最近已知地址回填；/json 能给出真实地址时以此为准并回写备忘。
     * 顺带按当前列表清理已消失目标的备忘，避免映射无限增长。</p>
     *
     * @param list 浏览器 /json 原始列表
     * @return 回填后的列表（原列表不做原地修改）
     */
    private List<Map<String, Object>> enrichTargetUrls(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        Set<String> alive = ConcurrentHashMap.newKeySet();
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Map<String, Object> target : list) {
            Object id = target.get("id");
            if (id == null) {
                result.add(target);
                continue;
            }
            String tid = String.valueOf(id);
            alive.add(tid);
            Object url = target.get("url");
            if (url != null && StringUtils.isNotBlank(String.valueOf(url))) {
                targetUrlHints.put(tid, String.valueOf(url));
                result.add(target);
                continue;
            }
            String hint = targetUrlHints.get(tid);
            if (hint == null || hint.isBlank()) {
                result.add(target);
                continue;
            }
            Map<String, Object> patched = new LinkedHashMap<>(target);
            patched.put("url", hint);
            result.add(patched);
        }
        targetUrlHints.keySet().retainAll(alive);
        return result;
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
     * <p>优先走 DevTools HTTP 端点 {@code PUT /json/new}（同步导航、返回完整 target）；
     * 端点不可用时回退浏览器级 WS 命令 {@code Target.createTarget}（此时若浏览器已停止，
     * {@link #requireBrowserClient()} 会先自动恢复浏览器再创建）。</p>
     *
     * <p>创建完成后把这个页面接上页面级事件域（{@code Runtime.enable} / {@code Network.enable}）：
     * 否则用户在没点过这个页面之前，它的 console / network 永远不会出现在面板里。
     * 事件域挂载较慢且允许失败，异步执行，不阻塞创建响应。</p>
     *
     * @param params 创建目标参数（如 url）
     * @return 创建结果（含 targetId）；失败时可能为 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTarget(Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String url = String.valueOf(safeParams.getOrDefault("url", "about:blank"));
        Map<String, Object> result = createTargetViaHttpEndpoint(url);
        if (result == null) {
            CdpWsClient client = requireBrowserClient();
            result = (Map<String, Object>) client.call("Target.createTarget", safeParams,
                    properties.getCommandTimeout());
        }
        Object targetId = result == null ? null : result.getOrDefault("targetId", result.get("id"));
        if (targetId != null) {
            String tid = String.valueOf(targetId);
            result.put("targetId", tid);
            // 页面通道一挂上，/json 就不再回填 url：先把创建地址记进备忘
            if (StringUtils.isNotBlank(url)) {
                targetUrlHints.put(tid, url);
            }
            Thread attachThread = new Thread(() -> attachPageEvents(tid), "cdp-attach-" + tid);
            attachThread.setDaemon(true);
            attachThread.start();
        }
        return result;
    }

    /**
     * 通过 DevTools HTTP 端点 {@code PUT /json/new?<url>} 创建页面
     *
     * <p>实测（Edge 157 @194）：走 WS {@code Target.createTarget} 创建的页面，
     * {@code GET /json} 列表里 url/title 全为空串，前端目标轨道只能显示裸 targetId；
     * 且该 WS 命令在内存吃紧的宿主上响应可达 30s+，拖到前端请求超时。
     * {@code /json/new} 同步完成导航并返回完整 target 信息（url 立即可用），响应毫秒级。</p>
     *
     * <p>注意：{@code /json/new} 把 {@code ?} 之后的整段 query 当作目标地址本身——
     * 写成 {@code ?url=...} 或做百分号编码都会被当成非法地址，页面落到 about:blank（实测复现）。
     * 因此这里直接拼接原始地址，仅把空格转成 {@code %20} 保证 HTTP 请求行合法。</p>
     *
     * @param url 初始地址
     * @return target 信息（id / url / title / webSocketDebuggerUrl）；端点不可用时返回 null
     */
    private Map<String, Object> createTargetViaHttpEndpoint(String url) {
        try {
            String query = url.replace(" ", "%20");
            URL endpoint = new URL("http://127.0.0.1:" + properties.getPort() + "/json/new?" + query);
            HttpURLConnection con = (HttpURLConnection) endpoint.openConnection();
            con.setRequestMethod("PUT");
            con.setConnectTimeout(5_000);
            con.setReadTimeout(15_000);
            try {
                Object parsed = Json.fromJson(con.getInputStream(), Map.class);
                return parsed instanceof Map ? (Map<String, Object>) parsed : null;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            log.info("[cdp] /json/new 创建页面失败，回退浏览器级 WS 命令: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 关闭目标
     *
     * @param targetId 页面标识
     * @return 关闭结果；失败时可能为 null
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
            // [P3C F1 豁免]
            } catch (Exception ignored) {
            }
        }
        attachedPages.remove(targetId);
        pageDispatchers.remove(targetId);
        targetUrlHints.remove(targetId);
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
            // 通道已断：丢掉旧对象，重新连一次；分发器标记也要清，否则新通道装不上
            pageClients.remove(targetId, existing);
            attachedPages.remove(targetId);
            pageDispatchers.remove(targetId);
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
     *
     * @param targetId 页面标识
     * @param method   CDP 方法名
     * @param params   调用参数
     * @return 调用结果（含 result 或 error 等信息）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pageCall(String targetId, String method, Map<String, Object> params) {
        return pageCall(targetId, method, params, properties.getCommandTimeout());
    }

    /**
     * 通过页面级客户端调用 CDP 命令（自定义超时）
     *
     * @param targetId 页面标识
     * @param method   CDP 方法名
     * @param params   调用参数
     * @param timeout  超时时间
     * @return 调用结果（含 result 或 error 等信息）
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
     *
     * @param value 待转换值
     * @return 数值，无法解析时返回 0
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
     *
     * @param targetId 页面标识
     * @param method   事件方法名
     * @param handler  事件处理器
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
     *
     * @return 类型为 page 的目标列表
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
     *
     * @return 目标总数
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
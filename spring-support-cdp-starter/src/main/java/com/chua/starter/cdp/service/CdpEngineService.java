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
     */
    private static final List<String> EVENT_METHODS = List.of(
            "Runtime.consoleAPI",
            "Runtime.exceptionThrown",
            "Runtime.bindingCalled",
            "Network.requestWillBeSent",
            "Network.responseReceived",
            "Network.requestServedFromCache",
            "Network.dataReceived",
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
     * 需要启用的 CDP 域
     */
    private static final List<String> ENABLED_DOMAINS = List.of(
            "Network.enable",
            "Runtime.enable",
            "Page.enable",
            "Performance.enable",
            "Accessibility.enable"
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
     * 全局事件订阅者（通过 CDN 回传到每个 WS 前端）
     */
    private final Map<String, List<Consumer<Map<String, Object>>>> globalSubscribers = new ConcurrentHashMap<>();

    /**
     * 开始一个浏览器
     */
    public Status start() {
        if (state == Status.STARTED) {
            return state;
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
            state = Status.STARTED;
            return state;
        } catch (RuntimeException | IOException e) {
            state = Status.FAILED;
            runtime.put("error", e.getMessage());
            log.error("[cdp] 启动失败: {}", e.getMessage(), e);
            stop();
            throw new IllegalStateException(e);
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
     * @param handler 订阅者
     */
    public void subscribeAll(Consumer<Map<String, Object>> handler) {
        boolean subscribed = false;
        for (String method : EVENT_METHODS) {
            List<Consumer<Map<String, Object>>> list = globalSubscribers.computeIfAbsent(method, k -> new ArrayList<>());
            if (!list.contains(handler)) {
                list.add(handler);
                subscribed = true;
            }
        }
        if (subscribed) {
            publishGlobalSubscribers();
        }
    }

    /**
     * 把全局订阅者注册到浏览器级客户端
     */
    private void publishGlobalSubscribers() {
        if (browserClient == null) {
            return;
        }
        for (String method : EVENT_METHODS) {
            List<Consumer<Map<String, Object>>> list = globalSubscribers.get(method);
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (Consumer<Map<String, Object>> handler : list) {
                try {
                    browserClient.on(method, handler);
                } catch (Exception ignored) {
                }
            }
        }
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
     * 停止浏览器
     */
    public Status stop() {
        if (state != Status.STARTED) {
            return state;
        }
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
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[cdp] 杀进程被中断");
                }
            }
            process = null;
            state = Status.STOPPED;
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
     * 创建目标
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTarget(Map<String, Object> params) {
        if (browserClient == null) {
            throw new IllegalStateException("浏览器未启动");
        }
        return (Map<String, Object>) browserClient.call("Target.createTarget", params, properties.getCommandTimeout());
    }

    /**
     * 关闭目标
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> closeTarget(String targetId) {
        if (browserClient == null) {
            throw new IllegalStateException("浏览器未启动");
        }
        Map<String, Object> result = (Map<String, Object>) browserClient.call("Target.closeTarget",
                Map.of("targetId", targetId), properties.getCommandTimeout());
        pageClients.remove(targetId);
        return result;
    }

    /**
     * 获取页面级 CDP 客户端（按需连接到 devtools/page/<targetId>）
     *
     * @param targetId 页面标识
     * @return 页面级客户端
     */
    public CdpWsClient pageClient(String targetId) {
        CdpWsClient existing = pageClients.get(targetId);
        if (existing != null) {
            return existing;
        }
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
        CdpWsClient client = pageClient(targetId);
        return (Map<String, Object>) client.call(method, params, properties.getCommandTimeout());
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
     * @param method 方法名，如 Runtime.consoleAPI
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
        return result;
    }

    /**
     * 当前页面的数量
     */
    private int targetCount() {
        return targets().size();
    }

    /**
     * 启动页面级的事件 + 命令通道
     */
    @SuppressWarnings("unchecked")
    private void subscribeEvents() {
        if (eventsSubscribed || browserClient == null) {
            return;
        }
        eventsSubscribed = true;
        CdpWsClient client = browserClient;
        for (String domain : ENABLED_DOMAINS) {
            try {
                client.call(domain, Map.of(), Duration.ofSeconds(10));
            } catch (Exception ignored) {
                // 不是所有版本的浏览器或目标都支持
            }
        }
        // 把全局的订阅者挂到 YUG 里
        for (String method : EVENT_METHODS) {
            List<Consumer<Map<String, Object>>> subs = globalSubscribers.get(method);
            if (subs == null || subs.isEmpty()) {
                continue;
            }
            for (Consumer<Map<String, Object>> sub : subs) {
                try {
                    client.on(method, sub);
                } catch (Exception ignored) {
                }
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

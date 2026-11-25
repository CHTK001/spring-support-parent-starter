package com.chua.starter.cdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * CDP 调试代理配置
 *
 * <p>所有 CDP（Chrome DevTools Protocol）能力受 {@code spring.cdp.enabled} 总开关控制；
 * 模块启动后（{@code enabled=true}）会自动在宿主机启动 Chromium，
 * 暴露固定调试端口 {@code port}（<p>9222</p>）和 WebSocket 端点 {@code wsPath}，
 * 前端 {@code pages/debug} 直接连 WebSocket 即可获取控制台 / 网络 / DOM 能力。</p>
 *
 * <p><h3>浏览器探测</h3>
 * 如果未指定 {@code executable}，启动时先调用 {@link com.chua.common.support.browser.cdp.CdpBrowserEngine#locateBrowser(String)}
 * 按环境变量 / Playwright 缓存 / 搜索路径寻找 Chromium / Chrome 可执行文件。</p>
 *
 * <p><h3>完整配置示例（application.yml）</h3>
 * 配置前缀 {@code spring.cdp}，实时页面反向代理嵌套前缀 {@code spring.cdp.live-proxy}：</p>
 * <pre>{@code
 * spring:
 *   cdp:
 *     enabled: true              # 总开关（默认 true）
 *     port: 9222                 # Chromium 调试端口（默认 9222）
 *     # executable: D:/tools/chrome/chrome.exe   # 缺省自动探测
 *     headless: true             # 无头启动（默认 true）
 *     # user-data-dir: D:/cdp-profile           # 缺省用系统临时目录 chua-cdp-*
 *     # proxy: http://127.0.0.1:7890            # 浏览器代理（缺省直连）
 *     viewport-width: 1280       # 窗口宽（默认 1280）
 *     viewport-height: 900       # 窗口高（默认 900）
 *     launch-timeout: 120s       # 启动超时（默认 120s）
 *     command-timeout: 30s       # 页面级 CDP 命令超时（默认 30s）
 *     screenshot-timeout: 60s    # 截屏超时（默认 60s）
 *     ws-path: /ws/cdp           # 前端 WebSocket 路径（默认 /ws/cdp）
 *     live-proxy:
 *       enabled: true            # 反向代理开关（默认 true）
 *       port: 9223               # 代理监听端口（默认 9223）
 *       host: 0.0.0.0            # 监听地址（默认 0.0.0.0）
 *       # external-url: http://192.168.1.10:9223   # 经反代/Nginx 转发时必填
 *       session-ttl: 30m         # 会话空闲 TTL（默认 30m）
 *       max-sessions: 8          # 最大并发会话（默认 8）
 *       allowed-hosts: []        # 上游主机白名单（空 = 不限制）
 *       inject-script: true      # 注入 base 与 fetch/XHR 等钩子（默认 true）
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "spring.cdp")
public class CdpProperties {

    /**
     * 总开关，false 时不开启任何 CDP 能力
     *
     * <p>含义：运行时开关——{@code false} 时 Bean 仍全部注册，但引擎不再拉起 Chromium、
     * 反向代理不再监听 {@code live-proxy.port}。
     * 默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 暴露给浏览器的调试端口（如 9222），浏览器启动后可直接连 `${port}` 获取版本
     *
     * <p>含义：以 {@code --remote-debugging-port=<port>} 传给 Chromium；启动前若被无头残留
     * 浏览器占用会先清理，仍被其它进程占用则启动失败。
     * 默认值：{@code 9222}（按配置原样使用，无 ≤0 回落）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     port: 9222
     * }</pre>
     */
    // [P3C A20 豁免]
    private int port = 9222;

    /**
     * Chrome/Chromium 可执行文件路径；为空时按环境变量 + Playwright 缓存自动探测
     *
     * <p>含义：浏览器命令行的第 0 个参数（可执行文件本身）。
     * 默认值：无（{@code null}）；为空时回落到
     * {@link com.chua.common.support.browser.cdp.CdpBrowserEngine#locateBrowser(String)}
     * 自动探测——依次尝试环境变量 {@code CHUA_CHROME} / {@code CHROME_PATH} / {@code CHROME_BIN}、
     * Playwright 缓存与常见安装路径；仍找不到则浏览器启动失败。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     executable: D:/tools/chrome/chrome.exe
     * }</pre>
     */
    private String executable;

    /**
     * 是否无头启动（新增：`--headless=new`），默认无头
     *
     * <p>含义：{@code true} 追加 {@code --headless=new} 且不传初始位置 URL；{@code false} 时
     * 以 {@code about:blank} 有头启动。
     * 默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     headless: true
     * }</pre>
     */
    private boolean headless = true;

    /**
     * 用户数据目录（持久化 Cookies/LocalStorage/书签）
     * ，默认使用系统临时目录；无法持久化
     *
     * <p>含义：以 {@code --user-data-dir=<dir>} 传给 Chromium；目录不存在会自动创建，
     * 并追加 {@code --auto-open-devtools-for-tabs}。
     * 默认值：无（{@code null}）；为空时回落到系统临时目录 {@code chua-cdp-*}——用完即弃，
     * 重启后 Cookies / 登录态不保留。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     user-data-dir: D:/cdp-profile
     * }</pre>
     */
    private String userDataDir;

    /**
     * 代理地址（如 `http://127.0.0.1:7890`），可选
     *
     * <p>含义：非空时以 {@code --proxy-server=<proxy>} 传给 Chromium，浏览器流量走该代理。
     * 默认值：无（{@code null}）；为空时不加代理参数，浏览器直连。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     proxy: http://127.0.0.1:7890
     * }</pre>
     */
    private String proxy;

    /**
     * 全局 User-Agent，默认使用系统自带
     *
     * <p>含义：非空时以 {@code --user-agent=<ua>} 传给 Chromium，覆盖页面默认 UA。
     * 默认值：无（{@code null}）；为空时使用浏览器自带 UA。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0 Safari/537.36
     * }</pre>
     */
    private String userAgent;

    /**
     * 窗口宽度（visual viewport）
     *
     * <p>含义：与 {@code viewportHeight} 拼成 {@code --window-size=<w>,<h>}；整页截屏未显式
     * 指定宽度时也用它兜底。
     * 默认值：{@code 1280}（按配置原样使用，无 ≤0 回落）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     viewport-width: 1280
     * }</pre>
     */
    // [P3C A20 豁免]
    private int viewportWidth = 1280;

    /**
     * 窗口高度（visual viewport）
     *
     * <p>含义：与 {@code viewportWidth} 拼成 {@code --window-size=<w>,<h>}；整页截屏未显式
     * 指定高度时也用它兜底。
     * 默认值：{@code 900}（按配置原样使用，无 ≤0 回落）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     viewport-height: 900
     * }</pre>
     */
    // [P3C A20 豁免]
    private int viewportHeight = 900;

    /**
     * 启动超时
     *
     * <p>含义：浏览器进程拉起后，等待 {@code http://127.0.0.1:<port>/json/version} 可访问的
     * 最长时间，超时判定启动失败。
     * 默认值：{@code 120s}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     launch-timeout: 120s
     * }</pre>
     */
    // [P3C A20 豁免]
    private Duration launchTimeout = Duration.ofSeconds(120);

    /**
     * 页面级 CDP 命令超时
     *
     * <p>{@code Page.captureScreenshot} 在真实页面上会做整页栅格化，840×808 的一屏
     * 大约要 1~3 秒，整页更高；30 秒对截屏偏紧（实测偶发超时），因此单列一个更宽的
     * 截屏超时。</p>
     *
     * <p>含义：{@code Page.navigate} / {@code Runtime.evaluate} / 目标创建与关闭等页面级
     * CDP 命令的默认超时。
     * 默认值：{@code 30s}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     command-timeout: 30s
     * }</pre>
     */
    // [P3C A20 豁免]
    private Duration commandTimeout = Duration.ofSeconds(30);

    /**
     * 截屏超时（整页栅格化比普通命令慢得多）
     *
     * <p>含义：{@code Page.captureScreenshot} 专用超时。
     * 默认值：{@code 60s}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     screenshot-timeout: 60s
     * }</pre>
     */
    // [P3C A20 豁免]
    private Duration screenshotTimeout = Duration.ofSeconds(60);

    /**
     * 用户级额外参数，给浏览器命令行追加 `--xxx`
     *
     * <p>含义：列表逐条追加到 Chromium 命令行末尾（如 {@code --disable-gpu}、{@code --no-sandbox}）。
     * 默认值：空列表（不追加任何额外参数）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     args:
     *       - --disable-gpu
     *       - --no-sandbox
     * }</pre>
     */
    private List<String> args = new ArrayList<>();

    /**
     * 暴露给前端的 WebSocket 路径（如 `/ws/cdp`）
     *
     * <p>含义：{@code CdpWebSocketConfig} 注册 CDP 指令透传端点（{@code CdpWebSocketHandler}）
     * 的路径，allowedOrigins 放开为 {@code *}。
     * 默认值：{@code /ws/cdp}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     ws-path: /ws/cdp
     * }</pre>
     */
    private String wsPath = "/ws/cdp";

    /**
     * 是否启用控制台事件订阅（`Runtime.consoleAPICalled`），默认开
     *
     * <p>含义：<b>预留项</b>——当前版本代码未读取该值：控制台事件在页面通道建立时随
     * {@code Runtime.enable} 一起开启，不能用它单独关闭控制台订阅。
     * 默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     enable-console: true
     * }</pre>
     */
    private boolean enableConsole = true;

    /**
     * 是否启用网络事件订阅（`Network.enable` + `Runtime.responseReceived`），默认开
     *
     * <p>含义：<b>预留项</b>——当前版本代码未读取该值：网络事件在页面通道建立时随
     * {@code Network.enable} 一起开启，不能用它单独关闭网络订阅。
     * 默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     enable-network: true
     * }</pre>
     */
    private boolean enableNetwork = true;

    /**
     * 实时页面反向代理配置
     *
     * <p>在独立端口上启动一个反向代理：无头浏览器与用户浏览器（iframe）都经由它访问
     * 目标站点，共享同一份服务端 Cookie 罐；响应剥离 X-Frame-Options / CSP 后
     * 注入 {@code <base>} 与 fetch/XHR 钩子，保证页面内的请求继续走代理。</p>
     *
     * <p>配置前缀：{@code spring.cdp.live-proxy}（嵌套于 {@code spring.cdp}）。
     * YAML 示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     live-proxy:
     *       enabled: true
     *       port: 9223
     *       session-ttl: 30m
     * }</pre>
     */
    private final LiveProxy liveProxy = new LiveProxy();

    /**
     * 实时页面反向代理配置项
     *
     * <p>配置前缀：{@code spring.cdp.live-proxy}。完整示例：</p>
     * <pre>{@code
     * spring:
     *   cdp:
     *     live-proxy:
     *       enabled: true            # 开关（默认 true）
     *       port: 9223               # 监听端口（默认 9223）
     *       host: 0.0.0.0            # 监听地址（默认 0.0.0.0）
     *       # external-url: http://192.168.1.10:9223   # 经反代/Nginx 转发时必填
     *       session-ttl: 30m         # 会话空闲 TTL（默认 30m）
     *       max-sessions: 8          # 最大并发会话（默认 8）
     *       allowed-hosts: []        # 上游主机白名单（空 = 不限制）
     *       inject-script: true      # 注入 base 与 fetch/XHR 等钩子（默认 true）
     * }</pre>
     */
    @Data
    public static class LiveProxy {

        /**
         * 是否启用实时页面代理
         *
         * <p>含义：{@code false} 时不启动反向代理端口（默认 9223），主服务与其它 CDP 能力不受影响。
         * 默认值：{@code true}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       enabled: true
         * }</pre>
         */
        private boolean enabled = true;

        /**
         * 代理监听端口（独立于主服务端口；浏览器 iframe 直连该端口）
         *
         * <p>含义：chua NIO 反向代理的监听端口；被占用仅记日志，不影响主服务与其它 CDP 能力。
         * 默认值：{@code 9223}（按配置原样使用，无 ≤0 回落）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       port: 9223
         * }</pre>
         */
        // [P3C A20 豁免]
        private int port = 9223;

        /**
         * 监听地址；默认全部网卡（前端与后端可能不同机）
         *
         * <p>含义：反向代理绑定地址。
         * 默认值：{@code 0.0.0.0}；生产建议改绑内网地址以收敛暴露面。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       host: 0.0.0.0
         * }</pre>
         */
        private String host = "0.0.0.0";

        /**
         * 对外暴露的代理基地址（如 {@code http://192.168.1.10:9223}）。
         * 前端经开发代理访问后端时 Host 头会被改写，此时必须显式配置；
         * 缺省按后端收到请求的 Host 推导。
         *
         * <p>含义：创建代理会话时返回的 {@code proxyBase} 基地址。
         * 默认值：无（{@code null}）；为空时按后端收到请求的 Host + 代理端口推导。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       external-url: http://192.168.1.10:9223
         * }</pre>
         */
        private String externalUrl;

        /**
         * 会话空闲 TTL，超时自动关闭（含无头浏览器页面）
         *
         * <p>含义：代理会话空闲超过该时长即被清扫线程关闭（每 30s 清扫一次）。
         * 默认值：{@code 30m}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       session-ttl: 30m
         * }</pre>
         */
        private Duration sessionTtl = Duration.ofMinutes(30);

        /**
         * 最大并发会话数；超限时关闭最久未用的会话
         *
         * <p>含义：并发代理会话上限，超限按最久未用（LRU）淘汰。
         * 默认值：{@code 8}（按配置原样使用，无 ≤0 回落）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       max-sessions: 8
         * }</pre>
         */
        // [P3C A20 豁免]
        private int maxSessions = 8;

        /**
         * 上游主机白名单；空 = 不限制（调试工具语义，仅建议内网/可信环境放开）
         *
         * <p>含义：非空时上游主机必须与白名单项精确相等或为其子域（大小写不敏感）；
         * 同时禁止把代理自身（{@code localhost} / {@code 127.0.0.1} 的代理端口）配为上游。
         * 默认值：空列表（不限制上游主机）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       allowed-hosts:
         *         - example.com
         *         - internal.example.com
         * }</pre>
         */
        private List<String> allowedHosts = new ArrayList<>();

        /**
         * 是否向 HTML 注入 {@code <base>} 与 fetch/XHR/点击/表单钩子
         *
         * <p>含义：注入后页面内的后续请求继续走代理，保证 iframe 内浏览不跳失。
         * 默认值：{@code true}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * spring:
         *   cdp:
         *     live-proxy:
         *       inject-script: true
         * }</pre>
         */
        private boolean injectScript = true;
    }
}

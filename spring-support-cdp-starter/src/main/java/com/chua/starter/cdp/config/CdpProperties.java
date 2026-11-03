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
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "spring.cdp")
public class CdpProperties {

    /**
     * 总开关，false 时不开启任何 CDP 能力
     */
    private boolean enabled = true;

    /**
     * 暴露给浏览器的调试端口（如 9222），浏览器启动后可直接连 `${port}` 获取版本
     */
    private int port = 9222;

    /**
     * Chrome/Chromium 可执行文件路径；为空时按环境变量 + Playwright 缓存自动探测
     */
    private String executable;

    /**
     * 是否无头启动（新增：`--headless=new`），默认无头
     */
    private boolean headless = true;

    /**
     * 用户数据目录（持久化 Cookies/LocalStorage/书签）
     * ，默认使用系统临时目录；无法持久化
     */
    private String userDataDir;

    /**
     * 代理地址（如 `http://127.0.0.1:7890`），可选
     */
    private String proxy;

    /**
     * 全局 User-Agent，默认使用系统自带
     */
    private String userAgent;

    /**
     * 窗口宽度（visual viewport）
     */
    private int viewportWidth = 1280;

    /**
     * 窗口高度（visual viewport）
     */
    private int viewportHeight = 900;

    /**
     * 启动超时
     */
    private Duration launchTimeout = Duration.ofSeconds(120);

    /**
     * 页面级 CDP 命令超时
     *
     * <p>{@code Page.captureScreenshot} 在真实页面上会做整页栅格化，840×808 的一屏
     * 大约要 1~3 秒，整页更高；30 秒对截屏偏紧（实测偶发超时），因此单列一个更宽的
     * 截屏超时。</p>
     */
    private Duration commandTimeout = Duration.ofSeconds(30);

    /**
     * 截屏超时（整页栅格化比普通命令慢得多）
     */
    private Duration screenshotTimeout = Duration.ofSeconds(60);

    /**
     * 用户级额外参数，给浏览器命令行追加 `--xxx`
     */
    private List<String> args = new ArrayList<>();

    /**
     * 暴露给前端的 WebSocket 路径（如 `/ws/cdp`）
     */
    private String wsPath = "/ws/cdp";

    /**
     * 是否启用控制台事件订阅（`Runtime.consoleAPICalled`），默认开
     */
    private boolean enableConsole = true;

    /**
     * 是否启用网络事件订阅（`Network.enable` + `Runtime.responseReceived`），默认开
     */
    private boolean enableNetwork = true;
}

package com.chua.starter.ai.support.manage.service;

import com.chua.common.support.browser.BrowserOptions;
import com.chua.common.support.browser.BrowserPage;
import com.chua.common.support.browser.cdp.CdpBrowserEngine;
import com.chua.common.support.browser.cdp.CdpLiveServer;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 供应商余额「账号密码登录」执行器。
 *
 * <p>查询类型 {@code extra.credit_kind = account-login} 时，余额令牌不再手工填写：
 * 页面上的「登录」按钮触发本服务，弹出内嵌浏览器窗口（CDP 引擎，common 层
 * {@link CdpBrowserEngine}，有头模式），由用户在窗口内自行输入账号密码完成上游门户
 * 登录；服务端按余额 SPI 的「账号登录」钩子截获会话令牌：</p>
 * <ul>
 *   <li>{@link CreditQueryProvider#loginPageUrl(String)}：登录页地址；</li>
 *   <li>{@link CreditQueryProvider#loginCaptureUrlPattern()}：令牌交换接口的地址特征，
 *       命中的响应体里按 {@link CreditQueryProvider#loginTokenJsonPath()} 下钻取令牌；</li>
 *   <li>{@link CreditQueryProvider#loginCookieName()}：登录成功后会话 Cookie 的键名；</li>
 *   <li>{@link CreditQueryProvider#loginStorageKey()}：令牌所在的 localStorage 键名。</li>
 * </ul>
 *
 * <p>登记表没收录的供应商也能用账密登录：在扩展配置里显式给出 {@code credit_login_url}
 * （登录页）与任一截获钩子——{@code credit_capture_pattern}（令牌交换接口的地址特征，
 * 正则）、{@code credit_token_path}（响应体里令牌的 JSON 路径，留空递归找
 * access_token / token）、{@code credit_cookie_name}（会话 Cookie 键名）、
 * {@code credit_storage_key}（localStorage 键名）——本服务按同一套轮询逻辑截获令牌。
 * 显式配置优先于 SPI 默认值。</p>
 *
 * <p>截获到的令牌回填 {@code extra.credit_key}（与 SPI 契约一致），余额查询、面板、
 * 快照等既有链路零改动复用。账号密码只出现在弹出的浏览器窗口里，不落库。</p>
 *
 * <p>实现说明：令牌交换响应的截获在页面上下文完成——注入的初始化脚本覆写
 * {@code fetch} / {@code XMLHttpRequest}，把命中地址特征的响应体暂存到
 * {@code window.__chuaCreditCaptures} 与 sessionStorage，Java 侧轮询读取。
 * 登录窗口使用独立引擎（有头），避免污染共享无头引擎的进程级参数。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCreditLoginService {

    /**
     * 查询类型标识（extra.credit_kind）：账号密码登录
     */
    public static final String KIND = "account-login";

    /**
     * 页面上暂存截获响应的变量名（window 属性 + sessionStorage 键同名）
     */
    private static final String CAPTURE_STORE = "__chuaCreditCaptures";

    /**
     * 等待用户在弹出窗口里完成登录的时间上限（毫秒）：覆盖打开门户、输入账号密码、
     * 验证码 / 滑块与登录跳转全程；超时未截获令牌按失败返回，窗口随即关闭
     */
    private static final long TOKEN_WAIT_MILLIS = 180_000L;

    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MILLIS = 800L;

    /**
     * 静默续期的回访等待上限（毫秒）：无头页面加载 + 平台可能的令牌轮换窗口。
     * 比人工登录短得多——没有输入环节，页面几十秒内还读不到任何令牌即认为登录态失效
     */
    private static final long RENEW_WAIT_MILLIS = 45_000L;

    /**
     * 单供应商余额密钥条数上限：多账号登录不断追加，超过上限时淘汰最早的令牌，
     * 防止误操作把密钥列表撑爆。
     */
    private static final int MAX_CREDIT_KEYS = 50;

    /**
     * 实时登录会话结束后结果的保留时长（毫秒）：前端轮询到终态前会话必须还在，
     * 终态后再留一段余量让迟到的轮询也能拿到结果，过期由回收器清理。
     */
    private static final long LIVE_RESULT_TTL_MILLIS = 15L * 60L * 1000L;

    /**
     * 实时登录会话回收周期（秒）
     */
    private static final long LIVE_REAPER_PERIOD_SECONDS = 60L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 渠道服务：仅用 updateExtra 回填截获到的令牌（不触碰渠道表其它字段）；
     * 追加合并前用 find 重读最新行，避免登录期间其它保存被旧快照覆盖
     */
    private final AccountChannelService accountChannelService;

    /**
     * 内嵌实时登录画面服务（懒启动）：把无头登录浏览器的画面以 MJPEG 推流、
     * 把用户操作经 CDP Input 派发回去，前端一个 iframe 即可「远程操作」服务端
     * 浏览器——登录窗口不再弹在服务端桌面上，而是嵌进管理页面里。
     */
    private volatile CdpLiveServer liveServer;

    /**
     * 实时登录会话表：sessionToken → 会话（含进行中与已出终态待领取的）
     */
    private final Map<String, LiveSession> liveSessions = new ConcurrentHashMap<>();

    /**
     * 终态会话回收器
     */
    private volatile ScheduledThreadPoolExecutor liveReaper;

    /**
     * 登录结果。
     *
     * @param ok       是否成功截获令牌
     * @param token    截获的会话令牌（成功时有值）
     * @param message  结果说明（失败时为原因）
     * @param loginUrl 实际打开的登录页地址
     */
    public record LoginOutcome(boolean ok, String token, String message, String loginUrl) {
    }

    /**
     * 静默续期结果。
     *
     * @param status   结果状态：{@link #RENEWED} / {@link #VALID_NO_ROTATION} / {@link #REJECTED}
     * @param token    平台轮换出的新令牌（仅 {@link #RENEWED} 时有值）
     * @param message  结果说明
     * @param renewUrl 实际回访的页面地址
     */
    public record RenewOutcome(String status, String token, String message, String renewUrl) {

        /**
         * 平台在回访时轮换了令牌，新令牌已截获
         */
        public static final String RENEWED = "renewed";

        /**
         * 门户会话仍有效，本次回访未轮换令牌（旧令牌继续可用）
         */
        public static final String VALID_NO_ROTATION = "valid-no-rotation";

        /**
         * 回访未读到会话令牌：登录态已失效，需要人工重新登录
         */
        public static final String REJECTED = "rejected";
    }

    /**
     * 登录 / 续期共用的令牌截获钩子集。
     *
     * @param loginUrl       登录页地址（extra.credit_login_url 优先，回落 SPI 登记页）
     * @param capturePattern 令牌交换接口的地址特征（extra.credit_capture_pattern 优先）
     * @param tokenPath      响应体里令牌的 JSON 路径（extra.credit_token_path 优先；空为递归找）
     * @param cookieName     会话 Cookie 键名（extra.credit_cookie_name 优先）
     * @param storageKey     localStorage 键名（extra.credit_storage_key 优先）
     */
    private record LoginHooks(String loginUrl, String capturePattern, String tokenPath,
                              String cookieName, String storageKey) {

        /**
         * 是否配置了至少一个令牌截获钩子。
         *
         * @return 配了地址特征 / Cookie / localStorage 任一即为 true
         */
        boolean hasCaptureHook() {
            return nonBlank(capturePattern) || nonBlank(cookieName) || nonBlank(storageKey);
        }
    }

    /**
     * 解析登录 / 续期共用的截获钩子：扩展配置显式值优先，回落余额 SPI 的登记默认值。
     *
     * <p>候选链里没有可登录实现（SPI 为 null）的供应商，只要在 extra 里配齐
     * {@code credit_login_url} 与任一截获钩子，即可走同一套 CDP 登录截获链路。</p>
     *
     * @param entity 供应商
     * @param spi    余额 SPI 实现（可为 null）
     * @param extra  供应商扩展配置
     * @return 截获钩子集
     */
    private LoginHooks hooksFor(SysAccountChannel entity, CreditQueryProvider spi, Map<String, Object> extra) {
        String baseUrl = entity.getBaseUrl();
        return new LoginHooks(
                firstNonBlank(text(extra.get("credit_login_url")),
                        spi == null ? null : spi.loginPageUrl(baseUrl)),
                firstNonBlank(text(extra.get("credit_capture_pattern")),
                        spi == null ? null : spi.loginCaptureUrlPattern()),
                firstNonBlank(text(extra.get("credit_token_path")),
                        spi == null ? null : spi.loginTokenJsonPath()),
                firstNonBlank(text(extra.get("credit_cookie_name")),
                        spi == null ? null : spi.loginCookieName()),
                firstNonBlank(text(extra.get("credit_storage_key")),
                        spi == null ? null : spi.loginStorageKey()));
    }

    /**
     * 弹出登录窗口，等待用户完成登录并截获会话令牌。
     *
     * <p>用独立有头 CDP 引擎打开登录页（真实浏览器窗口，用户在其中手动输入
     * 账号密码），轮询 SPI 钩子直到截获令牌或超时。成功时把令牌回填
     * {@code extra.credit_key}（persist=true 时落库）；失败不抛异常，
     * 返回带原因的 {@link LoginOutcome}，由调用方决定提示方式。</p>
     *
     * @param entity  供应商
     * @param spi     余额 SPI 实现（提供登录页与截获钩子；可为 null——此时按 extra 的通用截获配置执行）
     * @param persist 截获成功后是否把令牌落库（登录按钮 true，试查不弹窗）
     * @return 登录结果
     */
    public LoginOutcome login(SysAccountChannel entity, CreditQueryProvider spi, boolean persist) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        LoginHooks hooks = hooksFor(entity, spi, extra);
        String loginUrl = hooks.loginUrl();
        if (!nonBlank(loginUrl)) {
            return new LoginOutcome(false, null,
                    "该供应商没有可用的登录页（未收录登录入口，可在扩展配置 credit_login_url 里指定）", null);
        }
        if (!hooks.hasCaptureHook()) {
            return new LoginOutcome(false, null,
                    "该供应商未配置令牌截获规则：请补 credit_capture_pattern"
                            + "（令牌接口地址特征）或 credit_cookie_name / credit_storage_key",
                    loginUrl);
        }
        // 独立有头引擎：进程级参数（无头/视口等）以本引擎首次启动为准，
        // 与共享无头引擎互不影响；用完必须 close 释放浏览器进程
        CdpBrowserEngine engine = new CdpBrowserEngine();
        BrowserOptions browserOptions = new BrowserOptions().setHeadless(false).setWidth(1280).setHeight(900);
        try {
            engine.start(browserOptions);
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-login] 供应商 {} 启动登录浏览器失败: {}", entity.getProviderId(), e.getMessage());
            return new LoginOutcome(false, null, "无法启动登录浏览器：" + e.getMessage(), loginUrl);
        }
        // 必须先开空白页、注册截获脚本、再导航到登录页：addInitScript 只对「之后加载」的文档生效，
        // 若先 openPage(loginUrl) 再注册，登录页首屏就发出的令牌交换请求不会被截获（静默续期同序）
        try (BrowserPage page = engine.openPage(browserOptions)) {
            String capturePattern = hooks.capturePattern();
            if (nonBlank(capturePattern)) {
                page.addInitScript(captureScript(capturePattern));
            }
            page.navigate(loginUrl);
            String token = awaitToken(page, hooks, capturePattern);
            if (token == null || token.isBlank()) {
                return new LoginOutcome(false, null,
                        "等待登录超时：请在弹出的浏览器窗口内完成上游门户登录后重试", loginUrl);
            }
            if (persist) {
                persistToken(entity.getProviderId(), token);
            }
            log.info("[credit-login] 供应商 {} 登录成功，令牌 {} 已{}追加为余额密钥",
                    entity.getProviderId(), mask(token), persist ? "" : "未");
            return new LoginOutcome(true, token, "登录成功", loginUrl);
        } catch (RuntimeException e) {
            log.warn("[credit-login] 供应商 {} 登录失败: {}", entity.getProviderId(), e.getMessage());
            return new LoginOutcome(false, null, "登录执行失败：" + e.getMessage(), loginUrl);
        } finally {
            engine.close();
        }
    }

    /**
     * 静默续期：无头浏览器预置旧令牌回访上游门户，截获轮换出的新令牌。
     *
     * <p>与 {@link #login} 的差别：不弹窗、无需用户输入——把旧令牌（Cookie /
     * localStorage）预置进无头页面后打开「已登录落地页」，页面 SDK 认会话：
     * 平台若在回访时轮换令牌，新值按登录截获钩子（Cookie / localStorage /
     * 响应截获）读到并随 {@code renewed} 返回；整窗只见到旧令牌说明会话有效但
     * 平台未轮换（{@code valid-no-rotation}，旧令牌继续可用）；全程读不到任何
     * 令牌说明登录态已失效（{@code rejected}，由调用方告警并引导人工重登）。
     * 不落库——新令牌由调用方与续期状态字段合并后一次写入。</p>
     *
     * @param entity 供应商
     * @param spi    余额 SPI 实现（提供回访页与截获钩子；可为 null——此时按 extra 的通用截获配置执行）
     * @return 续期结果
     */
    public RenewOutcome renewByPageVisit(SysAccountChannel entity, CreditQueryProvider spi) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        List<String> keys = splitCreditKeys(text(extra.get("credit_key")));
        if (keys.isEmpty()) {
            return new RenewOutcome(RenewOutcome.REJECTED, null, "没有可续期的余额令牌", null);
        }
        return renewByPageVisit(entity, spi, keys.get(0));
    }

    /**
     * 静默续期（指定令牌）：多账号下逐令牌各回访一次，互不影响。
     *
     * @param entity   供应商
     * @param spi      余额 SPI 实现（提供回访页与截获钩子；可为 null）
     * @param oldToken 要续期的旧令牌（预置进无头页面作为登录态）
     * @return 续期结果
     */
    public RenewOutcome renewByPageVisit(SysAccountChannel entity, CreditQueryProvider spi, String oldToken) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        if (oldToken == null || oldToken.isBlank()) {
            return new RenewOutcome(RenewOutcome.REJECTED, null, "没有可续期的余额令牌", null);
        }
        LoginHooks hooks = hooksFor(entity, spi, extra);
        String renewUrl = spi == null ? null : spi.renewPageUrl(entity.getBaseUrl());
        if (!nonBlank(renewUrl)) {
            renewUrl = hooks.loginUrl();
        }
        if (!nonBlank(renewUrl)) {
            return new RenewOutcome(RenewOutcome.REJECTED, null, "该供应商没有可回访的续期页面", null);
        }
        if (!hooks.hasCaptureHook()) {
            return new RenewOutcome(RenewOutcome.REJECTED, null,
                    "该供应商未配置令牌截获规则：请补 credit_capture_pattern"
                            + "（令牌接口地址特征）或 credit_cookie_name / credit_storage_key",
                    renewUrl);
        }
        String capturePattern = hooks.capturePattern();
        // 独立无头引擎：续期无人值守，弹窗没有意义；进程级参数以本引擎首次启动为准，
        // 与共享无头引擎、登录有头引擎互不影响；用完必须 close 释放浏览器进程
        CdpBrowserEngine engine = new CdpBrowserEngine();
        try {
            engine.start(new BrowserOptions().setHeadless(true));
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-renew] 供应商 {} 启动续期浏览器失败: {}", entity.getProviderId(), e.getMessage());
            return new RenewOutcome(RenewOutcome.REJECTED, null, "无法启动续期浏览器：" + e.getMessage(), renewUrl);
        }
        try (BrowserPage page = engine.openPage()) {
            if (nonBlank(capturePattern)) {
                page.addInitScript(captureScript(capturePattern));
            }
            String cookieName = hooks.cookieName();
            if (nonBlank(cookieName)) {
                page.setCookiesForUrl(Map.of(cookieName, oldToken), renewUrl);
            }
            String storageKey = hooks.storageKey();
            if (nonBlank(storageKey)) {
                page.addInitScript(presetStorageScript(storageKey, oldToken));
            }
            page.navigate(renewUrl);
            // 整窗轮询：预置的旧令牌首轮就能读到，不能见旧即停——平台可能在页面
            // 加载数秒后才轮换；读到「与旧令牌不同」的值才算轮换成功
            String firstSeen = null;
            long deadline = System.currentTimeMillis() + RENEW_WAIT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                String token = peekToken(page, hooks, capturePattern);
                if (token != null && !token.isBlank()) {
                    if (firstSeen == null) {
                        firstSeen = token;
                    }
                    if (!token.equals(oldToken)) {
                        log.info("[credit-renew] 供应商 {} 平台已轮换令牌，新令牌 {}",
                                entity.getProviderId(), mask(token));
                        return new RenewOutcome(RenewOutcome.RENEWED, token, "平台已轮换令牌，新令牌已截获", renewUrl);
                    }
                }
                sleep();
            }
            if (firstSeen != null) {
                log.info("[credit-renew] 供应商 {} 门户会话仍有效，本次回访未轮换令牌", entity.getProviderId());
                return new RenewOutcome(RenewOutcome.VALID_NO_ROTATION, null,
                        "门户会话仍有效，本次回访未轮换令牌", renewUrl);
            }
            log.warn("[credit-renew] 供应商 {} 回访门户未读到会话令牌，登录态可能已失效", entity.getProviderId());
            return new RenewOutcome(RenewOutcome.REJECTED, null,
                    "回访门户未读到会话令牌：登录态可能已失效，请人工重新登录", renewUrl);
        } catch (RuntimeException e) {
            log.warn("[credit-renew] 供应商 {} 续期回访失败: {}", entity.getProviderId(), e.getMessage());
            return new RenewOutcome(RenewOutcome.REJECTED, null, "续期回访执行失败：" + e.getMessage(), renewUrl);
        } finally {
            engine.close();
        }
    }

    /**
     * 预置 localStorage 令牌的初始化脚本：在页面任何脚本运行前写入旧令牌，
     * 让页面 SDK 以「已登录」状态启动（登录截获钩子随后可读到轮换出的新值）。
     *
     * @param key   localStorage 键名
     * @param token 旧令牌
     * @return 初始化脚本
     */
    private String presetStorageScript(String key, String token) {
        return "(function(){ try { localStorage.setItem("
                + quote(key) + ", " + quote(token) + "); } catch (e) {} })()";
    }

    /**
     * 回填截获到的令牌：以「追加」语义合并进 {@code extra.credit_key} 并落库。
     *
     * <p>多账号口径：每次登录截获的令牌作为一条新密钥追加进余额密钥列表
     * （换行分隔、去重、上限 {@link #MAX_CREDIT_KEYS} 条），而不是覆盖——这样
     * 同一供应商可以登录多个上游账号，逐 Key 查询余额。合并前用
     * {@link AccountChannelService#find} 重读最新行再整列写回
     * （{@link AccountChannelService#updateExtra} 是整列覆盖），登录窗口打开的
     * 这几分钟里若表单已保存过其它配置，不会被登录线程用旧快照冲掉。</p>
     *
     * @param providerId 供应商标识
     * @param token      截获的会话令牌
     */
    private void persistToken(String providerId, String token) {
        try {
            SysAccountChannel fresh = accountChannelService.find(providerId);
            if (fresh == null) {
                log.warn("[credit-login] 供应商 {} 已不存在，令牌 {} 放弃回填", providerId, mask(token));
                return;
            }
            Map<String, Object> patch = fresh.getExtra() == null
                    ? new LinkedHashMap<>(4) : new LinkedHashMap<>(fresh.getExtra());
            List<String> keys = new ArrayList<>(splitCreditKeys(text(patch.get("credit_key"))));
            if (!keys.contains(token)) {
                keys.add(token);
                if (keys.size() > MAX_CREDIT_KEYS) {
                    keys = new ArrayList<>(keys.subList(keys.size() - MAX_CREDIT_KEYS, keys.size()));
                }
            }
            String joined = String.join("\n", keys);
            patch.put("credit_key", joined);
            accountChannelService.updateExtra(providerId, patch);
            log.info("[credit-login] 供应商 {} 令牌 {} 已追加为余额密钥（当前共 {} 条）",
                    providerId, mask(token), keys.size());
        } catch (RuntimeException e) {
            // 回填失败不影响本次查询：令牌仍随结果返回，调用方本次照常使用
            log.warn("[credit-login] 供应商 {} 令牌回填失败（本次仍按截获令牌查询）: {}",
                    providerId, e.getMessage());
        }
    }

    /**
     * 拆分余额密钥列表：多账号下 {@code extra.credit_key} 是「每行一个令牌」的
     * 多行文本（兼容逗号 / 分号分隔），去空去重保序。登录回填、余额逐 Key 查询、
     * 续期巡检共用这一个口径，避免各处拆分规则漂移。
     *
     * @param raw 余额密钥原文（可多行）
     * @return 去重后的令牌列表；空原文返回空列表
     */
    public static List<String> splitCreditKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String piece : raw.split("[\\r\\n,;，；]+")) {
            String key = piece.trim();
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    /**
     * 轮询截获令牌：按钩子依次尝试 Cookie / localStorage / 响应截获。
     *
     * @param page           浏览器页面
     * @param hooks          截获钩子集
     * @param capturePattern 令牌交换接口地址特征（可为 null）
     * @return 令牌；超时未截获返回 null
     */
    private String awaitToken(BrowserPage page, LoginHooks hooks, String capturePattern) {
        long deadline = System.currentTimeMillis() + TOKEN_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            String token = peekToken(page, hooks, capturePattern);
            if (token != null && !token.isBlank()) {
                return token;
            }
            sleep();
        }
        return null;
    }

    /**
     * 单轮令牌嗅探：按钩子依次尝试 Cookie / localStorage / 响应截获。
     *
     * <p>登录与静默续期共用同一套钩子读取口径：登录场景轮询到非空即成功；
     * 续期场景需比对「读到的令牌是否与旧值不同」来判定平台是否轮换。</p>
     *
     * @param page           浏览器页面
     * @param hooks          截获钩子集
     * @param capturePattern 令牌交换接口地址特征（可为 null）
     * @return 本轮读到的令牌；没有返回 null
     */
    private String peekToken(BrowserPage page, LoginHooks hooks, String capturePattern) {
        String cookieName = hooks.cookieName();
        if (nonBlank(cookieName)) {
            String value = page.cookies().get(cookieName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String storageKey = hooks.storageKey();
        if (nonBlank(storageKey)) {
            String raw = evaluateQuietly(page,
                    "localStorage.getItem(" + quote(storageKey) + ")");
            String token = unwrapStorageToken(raw);
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        if (nonBlank(capturePattern)) {
            String token = capturedToken(page, hooks.tokenPath());
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        return null;
    }

    /**
     * 从暂存区读取截获的响应体并按 JSON 路径取令牌。
     *
     * <p>暂存有两份：window 变量（同源单页内即时可读）与 sessionStorage
     * （登录跳转整页刷新后仍在）。两处都读到非空数组时逐条尝试。</p>
     *
     * @param page      浏览器页面
     * @param tokenPath 令牌 JSON 路径（null 时在响应体里递归找 access_token / token）
     * @return 令牌；没有截获到返回 null
     */
    private String capturedToken(BrowserPage page, String tokenPath) {
        String raw = evaluateQuietly(page,
                "(function(){ try { return JSON.stringify([].concat(window."
                        + CAPTURE_STORE + " || [], JSON.parse(sessionStorage.getItem('"
                        + CAPTURE_STORE + "') || '[]'))); } catch (e) { return '[]'; } })()");
        if (raw == null || raw.isBlank() || "[]".equals(raw)) {
            return null;
        }
        try {
            JsonNode rows = MAPPER.readTree(raw);
            if (!rows.isArray()) {
                return null;
            }
            for (JsonNode row : rows) {
                JsonNode body = row.get("body");
                if (body == null || body.isNull()) {
                    continue;
                }
                String token = tokenFromJson(body.asText(""), tokenPath);
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            // 响应体不是 JSON 或结构不符，继续等下一轮
        }
        return null;
    }

    /**
     * 从截获的响应体文本里取令牌。
     *
     * @param body      响应体文本
     * @param tokenPath 令牌 JSON 路径（按 / 分段下钻）；null 时递归找 access_token / token
     * @return 令牌；取不到为 null
     */
    private String tokenFromJson(String body, String tokenPath) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode current = root;
            if (tokenPath != null && !tokenPath.isBlank()) {
                for (String segment : tokenPath.split("/")) {
                    if (segment.isBlank()) {
                        continue;
                    }
                    current = current.get(segment);
                    if (current == null) {
                        return null;
                    }
                }
                return current.isTextual() || current.isNumber() ? current.asText() : null;
            }
            return findFirstToken(root);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 在 JSON 树里递归找第一个 access_token / token 字段（SPI 默认口径）。
     *
     * @param node JSON 节点
     * @return 令牌文本；找不到为 null
     */
    private String findFirstToken(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            for (String field : List.of("access_token", "token")) {
                JsonNode hit = node.get(field);
                if (hit != null && (hit.isTextual() || hit.isNumber()) && !hit.asText().isBlank()) {
                    return hit.asText();
                }
            }
            for (JsonNode child : node) {
                String token = findFirstToken(child);
                if (token != null) {
                    return token;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String token = findFirstToken(child);
                if (token != null) {
                    return token;
                }
            }
        }
        return null;
    }

    /**
     * 解 localStorage 暂存值：JSON 串时按 token / access_token 再解一层（SPI 契约）。
     *
     * @param raw localStorage 原值
     * @return 令牌；解不出为 null
     */
    private String unwrapStorageToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            for (String field : List.of("token", "access_token")) {
                JsonNode hit = node.get(field);
                if (hit != null && (hit.isTextual() || hit.isNumber()) && !hit.asText().isBlank()) {
                    return hit.asText();
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 暂存值按原文返回
        }
        return trimmed;
    }

    /**
     * 注入页面的响应截获脚本：覆写 fetch 与 XHR，把命中地址特征的响应体暂存。
     *
     * <p>暂存双写：window 变量（同源单页即时可读）+ sessionStorage（整页跳转后仍在）。
     * 响应体只留最近 20 条，避免长会话撑爆内存。</p>
     *
     * @param pattern 令牌交换接口地址特征（正则）
     * @return 初始化脚本
     */
    private String captureScript(String pattern) {
        String regex = quote(pattern);
        return "(function(){"
                + "var PATTERN = " + regex + ";"
                + "var store = function(url, body) {"
                + "  try {"
                + "    if (!new RegExp(PATTERN).test(String(url || ''))) { return; }"
                + "    var row = {url: String(url), body: String(body), at: Date.now()};"
                + "    var list = (window." + CAPTURE_STORE + " = window." + CAPTURE_STORE + " || []);"
                + "    list.push(row);"
                + "    if (list.length > 20) { list.splice(0, list.length - 20); }"
                + "    var saved = [];"
                + "    try { saved = JSON.parse(sessionStorage.getItem('" + CAPTURE_STORE + "') || '[]'); } catch (e) {}"
                + "    saved.push(row);"
                + "    if (saved.length > 20) { saved.splice(0, saved.length - 20); }"
                + "    sessionStorage.setItem('" + CAPTURE_STORE + "', JSON.stringify(saved));"
                + "  } catch (e) {}"
                + "};"
                + "if (!window." + CAPTURE_STORE + ") {"
                + "  var origFetch = window.fetch;"
                + "  if (origFetch) {"
                + "    window.fetch = function() {"
                + "      var args = arguments;"
                + "      return origFetch.apply(this, args).then(function(res) {"
                + "        try { res.clone().text().then(function(t) { store(res.url || args[0], t); }); } catch (e) {}"
                + "        return res;"
                + "      });"
                + "    };"
                + "  }"
                + "  var OrigXhrOpen = XMLHttpRequest.prototype.open;"
                + "  XMLHttpRequest.prototype.open = function(method, url) {"
                + "    this.__chuaUrl = url;"
                + "    return OrigXhrOpen.apply(this, arguments);"
                + "  };"
                + "  var OrigXhrSend = XMLHttpRequest.prototype.send;"
                + "  XMLHttpRequest.prototype.send = function() {"
                + "    var xhr = this;"
                + "    xhr.addEventListener('load', function() {"
                + "      try { store(xhr.__chuaUrl, xhr.responseText); } catch (e) {}"
                + "    });"
                + "    return OrigXhrSend.apply(this, arguments);"
                + "  };"
                + "}"
                + "})()";
    }

    /**
     * 执行页面脚本，异常时返回 null（轮询场景里单次失败不中断）。
     *
     * @param page  浏览器页面
     * @param script 脚本
     * @return 执行结果；失败为 null
     */
    private String evaluateQuietly(BrowserPage page, String script) {
        try {
            return page.evaluate(script, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 字符串转 JS 字面量（JSON 引号转义；转义失败时退化为逐字符反斜杠 u 转义）。
     *
     * @param value 原文
     * @return 可内插进脚本的双引号字面量
     */
    private String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value == null ? "" : value);
        } catch (Exception e) {
            StringBuilder escaped = new StringBuilder("\"");
            for (char c : (value == null ? "" : value).toCharArray()) {
                escaped.append(String.format("\\u%04x", (int) c));
            }
            return escaped.append('"').toString();
        }
    }

    /**
     * 令牌掩码：保留前 6 位 + 后 4 位。
     *
     * @param token 令牌
     * @return 掩码文本
     */
    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 10) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 6) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 读扩展配置里的字符串值。
     *
     * @param value 原值
     * @return 非空字符串；空为 ""
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 字符串是否有实际内容（非 null、非纯空白）。
     *
     * @param value 原值
     * @return 有内容为 true
     */
    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 取第一个有实际内容的值（显式配置优先、登记默认兜底的通用形式）。
     *
     * @param first  显式配置值
     * @param second 兜底值
     * @return 第一个非空值；都为空时为 null
     */
    private static String firstNonBlank(String first, String second) {
        return nonBlank(first) ? first : second;
    }

    // ===================== 实时登录（画面内嵌到管理页） =====================

    /**
     * 实时登录会话创建结果。
     *
     * @param ok           是否成功打开登录窗口
     * @param message      结果说明（失败时为原因）
     * @param sessionToken 会话标识（轮询状态 / 取消时回传）
     * @param livePath     画面嵌入路径（{@code /live/s/<token>}）
     * @param livePort     画面服务监听端口（失败时为 0；前端拼 {@code http://<后端主机>:<livePort><livePath>} 放进 iframe）
     * @param loginUrl     实际打开的登录页地址
     */
    public record LiveLoginHandle(boolean ok, String message, String sessionToken,
                                  String livePath, int livePort, String loginUrl) {
    }

    /**
     * 实时登录会话状态。
     *
     * @param status    pending / success / failed / unknown
     * @param message   结果说明（终态时有值）
     * @param tokenMask 截获令牌的掩码（success 时有值，只回掩码不回明文）
     */
    public record LiveLoginStatus(String status, String message, String tokenMask) {

        /** 进行中 */
        public static final String PENDING = "pending";

        /** 登录成功，令牌已追加为余额密钥 */
        public static final String SUCCESS = "success";

        /** 登录失败 / 超时 / 已取消 */
        public static final String FAILED = "failed";

        /** 会话不存在或已过期回收 */
        public static final String UNKNOWN = "unknown";
    }

    /**
     * 开启一局「实时登录」：在服务端起无头浏览器打开上游登录页，把画面经
     * {@link CdpLiveServer} 推成 MJPEG 流、把用户操作经 CDP Input 派发回去，
     * 前端用 iframe 嵌入即可在管理页里直接操作；后台线程持续按截获钩子嗅探
     * 令牌，截获即追加为余额密钥。与 {@link #login} 的区别：浏览器不再弹在
     * 服务端桌面（部署在远端机时用户根本看不到那个窗口），而是画面直达用户浏览器。
     *
     * @param entity 供应商
     * @param spi    余额 SPI 实现（提供登录页与截获钩子；可为 null——按 extra 的通用截获配置执行）
     * @return 会话创建结果（含嵌入路径；失败带原因）
     */
    public LiveLoginHandle startLiveLogin(SysAccountChannel entity, CreditQueryProvider spi) {
        Map<String, Object> extra = entity.getExtra() == null ? Map.of() : entity.getExtra();
        LoginHooks hooks = hooksFor(entity, spi, extra);
        String loginUrl = hooks.loginUrl();
        if (!nonBlank(loginUrl)) {
            return new LiveLoginHandle(false,
                    "该供应商没有可用的登录页（未收录登录入口，可在扩展配置 credit_login_url 里指定）", null, null, 0, null);
        }
        if (!hooks.hasCaptureHook()) {
            return new LiveLoginHandle(false,
                    "该供应商未配置令牌截获规则：请补 credit_capture_pattern"
                            + "（令牌接口地址特征）或 credit_cookie_name / credit_storage_key",
                    null, null, 0, loginUrl);
        }
        CdpLiveServer server;
        try {
            server = liveServer();
        } catch (RuntimeException e) {
            log.warn("[credit-live-login] 供应商 {} 启动内嵌画面服务失败: {}", entity.getProviderId(), e.getMessage());
            return new LiveLoginHandle(false, "无法启动登录画面服务：" + e.getMessage(), null, null, 0, loginUrl);
        }
        // 无头引擎：画面经 CDP screencast 推流，不依赖服务端桌面（部署在远端机也能登录）
        CdpBrowserEngine engine = new CdpBrowserEngine();
        BrowserOptions browserOptions = new BrowserOptions().setHeadless(true).setWidth(1280).setHeight(900);
        try {
            engine.start(browserOptions);
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-live-login] 供应商 {} 启动登录浏览器失败: {}", entity.getProviderId(), e.getMessage());
            return new LiveLoginHandle(false, "无法启动登录浏览器：" + e.getMessage(), null, null, 0, loginUrl);
        }
        String sessionToken = UUID.randomUUID().toString().replace("-", "");
        try {
            BrowserPage page = engine.openPage(browserOptions);
            String capturePattern = hooks.capturePattern();
            if (nonBlank(capturePattern)) {
                page.addInitScript(captureScript(capturePattern));
            }
            page.navigate(loginUrl);
            LiveSession session = new LiveSession(sessionToken, entity.getProviderId(),
                    engine, page, hooks, capturePattern);
            liveSessions.put(sessionToken, session);
            // 画面/交互接管：同一 token 重复观看时旧流自动让位；会话空闲 30 分钟自动回收
            server.view(page, sessionToken);
            Thread worker = new Thread(() -> awaitLiveToken(session),
                    "credit-live-login-" + sessionToken.substring(0, 8));
            worker.setDaemon(true);
            worker.start();
            log.info("[credit-live-login] 供应商 {} 实时登录窗口已就绪: {}", entity.getProviderId(), sessionToken);
            return new LiveLoginHandle(true, "登录窗口已就绪，请在画面中完成上游门户登录",
                    sessionToken, "/live/s/" + sessionToken, server.port(), loginUrl);
        } catch (RuntimeException e) {
            engine.close();
            log.warn("[credit-live-login] 供应商 {} 打开登录页失败: {}", entity.getProviderId(), e.getMessage());
            return new LiveLoginHandle(false, "打开登录页失败：" + e.getMessage(), null, null, 0, loginUrl);
        }
    }

    /**
     * 查询实时登录会话状态（前端轮询）。
     *
     * @param providerId   供应商标识（校验会话归属，防跨供应商读状态）
     * @param sessionToken 会话标识
     * @return 会话状态；会话不存在 / 已过期回收返回 unknown
     */
    public LiveLoginStatus liveLoginStatus(String providerId, String sessionToken) {
        LiveSession session = liveSessions.get(sessionToken);
        if (session == null || !session.providerId.equals(providerId)) {
            return new LiveLoginStatus(LiveLoginStatus.UNKNOWN, "登录会话不存在或已过期", null);
        }
        return new LiveLoginStatus(session.status, session.message, session.tokenMask);
    }

    /**
     * 取消实时登录会话：停掉后台嗅探并关闭服务端浏览器（用户关掉弹窗时调用，
     * 不让无头浏览器空转到超时）。
     *
     * @param providerId   供应商标识（校验会话归属）
     * @param sessionToken 会话标识
     * @return 是否取消了一个进行中的会话
     */
    public boolean cancelLiveLogin(String providerId, String sessionToken) {
        LiveSession session = liveSessions.get(sessionToken);
        if (session == null || !session.providerId.equals(providerId)
                || !LiveLoginStatus.PENDING.equals(session.status)) {
            return false;
        }
        session.cancelled = true;
        finish(session, LiveLoginStatus.FAILED, "已取消本次登录", null);
        closeLiveBrowser(session);
        log.info("[credit-live-login] 供应商 {} 实时登录已取消: {}", providerId, sessionToken);
        return true;
    }

    /**
     * 后台令牌嗅探：与 {@link #login} 同一套截获钩子轮询，截获即追加落库；
     * 超时 / 异常按 failed 收尾。会话记录保留 {@link #LIVE_RESULT_TTL_MILLIS}
     * 供前端领取结果，之后由回收器清理。
     *
     * @param session 实时登录会话
     */
    private void awaitLiveToken(LiveSession session) {
        String token = null;
        try {
            long deadline = System.currentTimeMillis() + TOKEN_WAIT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                // 已取消（浏览器已被关闭）：直接退出，状态由取消方写好
                if (session.cancelled || liveSessions.get(session.token) != session) {
                    return;
                }
                token = peekToken(session.page, session.hooks, session.capturePattern);
                if (token != null && !token.isBlank()) {
                    break;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
            if (session.cancelled) {
                return;
            }
            if (token != null && !token.isBlank()) {
                persistToken(session.providerId, token);
                finish(session, LiveLoginStatus.SUCCESS, "登录成功：会话令牌已追加为余额密钥", mask(token));
                log.info("[credit-live-login] 供应商 {} 登录成功，令牌 {} 已追加",
                        session.providerId, session.tokenMask);
            } else {
                finish(session, LiveLoginStatus.FAILED, "等待登录超时：请在登录窗口内完成上游门户登录后重试", null);
                log.warn("[credit-live-login] 供应商 {} 等待登录超时", session.providerId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(session, LiveLoginStatus.FAILED, "登录轮询被中断", null);
        } catch (RuntimeException e) {
            // 用户已主动取消（浏览器已关）时不再覆盖取消结果
            if (session.cancelled) {
                return;
            }
            finish(session, LiveLoginStatus.FAILED, "登录执行失败：" + e.getMessage(), null);
            log.warn("[credit-live-login] 供应商 {} 登录失败: {}", session.providerId, e.getMessage());
        } finally {
            closeLiveBrowser(session);
        }
    }

    /**
     * 写入终态：状态 + 说明 + 令牌掩码一次落定，并记录终态时间供回收器清理。
     *
     * @param session   实时登录会话
     * @param status    终态状态（success / failed）
     * @param message   结果说明
     * @param tokenMask 令牌掩码（success 时有值）
     */
    private static void finish(LiveSession session, String status, String message, String tokenMask) {
        session.status = status;
        session.message = message;
        session.tokenMask = tokenMask;
        session.finishedAt = System.currentTimeMillis();
    }

    /**
     * 关闭会话的服务端浏览器（页面随引擎一起释放；会话记录保留供领取结果）。
     *
     * @param session 实时登录会话
     */
    private void closeLiveBrowser(LiveSession session) {
        if (liveServer != null) {
            try {
                liveServer.close(session.token);
            } catch (Exception e) {
                log.debug("[credit-live-login] 移除画面会话失败: {}", e.getMessage());
            }
        }
        CdpBrowserEngine engine = session.engine;
        session.engine = null;
        session.page = null;
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                log.debug("[credit-live-login] 关闭登录浏览器失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 懒启动内嵌画面服务，并顺带启动终态会话回收器。
     *
     * @return 画面服务实例
     * @throws IllegalStateException 端口监听失败
     */
    private CdpLiveServer liveServer() {
        CdpLiveServer server = liveServer;
        if (server != null) {
            return server;
        }
        synchronized (this) {
            if (liveServer == null) {
                try {
                    liveServer = CdpLiveServer.start();
                } catch (IOException e) {
                    throw new IllegalStateException("启动登录画面服务失败：" + e.getMessage(), e);
                }
                ScheduledThreadPoolExecutor reaper = new ScheduledThreadPoolExecutor(1, runnable -> {
                    Thread thread = new Thread(runnable, "credit-live-login-reaper");
                    thread.setDaemon(true);
                    return thread;
                });
                reaper.scheduleWithFixedDelay(this::reapLiveSessions,
                        LIVE_REAPER_PERIOD_SECONDS, LIVE_REAPER_PERIOD_SECONDS, TimeUnit.SECONDS);
                liveReaper = reaper;
                log.info("[credit-live-login] 内嵌登录画面服务已启动: 端口 {}", liveServer.port());
            }
            return liveServer;
        }
    }

    /**
     * 回收已出终态且超过保留时长的会话记录（浏览器引擎在出终态时已关闭，
     * 这里只清记录本身，让迟到的轮询在保留期内仍能拿到结果）。
     */
    private void reapLiveSessions() {
        long deadline = System.currentTimeMillis() - LIVE_RESULT_TTL_MILLIS;
        for (LiveSession session : liveSessions.values()) {
            if (!LiveLoginStatus.PENDING.equals(session.status) && session.finishedAt > 0
                    && session.finishedAt < deadline) {
                liveSessions.remove(session.token);
            }
        }
    }

    /**
     * 释放内嵌画面服务与回收器（应用停机时）。
     */
    @PreDestroy
    public void shutdownLiveLogin() {
        ScheduledThreadPoolExecutor reaper = liveReaper;
        liveReaper = null;
        if (reaper != null) {
            reaper.shutdownNow();
        }
        liveSessions.values().forEach(this::closeLiveBrowser);
        liveSessions.clear();
        CdpLiveServer server = liveServer;
        liveServer = null;
        if (server != null) {
            server.close();
        }
    }

    /**
     * 一局实时登录会话：持有服务端无头浏览器与截获钩子，状态字段供前端轮询。
     */
    private static final class LiveSession {

        /** 会话标识（即画面服务的路径令牌） */
        final String token;

        /** 供应商标识（状态 / 取消接口校验归属） */
        final String providerId;

        /** 截获钩子集 */
        final LoginHooks hooks;

        /** 令牌交换接口地址特征（可为 null） */
        final String capturePattern;

        /** 服务端无头浏览器引擎（出终态或取消时关闭置 null） */
        volatile CdpBrowserEngine engine;

        /** 登录页面（随引擎释放） */
        volatile BrowserPage page;

        /** 状态：pending / success / failed */
        volatile String status = LiveLoginStatus.PENDING;

        /** 结果说明（终态时有值） */
        volatile String message = "";

        /** 截获令牌掩码（success 时有值） */
        volatile String tokenMask;

        /** 是否已被用户取消 */
        volatile boolean cancelled;

        /** 终态时间（回收器据此清理记录；0 = 未终态） */
        volatile long finishedAt;

        LiveSession(String token, String providerId, CdpBrowserEngine engine, BrowserPage page,
                    LoginHooks hooks, String capturePattern) {
            this.token = token;
            this.providerId = providerId;
            this.engine = engine;
            this.page = page;
            this.hooks = hooks;
            this.capturePattern = capturePattern;
        }
    }

    /**
     * 轮询等待
     */
    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("登录轮询被中断", e);
        }
    }
}

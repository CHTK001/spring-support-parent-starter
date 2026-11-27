package com.chua.starter.cdp.proxy;

import com.chua.starter.cdp.config.CdpProperties;
import com.chua.starter.cdp.service.CdpEngineService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 实时页面代理会话管理器
 *
 * <p>负责会话的创建 / 查找 / 关闭 / TTL 清扫，并维护「会话 ↔ 无头浏览器页面目标」
 * 的绑定：创建会话后让 CDP 浏览器经由代理打开同一地址，从而与 iframe 共享
 * 服务端 Cookie 罐与同一份真实页面状态。</p>
 *
 * <p>绑定是<b>异步</b>的：{@link #attachCdpTargetAsync(ProxySession)} 在后台完成
 * CDP 目标创建与经代理的整页导航（浏览器冷启动时可达数十秒），打开会话的调用方
 * 无需等待，iframe 可立即开始加载；需要无头页面就绪时用
 * {@link #awaitAttached(ProxySession)} 等待落定。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxySessionManager {

    /**
     * CDP 属性
     */
    private final CdpProperties properties;

    /**
     * CDP 引擎服务
     */
    private final CdpEngineService engine;

    /**
     * 活跃会话
     */
    private final Map<String, ProxySession> sessions = new ConcurrentHashMap<>();

    /**
     * 进行中的会话绑定任务（会话标识 -> targetId future；导航前等待其落定）
     */
    private final Map<String, CompletableFuture<String>> attachments = new ConcurrentHashMap<>();

    /**
     * TTL 清扫线程
     */
    private ScheduledExecutorService sweeper;

    /**
     * 绑定执行器：无头浏览器页面的创建 / 导航在后台完成，不阻塞打开会话的调用
     */
    private ExecutorService attacher;

    /**
     * 启动清扫与绑定线程
     */
    @PostConstruct
    public void init() {
        sweeper = new ScheduledThreadPoolExecutor(1, r -> { // [P3C C1 修复] 显式线程池替代 Executors 包装
            Thread thread = new Thread(r, "cdp-proxy-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::sweep, 30, 30, TimeUnit.SECONDS);
        attacher = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), r -> { // [P3C C1 修复] 显式线程池替代 Executors 包装
            Thread thread = new Thread(r, "cdp-proxy-attach");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 停止清扫、绑定并关闭全部会话
     */
    @PreDestroy
    public void destroy() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        if (attacher != null) {
            attacher.shutdownNow();
        }
        attachments.clear();
        for (String id : new ArrayList<>(sessions.keySet())) {
            close(id);
        }
    }

    /**
     * 打开一个代理会话（不创建 CDP 目标，{@link #attachCdpTargetAsync(ProxySession)} 负责绑定页面）
     *
     * @param url 初始上游地址
     * @return 会话
     */
    public synchronized ProxySession open(String url) {
        String trimmed = url == null ? "" : url.trim();
        URI uri = parseUpstream(trimmed);
        validateUpstream(uri);
        int max = Math.max(1, properties.getLiveProxy().getMaxSessions());
        while (sessions.size() >= max) {
            ProxySession oldest = sessions.values().stream()
                    .min(Comparator.comparingLong(ProxySession::getLastAccess))
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            log.info("[cdp-proxy] 会话数达到上限 {}，关闭最久未用的会话 {}", max, oldest.getId());
            close(oldest.getId());
        }
        ProxySession session = new ProxySession(newId(), trimmed);
        sessions.put(session.getId(), session);
        log.info("[cdp-proxy] 打开会话 {} -> {}", session.getId(), trimmed);
        return session;
    }

    /**
     * 异步为会话绑定无头浏览器页面
     *
     * <p>绑定包含创建 CDP 目标与经代理的整页导航（浏览器冷启动时耗时更长），
     * 放到后台线程执行，让打开会话的调用立即返回、iframe 立即可加载。
     * 绑定失败只降级（iframe 侧仍可用，事件 / 截屏能力暂缺），不影响会话本身。</p>
     *
     * @param session 会话
     * @return 绑定结果 future（targetId，失败时为 null）
     */
    public CompletableFuture<String> attachCdpTargetAsync(ProxySession session) {
        CompletableFuture<String> future = new CompletableFuture<>();
        attachments.put(session.getId(), future);
        try {
            attacher.execute(() -> future.complete(attachCdpTarget(session)));
        } catch (Exception e) {
            // 执行器不可用（如应用正在关闭）：按绑定失败处理，iframe 侧仍可用
            log.warn("[cdp-proxy] 绑定任务提交失败（iframe 仍可用）: {}", e.getMessage());
            future.complete(null);
        }
        return future;
    }

    /**
     * 为会话绑定无头浏览器页面
     *
     * <p>页面 URL 指向本机代理（空路径 = 会话当前地址），浏览器与 iframe 共享会话
     * Cookie 罐与页面状态；Console / Network 事件经由既有 CDP 通道进入调试面板。</p>
     *
     * @param session 会话
     * @return CDP targetId
     */
    public String attachCdpTarget(ProxySession session) {
        try {
            // 绑定期间会话可能已被关闭（收到 DELETE / 被清扫）：不再创建孤儿页面
            if (sessions.get(session.getId()) != session) {
                log.debug("[cdp-proxy] 会话 {} 已关闭，跳过无头浏览器绑定", session.getId());
                return null;
            }
            Map<String, Object> result = engine.createTarget(Map.of(
                    "url", selfBase() + "/" + session.getId() + "/"));
            String targetId = result == null || result.get("targetId") == null
                    ? null
                    : String.valueOf(result.get("targetId"));
            if (sessions.get(session.getId()) != session) {
                // 创建期间会话被关闭：立即回收刚创建的页面，避免孤儿页面常驻无头浏览器
                if (targetId != null) {
                    try {
                        engine.closeTarget(targetId);
                    } catch (Exception ignore) {
                        log.debug("[cdp-proxy] 回收孤儿页面失败: {}", targetId);
                    }
                }
                return null;
            }
            session.setTargetId(targetId);
            return targetId;
        } catch (Exception e) {
            // 无头浏览器不可用时降级：仅 iframe 侧可用，事件/截屏能力暂缺
            log.warn("[cdp-proxy] 绑定无头浏览器页面失败（iframe 仍可用）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 等待会话的无头浏览器页面绑定完成（绑定是异步的，导航前需要等它落定）
     *
     * @param session 会话
     * @return targetId；无绑定任务 / 绑定失败 / 超时时返回 {@code null}
     */
    public String awaitAttached(ProxySession session) {
        String attached = session.getTargetId();
        if (attached != null) {
            return attached;
        }
        CompletableFuture<String> future = attachments.get(session.getId());
        if (future == null) {
            return null;
        }
        try {
            // 绑定在后台进行（无头浏览器冷启动可达一分钟），等待只发生在显式导航前；
            // 下限 60s 保证冷启动场景也能等到绑定落定
            long waitSeconds = Math.max(properties.getCommandTimeout().getSeconds(), 60);
            return future.get(waitSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 绑定仍在进行 / 失败：读一次已设置的值（可能刚好完成），仍为空就按降级处理
            return session.getTargetId();
        }
    }

    /**
     * 会话内导航到新的上游地址（同步更新解析基准并让无头浏览器跟随）
     *
     * @param session 会话
     * @param url     目标地址（绝对地址）
     */
    public void navigate(ProxySession session, String url) {
        String trimmed = url == null ? "" : url.trim();
        URI uri = parseUpstream(trimmed);
        validateUpstream(uri);
        session.setCurrentUrl(uri.toString());
        String targetId = awaitAttached(session);
        if (targetId != null) {
            engine.pageCall(targetId, "Page.navigate",
                    Map.of("url", selfBase() + "/" + session.getId() + "/"));
        }
    }

    /**
     * 按标识取会话（命中即续期）
     *
     * @param id 会话标识
     * @return 会话；不存在或已过期返回 {@code null}
     */
    public ProxySession get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ProxySession session = sessions.get(id);
        if (session == null) {
            return null;
        }
        if (session.isExpired(properties.getLiveProxy().getSessionTtl().toMillis())) {
            log.info("[cdp-proxy] 会话空闲超时，关闭 {}", id);
            close(id);
            return null;
        }
        session.touch();
        return session;
    }

    /**
     * 关闭会话（含无头浏览器页面）
     *
     * @param id 会话标识
     */
    public void close(String id) {
        ProxySession session = sessions.remove(id);
        attachments.remove(id);
        if (session == null) {
            return;
        }
        String targetId = session.getTargetId();
        if (targetId != null) {
            try {
                engine.closeTarget(targetId);
            } catch (Exception e) {
                log.debug("[cdp-proxy] 关闭 CDP 目标失败: {}", e.getMessage());
            }
        }
    }

    /**
     * @return 全部活跃会话
     */
    public List<ProxySession> list() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * 上游合法性校验：仅 http/https、主机白名单、禁止回环指向代理自身
     *
     * @param uri 上游地址
     */
    public void validateUpstream(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 上游地址: " + uri);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("上游地址缺少主机: " + uri);
        }
        List<String> allowedHosts = properties.getLiveProxy().getAllowedHosts();
        if (CollectionUtils.isNotEmpty(allowedHosts)) {
            String lower = host.toLowerCase();
            boolean allowed = false;
            for (String candidate : allowedHosts) {
                String trimmed = candidate == null ? "" : candidate.trim().toLowerCase();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (lower.equals(trimmed) || lower.endsWith("." + trimmed)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new IllegalArgumentException("上游主机不在白名单: " + host);
            }
        }
        if (("localhost".equals(host) || "127.0.0.1".equals(host))
                && uri.getPort() == properties.getLiveProxy().getPort()) {
            throw new IllegalArgumentException("禁止把代理自身配置为上游");
        }
    }

    /**
     * 解析上游地址
     */
    private URI parseUpstream(String url) {
        try {
            return URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 URL: " + url);
        }
    }

    /**
     * 会话标识（32 位随机十六进制）
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 无头浏览器视角的代理基地址（浏览器与本服务同机，走 127.0.0.1）
     */
    private String selfBase() {
        return "http://127.0.0.1:" + properties.getLiveProxy().getPort();
    }

    /**
     * TTL 清扫
     */
    private void sweep() {
        long ttl = properties.getLiveProxy().getSessionTtl().toMillis();
        for (ProxySession session : sessions.values()) {
            if (session.isExpired(ttl)) {
                log.info("[cdp-proxy] 会话空闲超时，关闭 {}", session.getId());
                close(session.getId());
            }
        }
    }
}

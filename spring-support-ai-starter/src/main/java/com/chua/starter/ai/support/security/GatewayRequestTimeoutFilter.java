package com.chua.starter.ai.support.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关逐请求超时过滤器。
 *
 * <p>对应全局设置 {@code security} 分组的 {@code security_request_timeout_enabled} /
 * {@code security_request_timeout_millis}。</p>
 *
 * <h2>语义：首字节超时，不是总时长超时</h2>
 * <p>网关前面的 {@code /v2/v1/**} 是 SSE 流式接口，一次对话可能持续几分钟。若按「总处理
 * 时长」超时，流式响应会在固定时刻被掐断 —— 那不是防护，是故障。因此这里守的是
 * <b>下游开始响应之前的等待时间</b>（TTFB）：</p>
 * <ul>
 *   <li>到点仍未提交响应（{@code response.isCommitted() == false}）→ 写 504 并结束；</li>
 *   <li>已经提交（首字节已发出、正在流式输出）→ 不动它，交给下游跑完。</li>
 * </ul>
 *
 * <p><b>已知边界：</b>504 由看门狗线程写出，此时原请求线程仍在 handler 里跑。它后续的写
 * 操作会因响应已提交而失败（由容器吞掉/抛给容器），不会覆盖已发出的 504。这与 Tomcat
 * 自带的 {@code asyncTimeout} 不同 —— 后者要求异步 Servlet，本模块的链路是同步的。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class GatewayRequestTimeoutFilter extends AbstractGatewaySecurityFilter implements DisposableBean {

    /**
     * 配置分组名
     */
    private static final String GROUP = "security";

    /**
     * 字段：逐请求超时开关
     */
    private static final String FIELD_ENABLED = "security_request_timeout_enabled";

    /**
     * 字段：逐请求超时毫秒数
     */
    private static final String FIELD_MILLIS = "security_request_timeout_millis";

    /**
     * 超时默认值（毫秒）
     */
    private static final int DEFAULT_TIMEOUT_MILLIS = 60_000;

    /**
     * 看门狗线程数：只做「到点检查 + 写一个 504」，1 条足够
     */
    private static final int WATCHDOG_THREADS = 1;

    /**
     * 看门狗调度器（daemon，随 Spring 关闭）
     */
    private final ScheduledThreadPoolExecutor watchdog;

    /**
     * 构造器注入。
     *
     * @param globalSettingReader 全局设置读取器
     */
    public GatewayRequestTimeoutFilter(GlobalSettingReader globalSettingReader) {
        super(globalSettingReader);
        AtomicInteger seq = new AtomicInteger(0);
        this.watchdog = new ScheduledThreadPoolExecutor(WATCHDOG_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "ai-gateway-timeout-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        // 到点只做一次检查，任务取消后不留队列；核心线程也不保活
        this.watchdog.setRemoveOnCancelPolicy(true);
        this.watchdog.setKeepAliveTime(30L, TimeUnit.SECONDS);
        this.watchdog.allowCoreThreadTimeOut(true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long timeoutMillis = resolveTimeoutMillis(request);
        if (timeoutMillis <= 0) {
            safePass(request, response, chain);
            return;
        }
        AtomicBoolean fired = new AtomicBoolean(false);
        ScheduledFuture<?> task = watchdog.schedule(
                () -> rejectTimeout(request, response, timeoutMillis, fired),
                timeoutMillis, TimeUnit.MILLISECONDS);
        try {
            chain.doFilter(request, response);
        } finally {
            task.cancel(false);
        }
    }

    /**
     * 解析本次请求的超时毫秒数。
     *
     * <p>返回 0 表示「不做超时控制」：总开关关闭、本开关关闭、路径未命中、超时值 ≤ 0
     * 或读取配置异常，一律视为关闭 —— 拦截器自己绝不能成为系统故障点。</p>
     *
     * @param request 请求
     * @return 超时毫秒数；0 表示关闭
     */
    private long resolveTimeoutMillis(HttpServletRequest request) {
        try {
            if (!enabled() || !matches(request)) {
                return 0L;
            }
            if (!globalSettingReader().getBool(GROUP, FIELD_ENABLED, true)) {
                return 0L;
            }
            return globalSettingReader().getInt(GROUP, FIELD_MILLIS, DEFAULT_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.warn("[GatewayRequestTimeoutFilter] 读取超时配置失败，放行: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 看门狗动作：到点未提交响应则写 504。
     *
     * @param request       请求
     * @param response      响应
     * @param timeoutMillis 超时毫秒数
     * @param fired         是否已经由看门狗处理过（保证只写一次）
     */
    private void rejectTimeout(HttpServletRequest request, HttpServletResponse response,
                               long timeoutMillis, AtomicBoolean fired) {
        if (!fired.compareAndSet(false, true)) {
            return;
        }
        if (response.isCommitted()) {
            // 首字节已发出（流式响应）：不动它
            return;
        }
        try {
            log.warn("[GatewayRequestTimeoutFilter] 首字节超时 {}ms，拒绝 {} {}",
                    timeoutMillis, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Gateway Timeout\",\"timeoutMillis\":" + timeoutMillis + "}");
        } catch (IOException | IllegalStateException e) {
            log.warn("[GatewayRequestTimeoutFilter] 504 写出失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        watchdog.shutdownNow();
    }
}

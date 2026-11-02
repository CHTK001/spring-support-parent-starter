package com.chua.starter.ai.support.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 网关逐请求超时过滤器（顺序最后，包住整条 MVC 链）。
 *
 * <h2>为什么不走 AsyncContext.setTimeout</h2>
 * <p>实测（Tomcat 11 + Spring Boot 4.1，本地 8081）表明：过滤器里
 * {@code request.startAsync() + asyncContext.setTimeout(3000)} 对<b>阻塞式</b> Spring MVC
 * 控制器<b>不会生效</b> —— 一个 sleep 15 秒的接口照样跑满 18.8 秒并返回 200，只是事后多了
 * 一次无意义的 {@code onTimeout} 回调（异步超时只在请求「进入异步空闲态」后才计时，
 * 而阻塞中的工作线程从不进入该状态）。所以这层超时不能靠 AsyncContext。</p>
 *
 * <h2>实际策略：看门狗 + 抢写 504</h2>
 * <ol>
 *   <li>请求进入时挂一个定时任务（整机共享调度器，单线程即可）；</li>
 *   <li>链在阈值内返回 → 取消任务，一切照常；</li>
 *   <li>链超时 → 若响应尚未提交，看门狗直接写 504 JSON 并 flush。客户端在阈值时刻
 *       拿到明确的 504，而不是挂到连接超时；</li>
 *   <li>写完后把响应包上「丢弃后续写入」的壳，控制器迟到的输出不会再往这条连接里塞
 *       字节，避免 504 之后拖出一段脏正文；</li>
 *   <li>控制器线程本身无法被安全打断（Java 没有可用的强制终止），它会自己跑完 ——
 *       这是阻塞式 MVC 的固有边界，真正的线程级中断属于后续 NIO 通道层的优化项。</li>
 * </ol>
 *
 * <h2>只管同步处理</h2>
 * <p>SSE 控制器在 {@code chain.doFilter} 里立刻返回（返回 {@code SseEmitter}），链随即
 * 收尾并取消看门狗，因此本过滤器只覆盖「同步处理耗时」。SSE 的流时长由
 * {@code SseEmitter} 自身超时与上游 {@code FailoverTimeouts}（首包/整体）负责，两者不重叠。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayRequestTimeoutFilter extends AbstractGatewaySecurityFilter {

    /**
     * 超时看门狗：整机共享，只做定时回调
     */
    private final ScheduledExecutorService watchdog;

    /**
     * 构造器注入。
     *
     * @param settings 安全设置快照
     * @param watchdog 超时看门狗调度器
     */
    public GatewayRequestTimeoutFilter(SecuritySettings settings, ScheduledExecutorService watchdog) {
        super(settings);
        this.watchdog = watchdog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long timeoutMillis;
        try {
            if (!applies(request) || !settings().getBool("security_request_timeout_enabled", true)) {
                chain.doFilter(request, response);
                return;
            }
            timeoutMillis = settings().getInt("security_request_timeout_millis", 60_000);
            if (timeoutMillis <= 0) {
                chain.doFilter(request, response);
                return;
            }
        } catch (Exception e) {
            // 判定异常：按无超时处理
            log.warn("[GatewayRequestTimeoutFilter] 拦截判定异常，已放行: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }
        TimeoutResponse wrapped = new TimeoutResponse(response);
        ScheduledFuture<?> future = watchdog.schedule(
                () -> expire(request, wrapped, timeoutMillis), timeoutMillis, TimeUnit.MILLISECONDS);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            future.cancel(false);
        }
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) {
        // 超时需要包住整条链，逻辑在 doFilterInternal 里，这里不参与模板
        return false;
    }

    /**
     * 看门狗回调：链在阈值内没返回且响应尚未提交时，抢写 504。
     *
     * @param request       请求
     * @param response      响应壳
     * @param timeoutMillis 超时阈值
     */
    private void expire(HttpServletRequest request, TimeoutResponse response, long timeoutMillis) {
        if (!response.abort(timeoutMillis)) {
            return;
        }
        log.warn("[GatewayRequestTimeoutFilter] 同步处理超时 {}ms: {} {}", timeoutMillis,
                request.getMethod(), request.getRequestURI());
    }

    /**
     * 超时响应壳：把「超时后丢弃写入」与「抢写 504」收在一处。
     *
     * <p>除输出流与 {@link #abort} 外全部原样透传，因此对 Jackson、SSE 等下游透明。</p>
     */
    private static final class TimeoutResponse extends HttpServletResponseWrapper {

        /**
         * 是否已抢写 504（此后下游写入一律丢弃）
         */
        private volatile boolean expired;

        /**
         * 让「检查过期标志 + 实际写」与 abort 互斥，避免看门狗与请求线程同时写同一条响应
         */
        private final Object writeLock = new Object();

        TimeoutResponse(HttpServletResponse response) {
            super(response);
        }

        /**
         * 抢写 504。
         *
         * @param timeoutMillis 超时阈值
         * @return 抢写成功返回 true；响应已提交或已超时返回 false
         */
        boolean abort(long timeoutMillis) {
            synchronized (writeLock) {
                if (expired) {
                    return false;
                }
                if (isCommitted()) {
                    // 已经开始出正文（例如慢速流式响应）：状态码改不了，交给下游自己的超时
                    return false;
                }
                expired = true;
                HttpServletResponse raw = (HttpServletResponse) getResponse();
                String body = "{\"error\":\"Gateway Timeout\",\"timeout\":" + timeoutMillis + "}";
                try {
                    raw.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
                    raw.setContentType("application/json;charset=UTF-8");
                    raw.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    raw.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                    raw.flushBuffer();
                } catch (IllegalStateException stateException) {
                    // 下游已先拿了 Writer：改用字符流写，避免输出流/字符流模式冲突
                    try {
                        raw.getWriter().write(body);
                        raw.flushBuffer();
                    } catch (IOException e) {
                        log.debug("[GatewayRequestTimeoutFilter] 504 抢写失败: {}", e.getMessage());
                    }
                } catch (IOException e) {
                    log.debug("[GatewayRequestTimeoutFilter] 504 抢写失败（客户端可能已断开）: {}", e.getMessage());
                }
                return true;
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            final OutputStream target = getResponse().getOutputStream();
            return new ServletOutputStream() {

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // 阻塞式写出，不注册非阻塞监听
                }

                @Override
                public void write(int b) throws IOException {
                    synchronized (writeLock) {
                        if (!expired) {
                            target.write(b);
                        }
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    synchronized (writeLock) {
                        if (!expired) {
                            target.write(b, off, len);
                        }
                    }
                }

                @Override
                public void flush() throws IOException {
                    synchronized (writeLock) {
                        if (!expired) {
                            target.flush();
                        }
                    }
                }

                @Override
                public void close() throws IOException {
                    synchronized (writeLock) {
                        target.close();
                    }
                }
            };
        }

        @Override
        public void flushBuffer() throws IOException {
            synchronized (writeLock) {
                if (!expired) {
                    super.flushBuffer();
                }
            }
        }
    }
}

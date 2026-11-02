package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关并发数（在途连接）预算过滤器。
 *
 * <p>每个 APPKey 维护一个在途计数器：进入时加 1，超预算则 429 拒绝；退出时减 1
 * （SSE 这类长连接会在整条流期间持续占住预算，正是我们想限制的）。</p>
 *
 * <p>未携带 APPKey 的流量共享一个哨兵 Key，控制台的并发不会因缺少令牌而突破预算
 * （也不能用 {@code null} 做 map key —— {@link ConcurrentHashMap} 会直接抛 NPE）。</p>
 *
 * <h2>为什么覆盖 doFilterInternal</h2>
 * <p>这里必须在 {@code finally} 里归还预算，而基类模板要求「判定阶段不得调用链」。
 * 覆盖后仍遵守基类三条约定：判定异常放行、链只走一次、下游异常不吞。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayConnectionBudgetFilter extends AbstractGatewaySecurityFilter {

    /**
     * 公钥：并发开关
     */
    private static final String FIELD_BUDGET_ENABLED = "security_connection_budget_enabled";
    /**
     * 公钥：单 Key 最大在途并发
     */
    private static final String FIELD_BUDGET_MAX = "security_connection_budget_max";

    /**
     * 未携带 APPKey 的流量共用这个哨兵 Key
     */
    private static final String ANONYMOUS_KEY = "@anonymous";

    /**
     * 按 APPKey 分组的在途连接数。
     */
    private final ConcurrentHashMap<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param globalSettingReader 全局设置读取器
     */
    public GatewayConnectionBudgetFilter(GlobalSettingReader globalSettingReader) {
        super(globalSettingReader);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Lease lease = acquire(request, response);
        if (lease.rejected()) {
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            release(lease);
        }
    }

    /**
     * 申请一个在途名额：判定阶段异常一律按「放行」处理（返回空租约）。
     *
     * @param request  请求
     * @param response 响应
     * @return 租约（{@link Lease#rejected()} 为 true 表示已写完 429，不得再进链）
     */
    private Lease acquire(HttpServletRequest request, HttpServletResponse response) {
        AtomicInteger counter = null;
        String key = null;
        try {
            if (!applies(request)
                    || !globalSettingReader().getBool("security", FIELD_BUDGET_ENABLED, true)) {
                return Lease.pass();
            }
            int max = globalSettingReader().getInt("security", FIELD_BUDGET_MAX, 64);
            if (max <= 0) {
                return Lease.pass();
            }
            String appKey = StringUtils.trimToNull(resolveAppKey(request));
            key = appKey == null ? ANONYMOUS_KEY : appKey;
            counter = inFlight.computeIfAbsent(key, k -> new AtomicInteger(0));
            if (counter.incrementAndGet() > max) {
                counter.decrementAndGet();
                log.warn("[GatewayConnectionBudgetFilter] 在途并发超限 key={} max={}", key, max);
                rejectTooMany(request, response, String.valueOf(max));
                return Lease.REJECTED;
            }
            return new Lease(key, counter);
        } catch (Exception e) {
            log.warn("[GatewayConnectionBudgetFilter] 拦截判定异常，已放行: {}", e.getMessage());
            // 已加过计数器就立刻退回，避免预算被自己吞掉
            if (counter != null) {
                counter.decrementAndGet();
            }
            return Lease.pass();
        }
    }

    /**
     * 归还在途名额。
     *
     * @param lease 租约
     */
    private void release(Lease lease) {
        if (lease.counter == null) {
            return;
        }
        int remaining = lease.counter.decrementAndGet();
        if (remaining <= 0) {
            // 归零后移除，避免每个历史 APPKey 常驻一个计数器
            inFlight.remove(lease.key, lease.counter);
        }
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) {
        // 并发预算必须在 finally 里归还，逻辑在 doFilterInternal 里，这里不参与模板
        return false;
    }

    /**
     * 在途名额租约。
     */
    private static final class Lease {

        /**
         * 已拒答的租约：不再进入过滤器链
         */
        private static final Lease REJECTED = new Lease(null, null, true);

        /**
         * 已放行但未占名额的租约（开关关闭、未超限以外的降级路径）
         */
        private static final Lease PASS = new Lease(null, null, false);

        private final String key;

        private final AtomicInteger counter;

        private final boolean rejected;

        private Lease(String key, AtomicInteger counter) {
            this(key, counter, false);
        }

        private Lease(String key, AtomicInteger counter, boolean rejected) {
            this.key = key;
            this.counter = counter;
            this.rejected = rejected;
        }

        static Lease pass() {
            return PASS;
        }

        boolean rejected() {
            return rejected;
        }
    }
}

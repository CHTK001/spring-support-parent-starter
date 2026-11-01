package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
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
 * <p>每个 APPKey 维护一个在途计数器：进入时加 1，超预算则 429 拒绝；退出时减 1（支持
 * Long（SSE）连接长期占住预算）。</p>
 *
 * <p>未携带 APPKey 的流量共享一个 Key（凭借其账户上限），控制台的并发不会因缺少令牌
 * 而突破预算。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayConnectionBudgetFilter extends AbstractGatewaySecurityFilter {

    /**
     * 按 APPKey 分组的在途连接数（key 为 null 表示无凭证流量）。
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
        try {
            // 总开关关闭 / 路径未命中 → 放行
            if (!enabled() || !matches(request)) {
                safePass(request, response, chain);
                return;
            }
            int max = globalSettingReader().getInt("security", "security_connection_budget_max", 64);
            if (max <= 0) {
                safePass(request, response, chain);
                return;
            }
            // 按 APPKey（null 共享）获得计数器
            String key = StringUtils.trimToNull(resolveAppKey(request));
            AtomicInteger current = inFlight.computeIfAbsent(key, k -> new AtomicInteger(0));
            if (current.incrementAndGet() > max) {
                current.decrementAndGet();
                rejectTooMany(request, response, "在途连接超过 " + max);
                return;
            }
            // 真正请求：用 try/finally 保证只释放一次自己的预算
            try {
                safePass(request, response, chain);
            } finally {
                int released = current.decrementAndGet();
                if (released <= 0) {
                    inFlight.remove(key);
                }
            }
        } catch (Exception e) {
            log.warn("[GatewayConnectionBudgetFilter] 拦截异常，放行: {}", e.getMessage());
        }
        safePass(request, response, chain);
    }
}

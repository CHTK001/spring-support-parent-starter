package com.chua.starter.ai.support.manage;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 本地信任鉴权过滤器。
 *
 * <p>仅对 {@code /api/**} 与 {@code /ws} 路径生效（适配 nomicore 前端契约）：
 * <ul>
 *     <li>桌面模式携带 {@code x-nomi-local-trust} 头（Tauri 预加载注入的 per-boot 密钥）→ 标记为受信本地客户端放行；</li>
 *     <li>否则（WebUI 浏览器模式 / 本地开发）→ 默认放行。</li>
 * </ul>
 * 其余路径交由现有 {@code @Permission} / Bearer Token 机制处理，本过滤器不做干预。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@Order(-100)
public class LocalTrustAuthFilter extends OncePerRequestFilter {

    /**
     * 本地信任请求头
     */
    public static final String LOCAL_TRUST_HEADER = "x-nomi-local-trust";

    /**
     * 受信标记属性（供后续鉴权链读取）
     */
    public static final String TRUSTED_ATTR = "nomifun.local.trusted";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/") || uri.startsWith("/ws") || uri.startsWith("/v2")) {
            String trust = request.getHeader(LOCAL_TRUST_HEADER);
            if (trust != null && !trust.isBlank()) {
                request.setAttribute(TRUSTED_ATTR, Boolean.TRUE);
                log.debug("[local-trust] 受信本地客户端: uri={}", uri);
            }
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}

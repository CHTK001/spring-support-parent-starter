package com.chua.starter.monitor.support.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 证书页路径别名：把独立 ACME 部署时的上下文前缀 {@code /monitor/api} 剥掉。
 *
 * <p>证书页前端（{@code pages/server/src/api/acmeApi.ts}）沿用的是独立 ACME 应用
 * （spring-api-support-monitor-starter）的口径 {@code /monitor/api/v1/cert/**}，
 * 该前缀来自它的 {@code server.servlet.context-path=/monitor/api}。</p>
 *
 * <p>ACME 并入主后端进程后（见 {@link AcmeEmbeddedConfiguration}），主后端的
 * context-path 为空，控制器实际挂在 {@code /v1/cert/**}，于是
 * {@code /monitor/api/v1/cert/page} 找不到映射，落进静态资源兜底并返回
 * {@code No static resource monitor/api/v1/cert/page}（HTTP 500）。</p>
 *
 * <p>这里只做路径前缀剥离，不改前端、也不动独立部署：独立部署时本过滤器不参与，
 * 那条 context-path 仍然生效。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public class AcmePathAliasFilter extends OncePerRequestFilter {

    /**
     * 独立 ACME 应用的上下文前缀
     */
    public static final String ALIAS_PREFIX = "/monitor/api";

    /**
     * 剥离前缀后的分隔符
     */
    private static final String PATH_SEPARATOR = "/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String alias = ALIAS_PREFIX + PATH_SEPARATOR;
        if (uri == null || !uri.startsWith(alias)) {
            filterChain.doFilter(request, response);
            return;
        }
        String rewritten = uri.substring(ALIAS_PREFIX.length());
        filterChain.doFilter(new AliasRequest(request, uri, rewritten), response);
    }

    /**
     * 改写请求路径的包装器。
     *
     * <p>Spring MVC 的映射（PATH_PATTERN_PARSER 下由
     * {@code ServletRequestPathUtils} 解析）读的是 {@code getRequestURI()}，
     * 同时覆盖 {@code getServletPath()} / {@code getRequestURL()}，
     * 让日志、重定向与老式解析器看到的是同一条路径。</p>
     */
    private static final class AliasRequest extends HttpServletRequestWrapper {

        /**
         * 原始请求路径
         */
        private final String originalUri;

        /**
         * 剥离前缀后的请求路径
         */
        private final String rewrittenUri;

        /**
         * 构造包装器
         *
         * @param request      原始请求
         * @param originalUri  原始请求路径
         * @param rewrittenUri 剥离前缀后的请求路径
         */
        private AliasRequest(HttpServletRequest request, String originalUri, String rewrittenUri) {
            super(request);
            this.originalUri = originalUri;
            this.rewrittenUri = rewrittenUri;
        }

        @Override
        public String getRequestURI() {
            return rewrittenUri;
        }

        @Override
        public String getServletPath() {
            return rewrittenUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            String url = super.getRequestURL().toString();
            int index = url.indexOf(originalUri);
            if (index < 0) {
                return new StringBuffer(url);
            }
            return new StringBuffer(url.substring(0, index)).append(rewrittenUri);
        }
    }
}

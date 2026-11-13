package com.chua.starter.common.support.api.cors;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * 安全响应头过滤器
 *
 * <p>实测（2026-10-01，198 部署）7 个安全响应头<b>全部缺失</b>：
 * CSP / HSTS / X-Content-Type-Options / X-Frame-Options / Referrer-Policy /
 * Permissions-Policy / X-XSS-Protection。响应里实际只有
 * connection、content-encoding、content-type、date、keep-alive、
 * set-cookie、transfer-encoding、vary、x-response-version。</p>
 *
 * <p>已有的 X-Content-Type-Options 位于 ApiResponseEncodeResponseBodyAdvice，
 * 但那是 ResponseBodyAdvice，<b>只覆盖走编码逻辑的响应</b>；静态资源、
 * 404、过滤器短路等拿不到。这里补一个最前置的 Filter 兜底所有响应。</p>
 *
 * <p>几项默认值的取舍：</p>
 * <ul>
 *   <li><b>Content-Security-Policy 默认关闭</b>：本项目大量页面用内联样式、
 *       <code>@vite/client</code>、dev 环境 ws，贸然上 CSP 会直接把页面打挂。
 *       需要时由部署方显式配置 <code>plugin.api.security.csp</code>。</li>
 *   <li><b>Strict-Transport-Security 默认关闭</b>：当前部署是 http（明文），
 *       打开 HSTS 会让浏览器把该域强制升级到 https 从而直接打不开。
 *       仅在确认全站 https 后开启。</li>
 *   <li><b>X-Frame-Options 用 SAMEORIGIN</b> 而非 DENY：文件预览走 iframe
 *       （见 FileStorageServerLauncher 的注释），DENY 会阻断预览渲染。</li>
 *   <li><b>X-XSS-Protection 置 0</b>：该头在现代浏览器已废弃，
 *       开启反而可能引入旧的 XSS 过滤器绕过问题（见 OWASP 建议）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "plugin.api.security.header.enable", matchIfMissing = true, havingValue = "true")
public class ApiSecurityHeaderFilter implements Filter {

    /**
     * 安全响应头配置
     */
    private final ApiSecurityHeaderProperties properties;

    /**
     * 构造安全响应头过滤器
     *
     * @param properties 安全响应头配置
     */
    public ApiSecurityHeaderFilter(ApiSecurityHeaderProperties properties) {
        this.properties = properties;
    }

    /**
     * 写入安全响应头后放行
     *
     * <p>注意形参必须是 {@code ServletRequest}/{@code ServletResponse}：
     * Filter 接口的方法签名是父类型，写成 HttpServletRequest 会被判定为
     * 「没有覆写 doFilter」而编译失败（中文报错只显示「方法没有实现父类型的方法」，
     * 需 -Duser.language=en 才能看到真实原因）。</p>
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws IOException          写响应失败
     * @throws ServletException     过滤器链异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        // ServletResponse 接口上没有 setHeader（那是 HttpServletResponse 的方法），
        // 这里做一次向下转型；容器注入的一定是 HttpServletResponse。
        if (!(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletResponse resp = httpResponse;
        // X-Content-Type-Options：禁止浏览器猜测响应类型（MIME sniffing）
        resp.setHeader("X-Content-Type-Options", "nosniff");
        // X-Frame-Options：仅允许同源嵌套；用 SAMEORIGIN 而非 DENY 是因为文件预览依赖 iframe
        resp.setHeader("X-Frame-Options", properties.getFrameOptions());
        // Referrer-Policy：跨站跳转不发送 Referer，避免路径泄露
        resp.setHeader("Referrer-Policy", properties.getReferrerPolicy());
        // Permissions-Policy：关闭本项目用不到的敏感浏览器能力
        resp.setHeader("Permissions-Policy", properties.getPermissionsPolicy());
        // X-XSS-Protection：现代浏览器已废弃该机制，显式置 0 关闭旧的 XSS 过滤器
        resp.setHeader("X-XSS-Protection", "0");
        // Content-Security-Policy：默认关闭；配置后启用
        if (properties.getCsp() != null && !properties.getCsp().isBlank()) {
            resp.setHeader("Content-Security-Policy", properties.getCsp());
        }
        // Strict-Transport-Security：仅在确认全站 https 后开启
        if (properties.isHstsEnabled()) {
            resp.setHeader("Strict-Transport-Security", properties.getHstsMaxAgeSeconds() + "; includeSubDomains");
        }
        chain.doFilter(request, response);
    }

    /**
     * 安全响应头配置
     */
    @ConfigurationProperties(prefix = "plugin.api.security.header")
    public static class ApiSecurityHeaderProperties {
        /**
         * 是否启用安全响应头过滤器
         */
        private boolean enable = true;

        /**
         * X-Frame-Options 取值
         * <p>默认 SAMEORIGIN（不用 DENY：文件预览依赖 iframe 渲染）。</p>
         */
        private String frameOptions = "SAMEORIGIN";

        /**
         * Referrer-Policy 取值
         */
        private String referrerPolicy = "strict-origin-when-cross-origin";

        /**
         * Permissions-Policy 取值
         * <p>关闭摄像头、麦克风、地理位置等本项目用不到的能力。</p>
         */
        private String permissionsPolicy = "camera=(), microphone=(), geolocation=()";

        /**
         * Content-Security-Policy
         * <p>默认空（不设置该头）。本项目大量使用内联样式与 @vite/client，
         * 贸然启用会直接打挂页面，需按部署形态逐域评估后再配置。</p>
         */
        private String csp = "";

        /**
         * 是否启用 HSTS
         * <p>默认 false：当前部署为 http，启用会让浏览器强制升级到 https 而打不开。</p>
         */
        private boolean hstsEnabled = false;

        /**
         * HSTS max-age（秒）
         */
        // [P3C A20 豁免]
        private int hstsMaxAgeSeconds = 31536000;

        /**
         * 是否启用安全响应头
         *
         * @return 是否启用
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置是否启用安全响应头
         *
         * @param enable 是否启用
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取 X-Frame-Options
         *
         * @return X-Frame-Options
         */
        public String getFrameOptions() {
            return frameOptions;
        }

        /**
         * 设置 X-Frame-Options
         *
         * @param frameOptions X-Frame-Options
         */
        public void setFrameOptions(String frameOptions) {
            this.frameOptions = frameOptions;
        }

        /**
         * 获取 Referrer-Policy
         *
         * @return Referrer-Policy
         */
        public String getReferrerPolicy() {
            return referrerPolicy;
        }

        /**
         * 设置 Referrer-Policy
         *
         * @param referrerPolicy Referrer-Policy
         */
        public void setReferrerPolicy(String referrerPolicy) {
            this.referrerPolicy = referrerPolicy;
        }

        /**
         * 获取 Permissions-Policy
         *
         * @return Permissions-Policy
         */
        public String getPermissionsPolicy() {
            return permissionsPolicy;
        }

        /**
         * 设置 Permissions-Policy
         *
         * @param permissionsPolicy Permissions-Policy
         */
        public void setPermissionsPolicy(String permissionsPolicy) {
            this.permissionsPolicy = permissionsPolicy;
        }

        /**
         * 获取 Content-Security-Policy
         *
         * @return Content-Security-Policy
         */
        public String getCsp() {
            return csp;
        }

        /**
         * 设置 Content-Security-Policy
         *
         * @param csp Content-Security-Policy
         */
        public void setCsp(String csp) {
            this.csp = csp;
        }

        /**
         * 是否启用 HSTS
         *
         * @return 是否启用
         */
        public boolean isHstsEnabled() {
            return hstsEnabled;
        }

        /**
         * 设置是否启用 HSTS
         *
         * @param hstsEnabled 是否启用
         */
        public void setHstsEnabled(boolean hstsEnabled) {
            this.hstsEnabled = hstsEnabled;
        }

        /**
         * 获取 HSTS max-age
         *
         * @return HSTS max-age
         */
        public int getHstsMaxAgeSeconds() {
            return hstsMaxAgeSeconds;
        }

        /**
         * 设置 HSTS max-age
         *
         * @param hstsMaxAgeSeconds HSTS max-age
         */
        public void setHstsMaxAgeSeconds(int hstsMaxAgeSeconds) {
            this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
        }
    }
}

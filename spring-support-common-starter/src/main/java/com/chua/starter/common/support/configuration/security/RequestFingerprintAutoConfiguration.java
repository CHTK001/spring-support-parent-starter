package com.chua.starter.common.support.configuration.security;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring 请求指纹过滤器自动配置
 *
 * <p>支持计算请求指纹并按有效期去重，基于 {@code com.google.common.hash.Hashing}。</p>
 * <p>可通过 {@code plugin.security.fingerprint.*} 精确控制项启用/禁用。</p>
 *
 * @author CH
 * @since 2025-08-15
 */
@Configuration
public class RequestFingerprintAutoConfiguration {

    /**
     * 指纹配置 Bean，供其他 Bean 注入使用
     */
    @Bean
    public FingerprintProps fingerprintProps() {
        return new FingerprintProps();
    }

    /**
     * 创建请求指纹过滤器
     *
     * @param props 指纹配置
     * @return 过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    public Filter requestFingerprintFilter(FingerprintProps props) {
        final Map<String, Long> cache = new ConcurrentHashMap<>();
        return (ServletRequest req, ServletResponse res, FilterChain chain) -> {
            if (!props.isEnabled()) { chain.doFilter(req, res); return; }
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            // 包装以便读取请求体
            CachedBodyHttpServletRequest wrapped = null;
            if (props.isIncludeBody() && matchBodyMethod(request.getMethod(), props) && withinSizeLimit(request, props.getIncludeBodyMaxBytes())) {
                wrapped = new CachedBodyHttpServletRequest(request, props.getIncludeBodyMaxBytes());
                request = wrapped;
            }

            String fp = computeFingerprint(request, props, wrapped != null ? wrapped.getCachedBody() : null);
            if (fp == null) { chain.doFilter(req, res); return; }

            long now = System.currentTimeMillis();
            Long until = cache.get(fp);
            if (until != null && until >= now && props.isRejectDuplicate()) {
                response.setStatus(409);
                response.setHeader("X-Blocked-Reason", "DUPLICATE_REQUEST");
                response.setHeader(props.getHeaderName(), fp);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Duplicate request\"}");
                return;
            }
            if (props.getValidityMs() > 0) cache.put(fp, now + props.getValidityMs());
            response.setHeader(props.getHeaderName(), fp);
            chain.doFilter(request, res);

            // 简单清理
            if ((now & 0xFF) == 1) {
                cache.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
            }
        };
    }

    /**
     * 计算指纹
     *
     * @param req       请求
     * @param cfg       配置
     * @param bodyBytes 已缓存的请求体字节
     * @return 指纹十六进制字符串
     */
    private String computeFingerprint(HttpServletRequest req, FingerprintProps cfg, byte[] bodyBytes) {
        try {
            HashFunction hf = selectHash(cfg.getAlgorithm());
            Hasher hasher = hf.newHasher();
            if (cfg.getSalt() != null) hasher.putString(cfg.getSalt(), StandardCharsets.UTF_8);
            if (cfg.isIncludeMethod()) hasher.putString(nullToEmpty(req.getMethod()), StandardCharsets.UTF_8);
            if (cfg.isIncludePath()) hasher.putString(nullToEmpty(req.getRequestURI()), StandardCharsets.UTF_8);
            if (cfg.isIncludeParams()) {
                List<String> names = Collections.list(req.getParameterNames());
                Collections.sort(names);
                for (String k : names) {
                    hasher.putString(k, StandardCharsets.UTF_8);
                    for (String v : req.getParameterValues(k)) hasher.putString(nullToEmpty(v), StandardCharsets.UTF_8);
                }
            }
            if (cfg.getIncludeHeaders() != null && !cfg.getIncludeHeaders().isEmpty()) {
                List<String> keys = new ArrayList<>(cfg.getIncludeHeaders());
                keys.sort(String::compareTo);
                for (String k : keys) {
                    String v = req.getHeader(k);
                    if (v != null) { hasher.putString(k, StandardCharsets.UTF_8).putString(v, StandardCharsets.UTF_8); }
                }
            }
            if (cfg.isIncludeBody() && bodyBytes != null && bodyBytes.length > 0) {
                hasher.putBytes(bodyBytes);
            }
            return hasher.hash().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchBodyMethod(String method, FingerprintProps props) {
        if (method == null) {
            return false;
        }
        if (props.getBodyMethods() == null || props.getBodyMethods().isEmpty()) {
            return true;
        }
        return props.getBodyMethods().contains(method.toUpperCase(Locale.ROOT));
    }

    private boolean withinSizeLimit(HttpServletRequest req, long maxBytes) {
        if (maxBytes <= 0) {
            return true;
        }
        long len = req.getContentLengthLong();
        return len <= 0 || len <= maxBytes;
    }

    /**
     * 选择哈希算法
     *
     * @param algorithm 算法名称
     * @return 哈希函数
     */
    private HashFunction selectHash(String algorithm) {
        if (algorithm == null) {
            return Hashing.murmur3_128();
        }
        String a = algorithm.replace("-", "").toLowerCase(Locale.ROOT);
        return switch (a) {
            case "sha256" -> Hashing.sha256();
            case "sha1" -> Hashing.sha1();
            case "md5" -> Hashing.md5();
            case "murmur3_32", "murmur332" -> Hashing.murmur3_32();
            case "murmur3_128", "murmur3128" -> Hashing.murmur3_128();
            default -> Hashing.murmur3_128();
        };
    }

    /**
     * null 安全转换
     *
     * @param s 字符串
     * @return 非空字符串
     */
    private String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * 指纹配置属性
     */
    public static class FingerprintProps {
        private boolean enabled = false;
        private String headerName = "X-Request-Fingerprint";
        private String algorithm = "SHA-256";
        private String salt;
        private boolean includeMethod = true;
        private boolean includePath = true;
        private boolean includeParams = true;
        private Set<String> includeHeaders = new LinkedHashSet<>();
        private boolean includeBody = false;
        private long includeBodyMaxBytes = 1024 * 1024; // 1MB 默认上限
        private Set<String> bodyMethods = new LinkedHashSet<>(Arrays.asList("POST", "PUT", "PATCH"));
        private long validityMs = 60_000;
        private boolean rejectDuplicate = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }

        public boolean isIncludeMethod() {
            return includeMethod;
        }

        public void setIncludeMethod(boolean includeMethod) {
            this.includeMethod = includeMethod;
        }

        public boolean isIncludePath() {
            return includePath;
        }

        public void setIncludePath(boolean includePath) {
            this.includePath = includePath;
        }

        public boolean isIncludeParams() {
            return includeParams;
        }

        public void setIncludeParams(boolean includeParams) {
            this.includeParams = includeParams;
        }

        public Set<String> getIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(Set<String> includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        public boolean isIncludeBody() {
            return includeBody;
        }

        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        public long getIncludeBodyMaxBytes() {
            return includeBodyMaxBytes;
        }

        public void setIncludeBodyMaxBytes(long includeBodyMaxBytes) {
            this.includeBodyMaxBytes = includeBodyMaxBytes;
        }

        public Set<String> getBodyMethods() {
            return bodyMethods;
        }

        public void setBodyMethods(Set<String> bodyMethods) {
            this.bodyMethods = bodyMethods;
        }

        public long getValidityMs() {
            return validityMs;
        }

        public void setValidityMs(long validityMs) {
            this.validityMs = validityMs;
        }

        public boolean isRejectDuplicate() {
            return rejectDuplicate;
        }

        public void setRejectDuplicate(boolean rejectDuplicate) {
            this.rejectDuplicate = rejectDuplicate;
        }
    }

    /**
     * 带缓存请求体的 Request 包装器
     */
    static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;
        /**
         * 构造
         *
         * @param request 原请求
         * @param maxBytes 最大读取字节数（<=0 表示不限制）
         */
        CachedBodyHttpServletRequest(HttpServletRequest request, long maxBytes) throws java.io.IOException {
            super(request);
            java.io.InputStream is = request.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long remaining = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
            int read;
            while ((read = is.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                baos.write(buf, 0, read);
                remaining -= read;
                if (remaining <= 0) break;
            }
            this.cachedBody = baos.toByteArray();
        }
        byte[] getCachedBody() { return cachedBody; }
        @Override
        public ServletInputStream getInputStream() {
            final java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { }
                @Override public int read() { return bais.read(); }
            };
        }
        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}



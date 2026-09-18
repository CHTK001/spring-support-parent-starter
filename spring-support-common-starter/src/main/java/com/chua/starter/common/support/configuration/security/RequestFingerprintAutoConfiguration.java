package com.chua.starter.common.support.configuration.security;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
* Spring 请求指纹过滤器自动配置
*
* <p>支持计算请求指纹并按有效期去重，基于 {@code com.google.common.hash.Hashing}。</p>
* <p>可通过 {@code plugin.security.fingerprint.*} 精确控制项启用/禁用。</p>
*
* @since 2025-08-15
* @author CH
 */
@Configuration
@EnableConfigurationProperties(RequestFingerprintAutoConfiguration.FingerprintProps.class)
public class RequestFingerprintAutoConfiguration {

    /** Fingerprintcleaner */
    private ScheduledExecutorService fingerprintCleaner;

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
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fingerprint-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(
                () -> cache.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis()),
                1, 1, TimeUnit.MINUTES
        );
        this.fingerprintCleaner = cleaner;
        return (ServletRequest req, ServletResponse res, FilterChain chain) -> {
            if (!props.isEnabled()) {
                chain.doFilter(req, res);
                return;
            }
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            // 包装以便读取请求体
            CachedBodyHttpServletRequest wrapped = null;
            if (props.isIncludeBody() && matchBodyMethod(request.getMethod(), props) && withinSizeLimit(request, props.getIncludeBodyMaxBytes())) {
                wrapped = new CachedBodyHttpServletRequest(request, props.getIncludeBodyMaxBytes());
                request = wrapped;
            }

            String fp = computeFingerprint(request, props, wrapped != null ? wrapped.getCachedBody() : null);
            if (fp == null) {
                chain.doFilter(req, res);
                return;
            }

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
            if (props.getValidityMs() > 0) {
                cache.put(fp, now + props.getValidityMs());
            }
            response.setHeader(props.getHeaderName(), fp);
            chain.doFilter(request, res);
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
            if (cfg.getSalt() != null) {
                hasher.putString(cfg.getSalt(), StandardCharsets.UTF_8);
            }
            if (cfg.isIncludeMethod()) {
                hasher.putString(nullToEmpty(req.getMethod()), StandardCharsets.UTF_8);
            }
            if (cfg.isIncludePath()) {
                hasher.putString(nullToEmpty(req.getRequestURI()), StandardCharsets.UTF_8);
            }
            if (cfg.isIncludeParams()) {
                List<String> names = Collections.list(req.getParameterNames());
                Collections.sort(names);
                for (String k : names) {
                    hasher.putString(k, StandardCharsets.UTF_8);
                    for (String v : req.getParameterValues(k)) {
                        hasher.putString(nullToEmpty(v), StandardCharsets.UTF_8);
                    }
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

    /**
    * 匹配主体方法
    *
    * @param method 方法
    * @param props props
    * @return 匹配主体方法的结果
    */
    private boolean matchBodyMethod(String method, FingerprintProps props) {
        if (method == null) {
            return false;
        }
        if (props.getBodyMethods() == null || props.getBodyMethods().isEmpty()) {
            return true;
        }
        return props.getBodyMethods().contains(method.toUpperCase(Locale.ROOT));
    }

    /**
    * Within获取大小限制
    *
    * @param req req
    * @param maxBytes 最大bytes
    * @return within大小限制的结果
    */
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
    * 空 安全转换
    *
    * @param s 字符串
    * @return 非空字符串
    */
    private String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
    * 指纹配置属性
    * @author CH
    * @since 4.0.0
    */
    @ConfigurationProperties(prefix = "plugin.security.fingerprint")
    public static class FingerprintProps {
        /** 是否启用 */
        private boolean enabled = false;
        /** 头部名称 */
        private String headerName = "X-Request-Fingerprint";
        /** 算法 */
        private String algorithm = "SHA-256";
        /** Salt */
        private String salt;
        /** Includemethod */
        private boolean includeMethod = true;
        /** Include路径 */
        private boolean includePath = true;
        /** Include参数 */
        private boolean includeParams = true;
        /** Includeheaders */
        private Set<String> includeHeaders = new LinkedHashSet<>();
        /** Include请求体 */
        private boolean includeBody = false;
        /** 1MB 默认上限 */
        private long includeBodyMaxBytes = 1024 * 1024;
        /** 请求体方法 */
        private Set<String> bodyMethods = new LinkedHashSet<>(Arrays.asList("POST", "PUT", "PATCH"));
        /** validityms */
        private long validityMs = 60_000;
        /** Rejectduplicate */
        private boolean rejectDuplicate = false;

        /**
        * 是否已启用
        *
        * @return 是否已启用的结果
        */
        public boolean isEnabled() {
            return enabled;
        }

        /**
        * 设置已启用
        *
        * @param enabled 已启用
        */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
        * 获取头部名称
        *
        * @return 获取头部名称的结果
        */
        public String getHeaderName() {
            return headerName;
        }

        /**
        * 设置头部名称
        *
        * @param headerName 头部名称
        */
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /**
        * 获取Algorithm
        *
        * @return 获取algorithm的结果
        */
        public String getAlgorithm() {
            return algorithm;
        }

        /**
        * 设置Algorithm
        *
        * @param algorithm algorithm
        */
        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        /**
        * 获取Salt
        *
        * @return 获取salt的结果
        */
        public String getSalt() {
            return salt;
        }

        /**
        * 设置Salt
        *
        * @param salt salt
        */
        public void setSalt(String salt) {
            this.salt = salt;
        }

        /**
        * 是否include方法
        *
        * @return 是否include方法的结果
        */
        public boolean isIncludeMethod() {
            return includeMethod;
        }

        /**
        * 设置include方法
        *
        * @param includeMethod include方法
        */
        public void setIncludeMethod(boolean includeMethod) {
            this.includeMethod = includeMethod;
        }

        /**
        * 是否include路径
        *
        * @return 是否include路径的结果
        */
        public boolean isIncludePath() {
            return includePath;
        }

        /**
        * 设置include路径
        *
        * @param includePath include路径
        */
        public void setIncludePath(boolean includePath) {
            this.includePath = includePath;
        }

        /**
        * 是否include参数
        *
        * @return 是否include参数的结果
        */
        public boolean isIncludeParams() {
            return includeParams;
        }

        /**
        * 设置include参数
        *
        * @param includeParams include参数
        */
        public void setIncludeParams(boolean includeParams) {
            this.includeParams = includeParams;
        }

        /**
        * 获取include头部
        *
        * @return 获取include头部的结果
        */
        public Set<String> getIncludeHeaders() {
            return includeHeaders;
        }

        /**
        * 设置include头部
        *
        * @param includeHeaders include头部
        */
        public void setIncludeHeaders(Set<String> includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        /**
        * 是否include主体
        *
        * @return 是否include主体的结果
        */
        public boolean isIncludeBody() {
            return includeBody;
        }

        /**
        * 设置include主体
        *
        * @param includeBody include主体
        */
        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        /**
        * 获取include主体最大值Bytes
        *
        * @return 获取include主体最大bytes的结果
        */
        public long getIncludeBodyMaxBytes() {
            return includeBodyMaxBytes;
        }

        /**
        * 设置include主体最大值Bytes
        *
        * @param includeBodyMaxBytes include主体最大bytes
        */
        public void setIncludeBodyMaxBytes(long includeBodyMaxBytes) {
            this.includeBodyMaxBytes = includeBodyMaxBytes;
        }

        /**
        * 获取主体方法
        *
        * @return 获取主体方法的结果
        */
        public Set<String> getBodyMethods() {
            return bodyMethods;
        }

        /**
        * 设置主体方法
        *
        * @param bodyMethods 主体方法
        */
        public void setBodyMethods(Set<String> bodyMethods) {
            this.bodyMethods = bodyMethods;
        }

        /**
        * 获取validityms
        *
        * @return 获取validityms的结果
        */
        public long getValidityMs() {
            return validityMs;
        }

        /**
        * 设置validityms
        *
        * @param validityMs validityms
        */
        public void setValidityMs(long validityMs) {
            this.validityMs = validityMs;
        }

        /**
        * 是否reject重复
        *
        * @return 是否reject重复的结果
        */
        public boolean isRejectDuplicate() {
            return rejectDuplicate;
        }

        /**
        * 设置reject重复
        *
        * @param rejectDuplicate reject重复
        */
        public void setRejectDuplicate(boolean rejectDuplicate) {
            this.rejectDuplicate = rejectDuplicate;
        }
    }

    /**
    * 应用销毁时关闭指纹缓存清理线程池
    */
    @PreDestroy
    public void destroy() {
        if (fingerprintCleaner != null) {
            fingerprintCleaner.shutdownNow();
        }
    }

    /**
    * 带缓存请求体的 请求 包装器
    * @author CH
    * @since 4.0.0
    */
    static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        /** Cached请求体 */
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
                if (remaining <= 0) {
                    break;
                }
            }
            this.cachedBody = baos.toByteArray();
        }
        byte[] getCachedBody() { return cachedBody; }
        @Override
        /** 获取输入流 */
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
        /** 获取Reader */
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}


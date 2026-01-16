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
    /**
     * 获取 cache
     *
     * @return cache
     */
    public Map<String, Long> getCache() {
        return cache;
    }

    /**
     * 设置 cache
     *
     * @param cache cache
     */
    public void setCache(Map<String, Long> cache) {
        this.cache = cache;
    }

    /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 wrapped
     *
     * @return wrapped
     */
    public CachedBodyHttpServletRequest getWrapped() {
        return wrapped;
    }

    /**
     * 设置 wrapped
     *
     * @param wrapped wrapped
     */
    public void setWrapped(CachedBodyHttpServletRequest wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * 获取 fp
     *
     * @return fp
     */
    public String getFp() {
        return fp;
    }

    /**
     * 设置 fp
     *
     * @param fp fp
     */
    public void setFp(String fp) {
        this.fp = fp;
    }

    /**
     * 获取 now
     *
     * @return now
     */
    public long getNow() {
        return now;
    }

    /**
     * 设置 now
     *
     * @param now now
     */
    public void setNow(long now) {
        this.now = now;
    }

    /**
     * 获取 until
     *
     * @return until
     */
    public Long getUntil() {
        return until;
    }

    /**
     * 设置 until
     *
     * @param until until
     */
    public void setUntil(Long until) {
        this.until = until;
    }

    /**
     * 获取 hf
     *
     * @return hf
     */
    public HashFunction getHf() {
        return hf;
    }

    /**
     * 设置 hf
     *
     * @param hf hf
     */
    public void setHf(HashFunction hf) {
        this.hf = hf;
    }

    /**
     * 获取 hasher
     *
     * @return hasher
     */
    public Hasher getHasher() {
        return hasher;
    }

    /**
     * 设置 hasher
     *
     * @param hasher hasher
     */
    public void setHasher(Hasher hasher) {
        this.hasher = hasher;
    }

    /**
     * 获取 names
     *
     * @return names
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * 设置 names
     *
     * @param names names
     */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /**
     * 获取 keys
     *
     * @return keys
     */
    public List<String> getKeys() {
        return keys;
    }

    /**
     * 设置 keys
     *
     * @param keys keys
     */
    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    /**
     * 获取 v
     *
     * @return v
     */
    public String getV() {
        return v;
    }

    /**
     * 设置 v
     *
     * @param v v
     */
    public void setV(String v) {
        this.v = v;
    }

                                    /**
     * 获取 len
     *
     * @return len
     */
    public long getLen() {
        return len;
    }

    /**
     * 设置 len
     *
     * @param len len
     */
    public void setLen(long len) {
        this.len = len;
    }

    /**
     * 获取 a
     *
     * @return a
     */
    public String getA() {
        return a;
    }

    /**
     * 设置 a
     *
     * @param a a
     */
    public void setA(String a) {
        this.a = a;
    }

            /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 headerName
     *
     * @return headerName
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * 设置 headerName
     *
     * @param headerName headerName
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     * 获取 algorithm
     *
     * @return algorithm
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * 设置 algorithm
     *
     * @param algorithm algorithm
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * 获取 salt
     *
     * @return salt
     */
    public String getSalt() {
        return salt;
    }

    /**
     * 设置 salt
     *
     * @param salt salt
     */
    public void setSalt(String salt) {
        this.salt = salt;
    }

    /**
     * 获取 includeMethod
     *
     * @return includeMethod
     */
    public boolean getIncludeMethod() {
        return includeMethod;
    }

    /**
     * 设置 includeMethod
     *
     * @param includeMethod includeMethod
     */
    public void setIncludeMethod(boolean includeMethod) {
        this.includeMethod = includeMethod;
    }

    /**
     * 获取 includePath
     *
     * @return includePath
     */
    public boolean getIncludePath() {
        return includePath;
    }

    /**
     * 设置 includePath
     *
     * @param includePath includePath
     */
    public void setIncludePath(boolean includePath) {
        this.includePath = includePath;
    }

    /**
     * 获取 includeParams
     *
     * @return includeParams
     */
    public boolean getIncludeParams() {
        return includeParams;
    }

    /**
     * 设置 includeParams
     *
     * @param includeParams includeParams
     */
    public void setIncludeParams(boolean includeParams) {
        this.includeParams = includeParams;
    }

    /**
     * 获取 includeHeaders
     *
     * @return includeHeaders
     */
    public Set<String> getIncludeHeaders() {
        return includeHeaders;
    }

    /**
     * 设置 includeHeaders
     *
     * @param includeHeaders includeHeaders
     */
    public void setIncludeHeaders(Set<String> includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    /**
     * 获取 includeBody
     *
     * @return includeBody
     */
    public boolean getIncludeBody() {
        return includeBody;
    }

    /**
     * 设置 includeBody
     *
     * @param includeBody includeBody
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    /**
     * 获取 includeBodyMaxBytes
     *
     * @return includeBodyMaxBytes
     */
    public long getIncludeBodyMaxBytes() {
        return includeBodyMaxBytes;
    }

    /**
     * 设置 includeBodyMaxBytes
     *
     * @param includeBodyMaxBytes includeBodyMaxBytes
     */
    public void setIncludeBodyMaxBytes(long includeBodyMaxBytes) {
        this.includeBodyMaxBytes = includeBodyMaxBytes;
    }

    /**
     * 获取 bodyMethods
     *
     * @return bodyMethods
     */
    public Set<String> getBodyMethods() {
        return bodyMethods;
    }

    /**
     * 设置 bodyMethods
     *
     * @param bodyMethods bodyMethods
     */
    public void setBodyMethods(Set<String> bodyMethods) {
        this.bodyMethods = bodyMethods;
    }

    /**
     * 获取 validityMs
     *
     * @return validityMs
     */
    public long getValidityMs() {
        return validityMs;
    }

    /**
     * 设置 validityMs
     *
     * @param validityMs validityMs
     */
    public void setValidityMs(long validityMs) {
        this.validityMs = validityMs;
    }

    /**
     * 获取 rejectDuplicate
     *
     * @return rejectDuplicate
     */
    public boolean getRejectDuplicate() {
        return rejectDuplicate;
    }

    /**
     * 设置 rejectDuplicate
     *
     * @param rejectDuplicate rejectDuplicate
     */
    public void setRejectDuplicate(boolean rejectDuplicate) {
        this.rejectDuplicate = rejectDuplicate;
    }

    /**
     * 获取 cachedBody
     *
     * @return cachedBody
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    /**
     * 设置 cachedBody
     *
     * @param cachedBody cachedBody
     */
    public void setCachedBody(byte[] cachedBody) {
        this.cachedBody = cachedBody;
    }

    /**
     * 获取 buf
     *
     * @return buf
     */
    public byte[] getBuf() {
        return buf;
    }

    /**
     * 设置 buf
     *
     * @param buf buf
     */
    public void setBuf(byte[] buf) {
        this.buf = buf;
    }

    /**
     * 获取 remaining
     *
     * @return remaining
     */
    public long getRemaining() {
        return remaining;
    }

    /**
     * 设置 remaining
     *
     * @param remaining remaining
     */
    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    /**
     * 获取 read
     *
     * @return read
     */
    public int getRead() {
        return read;
    }

    /**
     * 设置 read
     *
     * @param read read
     */
    public void setRead(int read) {
        this.read = read;
    }

                    }
}



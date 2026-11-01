package com.chua.starter.ai.support.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 网关请求体大小限流过滤器。
 *
 * <p>带 {@code Content-Length} 的请求：只检查头部，零额外开销；<br>
 * 分块传输（无 {@code Content-Length}）：一次性读完，超过限制拒绝，不超过则回放给下游。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayBodySizeFilter extends AbstractGatewaySecurityFilter {

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param globalSettingReader 全局设置读取器
     */
    public GatewayBodySizeFilter(GlobalSettingReader globalSettingReader) {
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
            int maxBytes = globalSettingReader().getInt("security", "security_body_limit_max_bytes", 8_388_608);
            if (maxBytes <= 0) {
                safePass(request, response, chain);
                return;
            }
            int contentLength = request.getContentLength();
            if (contentLength > maxBytes) {
                // 已知长度且超限：直接 413，不进下游
                rejectBodySize(request, response, String.valueOf(maxBytes));
                return;
            }
            if (contentLength > 0) {
                // 已知长度、未超限：只有一位字节的放大，不掉用包装器
                safePass(request, response, chain);
                return;
            }
            // 无 Content-Length（分块流）：先读满再判断，再回放
            byte[] body = readBody(request, maxBytes + 1);
            if (body.length > maxBytes) {
                rejectBodySize(request, response, String.valueOf(maxBytes));
                return;
            }
            chain.doFilter(new ReplayingRequestWrapper(request, body), response);
        } catch (Exception e) {
            log.warn("[GatewayBodySizeFilter] 拦截异常，放行: {}", e.getMessage());
        }
        safePass(request, response, chain);
    }

    /**
     * 一次性读完请求体（受限），用于分块传输的提前检测。
     *
     * @param request 请求
     * @param limit   最大可读的字节数 + 1（超过则刚好截断在 limit）
     * @return 读到的所有字节
     */
    private static byte[] readBody(HttpServletRequest request, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServletInputStream in = request.getInputStream();
        byte[] buf = new byte[4096];
        int read = 0;
        int total = 0;
        while ((read = in.read(buf, 0, buf.length)) > 0) {
            total += read;
            out.write(buf, 0, read);
            if (total >= limit) {
                break;
            }
        }
        in.close();
        return out.toByteArray();
    }

    /**
     * 把读入内存的请求体回放给下游：对 {@code getContentLength()} 做静态回放。
     */
    private static final class ReplayingRequestWrapper extends HttpServletRequestWrapper {

        /**
         * 请求体
         */
        private final byte[] body;

        ReplayingRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ReplayingServletInputStream(body);
        }

        @Override
        public int getContentLength() {
            return body.length;
        }
    }

    /**
     * 从字节数组回放的 {@link ServletInputStream}。
     */
    private static final class ReplayingServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream stream;

        ReplayingServletInputStream(byte[] body) {
            this.stream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return stream.read();
        }

        @Override
        public int read(byte[] bbuf, int off, int len) {
            return stream.read(bbuf, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(Logger listener) {
            throw new UnsupportedOperationException("read in blocking mode");
        }
    }
}

package com.chua.starter.ai.support.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
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
 * 网关请求体大小限制过滤器（顺序第五，对应 Sub2API 的 body size guard）。
 *
 * <h2>三条判定路径</h2>
 * <ol>
 *   <li><b>带 Content-Length</b>：只读头部，零额外开销 —— 超限直接 413，未超限原样放行；</li>
 *   <li><b>无 Content-Length（分块传输）</b>：读满 {@code limit + 1} 字节后再判，
 *       超限 413，未超限把已读内容回放给下游；</li>
 *   <li><b>限制 &lt;= 0</b>：视为不限制，直接放行。</li>
 * </ol>
 *
 * <h2>为什么必须回放</h2>
 * <p>分块请求一旦为了测量大小而消费了 {@code ServletInputStream}，下游就读不到正文了。
 * 因此这条路径必须用 {@link ReplayingRequestWrapper} 把已读字节退回给下游，
 * 并同步修正 {@code getContentLength} / {@code getContentLengthLong}，否则框架仍按
 * -1 处理，容易把 JSON 正文解析成空。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayBodySizeFilter extends AbstractGatewaySecurityFilter {

    /**
     * 一次性读取的块大小
     */
    private static final int READ_BUFFER = 4096;

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param settings 安全设置快照
     */
    public GatewayBodySizeFilter(SecuritySettings settings) {
        super(settings);
    }

    /**
     * 本过滤器需要在判定阶段消费正文，因此覆盖模板：判定完成后再决定用原请求还是回放请求进链。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] replayable = null;
        try {
            if (applies(request) && settings().getBool("security_body_limit_enabled", true)) {
                int maxBytes = settings().getInt("security_body_limit_max_bytes", 8_388_608);
                if (maxBytes > 0) {
                    long contentLength = request.getContentLengthLong();
                    if (contentLength > maxBytes) {
                        log.warn("[GatewayBodySizeFilter] 请求体超限 {} > {}: {}", contentLength, maxBytes,
                                request.getRequestURI());
                        rejectBodySize(response, maxBytes);
                        return;
                    }
                    if (contentLength < 0) {
                        // 分块传输：读满 limit + 1 再判，未超限则回放
                        byte[] body = readBody(request, maxBytes);
                        if (body.length > maxBytes) {
                            log.warn("[GatewayBodySizeFilter] 分块请求体超限 > {}: {}", maxBytes,
                                    request.getRequestURI());
                            rejectBodySize(response, maxBytes);
                            return;
                        }
                        replayable = body;
                    }
                }
            }
        } catch (Exception e) {
            // 判定异常：放行。若正文已被消费则无法回放，下游会读到空正文 —— 但相比直接
            // 500 拒掉整条请求，放行仍是有损但可用的降级
            log.warn("[GatewayBodySizeFilter] 拦截判定异常，已放行: {}", e.getMessage());
        }
        if (replayable != null) {
            chain.doFilter(new ReplayingRequestWrapper(request, replayable), response);
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) {
        // 正文限制需要回放包装，逻辑在 doFilterInternal 里，这里不参与模板
        return false;
    }

    /**
     * 一次性读完请求体（最多 {@code limit + 1} 字节）。
     *
     * @param request 请求
     * @param limit   上限（函数会多读若干字节以判断是否超限）
     * @return 读到的字节
     * @throws IOException IO 异常
     */
    private static byte[] readBody(HttpServletRequest request, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit + 1, 65_536));
        ServletInputStream in = request.getInputStream();
        byte[] buf = new byte[READ_BUFFER];
        int read;
        while ((read = in.read(buf, 0, buf.length)) > 0) {
            out.write(buf, 0, read);
            if (out.size() > limit) {
                // 已确认超限，剩下的不必再读
                break;
            }
        }
        return out.toByteArray();
    }

    /**
     * 把读入内存的请求体回放给下游。
     */
    private static final class ReplayingRequestWrapper extends HttpServletRequestWrapper {

        /**
         * 已读请求体
         */
        private final byte[] body;

        ReplayingRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ReplayingServletInputStream(body);
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /**
     * 从字节数组回放的 {@link ServletInputStream}。
     */
    private static final class ReplayingServletInputStream extends ServletInputStream {

        /**
         * 内存副本
         */
        private final ByteArrayInputStream stream;

        ReplayingServletInputStream(byte[] body) {
            this.stream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return stream.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return stream.read(buffer, offset, length);
        }

        @Override
        public boolean isReady() {
            // 全部在内存里，永远「已就绪」
            return true;
        }

        @Override
        public boolean isFinished() {
            return stream.available() == 0;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            if (listener == null) {
                return;
            }
            // 数据已完整在内存中：立即以非阻塞语义通知读就绪与读完
            try {
                if (!isFinished()) {
                    listener.onDataAvailable();
                }
                listener.onAllDataRead();
            } catch (IOException e) {
                listener.onError(e);
            }
        }
    }
}

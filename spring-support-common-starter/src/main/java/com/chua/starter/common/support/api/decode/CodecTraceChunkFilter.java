// [P3C A20 豁免] A20/A30/A31: 含日志/协议头/JSON 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.api.decode;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * codec 分段传输过滤器（traceId 分片协议）
 *
 * <p>与 AES 整包加密模式并存的第二条传输通道：前端把「同一逻辑请求」的密文
 * 拆成多个小分片，携带同一 traceId 分多次上传；本过滤器按 traceId 缓存分片，
 * 收齐末片后重组完整密文并放行原有解密管线（{@link ApiRequestDecodeBodyAdvice}），
 * 再把（已编码的）响应体按分片缓存，供前端凭 traceId 逐片拉取重组。</p>
 *
 * <p>协议约定（与前端 httpCrypto.ts 对齐）：</p>
 * <ul>
 *     <li>请求头 {@code X-Codec-Trace-Id}：同一逻辑请求的唯一追踪 ID</li>
 *     <li>请求头 {@code X-Codec-Chunk-Index}：当前分片序号（0 起始）</li>
 *     <li>请求头 {@code X-Codec-Chunk-Total}：总分片数</li>
 *     <li>非 GET/HEAD 且带 traceId 头 → 视为分片上传；非末片直接短路返回确认，
 *     末片触发真实业务管线（鉴权/解密/响应编码均只走一次）</li>
 *     <li>GET/HEAD 且带 traceId 头 → 视为响应分片拉取，按序号返回缓存的响应分片</li>
 * </ul>
 *
 * <p>开关：系统配置 {@code CodecTraceOpen}（group=config），关闭时带 traceId 头的
 * 请求一律 400 拒绝，避免前端/后端开关不一致时把分片体误当业务体。</p>
 *
 * <p>资源防护：分片会话绑定 method+URI、限制分片数/总字节数/并发会话数，
 * 会话按 TTL 惰性过期（借每次访问顺带清理，不新增定时线程）。</p>
 *
 * @since 2026/11/20
 * @author CH
 */
public class CodecTraceChunkFilter implements Filter {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(CodecTraceChunkFilter.class);

    /**
     * 分片协议头：逻辑请求追踪 ID
     */
    public static final String HEADER_TRACE_ID = "X-Codec-Trace-Id";

    /**
     * 分片协议头：当前分片序号（0 起始）
     */
    public static final String HEADER_CHUNK_INDEX = "X-Codec-Chunk-Index";

    /**
     * 分片协议头：总分片数
     */
    public static final String HEADER_CHUNK_TOTAL = "X-Codec-Chunk-Total";

    /**
     * 拒绝响应体：分段传输未开启
     */
    private static final String REJECT_DISABLED_MSG = "{\"code\":400,\"msg\":\"codec trace transport disabled\"}";

    /**
     * 拒绝响应体：分片协议校验失败
     */
    private static final String REJECT_INVALID_MSG = "{\"code\":400,\"msg\":\"invalid codec trace chunk\"}";

    /**
     * 拒绝响应体：分片会话不存在或已过期
     */
    private static final String REJECT_NOT_FOUND_MSG = "{\"code\":404,\"msg\":\"codec trace session not found\"}";

    /**
     * 单逻辑请求最大分片数
     */
    private static final int MAX_CHUNK_TOTAL = 1024;

    /**
     * 单逻辑请求最大重组字节数（32MB）
     */
    private static final int MAX_ASSEMBLED_BYTES = 32 * 1024 * 1024;

    /**
     * 响应分片大小（64KB）
     */
    private static final int RESPONSE_CHUNK_SIZE = 64 * 1024;

    /**
     * 分片会话存活时长（毫秒）
     */
    private static final long BUFFER_TTL_MS = 60_000L;

    /**
     * 分片会话最大并发数
     */
    private static final int MAX_BUFFERS = 1024;

    /**
     * 请求解码注册器（读取 CodecTraceOpen 开关；decode 关闭时为 null）
     */
    private final ApiRequestDecodeRegister register;

    /**
     * 分片会话缓存：traceId → 分片缓冲
     */
    private final ConcurrentHashMap<String, TraceBuffer> buffers = new ConcurrentHashMap<>();

    /**
     * 创建 codec 分段传输过滤器
     *
     * @param register 请求解码注册器（可为 null，视为功能关闭）
     */
    public CodecTraceChunkFilter(ApiRequestDecodeRegister register) {
        this.register = register;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }
        String traceId = req.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        traceId = traceId.trim();
        if (register == null || !register.isTraceOpen()) {
            log.warn("[CodecTrace] 分段传输未开启，拒绝带 traceId 的请求 uri={}", req.getRequestURI());
            reject(resp, HttpServletResponse.SC_BAD_REQUEST, REJECT_DISABLED_MSG);
            return;
        }
        String method = req.getMethod().toUpperCase();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            servePull(req, resp, traceId);
            return;
        }
        handleUpload(req, resp, chain, traceId, method);
    }

    /**
     * 处理分片上传：缓存分片；非末片短路返回确认，末片重组并放行业务管线。
     *
     * @param req     请求
     * @param resp    响应
     * @param chain   过滤器链
     * @param traceId 追踪 ID
     * @param method  请求方法（大写）
     */
    private void handleUpload(HttpServletRequest req, HttpServletResponse resp, FilterChain chain,
                              String traceId, String method) throws IOException, ServletException {
        int index = parseHeader(req, HEADER_CHUNK_INDEX, -1);
        int total = parseHeader(req, HEADER_CHUNK_TOTAL, -1);
        if (index < 0 || total <= 0 || index >= total || total > MAX_CHUNK_TOTAL) {
            reject(resp, HttpServletResponse.SC_BAD_REQUEST, REJECT_INVALID_MSG);
            return;
        }
        byte[] body = req.getInputStream().readAllBytes();
        TraceBuffer buffer = buffers.compute(traceId, (key, existed) ->
                existed != null ? existed : new TraceBuffer(method, req.getRequestURI(), total));
        if (!buffer.matches(method, req.getRequestURI(), total) || !buffer.store(index, body)) {
            reject(resp, HttpServletResponse.SC_BAD_REQUEST, REJECT_INVALID_MSG);
            return;
        }
        purgeExpired();
        if (index < total - 1) {
            writeJson(resp, HttpServletResponse.SC_OK, "{\"code\":200,\"msg\":\"chunk accepted\",\"data\":{\"traceId\":\""
                    + escapeJson(traceId) + "\",\"index\":" + index + ",\"total\":" + total + "}}");
            return;
        }
        completeAndDispatch(req, resp, chain, traceId, buffer);
    }

    /**
     * 末片到达：重组完整密文、放行业务管线并缓存响应分片，最后返回确认（含响应分片总数与业务状态码）。
     *
     * @param req     请求
     * @param resp    响应
     * @param chain   过滤器链
     * @param traceId 追踪 ID
     * @param buffer  分片缓冲
     */
    private void completeAndDispatch(HttpServletRequest req, HttpServletResponse resp, FilterChain chain,
                                     String traceId, TraceBuffer buffer) throws IOException, ServletException {
        byte[] assembled = buffer.assemble();
        if (assembled == null) {
            reject(resp, HttpServletResponse.SC_BAD_REQUEST, REJECT_INVALID_MSG);
            return;
        }
        CapturedResponse captured = new CapturedResponse(resp);
        try {
            chain.doFilter(new AssembledRequest(req, assembled), captured);
        } finally {
            buffer.completeResponse(captured.status(), split(captured.bytes()));
        }
        writeJson(resp, HttpServletResponse.SC_OK, "{\"code\":200,\"msg\":\"trace completed\",\"data\":{\"traceId\":\""
                + escapeJson(traceId) + "\",\"total\":" + buffer.responseChunkCount() + ",\"status\":"
                + captured.status() + "}}");
    }

    /**
     * 响应分片拉取：按序号返回缓存的响应分片，末片拉取后清理会话。
     *
     * @param req     请求
     * @param resp    响应
     * @param traceId 追踪 ID
     */
    private void servePull(HttpServletRequest req, HttpServletResponse resp, String traceId) throws IOException {
        int index = parseHeader(req, HEADER_CHUNK_INDEX, -1);
        TraceBuffer buffer = buffers.get(traceId);
        byte[][] chunks = buffer == null ? null : buffer.responseChunks();
        if (buffer == null || chunks == null || index < 0 || index >= chunks.length) {
            reject(resp, HttpServletResponse.SC_NOT_FOUND, REJECT_NOT_FOUND_MSG);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/octet-stream");
        resp.setContentLength(chunks[index].length);
        resp.getOutputStream().write(chunks[index]);
        resp.flushBuffer();
        if (index == chunks.length - 1) {
            buffers.remove(traceId);
        }
    }

    /**
     * 惰性清理：过期会话直接移除；会话数超上限时淘汰最旧会话。
     * 借每次分片上传顺带执行，不引入定时线程。
     */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TraceBuffer> entry : buffers.entrySet()) {
            if (entry.getValue().expired(now)) {
                buffers.remove(entry.getKey());
            }
        }
        while (buffers.size() > MAX_BUFFERS) {
            String eldest = null;
            long eldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, TraceBuffer> entry : buffers.entrySet()) {
                if (entry.getValue().createdAt() < eldestTime) {
                    eldestTime = entry.getValue().createdAt();
                    eldest = entry.getKey();
                }
            }
            if (eldest == null || buffers.remove(eldest) == null) {
                break;
            }
        }
    }

    /**
     * 解析整型请求头
     *
     * @param req          请求
     * @param name         头名
     * @param defaultValue 解析失败时的默认值
     * @return 头值
     */
    private static int parseHeader(HttpServletRequest req, String name, int defaultValue) {
        String value = req.getHeader(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 写出 JSON 拒绝响应
     *
     * @param resp    响应
     * @param status  HTTP 状态码
     * @param message JSON 文本
     */
    private static void reject(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写出 JSON 确认响应
     *
     * @param resp   响应
     * @param status HTTP 状态码
     * @param json   JSON 文本
     */
    private static void writeJson(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JSON 字符串转义（仅 traceId，来源为前端随机数，双引号/反斜杠兜底）
     *
     * @param value 原文
     * @return 转义结果
     */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 把完整响应体切成分片（至少一片，空体为 0 长片）
     *
     * @param data 完整响应体
     * @return 分片数组
     */
    private static byte[][] split(byte[] data) {
        int count = Math.max(1, (data.length + RESPONSE_CHUNK_SIZE - 1) / RESPONSE_CHUNK_SIZE);
        byte[][] chunks = new byte[count][];
        for (int i = 0; i < count; i++) {
            int from = i * RESPONSE_CHUNK_SIZE;
            int len = Math.min(RESPONSE_CHUNK_SIZE, data.length - from);
            chunks[i] = Arrays.copyOfRange(data, from, from + len);
        }
        return chunks;
    }

    /**
     * 分片会话：同一 traceId 的请求分片与响应分片缓冲
     */
    private static final class TraceBuffer {
        private final String method;
        private final String uri;
        private final int total;
        private final long createdAt = System.currentTimeMillis();
        private final byte[][] chunks;
        private final Object lock = new Object();
        private int received = 0;
        private volatile byte[][] responseChunks;
        private volatile int responseStatus = 200;

        TraceBuffer(String method, String uri, int total) {
            this.method = method;
            this.uri = uri;
            this.total = total;
            this.chunks = new byte[total][];
        }

        /**
         * 会话是否匹配当前请求（防跨请求串片）
         */
        boolean matches(String nextMethod, String nextUri, int nextTotal) {
            return total == nextTotal && method.equals(nextMethod) && uri.equals(nextUri);
        }

        /**
         * 存放分片；越界/重复/超限返回 false
         */
        boolean store(int index, byte[] body) {
            synchronized (lock) {
                if (index < 0 || index >= total || chunks[index] != null) {
                    return false;
                }
                long accumulated = 0;
                for (byte[] chunk : chunks) {
                    if (chunk != null) {
                        accumulated += chunk.length;
                    }
                }
                if (accumulated + body.length > MAX_ASSEMBLED_BYTES) {
                    return false;
                }
                chunks[index] = body;
                received++;
                return true;
            }
        }

        /**
         * 重组完整密文；存在缺片返回 null
         */
        byte[] assemble() {
            synchronized (lock) {
                if (received < total) {
                    return null;
                }
                int size = 0;
                for (byte[] chunk : chunks) {
                    size += chunk.length;
                }
                if (size > MAX_ASSEMBLED_BYTES) {
                    return null;
                }
                byte[] out = new byte[size];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, out, offset, chunk.length);
                    offset += chunk.length;
                }
                return out;
            }
        }

        /**
         * 业务管线结束后缓存响应分片
         */
        void completeResponse(int status, byte[][] responseChunks) {
            this.responseStatus = status;
            this.responseChunks = responseChunks;
        }

        byte[][] responseChunks() {
            return responseChunks;
        }

        int responseChunkCount() {
            byte[][] chunks = responseChunks;
            return chunks == null ? 0 : chunks.length;
        }

        int responseStatus() {
            return responseStatus;
        }

        long createdAt() {
            return createdAt;
        }

        boolean expired(long now) {
            return now - createdAt > BUFFER_TTL_MS;
        }
    }

    /**
     * 重组后的请求包装：以完整密文作为请求体交由原有解密管线读取
     */
    private static final class AssembledRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        AssembledRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
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
     * 内存字节流形式的 ServletInputStream
     */
    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final byte[] data;
        private int position = 0;

        ByteArrayServletInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean isFinished() {
            return position >= data.length;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // 一次性内存流，不支持异步读取监听
        }

        @Override
        public int read() {
            if (position >= data.length) {
                return -1;
            }
            return data[position++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (position >= data.length) {
                return -1;
            }
            int count = Math.min(len, data.length - position);
            System.arraycopy(data, position, b, off, count);
            position += count;
            return count;
        }
    }

    /**
     * 响应捕获包装：把业务管线写出的（已编码）响应体缓存到内存
     */
    private static final class CapturedResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        CapturedResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // 内存捕获，不支持异步写入监听
                }

                @Override
                public void write(int b) {
                    buffer.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    buffer.write(b, off, len);
                }
            };
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
        }

        byte[] bytes() {
            return buffer.toByteArray();
        }

        int status() {
            return getStatus();
        }
    }
}

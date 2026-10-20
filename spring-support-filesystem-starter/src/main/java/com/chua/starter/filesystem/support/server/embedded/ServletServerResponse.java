package com.chua.starter.filesystem.support.server.embedded;

import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.server.response.ServerResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 把 common-starter 的 {@code ServerResponse} 适配到 {@link HttpServletResponse}。
 *
 * <p>文件存储过滤器统一走 {@code setStatus/setHeader/setBody/end}，因此这里以
 * <b>缓冲 + 结束时一次性写出</b> 为主：状态码与响应头在 {@link #end()} 时落到 Servlet 响应，
 * 避免过滤器在链中途设置的头被容器提前提交。若调用方改用
 * {@link #getOutputStream()} 直接写流，则在首次写入时提交响应头。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ServletServerResponse implements ServerResponse {

    /**
     * 原始 Servlet 响应
     */
    private final HttpServletResponse response;

    /**
     * 待写入的响应头
     */
    private HttpHeader headers = HttpHeader.create();

    /**
     * 响应体缓冲区
     */
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(2048);

    /**
     * 状态码
     */
    private int status = HttpServletResponse.SC_OK;

    /**
     * 响应头是否已提交给 Servlet 容器
     */
    private boolean committed;

    /**
     * 是否已调用 {@link #end()}
     */
    private boolean ended;

    /**
     * 是否已改用输出流直接写（此时不再写出缓冲区）
     */
    private boolean streamUsed;

    /**
     * 创建响应适配器。
     *
     * @param response 原始响应
     */
    public ServletServerResponse(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public ServerResponse setStatus(int statusCode) {
        this.status = statusCode;
        return this;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public ServerResponse setHeader(String name, String value) {
        if (name == null || value == null) {
            return this;
        }
        if (committed || response.isCommitted()) {
            response.addHeader(name, value);
        } else {
            headers.add(name, value);
        }
        return this;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public HttpHeader getHeaders() {
        return headers;
    }

    @Override
    public ServerResponse setContentType(String contentType) {
        return setHeader("Content-Type", contentType);
    }

    @Override
    public String getContentType() {
        return headers.get("Content-Type");
    }

    @Override
    public ServerResponse setBody(byte[] body) {
        buffer.reset();
        if (body != null && body.length > 0) {
            buffer.write(body, 0, body.length);
        }
        return this;
    }

    @Override
    public ServerResponse setBody(String body) {
        return setBody(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public byte[] getBody() {
        return buffer.toByteArray();
    }

    @Override
    public OutputStream getOutputStream() {
        streamUsed = true;
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                commit();
                response.getOutputStream().write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                commit();
                response.getOutputStream().write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                commit();
                response.getOutputStream().flush();
            }
        };
    }

    @Override
    public ServerResponse sendRedirect(String location) {
        setStatus(HttpServletResponse.SC_FOUND);
        setHeader("Location", location);
        end();
        return this;
    }

    @Override
    public ServerResponse sendError(int statusCode, String message) {
        setStatus(statusCode);
        setHeader("Content-Type", "text/plain;charset=UTF-8");
        setBody(message == null ? "" : message);
        end();
        return this;
    }

    @Override
    public void flush() {
        commit();
        try {
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new UncheckedIOException("刷新响应失败", e);
        }
    }

    @Override
    public boolean isCommitted() {
        return committed || response.isCommitted();
    }

    @Override
    public boolean isEnded() {
        return ended;
    }

    @Override
    public ServerResponse reset() {
        if (!committed && !response.isCommitted()) {
            status = HttpServletResponse.SC_OK;
            headers = HttpHeader.create();
            buffer.reset();
            streamUsed = false;
        }
        return this;
    }

    @Override
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        try {
            if (!response.isCommitted()) {
                commit();
                byte[] body = streamUsed ? new byte[0] : buffer.toByteArray();
                if (body.length > 0) {
                    response.getOutputStream().write(body);
                }
            }
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new UncheckedIOException("写出响应失败", e);
        }
    }

    @Override
    public void writeRaw(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            buffer.write(bytes, 0, bytes.length);
        }
    }

    /**
     * 把状态码与响应头提交给 Servlet 容器（幂等）。
     */
    private void commit() {
        if (committed || response.isCommitted()) {
            committed = true;
            return;
        }
        response.setStatus(status);
        for (java.util.Map.Entry<String, String> entry : headers.toMap().entrySet()) {
            if (entry.getValue() != null) {
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }
        committed = true;
    }
}

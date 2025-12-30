package com.chua.starter.rust.server.servlet;

import com.chua.starter.rust.server.ipc.ResponseMessage;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Rust HTTP Servlet 响应实现
 *
 * @author CH
 * @since 4.0.0
 */
public class RustHttpServletResponse implements HttpServletResponse {

    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    private final long requestId;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private final RustServletOutputStream outputStream = new RustServletOutputStream();

    private int status = SC_OK;
    private String characterEncoding = StandardCharsets.UTF_8.name();
    private String contentType;
    private PrintWriter writer;
    private boolean committed = false;
    private Locale locale = Locale.getDefault();

    public RustHttpServletResponse(long requestId) {
        this.requestId = requestId;
    }

    /**
     * 转换为 IPC 响应消息
     */
    public ResponseMessage toResponseMessage() {
        // 添加 Cookie 到 headers
        for (Cookie cookie : cookies) {
            addHeader("Set-Cookie", formatCookie(cookie));
        }

        // 设置 Content-Type
        if (contentType != null) {
            String ct = contentType;
            if (characterEncoding != null && !ct.contains("charset")) {
                ct += "; charset=" + characterEncoding;
            }
            setHeader("Content-Type", ct);
        }

        byte[] body = null;
        if (writer != null) {
            writer.flush();
        }
        if (outputStream.size() > 0) {
            body = outputStream.toByteArray();
        }

        return ResponseMessage.builder()
                .requestId(requestId)
                .status(status)
                .headers(headers)
                .body(body)
                .build();
    }

    private String formatCookie(Cookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(cookie.getValue());
        if (cookie.getMaxAge() >= 0) {
            sb.append("; Max-Age=").append(cookie.getMaxAge());
        }
        if (cookie.getPath() != null) {
            sb.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            sb.append("; Domain=").append(cookie.getDomain());
        }
        if (cookie.getSecure()) {
            sb.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            sb.append("; HttpOnly");
        }
        return sb.toString();
    }

    // ==================== HttpServletResponse 方法 ====================

    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        setStatus(sc);
        committed = true;
        if (msg != null) {
            setContentType("text/html;charset=UTF-8");
            getWriter().write("<html><body><h1>" + sc + " " + msg + "</h1></body></html>");
        }
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, null);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        setStatus(SC_FOUND);
        setHeader("Location", location);
        committed = true;
    }

    @Override
    public void setDateHeader(String name, long date) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        sdf.setTimeZone(GMT);
        setHeader(name, sdf.format(new Date(date)));
    }

    @Override
    public void addDateHeader(String name, long date) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        sdf.setTimeZone(GMT);
        addHeader(name, sdf.format(new Date(date)));
    }

    @Override
    public void setHeader(String name, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name, values);
    }

    @Override
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> values = headers.get(name);
        return values != null ? values : Collections.emptyList();
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    // ==================== ServletResponse 方法 ====================

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new java.io.OutputStreamWriter(outputStream,
                    characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8.name()));
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        if (!committed) {
            Charset.forName(charset); // 验证
            this.characterEncoding = charset;
        }
    }

    @Override
    public void setContentLength(int len) {
        setIntHeader("Content-Length", len);
    }

    @Override
    public void setContentLengthLong(long len) {
        setHeader("Content-Length", String.valueOf(len));
    }

    @Override
    public void setContentType(String type) {
        if (!committed) {
            this.contentType = type;
            // 解析 charset
            if (type != null && type.contains("charset=")) {
                int idx = type.indexOf("charset=");
                String charset = type.substring(idx + 8);
                int semicolon = charset.indexOf(';');
                if (semicolon > 0) {
                    charset = charset.substring(0, semicolon);
                }
                this.characterEncoding = charset.trim();
            }
        }
    }

    @Override
    public void setBufferSize(int size) {
        // 忽略
    }

    @Override
    public int getBufferSize() {
        return outputStream.size();
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        outputStream.flush();
        committed = true;
    }

    @Override
    public void resetBuffer() {
        if (!committed) {
            outputStream.reset();
        }
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void reset() {
        if (!committed) {
            headers.clear();
            cookies.clear();
            status = SC_OK;
            outputStream.reset();
            writer = null;
        }
    }

    @Override
    public void setLocale(Locale loc) {
        if (!committed && loc != null) {
            this.locale = loc;
        }
    }

    @Override
    public Locale getLocale() {
        return locale;
    }
}

package com.chua.starter.rust.server.servlet;

import com.chua.starter.rust.server.ipc.RequestMessage;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

/**
 * Rust HTTP Servlet 请求实现
 *
 * @author CH
 * @since 4.0.0
 */
public class RustHttpServletRequest implements HttpServletRequest {

    private final RequestMessage message;
    private final ServletContext servletContext;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String[]> parameters;
    private final RustServletInputStream inputStream;

    private String characterEncoding = StandardCharsets.UTF_8.name();
    private BufferedReader reader;
    private boolean asyncStarted = false;
    private AsyncContext asyncContext;
    private DispatcherType dispatcherType = DispatcherType.REQUEST;

    public RustHttpServletRequest(RequestMessage message, ServletContext servletContext) {
        this.message = message;
        this.servletContext = servletContext;
        this.inputStream = new RustServletInputStream(message.getBody());
        this.parameters = parseParameters();
    }

    private Map<String, String[]> parseParameters() {
        Map<String, List<String>> params = new LinkedHashMap<>();

        // 解析 URL 查询参数
        String queryString = message.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            parseQueryString(queryString, params);
        }

        // 解析 POST 表单参数
        String contentType = getContentType();
        if ("POST".equalsIgnoreCase(message.getMethod()) &&
                contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            byte[] body = message.getBody();
            if (body != null && body.length > 0) {
                parseQueryString(new String(body, StandardCharsets.UTF_8), params);
            }
        }

        // 转换为 String[] 格式
        Map<String, String[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        return result;
    }

    private void parseQueryString(String queryString, Map<String, List<String>> params) {
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            try {
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
                String value = idx > 0 && pair.length() > idx + 1 ?
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== HttpServletRequest 方法 ====================

    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        String cookieHeader = message.getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        List<Cookie> cookies = new ArrayList<>();
        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2) {
                cookies.add(new Cookie(kv[0].trim(), kv[1].trim()));
            }
        }
        return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
    }

    @Override
    public long getDateHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1L;
        }
        try {
            return java.time.ZonedDateTime.parse(value,
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse date header: " + value);
        }
    }

    @Override
    public String getHeader(String name) {
        return message.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (message.getHeaders() == null) {
            return Collections.emptyEnumeration();
        }
        for (Map.Entry<String, List<String>> entry : message.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return Collections.enumeration(entry.getValue());
            }
        }
        return Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        if (message.getHeaders() == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(message.getHeaders().keySet());
    }

    @Override
    public int getIntHeader(String name) {
        String value = getHeader(name);
        return value == null ? -1 : Integer.parseInt(value);
    }

    @Override
    public String getMethod() {
        return message.getMethod();
    }

    @Override
    public String getPathInfo() {
        String contextPath = getContextPath();
        String requestUri = getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    @Override
    public String getPathTranslated() {
        String pathInfo = getPathInfo();
        return pathInfo != null ? servletContext.getRealPath(pathInfo) : null;
    }

    @Override
    public String getContextPath() {
        return servletContext != null ? servletContext.getContextPath() : "";
    }

    @Override
    public String getQueryString() {
        return message.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        Cookie[] cookies = getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @Override
    public String getRequestURI() {
        return message.getPath();
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        url.append(scheme).append("://").append(getServerName());
        if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
            url.append(':').append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    @Override
    public String getServletPath() {
        return getRequestURI();
    }

    @Override
    public HttpSession getSession(boolean create) {
        // 简化实现，返回 null
        return null;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return getRequestedSessionId() != null;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        throw new ServletException("Not supported");
    }

    @Override
    public void logout() throws ServletException {
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        throw new ServletException("Upgrade not supported");
    }

    // ==================== ServletRequest 方法 ====================

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        Charset.forName(env); // 验证编码是否支持
        this.characterEncoding = env;
    }

    @Override
    public int getContentLength() {
        return message.getBody() != null ? message.getBody().length : -1;
    }

    @Override
    public long getContentLengthLong() {
        return getContentLength();
    }

    @Override
    public String getContentType() {
        return message.getHeader("Content-Type");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameters.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameters.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public String getProtocol() {
        return message.getProtocol();
    }

    @Override
    public String getScheme() {
        String proto = message.getHeader("X-Forwarded-Proto");
        return proto != null ? proto : "http";
    }

    @Override
    public String getServerName() {
        String host = message.getHeader("Host");
        if (host != null) {
            int colonIdx = host.indexOf(':');
            return colonIdx > 0 ? host.substring(0, colonIdx) : host;
        }
        return "localhost";
    }

    @Override
    public int getServerPort() {
        String host = message.getHeader("Host");
        if (host != null) {
            int colonIdx = host.indexOf(':');
            if (colonIdx > 0) {
                try {
                    return Integer.parseInt(host.substring(colonIdx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "https".equals(getScheme()) ? 443 : 80;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(inputStream,
                    characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8.name()));
        }
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        String forwarded = message.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = message.getRemoteAddr();
        if (remoteAddr != null) {
            int colonIdx = remoteAddr.lastIndexOf(':');
            return colonIdx > 0 ? remoteAddr.substring(0, colonIdx) : remoteAddr;
        }
        return "127.0.0.1";
    }

    @Override
    public String getRemoteHost() {
        return getRemoteAddr();
    }

    @Override
    public void setAttribute(String name, Object o) {
        if (o == null) {
            removeAttribute(name);
        } else {
            attributes.put(name, o);
        }
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public Locale getLocale() {
        String acceptLanguage = message.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            String[] parts = acceptLanguage.split(",")[0].split("-");
            if (parts.length >= 2) {
                return new Locale(parts[0], parts[1]);
            }
            return new Locale(parts[0]);
        }
        return Locale.getDefault();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(Collections.singletonList(getLocale()));
    }

    @Override
    public boolean isSecure() {
        return "https".equals(getScheme());
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return servletContext != null ? servletContext.getRequestDispatcher(path) : null;
    }

    @Override
    public int getRemotePort() {
        String remoteAddr = message.getRemoteAddr();
        if (remoteAddr != null) {
            int colonIdx = remoteAddr.lastIndexOf(':');
            if (colonIdx > 0) {
                try {
                    return Integer.parseInt(remoteAddr.substring(colonIdx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    @Override
    public String getLocalName() {
        return getServerName();
    }

    @Override
    public String getLocalAddr() {
        String localAddr = message.getLocalAddr();
        if (localAddr != null) {
            int colonIdx = localAddr.lastIndexOf(':');
            return colonIdx > 0 ? localAddr.substring(0, colonIdx) : localAddr;
        }
        return "127.0.0.1";
    }

    @Override
    public int getLocalPort() {
        return getServerPort();
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return startAsync(this, null);
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
            throws IllegalStateException {
        asyncStarted = true;
        return asyncContext;
    }

    @Override
    public boolean isAsyncStarted() {
        return asyncStarted;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }

    @Override
    public String getRequestId() {
        return String.valueOf(message.getRequestId());
    }

    @Override
    public String getProtocolRequestId() {
        return getRequestId();
    }

    @Override
    public ServletConnection getServletConnection() {
        return null;
    }
}

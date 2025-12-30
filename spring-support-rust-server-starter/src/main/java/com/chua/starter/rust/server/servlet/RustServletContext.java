package com.chua.starter.rust.server.servlet;

import jakarta.servlet.*;
import jakarta.servlet.descriptor.JspConfigDescriptor;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rust Servlet 上下文实现
 *
 * @author CH
 * @since 4.0.0
 */
public class RustServletContext implements ServletContext {

    private final String contextPath;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> initParameters = new ConcurrentHashMap<>();
    private final Map<String, ServletRegistration.Dynamic> servletRegistrations = new ConcurrentHashMap<>();
    private final Map<String, FilterRegistration.Dynamic> filterRegistrations = new ConcurrentHashMap<>();
    private final Set<SessionTrackingMode> sessionTrackingModes = EnumSet.of(SessionTrackingMode.COOKIE);

    private String serverInfo = "Rust HTTP Server/1.0";
    private int sessionTimeout = 30;

    public RustServletContext(String contextPath) {
        this.contextPath = contextPath != null ? contextPath : "";
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public ServletContext getContext(String uripath) {
        return this;
    }

    @Override
    public int getMajorVersion() {
        return 6;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 6;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        if (file == null) return null;
        String ext = file.substring(file.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "txt" -> "text/plain";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        return Collections.emptySet();
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        return getClass().getResource(path);
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        return getClass().getResourceAsStream(path);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }

    @Override
    public void log(String msg) {
        System.out.println("[RustServletContext] " + msg);
    }

    @Override
    public void log(String message, Throwable throwable) {
        System.err.println("[RustServletContext] " + message);
        throwable.printStackTrace();
    }

    @Override
    public String getRealPath(String path) {
        try {
            URL resource = getResource(path);
            if (resource != null) {
                return new File(resource.toURI()).getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public String getServerInfo() {
        return serverInfo;
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        if (initParameters.containsKey(name)) {
            return false;
        }
        initParameters.put(name, value);
        return true;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object object) {
        if (object == null) {
            removeAttribute(name);
        } else {
            attributes.put(name, object);
        }
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public String getServletContextName() {
        return "Rust HTTP Server";
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return servletRegistrations.get(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Collections.unmodifiableMap(servletRegistrations);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return filterRegistrations.get(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return Collections.unmodifiableMap(filterRegistrations);
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return new SessionCookieConfig() {
            private String name = "JSESSIONID";
            private String domain;
            private String path = "/";
            private String comment;
            private boolean httpOnly = true;
            private boolean secure = false;
            private int maxAge = -1;

            @Override public void setName(String name) { this.name = name; }
            @Override public String getName() { return name; }
            @Override public void setDomain(String domain) { this.domain = domain; }
            @Override public String getDomain() { return domain; }
            @Override public void setPath(String path) { this.path = path; }
            @Override public String getPath() { return path; }
            @Override public void setComment(String comment) { this.comment = comment; }
            @Override public String getComment() { return comment; }
            @Override public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
            @Override public boolean isHttpOnly() { return httpOnly; }
            @Override public void setSecure(boolean secure) { this.secure = secure; }
            @Override public boolean isSecure() { return secure; }
            @Override public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
            @Override public int getMaxAge() { return maxAge; }
            @Override public void setAttribute(String name, String value) {}
            @Override public String getAttribute(String name) { return null; }
            @Override public Map<String, String> getAttributes() { return Collections.emptyMap(); }
        };
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        this.sessionTrackingModes.clear();
        this.sessionTrackingModes.addAll(sessionTrackingModes);
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return EnumSet.of(SessionTrackingMode.COOKIE);
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return sessionTrackingModes;
    }

    @Override
    public void addListener(String className) {}

    @Override
    public <T extends EventListener> void addListener(T t) {}

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {}

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void declareRoles(String... roleNames) {}

    @Override
    public String getVirtualServerName() {
        return "rust-server";
    }

    @Override
    public int getSessionTimeout() {
        return sessionTimeout;
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public String getRequestCharacterEncoding() {
        return "UTF-8";
    }

    @Override
    public void setRequestCharacterEncoding(String encoding) {}

    @Override
    public String getResponseCharacterEncoding() {
        return "UTF-8";
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {}
}

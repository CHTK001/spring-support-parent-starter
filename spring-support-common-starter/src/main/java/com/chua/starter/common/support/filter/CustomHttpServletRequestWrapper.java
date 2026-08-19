package com.chua.starter.common.support.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 自定义HttpServletRequest请求包装器
 * <p>
 * 用于包装原始的HttpServletRequest，支持多次读取请求体内容。
 * 解决了Servlet规范中请求体只能读取一次的限制问题。
 * </p>
 *
 * @since 2023/09/08
 * @version 1.0.0
 * @author CH
 */
public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 缓存的请求体字节数组
     */
    private final byte[] body;

    /**
     * 创建 CustomHttpServletRequestWrapper 实例
     * @param request request
     */
    public CustomHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        body = request.getInputStream().readAllBytes();
    }

    /**
     * 创建 CustomHttpServletRequestWrapper 实例
     * @param request request
     * @param String String
     */
    public CustomHttpServletRequestWrapper(HttpServletRequest request, String body) {
        super(request);
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    /** 获取InputStream */
    public ServletInputStream getInputStream() throws IOException {
        return new CustomServletInputStream(body);
    }

    @Override
    /** 获取Reader */
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
    }

    @Override
    /** 获取Session */
    public HttpSession getSession() {
        return super.getSession();
    }

    @Override
    /** 获取Parameter */
    public String getParameter(String name) {
        return super.getParameter(name);
    }

    @Override
    /** 获取ParameterValues */
    public String[] getParameterValues(String name) {
        return super.getParameterValues(name);
    }

    /** 获取Body */
    public String getBody() {
        return new String(body);
    }
}

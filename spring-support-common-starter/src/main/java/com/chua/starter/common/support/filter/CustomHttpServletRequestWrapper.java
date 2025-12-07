package com.chua.starter.common.support.filter;

import com.chua.common.support.utils.IoUtils;
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
 * @author CH
 * @since 2023/09/08
 * @version 1.0.0
 */
public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 缓存的请求体字节数组
     */
    private final byte[] body;

    public CustomHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        body = IoUtils.toByteArray(request.getInputStream());
    }

    public CustomHttpServletRequestWrapper(HttpServletRequest request, String body) {
        super(request);
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CustomServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
    }

    @Override
    public HttpSession getSession() {
        return super.getSession();
    }

    @Override
    public String getParameter(String name) {
        return super.getParameter(name);
    }

    @Override
    public String[] getParameterValues(String name) {
        return super.getParameterValues(name);
    }

    public String getBody() {
        return new String(body);
    }
}


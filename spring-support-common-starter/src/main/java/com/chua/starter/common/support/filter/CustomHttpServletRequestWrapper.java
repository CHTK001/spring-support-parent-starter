package com.chua.starter.common.support.filter;

import com.chua.common.support.utils.IoUtils;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 自定义httpservlet请求包装器
 *
 * @author CH
 * @since 2023/09/08
 */
public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private byte[] body;

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
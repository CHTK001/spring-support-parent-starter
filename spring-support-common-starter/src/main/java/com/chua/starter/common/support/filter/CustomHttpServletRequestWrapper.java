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
* 自定义httpservlet请求请求包装器
* <p>
* 用于包装原始的httpservlet请求，支持多次读取请求体内容。
* 解决了Servlet规范中请求体只能读取一次的限制问题。
* </p>
*
* @since 2023/09/08
* @版本 1.0.0
* @author CH
 */
public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
    * 缓存的请求体字节数组
     */
    private final byte[] body;

    /**
    * 创建 习俗httpservlet请求包装器 实例
    * @param request 请求
     */
    public CustomHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        /**
        * 父。
        * @param request 请求
         */
        super(request);
        body = request.getInputStream().readAllBytes();
    }

    /**
    * 创建 习俗httpservlet请求包装器 实例
    * @param request 请求
    * @param body 字符串
    * @param body 主体
     */
    public CustomHttpServletRequestWrapper(HttpServletRequest request, String body) {
        /**
        * 父。
        * @param request 请求
         */
        super(request);
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    /** 获取输入流 */
    public ServletInputStream getInputStream() throws IOException {
        /**
        * 习俗servlet输入流。
        * @param body 主体
        * @return 习俗servlet输入流的结果
         */
        return new CustomServletInputStream(body);
    }

    @Override
    /** 获取Reader */
    public BufferedReader getReader() throws IOException {
        /**
        * 缓冲读取。
        * @param InputStreamReader(getInputStream( 输入流读取(获取输入流(
        * @return 缓冲读取的结果
         */
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
    }

    @Override
    /** 获取会话 */
    public HttpSession getSession() {
        return super.getSession();
    }

    @Override
    /** 获取参数 */
    public String getParameter(String name) {
        return super.getParameter(name);
    }

    @Override
    /** 获取参数价值观 */
    public String[] getParameterValues(String name) {
        return super.getParameterValues(name);
    }

    /**
    * 获取主体
    *
    * @return 获取主体的结果
     */
    public String getBody() {
        /**
        * 字符串。
        * @param body 主体
        * @return 字符串的结果
         */
        return new String(body);
    }
}

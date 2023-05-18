package com.chua.starter.common.support.transpond.filter;

import org.apache.http.HttpRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

/**
 * 过滤器
 *
 * @author CH
 */
public interface ProxyFilter {

    /**
     * 过滤
     *
     * @param source       源
     * @param proxyRequest 请求
     * @return 结果
     */
    InputStream filter(HttpRequest proxyRequest, InputStream source);

    /**
     * 拷贝消息头
     *
     * @param proxyRequest 请求
     */
    void copyRequestHeaders(HttpRequest proxyRequest);

    /**
     * 拷贝消息头
     *
     * @param servletResponse 请求
     */
    void copyResponseHeaders(HttpServletResponse servletResponse);
}

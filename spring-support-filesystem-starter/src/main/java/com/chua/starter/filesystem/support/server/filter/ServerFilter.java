package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

/**
 * HTTP 请求过滤器接口，每个过滤器可决定是否将请求传递给链中的下一个过滤器。
 *
 * @author CH
 * @since 4.0.0
 */
@FunctionalInterface
public interface ServerFilter {

    /**
     * 处理请求并决定是否继续过滤器链。
     *
     * @param exchange HTTP 请求/响应上下文
     * @param chain    过滤器链，用于传递到下一个过滤器或终态处理器
     * @throws Exception 处理异常
     */
    void doFilter(HttpExchange exchange, FilterChain chain) throws Exception;
}

package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;

/**
* 请求日志过滤器，记录每个请求的方法、URI、状态码和处理耗时。
*
* @author CH
* @since 4.0.0
 */
@Slf4j
public class LoggingFilter implements ServerFilter {

    /**
    * 执行过滤器逻辑：记录请求前后信息并继续链式处理。
    *
    * @param exchange HTTP 请求/响应上下文
    * @param chain    过滤器链
    * @throws Exception 处理异常
     */
    @Override
    public void doFilter(HttpExchange exchange, FilterChain chain) throws Exception {
        long start = System.currentTimeMillis();
        String uri = exchange.getRequestURI().toString();
        String method = exchange.getRequestMethod();
        try {
            chain.doFilter(exchange);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = exchange.getResponseCode();
            log.info("{} {} -> {} ({}ms)", method, uri, status, duration);
        }
    }
}

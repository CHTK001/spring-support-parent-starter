"package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingFilter implements ServerFilter {
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
"
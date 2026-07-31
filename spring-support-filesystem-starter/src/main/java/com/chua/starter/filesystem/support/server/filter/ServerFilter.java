"package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

@FunctionalInterface
public interface ServerFilter {
    void doFilter(HttpExchange exchange, FilterChain chain) throws Exception;
}
"
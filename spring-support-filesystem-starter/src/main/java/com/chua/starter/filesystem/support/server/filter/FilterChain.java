package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

public interface FilterChain {
    void doFilter(HttpExchange exchange) throws Exception;

    static FilterChain build(List<ServerFilter> filters, ServerFilter finalHandler) {
        if (filters == null || filters.isEmpty()) {
            return exchange -> finalHandler.doFilter(exchange, null);
        }
        return new DefaultFilterChain(filters, finalHandler);
    }
}

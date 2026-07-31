"package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

public class DefaultFilterChain implements FilterChain {
    private final List<ServerFilter> filters;
    private final ServerFilter finalHandler;
    private int index = 0;

    public DefaultFilterChain(List<ServerFilter> filters, ServerFilter finalHandler) {
        this.filters = filters;
        this.finalHandler = finalHandler;
    }

    @Override
    public void doFilter(HttpExchange exchange) throws Exception {
        if (index < filters.size()) {
            ServerFilter filter = filters.get(index++);
            filter.doFilter(exchange, this);
        } else {
            finalHandler.doFilter(exchange, null);
        }
    }
}
"
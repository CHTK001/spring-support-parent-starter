package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

/**
 * 默认过滤器链实现。
 *
 * <p>按顺序依次执行各 {@link ServerFilter}，最终调用提供的终态处理器。
 *
 * @author CH
 * @since 4.0.0
 */
public class DefaultFilterChain implements FilterChain {

    /** 过滤器列表 */
    private final List<ServerFilter> filters;
    /** 终态处理器 */
    private final ServerFilter finalHandler;
    /** 当前执行到的过滤器索引 */
    private int index = 0;

    /**
     * 创建过滤器链。
     *
     * @param filters       过滤器列表，可为空
     * @param finalHandler  终态处理器，不能为 null
     */
    public DefaultFilterChain(List<ServerFilter> filters, ServerFilter finalHandler) {
        this.filters = filters;
        this.finalHandler = finalHandler;
    }

    /**
     * 执行过滤器链。
     *
     * @param exchange HTTP 请求/响应上下文
     * @throws Exception 过滤器执行异常
     */
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

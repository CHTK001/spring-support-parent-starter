package com.chua.starter.filesystem.support.server.filter;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

/**
* HTTP 请求过滤器链接口。
*
* <p>过滤器按顺序执行，每个过滤器决定是否将请求传递给下一个过滤器。
*
* @author CH
* @since 4.0.0
 */
public interface FilterChain {

    /**
    * 执行过滤器链，处理当前请求。
    *
    * @param exchange HTTP 请求/响应上下文
    * @throws Exception 处理异常
     */
    void doFilter(HttpExchange exchange) throws Exception;

    /**
    * 根据过滤器列表和终态处理器构建过滤器链。
    *
    * @param filters      过滤器列表，可为空
    * @param finalHandler 终态处理器，不能为 空
    * @return 构建的过滤器链实例
     */
    static FilterChain build(List<ServerFilter> filters, ServerFilter finalHandler) {
        if (filters == null || filters.isEmpty()) {
            return exchange -> finalHandler.doFilter(exchange, null);
        }
        /**
        * 默认过滤器chain。
        * @param filters 过滤器
        * @param finalHandler 最终处理器
        * @return 默认过滤器chain的结果
         */
        return new DefaultFilterChain(filters, finalHandler);
    }
}

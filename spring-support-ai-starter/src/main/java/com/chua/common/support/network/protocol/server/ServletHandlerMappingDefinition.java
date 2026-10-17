package com.chua.common.support.network.protocol.server;

import com.chua.common.support.network.http.HttpMethod;

import java.util.function.Function;

/**
 * Servlet 处理器映射定义。
 *
 * @param path    请求路径
 * @param method  请求方法
 * @param handler 处理器函数
 * @since 4.0.0.42
 * @author CH
 */
public record ServletHandlerMappingDefinition(
        String path,
        HttpMethod method,
        Function<Object, Object> handler) {
}

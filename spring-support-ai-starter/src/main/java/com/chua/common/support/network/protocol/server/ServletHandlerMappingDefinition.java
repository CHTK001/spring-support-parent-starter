package com.chua.common.support.network.protocol.server;

import com.chua.common.support.network.http.HttpMethod;
import lombok.Data;

import java.util.function.Function;

/**
 * Servlet 处理器映射定义。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class ServletHandlerMappingDefinition {

    /**
     * 请求路径
     */
    private String path;

    /**
     * 请求方法
     */
    private HttpMethod method;

    /**
     * 处理器函数
     */
    private Function<Object, Object> handler;

    /**
     * 全参构造。
     *
     * @param path    请求路径
     * @param method  请求方法
     * @param handler 处理器函数
     */
    public ServletHandlerMappingDefinition(String path, HttpMethod method, Function<Object, Object> handler) {
        this.path = path;
        this.method = method;
        this.handler = handler;
    }
}

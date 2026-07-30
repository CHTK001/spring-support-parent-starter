package com.chua.common.support.network.protocol.server;

import com.chua.common.support.network.http.HttpMethod;
import lombok.Data;

import java.util.function.Function;

@Data
public class ServletHandlerMappingDefinition {

    private String path;
    private HttpMethod method;
    private Function<Object, Object> handler;

    public ServletHandlerMappingDefinition(String path, HttpMethod method, Function<Object, Object> handler) {
        this.path = path;
        this.method = method;
        this.handler = handler;
    }
}

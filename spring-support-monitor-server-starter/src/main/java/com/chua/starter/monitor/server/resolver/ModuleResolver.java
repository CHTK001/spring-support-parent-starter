package com.chua.starter.monitor.server.resolver;

import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;

/**
 * 命令解析器
 *
 * @author CH
 */
public interface ModuleResolver {
    /**
     * 解释
     *
     * @param request 请求
     * @return {@link Response}
     */
    Response resolve(Request request);
}

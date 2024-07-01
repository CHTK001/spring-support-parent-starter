package com.chua.starter.monitor.server.resolver.adator;

import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;

/**
 * 命令适配器
 *
 * @author CH
 */
public interface CommandAdaptor {
    /**
     * 解释
     *
     * @param request 请求
     * @return {@link Response}
     */
    Response resolve(Request request);
}

package com.chua.starter.monitor.server.resolver.adator;

import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.starter.monitor.server.request.ReportQuery;

/**
 * 命令适配器
 *
 * @author CH
 */
public interface CommandAdaptor {
    /**
     * 解释
     *
     * @param request
     * @param reportQuery 请求
     * @return {@link Response}
     */
    Response resolve(Request request, ReportQuery reportQuery);
}

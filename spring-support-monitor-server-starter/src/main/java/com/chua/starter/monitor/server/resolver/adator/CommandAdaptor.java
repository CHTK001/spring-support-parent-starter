package com.chua.starter.monitor.server.resolver.adator;

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
     * @param reportQuery 请求
     * @return {@link Response}
     */
    Response resolve(ReportQuery reportQuery);
}

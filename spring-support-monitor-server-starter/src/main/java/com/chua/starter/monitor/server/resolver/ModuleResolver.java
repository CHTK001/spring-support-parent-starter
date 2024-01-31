package com.chua.starter.monitor.server.resolver;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

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
     * @return {@link BootResponse}
     */
    BootResponse resolve(BootRequest request);
}

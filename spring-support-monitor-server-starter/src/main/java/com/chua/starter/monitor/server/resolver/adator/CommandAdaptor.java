package com.chua.starter.monitor.server.resolver.adator;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

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
     * @return {@link BootResponse}
     */
    BootResponse resolve(BootRequest request);
}

package com.chua.starter.unified.client.support.handler;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

/**
 * 处理程序
 *
 * @author CH
 */
public interface Handler {


    /**
     * 处理
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    BootResponse handle(BootRequest request);
}

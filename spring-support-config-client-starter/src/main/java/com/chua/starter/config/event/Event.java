package com.chua.starter.config.event;

import io.vertx.core.http.HttpServerResponse;

/**
 * 事件
 *
 * @author CH
 * @since 2023/09/10
 */
public interface Event {
    /**
     * 论监听器
     *
     * @param response 回答
     */
    void onListener(HttpServerResponse response);
}

package com.chua.starter.config.event;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import io.vertx.core.http.HttpServerResponse;

/**
 * 事件
 *
 * @author CH
 * @since 2023/09/10
 */
@Spi("health")
public class HealthEvent implements Event{
    @Override
    public void onListener(HttpServerResponse response) {
        response.end(new JSONObject().fluentPut("status", "UP").toString());

    }
}

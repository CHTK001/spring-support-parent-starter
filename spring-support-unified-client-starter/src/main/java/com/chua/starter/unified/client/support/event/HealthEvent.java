package com.chua.starter.unified.client.support.event;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.JsonObject;
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
        response.end(new JsonObject().fluentPut("status", "UP").toString());

    }
}

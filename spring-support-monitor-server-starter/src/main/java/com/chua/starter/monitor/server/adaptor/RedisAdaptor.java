package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class RedisAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest redisReport) {
        socketSessionTemplate.send("redis", Json.toJson(redisReport));
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

}

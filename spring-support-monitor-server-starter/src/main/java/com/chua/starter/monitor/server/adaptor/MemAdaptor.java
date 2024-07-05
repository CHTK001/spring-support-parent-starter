package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import jakarta.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("memory")
public class MemAdaptor implements Adaptor<MonitorRequest> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest mem) {
        socketSessionTemplate.send("mem", Json.toJson(mem));
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }


    @Override
    public boolean intoTimeSeries() {
        return true;
    }
}

package com.chua.starter.monitor.server.adaptor;

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
public class CpuAdaptor implements Adaptor<MonitorRequest> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest cpu) {
        socketSessionTemplate.send("cpu", Json.toJson(cpu));
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

package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.MapUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;

import java.util.Map;

/**
 * process 适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class ProcessAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest process) {
        process.setData(MapUtils.get((Map)process.getData(), "data"));
        socketSessionTemplate.send("process", Json.toJson(process));
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

}

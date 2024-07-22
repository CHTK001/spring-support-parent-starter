package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.MapUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;

import java.util.Map;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class JvmAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest jvm) {
        jvm.setData(MapUtils.get((Map)jvm.getData(), "data"));
        socketSessionTemplate.send("jvm", Json.toJson(jvm));
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

    @Override
    public boolean intoSet() {
        return true;
    }
}

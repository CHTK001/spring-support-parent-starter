package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class LogAdaptor implements Adaptor<MonitorRequest> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(MonitorRequest request) {
        socketSessionTemplate.send("log", StringUtils.format("[{}({}:{})] -> {}", request.getAppName(), request.getServerHost() , request.getServerPort(), request.getData()));
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }
}

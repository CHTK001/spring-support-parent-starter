package com.chua.starter.monitor.server.adaptor;

import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.pojo.ServiceTarget;

import jakarta.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class ServerAdaptor implements Adaptor<ServiceTarget> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(ServiceTarget serviceTarget) {
    }

    @Override
    public Class<ServiceTarget> getType() {
        return ServiceTarget.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

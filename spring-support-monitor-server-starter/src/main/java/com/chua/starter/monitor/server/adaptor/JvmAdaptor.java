package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.oshi.support.Jvm;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class JvmAdaptor implements Adaptor<Jvm> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(Jvm jvm) {
        socketSessionTemplate.send("jvm", Json.toJson(jvm));
    }

    @Override
    public Class<Jvm> getType() {
        return Jvm.class;
    }
}

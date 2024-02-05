package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.oshi.support.Network;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class NetworkAdaptor implements Adaptor<Network> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(Network network) {
        socketSessionTemplate.send("network", Json.toJson(network));
    }

    @Override
    public Class<Network> getType() {
        return Network.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

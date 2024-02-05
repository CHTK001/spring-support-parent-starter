package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.oshi.support.Mem;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class MemAdaptor implements Adaptor<Mem> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(Mem mem) {
        socketSessionTemplate.send("mem", Json.toJson(mem));
    }

    @Override
    public Class<Mem> getType() {
        return Mem.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

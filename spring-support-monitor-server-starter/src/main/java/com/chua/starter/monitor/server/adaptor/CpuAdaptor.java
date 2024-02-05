package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.oshi.support.Cpu;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class CpuAdaptor implements Adaptor<Cpu> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(Cpu cpu) {
        socketSessionTemplate.send("cpu", Json.toJson(cpu));
    }

    @Override
    public Class<Cpu> getType() {
        return Cpu.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

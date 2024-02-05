package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.oshi.support.SysFile;
import com.chua.socketio.support.session.SocketSessionTemplate;

import javax.annotation.Resource;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class DiskAdaptor implements Adaptor<SysFile> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public void doAdaptor(SysFile sysFile) {
        socketSessionTemplate.send("disk", Json.toJson(sysFile));
    }

    @Override
    public Class<SysFile> getType() {
        return SysFile.class;
    }

    @Override
    public boolean intoDb() {
        return true;
    }
}

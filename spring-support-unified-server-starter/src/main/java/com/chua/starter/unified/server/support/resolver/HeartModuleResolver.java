package com.chua.starter.unified.server.support.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;

/**
 * 心脏模块分解器
 *
 * @author CH
 */
@Spi("HEART")
public class HeartModuleResolver implements ModuleResolver{
    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        if(commandType == CommandType.PING) {
            return BootResponse.builder().commandType(CommandType.PONG).build();
        }
        return null;
    }
}

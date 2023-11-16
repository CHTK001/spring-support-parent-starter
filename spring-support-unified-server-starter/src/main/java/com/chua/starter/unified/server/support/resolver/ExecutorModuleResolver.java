package com.chua.starter.unified.server.support.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.unified.server.support.adator.ExecutorCommandAdaptor;

/**
 * 心脏模块分解器
 *
 * @author CH
 */
@Spi("executor")
public class ExecutorModuleResolver implements ModuleResolver{
    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        return ServiceProvider.of(ExecutorCommandAdaptor.class).getNewExtension(commandType).resolve(request);
    }
}

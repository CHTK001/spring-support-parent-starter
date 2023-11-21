package com.chua.starter.unified.server.support.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.unified.server.support.resolver.adator.ConfigCommandAdaptor;

/**
 * 配置模块解析器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("config")
public class ConfigModuleResolver implements ModuleResolver{


    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        return ServiceProvider.of(ConfigCommandAdaptor.class).getNewExtension(commandType).resolve(request);
    }
}
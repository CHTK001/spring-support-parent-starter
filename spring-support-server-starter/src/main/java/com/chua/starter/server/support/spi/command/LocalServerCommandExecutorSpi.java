package com.chua.starter.server.support.spi.command;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerCommandExecutorSpi;
import com.chua.starter.server.support.util.ServerCommandSupport;

/**
 * 本机命令执行 SPI。
 */
@Spi({"local", "LOCAL"})
public class LocalServerCommandExecutorSpi implements ServerCommandExecutorSpi {

    @Override
    public ServerCommandSupport.CommandResult execute(ServerHost host, String command) throws Exception {
        return ServerCommandSupport.runLocal(host, command);
    }
}

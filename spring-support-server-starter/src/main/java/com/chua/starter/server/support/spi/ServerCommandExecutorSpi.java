package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.util.ServerCommandSupport;

/**
 * 服务器命令执行 SPI。
 *
 * <p>协议实现保持无状态，连接参数统一通过 {@link ServerHost} 传入，
 * 避免主机配置变更时需要重建 SPI 实例。
 */
public interface ServerCommandExecutorSpi {

    /**
     * 在目标服务器上执行命令并返回标准化结果。
     */
    ServerCommandSupport.CommandResult execute(ServerHost host, String command) throws Exception;
}

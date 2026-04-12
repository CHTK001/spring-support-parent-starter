package com.chua.starter.server.support.spi.command;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerCommandExecutorSpi;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.chua.winrm.support.client.WinRmExecClient;

/**
 * WinRM 命令执行 SPI。
 */
@Spi({"winrm", "WINRM"})
public class WinRmServerCommandExecutorSpi implements ServerCommandExecutorSpi {

    @Override
    public ServerCommandSupport.CommandResult execute(ServerHost host, String command) throws Exception {
        WinRmExecClient client = new WinRmExecClient(toClientSetting(host, 5985));
        try {
            client.connect();
            var result = client.executeCommand(command);
            return new ServerCommandSupport.CommandResult(result.isSuccess(), result.getExitCode(), result.getFullOutput());
        } finally {
            client.closeQuietly();
        }
    }

    /**
     * 把服务器配置转换成 WinRM 客户端连接参数。
     */
    private ClientSetting toClientSetting(ServerHost host, int defaultPort) {
        return ClientSetting.builder()
                .host(host.getHost())
                .port(host.getPort() == null ? defaultPort : host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .build();
    }
}

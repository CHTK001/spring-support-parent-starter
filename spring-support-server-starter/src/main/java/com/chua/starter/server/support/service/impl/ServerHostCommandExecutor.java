package com.chua.starter.server.support.service.impl;

import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.ssh.support.client.LinuxExecClient;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.chua.winrm.support.client.WinRmExecClient;
import org.springframework.stereotype.Service;

@Service
public class ServerHostCommandExecutor {

    public ServerCommandSupport.CommandResult execute(ServerHost host, String command) throws Exception {
        if ("LOCAL".equalsIgnoreCase(host.getServerType())) {
            return ServerCommandSupport.runLocal(host, command);
        }
        if ("WINRM".equalsIgnoreCase(host.getServerType())) {
            WinRmExecClient client = new WinRmExecClient(toClientSetting(host, 5985));
            try {
                client.connect();
                var result = client.executeCommand(command);
                return new ServerCommandSupport.CommandResult(result.isSuccess(), result.getExitCode(), result.getFullOutput());
            } finally {
                client.closeQuietly();
            }
        }
        LinuxExecClient client = new LinuxExecClient(toClientSetting(host, 22));
        try {
            client.connect();
            var result = client.executeCommand(command);
            return new ServerCommandSupport.CommandResult(result.isSuccess(), result.getExitCode(), result.getFullOutput());
        } finally {
            client.closeQuietly();
        }
    }

    private ClientSetting toClientSetting(ServerHost host, int defaultPort) {
        return ClientSetting.builder()
                .host("LOCAL".equalsIgnoreCase(host.getServerType()) ? "127.0.0.1" : host.getHost())
                .port(host.getPort() == null ? defaultPort : host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .build();
    }
}

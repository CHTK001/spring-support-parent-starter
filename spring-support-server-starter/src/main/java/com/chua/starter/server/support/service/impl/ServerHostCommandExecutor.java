package com.chua.starter.server.support.service.impl;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerCommandExecutorSpi;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServerHostCommandExecutor {

    private final com.chua.common.support.core.spi.ServiceProvider<ServerCommandExecutorSpi> provider =
            ServiceProvider.of(ServerCommandExecutorSpi.class);

    /**
     * 根据服务器协议委派到对应 SPI 执行命令。
     */
    public ServerCommandSupport.CommandResult execute(ServerHost host, String command) throws Exception {
        ServerCommandExecutorSpi executor = resolveExecutor(host);
        if (executor == null) {
            throw new IllegalStateException("未找到服务器命令执行 SPI: " + resolveType(host));
        }
        return executor.execute(host, command);
    }

    /**
     * 获取协议对应的命令执行器，缺省回落到 local。
     */
    private ServerCommandExecutorSpi resolveExecutor(ServerHost host) {
        ServerCommandExecutorSpi executor = provider.getExtension(resolveType(host));
        return executor != null ? executor : provider.getExtension("local");
    }

    /**
     * 标准化 SPI 名称，避免协议大小写影响查找。
     */
    private String resolveType(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getServerType())) {
            return "local";
        }
        return host.getServerType().trim().toLowerCase(Locale.ROOT);
    }
}

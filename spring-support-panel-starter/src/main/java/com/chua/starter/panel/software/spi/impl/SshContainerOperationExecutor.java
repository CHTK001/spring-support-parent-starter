package com.chua.starter.panel.software.spi.impl;

import com.chua.starter.panel.software.spi.ContainerOperationExecutor;
import java.util.function.Consumer;

public class SshContainerOperationExecutor implements ContainerOperationExecutor {
    private String host;
    private int port;
    private String username;

    public void configure(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    @Override
    public String execute(String command, Consumer<String> lineConsumer) {
        return "";
    }
}

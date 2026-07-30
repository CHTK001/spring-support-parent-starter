package com.chua.starter.panel.software.spi.impl;

import com.chua.starter.panel.software.spi.ContainerOperationExecutor;
import java.util.function.Consumer;

public class DockerContainerOperationExecutor implements ContainerOperationExecutor {
    private String host;
    private int port;

    public void configure(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String execute(String command, Consumer<String> lineConsumer) {
        return "";
    }
}

package com.chua.starter.panel.software.spi;

import java.util.function.Consumer;

@FunctionalInterface
public interface ContainerOperationExecutor {
    String execute(String command, Consumer<String> lineConsumer);
}

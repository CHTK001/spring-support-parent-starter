package com.chua.starter.soft.support.service;

import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.spi.SoftCommandObserver;

public interface SoftTargetCommandExecutor {

    boolean supports(String targetType);

    SoftOperationResult execute(SoftTarget target, String command) throws Exception;

    default SoftOperationResult execute(SoftTarget target, String command, SoftCommandObserver observer) throws Exception {
        SoftOperationResult result = execute(target, command);
        if (observer == null || result == null || result.getOutput() == null) {
            return result;
        }
        String[] lines = result.getOutput().split("\\R");
        for (String line : lines) {
            if (!line.isBlank()) {
                observer.onStdout(line);
            }
        }
        return result;
    }

    default SoftOperationResult executeBackground(
            SoftTarget target,
            String command,
            String stdoutPath,
            String stderrPath
    ) throws Exception {
        return execute(target, command);
    }

    String readFile(SoftTarget target, String path) throws Exception;

    void writeFile(SoftTarget target, String path, String content) throws Exception;
}

package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalSoftTargetCommandExecutor implements SoftTargetCommandExecutor {

    @Override
    public boolean supports(String targetType) {
        return "LOCAL".equalsIgnoreCase(targetType);
    }

    @Override
    public SoftOperationResult execute(SoftTarget target, String command) throws Exception {
        return SoftCommandSupport.runLocal(SoftCommandSupport.wrapShellCommand(System.getProperty("os.name"), command));
    }

    @Override
    public SoftOperationResult execute(SoftTarget target, String command, SoftCommandObserver observer) throws Exception {
        return SoftCommandSupport.runLocal(
                SoftCommandSupport.wrapShellCommand(System.getProperty("os.name"), command),
                observer,
                !SoftCommandSupport.isWindows(System.getProperty("os.name"))
        );
    }

    @Override
    public SoftOperationResult executeBackground(
            SoftTarget target,
            String command,
            String stdoutPath,
            String stderrPath
    ) throws Exception {
        if (!SoftCommandSupport.isWindows(System.getProperty("os.name"))) {
            return execute(target, command);
        }
        return SoftCommandSupport.runLocalBackground(
                SoftCommandSupport.wrapShellCommand(System.getProperty("os.name"), command),
                stdoutPath,
                stderrPath
        );
    }

    @Override
    public String readFile(SoftTarget target, String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Override
    public void writeFile(SoftTarget target, String path, String content) throws Exception {
        Path targetPath = Path.of(path);
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Files.writeString(targetPath, content == null ? "" : content, StandardCharsets.UTF_8);
    }
}

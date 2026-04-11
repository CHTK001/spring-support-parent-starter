package com.chua.starter.soft.support.spi.impl;

import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.common.support.network.protocol.client.ExecClient;
import com.chua.ssh.support.client.LinuxExecClient;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SshSoftTargetCommandExecutor implements SoftTargetCommandExecutor {

    @Override
    public boolean supports(String targetType) {
        return "SSH".equalsIgnoreCase(targetType);
    }

    @Override
    public SoftOperationResult execute(SoftTarget target, String command) throws Exception {
        LinuxExecClient client = new LinuxExecClient(toClientSetting(target, 22));
        try {
            client.connect();
            var result = client.executeCommand(command);
            return SoftOperationResult.builder()
                    .success(result.isSuccess())
                    .accepted(result.isSuccess())
                    .finished(true)
                    .exitCode(result.getExitCode())
                    .command(command)
                    .message(result.isSuccess() ? "执行成功" : "执行失败")
                    .output(result.getFullOutput())
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public SoftOperationResult execute(SoftTarget target, String command, SoftCommandObserver observer) throws Exception {
        LinuxExecClient client = new LinuxExecClient(toClientSetting(target, 22));
        StringBuilder output = new StringBuilder();
        AtomicInteger exitCode = new AtomicInteger(-1);
        try {
            client.connect();
            client.executeCommandWithCallback(command, new ExecClient.ExecOutputCallback() {
                @Override
                public void onOutput(String line) {
                    appendLine(output, line);
                    if (observer != null) {
                        observer.onStdout(line);
                    }
                }

                @Override
                public void onError(String line) {
                    appendLine(output, line);
                    if (observer != null) {
                        observer.onStderr(line);
                    }
                }

                @Override
                public void onComplete(int code) {
                    exitCode.set(code);
                }

                @Override
                public void onException(Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            return SoftOperationResult.builder()
                    .success(exitCode.get() == 0)
                    .accepted(exitCode.get() == 0)
                    .finished(true)
                    .exitCode(exitCode.get())
                    .command(command)
                    .message(exitCode.get() == 0 ? "执行成功" : "执行失败")
                    .output(output.toString())
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public String readFile(SoftTarget target, String path) throws Exception {
        SftpClient client = new SftpClient(toClientSetting(target, 22));
        try {
            client.connect();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            client.downloadFile(path, outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public void writeFile(SoftTarget target, String path, String content) throws Exception {
        SftpClient client = new SftpClient(toClientSetting(target, 22));
        try {
            client.connect();
            client.uploadFile(new ByteArrayInputStream((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)), path);
        } finally {
            client.closeQuietly();
        }
    }

    private ClientSetting toClientSetting(SoftTarget target, int defaultPort) {
        return ClientSetting.builder()
                .host(target.getHost())
                .port(target.getPort() == null ? defaultPort : target.getPort())
                .username(target.getUsername())
                .password(target.getPassword())
                .build();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (line == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(System.lineSeparator());
        }
        builder.append(line);
    }
}

package com.chua.starter.docker.local;

import com.chua.ssh.support.client.SshClient;

import java.io.IOException;

/**
 * SSH PTY 终端适配器：把 ssh-starter 的 {@link SshClient.TerminalOperation}
 * 适配为本模块的 {@link DockerTerminal} 抽象，供容器 exec 会话统一持有。
 *
 * @author CH
 * @since 4.0.0.52
 */
public class SshDockerTerminal implements DockerTerminal {

    /**
     * ssh-starter 的 PTY 终端操作
     */
    private final SshClient.TerminalOperation terminal;

    /**
     * 构造适配器
     *
     * @param terminal SSH 终端操作
     */
    public SshDockerTerminal(SshClient.TerminalOperation terminal) {
        this.terminal = terminal;
    }

    @Override
    public void send(String command) throws IOException {
        terminal.send(command);
    }

    @Override
    public void sendRaw(String input) throws IOException {
        terminal.sendRaw(input);
    }

    @Override
    public void close() {
        terminal.close();
    }
}

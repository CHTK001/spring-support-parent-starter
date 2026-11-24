package com.chua.starter.docker.local;

import java.io.IOException;

/**
 * Docker 交互式终端抽象。
 *
 * <p>容器 exec 终端有两条实现路径：远程主机走 SSH PTY
 * （{@link SshDockerTerminal} 适配 ssh-starter 的终端操作），
 * 本机走本地进程 {@code docker exec -i}（{@link LocalDockerProcessTerminal}）。
 * 上层服务只依赖本接口，不感知底层通道差异。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface DockerTerminal extends AutoCloseable {

    /**
     * 发送一行命令（自动追加换行）
     *
     * @param command 命令文本
     * @throws IOException 写入失败或终端已关闭
     */
    void send(String command) throws IOException;

    /**
     * 原样发送输入（不追加换行），用于交互按键、粘贴与退格
     *
     * @param input 原始输入
     * @throws IOException 写入失败或终端已关闭
     */
    void sendRaw(String input) throws IOException;

    /**
     * 关闭终端并释放底层会话/进程（幂等）
     */
    @Override
    void close();
}

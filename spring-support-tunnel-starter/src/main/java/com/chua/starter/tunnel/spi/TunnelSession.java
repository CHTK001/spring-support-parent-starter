package com.chua.starter.tunnel.spi;

import java.util.List;

/**
 * 一条已建立的隧道会话。
 *
 * <p>{@link TunnelProvider#open} 成功时返回本对象，编排层只负责持有它、
 * 在停止/更新/删除时调用 {@link #close()}，不关心底层是长连接客户端
 * （ngrok）还是子进程（cloudflared）。</p>
 *
 * <p>实现应当幂等：{@link #close()} 被重复调用不得抛异常，否则编排层的
 * 「先停止再重启」流程会因清理失败而中断。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface TunnelSession extends AutoCloseable {

    /**
     * 公网访问地址（一个隧道可能返回多个，例如同时给出 http / https）。
     *
     * @return 公网地址列表，未知时返回空列表而不是 null
     */
    List<String> publicUrls();

    /**
     * 关闭隧道并释放底层资源。
     */
    @Override
    void close();
}

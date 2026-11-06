package com.chua.starter.tunnel.support;

import com.chua.starter.tunnel.spi.TunnelSession;

import java.util.List;

/**
 * 测试用隧道会话。
 *
 * <p>配合 {@link TestTunnelProvider} 使用：<code>open()</code> 返回本对象，
 * 用于验证会话的生命周期与公网地址的回填。</p>
 *
 * @see TestTunnelProvider
 * @author CH
 * @since 4.0.0.51
 */
public class TestTunnelSession implements TunnelSession {

    private final String id;

    private final List<String> publicUrls;

    /**
     * 创建会话。
     *
     * @param id         映射标识
     * @param publicUrls 公网访问地址
     */
    public TestTunnelSession(String id, List<String> publicUrls) {
        this.id = id;
        this.publicUrls = publicUrls;
    }

    @Override
    public List<String> publicUrls() {
        return publicUrls;
    }

    @Override
    public void close() {
        // 桩实现：关闭为无操作，测试不依赖 CLI 调用。
    }

    @Override
    public String toString() {
        return "[Test] " + id + " " + publicUrls;
    }
}

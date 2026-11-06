package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用隧道提供方。
 *
 * <p>用来代替真实存在的 ngrok / Cloudflare Tunnel：测试环境里没有可执行文件，
 * 也不应有厂商凭据。存根只验证「统一入口 + 编排服务」的公共逻辑，
 * <b>不模拟 CLI 行为</b>（不模拟子进程、不捕获输出）。</p>
 *
 * <p>一个 <code>TestTunnelProvider</code> 对应一种提供方身份；如需注册两个
 * 不同名称的提供方（供白名单裁剪测试），实例化两次即可，
 * 注册表不允许名称重复：</p>
 *
 * <pre>
 *   new TestTunnelProvider("tunnel-test", true)
 * </pre>
 * @author CH
 * @since 4.0.0.51
 */
public class TestTunnelProvider implements TunnelProvider {

    private static final List<String> SUPPORTED = List.of("http", "tcp");

    private static final String DEFAULT_MODE = "http";

    private final String name;

    private final boolean available;

    private final Map<String, Object> capabilities = new LinkedHashMap<>();

    /**
     * 创建提供方。
     *
     * @param name      提供方标识
     * @param available 是否可用（<code>isAvailable</code> 的返回值）
     */
    public TestTunnelProvider(String name, boolean available) {
        this.name = name;
        this.available = available;
        capabilities.put("stub", "test");
        if (!available) {
            capabilities.put("binary", "not-found");
            capabilities.put("available", false);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String displayName() {
        return name + " Test";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public List<String> supportedModes() {
        return SUPPORTED;
    }

    @Override
    public String defaultMode() {
        return DEFAULT_MODE;
    }

    @Override
    public Map<String, Object> capabilities() {
        return capabilities;
    }

    @Override
    public TunnelSession open(TunnelRecord record) {
        if (!available) {
            throw new IllegalStateException("提供方 " + name + " 不可用");
        }
        return new TestTunnelSession(record.getId(), List.of("http://public." + name + ".tunnel"));
    }

    @Override
    public void applyInput(TunnelRecord target, TunnelRecord input) {
        TunnelInputs.requirePort(input);
        TunnelInputs.requireMode(input.getMode(), SUPPORTED, name);
        TunnelInputs.applyCommon(target, input, name);
    }

    @Override
    public String toString() {
        return "[Test] " + name;
    }
}

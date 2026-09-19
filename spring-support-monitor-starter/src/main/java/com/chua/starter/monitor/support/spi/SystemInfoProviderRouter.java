package com.chua.starter.monitor.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provider 路由器：根据主机信息选择 {@link SystemInfoProvider} 实现。
 *
 * <p>按注册顺序匹配，优先级：Local > Agent > Linux/Mac SSH > Windows WinRM。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@RequiredArgsConstructor
public class SystemInfoProviderRouter {

    /** 所有可用的 Provider（Spring 注入，顺序即优先级） */
    private final List<SystemInfoProvider> providers;

    /**
     * 为指定主机选择可用的 Provider。
     *
     * @param host 目标主机
     * @return 匹配到的 Provider
     * @throws IllegalStateException 无任何 Provider 支持该主机
     */
    public SystemInfoProvider route(MsServerHost host) {
        for (SystemInfoProvider p : providers) {
            try {
                if (p.supports(host)) {
                    return p;
                }
            } catch (Exception ignored) {
                // 单个 Provider 判定异常不影响后续匹配
            }
        }
        throw new IllegalStateException("无可用 Provider for host: " + host);
    }
}

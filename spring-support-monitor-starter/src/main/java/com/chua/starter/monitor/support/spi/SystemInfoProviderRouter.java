package com.chua.starter.monitor.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provider 路由器：从 SPI 扩展点中选择主机的采集实现。
 *
 * <p>候选来自 {@link ServiceProvider}，实现在 provider 构造期由 {@code META-INF/extensions}
 * 注册文件与接口同包扫描两条发现路径登记，按 {@code @Spi} 的 order 降序取第一个
 * {@code supports} 的实现：local(400) &gt; agent(300) &gt; ssh(200) &gt; winrm(100)。
 * 新增通道只需提交实现类（或追加注册文件），不必改动本类。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SystemInfoProviderRouter {

    /**
     * 系统信息采集扩展点
     */
    private final ServiceProvider<SystemInfoProvider> extensions = ServiceProvider.of(SystemInfoProvider.class);

    /**
     * 为指定主机选择可用的 Provider。
     *
     * @param host 目标主机
     * @return 匹配到的 Provider
     * @throws IllegalStateException 无任何 Provider 支持该主机
     */
    public SystemInfoProvider route(MsServerHost host) {
        List<String> tried = new ArrayList<>();
        for (SystemInfoProvider provider : ordered()) {
            try {
                if (provider.supports(host)) {
                    return provider;
                }
            } catch (Exception ignored) {
                // 单个 Provider 判定异常不影响后续匹配
            }
            tried.add(provider.getClass().getSimpleName());
        }
        throw new IllegalStateException("无可用 Provider for host: " + host + ", 已尝试: " + tried);
    }

    /**
     * 按优先级取回全部可用 Provider。
     *
     * @return 优先级降序的实例列表，无实现时为空列表
     */
    public List<SystemInfoProvider> ordered() {
        List<SystemInfoProvider> providers = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            SystemInfoProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider != null && seen.add(provider.getClass())) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            log.warn("SystemInfoProvider 扩展点无任何可用实现");
        }
        return providers;
    }
}

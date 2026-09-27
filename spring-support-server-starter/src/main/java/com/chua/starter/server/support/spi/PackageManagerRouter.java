package com.chua.starter.server.support.spi;

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
 * 包管理器 Provider 路由器。
 *
 * <p>候选来自 {@link ServiceProvider}，实现由 {@code META-INF/extensions} 注册文件与
 * 接口同包扫描两条发现路径登记，按 {@code @Spi} 的 order 降序取第一个
 * {@code supports(host)} 为真的实现：dnf(130) &gt; yum(120) &gt; apt(110)，
 * Windows 由 winget(110) 处理。新增包管理器只需提交实现类（或追加注册文件），
 * 不必改动本类。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class PackageManagerRouter {

    /**
     * 包管理器扩展点
     */
    private final ServiceProvider<PackageManagerProvider> extensions =
            ServiceProvider.of(PackageManagerProvider.class);

    /**
     * 为指定主机选择包管理器 Provider。
     *
     * @param host 目标主机
     * @return 匹配到的 Provider
     * @throws IllegalStateException 无任何 Provider 支持该主机
     */
    public PackageManagerProvider route(MsServerHost host) {
        List<String> tried = new ArrayList<>();
        for (PackageManagerProvider provider : ordered()) {
            try {
                if (provider.supports(host)) {
                    return provider;
                }
            } catch (Exception ignored) {
                // 单个 Provider 判定异常不影响后续匹配
            }
            tried.add(provider.getClass().getSimpleName());
        }
        throw new IllegalStateException("无可用包管理器 Provider for host: "
                + (host == null ? "null" : host.getId()) + ", 已尝试: " + tried);
    }

    /**
     * 按优先级取回全部可用 Provider。
     *
     * @return 优先级降序的实例列表，无实现时为空列表
     */
    public List<PackageManagerProvider> ordered() {
        List<PackageManagerProvider> providers = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            PackageManagerProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider != null && seen.add(provider.getClass())) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            log.warn("PackageManagerProvider 扩展点无任何可用实现");
        }
        return providers;
    }
}

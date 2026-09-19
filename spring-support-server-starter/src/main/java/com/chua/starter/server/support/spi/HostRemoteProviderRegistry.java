package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 主机远程操作 提供者注册中心
 * <p>
 * 实现按 {@code @Spi} 注册到 {@link HostRemoteProvider} 扩展点，本类取其全部定义并按
 * {@link HostRemoteProvider#osTypes()} 建索引；部署/启停/实时日志/指标采集/守护探测等入口
 * 统一通过本注册中心解析实现，从而做到「新增一种服务器系统只加一个实现类，上层零改动」。
 * </p>
 *
 * <p>
 * 之所以必须做传输层抽象而不是只抽象命令串：Windows 默认不提供 SSH，
 * 把 cmd/PowerShell 命令塞进 SSH 通道在真实 Windows 主机上根本跑不通。
 * </p>
 *
 * <p>
 * 索引在首次解析时构建：SPI 实例的依赖由 Spring 装配链注入，容器就绪前构建会得到半初始化实例。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class HostRemoteProviderRegistry {

    /**
     * 远程操作扩展点
     */
    private final ServiceProvider<HostRemoteProvider> extensions = ServiceProvider.of(HostRemoteProvider.class);

    /**
     * 系统类型 -> 实现索引 + 兜底实现，首次使用时构建
     */
    private volatile Registry registry;

    /**
     * 按系统类型解析提供者
     *
     * @param osType 系统类型（如 LINUX/WINDOWS）
     * @return Optional，可能为空
     */
    public Optional<HostRemoteProvider> resolve(String osType) {
        Registry current = registry();
        if (osType == null || osType.isBlank()) {
            return Optional.ofNullable(current.fallback());
        }
        HostRemoteProvider provider = current.index().get(osType.trim().toUpperCase(Locale.ROOT));
        if (provider != null) {
            return Optional.of(provider);
        }
        if (current.fallback() != null) {
            log.warn("[remote] 系统类型 {} 未注册专用实现，回退兜底实现 {}",
                    osType, current.fallback().getClass().getSimpleName());
            return Optional.of(current.fallback());
        }
        return Optional.empty();
    }

    /**
     * 按主机解析提供者
     *
     * @param host 主机
     * @return Optional，可能为空
     */
    public Optional<HostRemoteProvider> resolve(MsServerHost host) {
        if (host == null) {
            return Optional.empty();
        }
        return resolve(host.getServerHostOsType());
    }

    /**
     * 按主机解析提供者，解析不到时抛出带明确指引的异常
     * <p>
     * 与 {@link #resolve(MsServerHost)} 的区别：用于「必须有实现才能继续」的链路，
     * 报错信息直接指出该补哪种系统的实现类，避免部署时出现难以定位的空指针。
     * </p>
     *
     * @param host 主机
     * @return 提供者，永不为 null
     * @throws IllegalStateException 无任何可用实现时抛出
     */
    public HostRemoteProvider require(MsServerHost host) {
        return resolve(host).orElseThrow(() -> new IllegalStateException(
                "无可用的远程操作实现: osType=" + (host == null ? "null" : host.getServerHostOsType())
                        + "，请新增 HostRemoteProvider 实现并声明 osTypes()"));
    }

    /**
     * 按系统类型解析提供者，解析不到时抛出带明确指引的异常
     *
     * @param osType 系统类型
     * @return 提供者，永不为 null
     * @throws IllegalStateException 无任何可用实现时抛出
     */
    public HostRemoteProvider require(String osType) {
        return resolve(osType).orElseThrow(() -> new IllegalStateException(
                "无可用的远程操作实现: osType=" + osType
                        + "，请新增 HostRemoteProvider 实现并声明 osTypes()"));
    }

    /**
     * 解析系统类型对应的命令 shell 方言
     * <p>
     * 供「AI 生成启停/检测脚本」「高危命令审计」等入口使用：方言必须与
     * {@link HostRemoteProvider#exec} 的通道一致，否则生成出来的脚本执行不了。
     * </p>
     *
     * @param osType 系统类型（如 LINUX/WINDOWS），可为空
     * @return shell 方言，解析不到时按 POSIX 兜底
     */
    public HostShellDialect shellDialectOf(String osType) {
        return resolve(osType).map(HostRemoteProvider::shellDialect).orElse(HostShellDialect.POSIX);
    }

    /**
     * 已注册的系统类型与实现（诊断/运维排查用）
     *
     * @return 系统类型 -> 实现类名
     */
    public Map<String, String> describe() {
        Registry current = registry();
        Map<String, String> out = new LinkedHashMap<>();
        current.index().forEach((type, provider) -> out.put(type, provider.getClass().getSimpleName()));
        if (current.fallback() != null) {
            out.put("*", current.fallback().getClass().getSimpleName() + "(fallback)");
        }
        return out;
    }

    /**
     * 取注册结果，必要时构建。
     *
     * @return 索引与兜底实现
     */
    private Registry registry() {
        Registry current = registry;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (registry == null) {
                registry = build();
            }
            return registry;
        }
    }

    /**
     * 遍历扩展点定义建索引，同一实现类的多个别名只登记一次。
     *
     * @return 索引与兜底实现
     */
    private Registry build() {
        Map<String, HostRemoteProvider> index = new HashMap<>();
        HostRemoteProvider fallback = null;
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            HostRemoteProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            for (String osType : provider.osTypes()) {
                if (osType == null || osType.isBlank()) {
                    continue;
                }
                HostRemoteProvider exists = index.put(osType.trim().toUpperCase(Locale.ROOT), provider);
                if (exists != null) {
                    log.warn("[remote] 系统类型 {} 存在多个实现，后注册者生效: {} 覆盖 {}",
                            osType, provider.getClass().getSimpleName(), exists.getClass().getSimpleName());
                }
            }
            if (provider.fallback() && fallback == null) {
                fallback = provider;
            }
            log.info("[remote] 注册远程操作 Provider: osTypes={}, fallback={}, impl={}",
                    provider.osTypes(), provider.fallback(), provider.getClass().getSimpleName());
        }
        if (index.isEmpty() && fallback == null) {
            log.warn("[remote] HostRemoteProvider 扩展点无任何实现，部署/启停/日志链路不可用");
        }
        return new Registry(Collections.unmodifiableMap(index), fallback);
    }

    /**
     * 一次构建结果：系统类型索引与兜底实现。
     *
     * @param index    系统类型 -> 实现
     * @param fallback 兜底实现，可为 null
     */
    private record Registry(Map<String, HostRemoteProvider> index, HostRemoteProvider fallback) {
    }
}

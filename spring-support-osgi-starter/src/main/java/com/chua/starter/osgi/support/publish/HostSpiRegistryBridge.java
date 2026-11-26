// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.osgi.support.publish;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.spi.definition.ServiceDefinitionUtils;
import com.chua.common.support.spi.resolver.OsgiServiceResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OSGi 服务注册表 → 宿主 {@link ServiceProvider} 入口桥。
 *
 * <p>{@link BundleSpiAutoPublisher} 把 bundle 中的 {@code @Spi} 实现发布为 OSGi
 * 服务之后，本桥把同一实例同步登记进宿主侧的
 * {@code ServiceProvider.of(服务接口)}，让既有消费入口
 * （如 {@code NativeServices.get(type)}、
 * {@code ServiceProvider.of(type).getExtension(null)}）无需感知 OSGi
 * 即可取到 bundle 提供的实现（如 RDP 原生桥 {@code RdpNativeSpi}）。</p>
 *
 * <p>类空间契约：仅当服务接口类型能由宿主类空间解析出<b>同一个</b> {@code Class}
 * 时才登记（bundle 经 Import / {@code DynamicImport-Package: *} 共享宿主接口）；
 * bundle 私有副本接口对宿主不可见，跳过登记，避免向宿主注册宿主无法消费的类型。</p>
 *
 * <p>登记的 {@link ServiceDefinition} 以 {@link OsgiServiceResolver} 作为
 * finderType 标记、以「bundle 符号名$实现类全限定名」为唯一名称；bundle
 * STOPPED / UPDATED / UNINSTALLED 时按名称精确摘除，随插件存亡，不残留幽灵实现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
final class HostSpiRegistryBridge {

    /**
     * 按 bundle 符号名跟踪已登记进宿主 ServiceProvider 的服务
     * （接口类型 + 唯一名称），用于随 bundle 生命周期精确反注册。
     */
    private final ConcurrentMap<String, Set<HostRegistration>> registrationsByBundle = new ConcurrentHashMap<>();

    /**
     * 把 bundle 发布的 SPI 实现登记进宿主 ServiceProvider。
     *
     * <p>幂等：同一 bundle 的同一（接口，实现）对重复登记只会生效一次。</p>
     *
     * @param symbolicName bundle 符号名
     * @param type         服务接口类型（bundle 类空间解析出的 Class）
     * @param instance     服务实例
     */
    void register(String symbolicName, Class<?> type, Object instance) {
        if (symbolicName == null || symbolicName.isBlank() || type == null || instance == null) {
            return;
        }
        if (!hostVisible(type)) {
            // bundle 私有副本接口：宿主类空间解析不出同一 Class，登记了也无法被宿主消费
            log.debug("[OSGi] bundle {} 接口 {} 非宿主可见（bundle 私有副本），跳过宿主 SPI 登记",
                    symbolicName, type.getName());
            return;
        }
        String name = registrationName(symbolicName, instance.getClass());
        if (!markRegistered(symbolicName, type, name)) {
            // 已登记过，幂等返回
            return;
        }
        try {
            ServiceProvider.of(type).register(buildDefinition(type, instance, name));
        } catch (Throwable ex) {
            unmarkRegistered(symbolicName, type, name);
            log.warn("[OSGi] bundle {} SPI 服务登记进宿主 ServiceProvider 失败: {} - {}",
                    symbolicName, type.getName(), ex.getMessage());
        }
    }

    /**
     * 摘除指定 bundle 登记进宿主 ServiceProvider 的全部 SPI 实现（幂等）。
     *
     * @param symbolicName bundle 符号名
     */
    void unregister(String symbolicName) {
        if (symbolicName == null || symbolicName.isBlank()) {
            return;
        }
        Set<HostRegistration> registrations = registrationsByBundle.remove(symbolicName);
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        for (HostRegistration registration : registrations) {
            try {
                ServiceProvider.of(registration.type())
                        .unregister(registration.name(), OsgiServiceResolver.class);
            } catch (Throwable ex) {
                log.warn("[OSGi] bundle {} 宿主 SPI 反注册失败: {} - {}",
                        symbolicName, registration.name(), ex.getMessage());
            }
        }
    }

    /**
     * 摘除全部 bundle 的宿主 SPI 登记（宿主关闭时兜底清理）。
     */
    void unregisterAll() {
        for (String symbolicName : registrationsByBundle.keySet().toArray(new String[0])) {
            unregister(symbolicName);
        }
    }

    /**
     * 构造宿主侧服务定义。
     *
     * @param type     服务接口类型
     * @param instance 服务实例
     * @param name     唯一名称（bundle 符号名$实现类全限定名，大写）
     * @return 服务定义
     */
    private ServiceDefinition buildDefinition(Class<?> type, Object instance, String name) {
        ServiceDefinition definition = ServiceDefinitionUtils.buildDefinitionAlias(type,
                OsgiServiceResolver.class, instance, instance.getClass(), null, name, 0);
        definition.setLoaded(true);
        definition.setLoadTime(System.currentTimeMillis());
        return definition;
    }

    /**
     * 接口类型是否宿主可见（宿主类空间能解析出同一个 Class）。
     *
     * @param type 服务接口类型
     * @return 宿主可见返回 {@code true}
     */
    private boolean hostVisible(Class<?> type) {
        ClassLoader hostClassLoader = HostSpiRegistryBridge.class.getClassLoader();
        if (hostClassLoader == null) {
            return true;
        }
        try {
            return Class.forName(type.getName(), false, hostClassLoader) == type;
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * 构造登记唯一名称。
     *
     * @param symbolicName bundle 符号名
     * @param implClass    实现类
     * @return 唯一名称（大写，与 ServiceProvider 名称口径一致）
     */
    private String registrationName(String symbolicName, Class<?> implClass) {
        return (symbolicName + "$" + implClass.getName()).toUpperCase();
    }

    /**
     * 记录登记关系；返回 {@code false} 表示该（接口，名称）对已登记过。
     *
     * @param symbolicName bundle 符号名
     * @param type         服务接口类型
     * @param name         唯一名称
     * @return 首次登记返回 {@code true}
     */
    private boolean markRegistered(String symbolicName, Class<?> type, String name) {
        return registrationsByBundle
                .computeIfAbsent(symbolicName, key -> ConcurrentHashMap.newKeySet())
                .add(new HostRegistration(type, name));
    }

    /**
     * 撤销登记关系（登记进 ServiceProvider 失败时回滚跟踪记录）。
     *
     * @param symbolicName bundle 符号名
     * @param type         服务接口类型
     * @param name         唯一名称
     */
    private void unmarkRegistered(String symbolicName, Class<?> type, String name) {
        Set<HostRegistration> registrations = registrationsByBundle.get(symbolicName);
        if (registrations != null) {
            registrations.remove(new HostRegistration(type, name));
        }
    }

    /**
     * 宿主登记记录：服务接口类型 + 唯一名称。
     */
    private record HostRegistration(Class<?> type, String name) {
    }
}
